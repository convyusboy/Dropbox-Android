package com.dropboxandridho;

import java.io.File;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.dropbox.android.sample.R;
import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.android.AuthActivity;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;

//mApi.getThumbnail(path, mFos, ThumbSize.BESTFIT_960x640,
//ThumbFormat.JPEG, null);

public class DropboxActivity extends Activity implements OnClickListener {
    private static final String TAG = "DropboxActivity";
    private Log log;
    
    SharedPreferences sp;
    Drawable mDrawable;

    ///////////////////////////////////////////////////////////////////////////
    //                      Your app-specific settings.                      //
    ///////////////////////////////////////////////////////////////////////////

    final static private String APP_KEY = "guxbk7au0mvvo1f";
    final static private String APP_SECRET = "ml72ovb1x07u6k1";

    ///////////////////////////////////////////////////////////////////////////
    //                      End app-specific settings.                       //
    ///////////////////////////////////////////////////////////////////////////

    // You don't need to change these, leave them alone.
    final static private String ACCOUNT_PREFS_NAME = "prefs";
    final static private String ACCESS_KEY_NAME = "ACCESS_KEY";
    final static private String ACCESS_SECRET_NAME = "ACCESS_SECRET";

    private static final boolean USE_OAUTH1 = false;

    DropboxAPI<AndroidAuthSession> mApi;

    private boolean mLoggedIn;

    // Android widgets
    private Button mAuth;
    private LinearLayout mDisplay;
    private Button mPicture;
    private Button mBrowse;
    private LinearLayout mContainer;

    private ImageView mImage;

    private final String PHOTO_DIR = "/Photos/";

    final static private int NEW_PICTURE = 1;
    private String mCameraFileName;

    private final Handler handler = new Handler() {
        public void handleMessage(Message msg) {
 
            ArrayList<String> result = msg.getData().getStringArrayList("data");
            
            if (result.size() > 0)
            	saveArray(result,"browse",DropboxActivity.this.getApplicationContext());            	

            result = loadArray("browse",DropboxActivity.this.getApplicationContext());
            for (String fileName : result) {
                Log.i("ListFiles", fileName);
 
                TextView tv = new TextView(DropboxActivity.this);
                
               	tv.setText(fileName);
               	final String fPath = fileName;
                tv.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						mDrawable = Drawable.createFromPath(DropboxActivity.this.getApplicationContext().getCacheDir().getAbsolutePath()+"/"+fPath);						
			            mImage.setImageDrawable(mDrawable);
			            log.d(TAG,DropboxActivity.this.getApplicationContext().getCacheDir().getAbsolutePath()+"/"+fPath);
					}
				});
 
                mContainer.addView(tv);
            }
        }
    };
    
    public boolean saveArray(ArrayList<String> result, String arrayName, Context mContext) {   
        SharedPreferences prefs = mContext.getSharedPreferences("preferencename", 0);  
        SharedPreferences.Editor editor = prefs.edit();  
        editor.putInt(arrayName +"_size", result.size());  
        for(int i=0;i<result.size();i++)  
            editor.putString(arrayName + "_" + i, result.get(i));  
        return editor.commit();  
    } 
    
    public ArrayList<String> loadArray(String arrayName, Context mContext) {  
        SharedPreferences prefs = mContext.getSharedPreferences("preferencename", 0);  
        int size = prefs.getInt(arrayName + "_size", 0);  
        ArrayList<String> array = new ArrayList<String>();  
        for(int i=0;i<size;i++)  
            array.add(prefs.getString(arrayName + "_" + i, null));  
        return array;  
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mCameraFileName = savedInstanceState.getString("mCameraFileName");
        }

        // We create a new AuthSession so that we can use the Dropbox API.
        AndroidAuthSession session = buildSession();
        mApi = new DropboxAPI<AndroidAuthSession>(session);

        // Basic Android widgets
        setContentView(R.layout.main);

        checkAppKeySetup();
        
        mContainer = (LinearLayout)findViewById(R.id.container_files);
        
        // This is the button to login and logout
        mAuth = (Button)findViewById(R.id.auth_button);
        mAuth.setOnClickListener(this);

        mDisplay = (LinearLayout)findViewById(R.id.logged_in_display);

        // This is where a photo is displayed
        mImage = (ImageView)findViewById(R.id.image_view);

        // This is the button to take a photo
        mPicture = (Button)findViewById(R.id.picture_button);
        mPicture.setOnClickListener(this);

        // This is the button to take a photo
        mBrowse = (Button)findViewById(R.id.browse_button);
        mBrowse.setOnClickListener(this);

        // Display the proper UI state if logged in or not
        setLoggedIn(mApi.getSession().isLinked());

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("mCameraFileName", mCameraFileName);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AndroidAuthSession session = mApi.getSession();

        // The next part must be inserted in the onResume() method of the
        // activity from which session.startAuthentication() was called, so
        // that Dropbox authentication completes properly.
        if (session.authenticationSuccessful()) {
            try {
                // Mandatory call to complete the auth
                session.finishAuthentication();

                // Store it locally in our app for later use
                storeAuth(session);
                setLoggedIn(true);
            } catch (IllegalStateException e) {
                showToast("Couldn't authenticate with Dropbox:" + e.getLocalizedMessage());
                Log.i(TAG, "Error authenticating", e);
            }
        }
    }

    // This is what gets called on finishing a media piece to import
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (requestCode == NEW_PICTURE && resultCode == RESULT_OK && null != data) {
            Uri selectedImage = data.getData();
            String[] filePathColumn = { MediaStore.Images.Media.DATA };
 
            Cursor cursor = getContentResolver().query(selectedImage,
                    filePathColumn, null, null, null);
            cursor.moveToFirst();
 
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            cursor.close();
                         
            File file = new File(picturePath);
            
            if (selectedImage != null) {
            	UploadPicture upload = new UploadPicture(this, mApi, PHOTO_DIR, file);
                upload.execute();
                mImage.setImageBitmap(BitmapFactory.decodeFile(picturePath));
            }
        }
    }

    private void logOut() {
        // Remove credentials from the session
        mApi.getSession().unlink();

        // Clear our stored keys
        clearKeys();
        // Change UI state to display logged out version
        setLoggedIn(false);
    }

    /**
     * Convenience function to change UI state based on being logged in
     */
    private void setLoggedIn(boolean loggedIn) {
    	mLoggedIn = loggedIn;
    	if (loggedIn) {
    		mAuth.setText("Logout");
            mDisplay.setVisibility(View.VISIBLE);
    	} else {
    		mAuth.setText("Login to Dropbox");
            mDisplay.setVisibility(View.GONE);
            mImage.setImageDrawable(null);
    	}
    }

    private void checkAppKeySetup() {
        // Check to make sure that we have a valid app key
        if (APP_KEY.startsWith("CHANGE") ||
                APP_SECRET.startsWith("CHANGE")) {
            showToast("You must apply for an app key and secret from developers.dropbox.com, and add them to the DBRoulette ap before trying it.");
            finish();
            return;
        }

        // Check if the app has set up its manifest properly.
        Intent testIntent = new Intent(Intent.ACTION_VIEW);
        String scheme = "db-" + APP_KEY;
        String uri = scheme + "://" + AuthActivity.AUTH_VERSION + "/test";
        testIntent.setData(Uri.parse(uri));
        PackageManager pm = getPackageManager();
        if (0 == pm.queryIntentActivities(testIntent, 0).size()) {
            showToast("URL scheme in your app's " +
                    "manifest is not set up correctly. You should have a " +
                    "com.dropbox.client2.android.AuthActivity with the " +
                    "scheme: " + scheme);
            finish();
        }
    }

    private void showToast(String msg) {
        Toast error = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        error.show();
    }

    /**
     * Shows keeping the access keys returned from Trusted Authenticator in a local
     * store, rather than storing user name & password, and re-authenticating each
     * time (which is not to be done, ever).
     */
    private void loadAuth(AndroidAuthSession session) {
        SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        String key = prefs.getString(ACCESS_KEY_NAME, null);
        String secret = prefs.getString(ACCESS_SECRET_NAME, null);
        if (key == null || secret == null || key.length() == 0 || secret.length() == 0) return;

        if (key.equals("oauth2:")) {
            // If the key is set to "oauth2:", then we can assume the token is for OAuth 2.
            session.setOAuth2AccessToken(secret);
        } else {
            // Still support using old OAuth 1 tokens.
            session.setAccessTokenPair(new AccessTokenPair(key, secret));
        }
    }

    /**
     * Shows keeping the access keys returned from Trusted Authenticator in a local
     * store, rather than storing user name & password, and re-authenticating each
     * time (which is not to be done, ever).
     */
    private void storeAuth(AndroidAuthSession session) {
        // Store the OAuth 2 access token, if there is one.
        String oauth2AccessToken = session.getOAuth2AccessToken();
        if (oauth2AccessToken != null) {
            SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
            Editor edit = prefs.edit();
            edit.putString(ACCESS_KEY_NAME, "oauth2:");
            edit.putString(ACCESS_SECRET_NAME, oauth2AccessToken);
            edit.commit();
            return;
        }
        // Store the OAuth 1 access token, if there is one.  This is only necessary if
        // you're still using OAuth 1.
        AccessTokenPair oauth1AccessToken = session.getAccessTokenPair();
        if (oauth1AccessToken != null) {
            SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
            Editor edit = prefs.edit();
            edit.putString(ACCESS_KEY_NAME, oauth1AccessToken.key);
            edit.putString(ACCESS_SECRET_NAME, oauth1AccessToken.secret);
            edit.commit();
            return;
        }
    }

    private void clearKeys() {
        SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        Editor edit = prefs.edit();
        edit.clear();
        edit.commit();
    }

    private AndroidAuthSession buildSession() {
        AppKeyPair appKeyPair = new AppKeyPair(APP_KEY, APP_SECRET);

        AndroidAuthSession session = new AndroidAuthSession(appKeyPair);
        loadAuth(session);
        return session;
    }
    
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.auth_button: 
            // This logs you out if you're logged in, or vice versa
            if (mLoggedIn) {
                logOut();
            } else {
                // Start the remote authentication
                if (USE_OAUTH1) {
                    mApi.getSession().startAuthentication(DropboxActivity.this);
                } else {
                    mApi.getSession().startOAuth2Authentication(DropboxActivity.this);
                }
            }
 
            break;
        case R.id.browse_button: 
        	mContainer.removeAllViews();
            BrowseAllPictures browse = new BrowseAllPictures(DropboxActivity.this, mApi, PHOTO_DIR, handler);
            browse.execute();
 
            break;
        case R.id.picture_button:        	
    		Intent i = new Intent(
                    Intent.ACTION_PICK,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(i, NEW_PICTURE);

            break;
        default:
            break;
        }
    }
}
