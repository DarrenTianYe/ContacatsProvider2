package com.sprd.providers.contacts;

import android.content.Context;
import android.os.Debug;
import android.util.Log;
import com.android.providers.contacts.R;
import com.sprd.providers.geocode.GeocodeContract.Geocode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
* SPRD:
* 
* @{
*/
public class LoadDatabase {
    private final static String TAG = "LoadDatabase";
    private static final boolean DEBUG = Debug.isDebug();

    private final static String DATA_PATH = "data/data/com.android.providers.contacts/databases/";

    private OnLoadComplete mOnLoadListener;
    private Context mContext;

    public LoadDatabase(Context context, OnLoadComplete listener) {
        if (listener == null) {
            throw new NullPointerException("OnLoadComplete can not be nunll");
        }

        mContext = context;
        mOnLoadListener = listener;
    }

    public void excute() {
        if (DEBUG)
            Log.d(TAG, "excute...");

        String path = DATA_PATH + Geocode.DATABASE_NAME;
        File file = new File(path);
        if (file != null && file.exists()) {
            if (DEBUG)
                Log.d(TAG, "database " + Geocode.DATABASE_NAME + "have exist.");
            beginCopyDatabase();
            return;
        }

        importDatabase();
    }

    private void beginCopyDatabase() {
        mOnLoadListener.onLoadComplete(mContext);
    }

    /**
     * import database file to data/data/apkpath
     */
    private void importDatabase() {
        if (DEBUG)
            Log.d(TAG, "importDatabase...");
        new CopyDatabaseThread().start();
    }

    class CopyDatabaseThread extends Thread {

        @Override
        public void run() {
            FileOutputStream outStream = null;
            InputStream inputStream = null;
            try {
                File file = new File(DATA_PATH);
                if (null != file || !file.exists()) {
                    file.mkdir();
                }

                if (DEBUG)
                    Log.d(TAG, "start copy database " + Geocode.DATABASE_NAME);

                inputStream = mContext.getResources().openRawResource(R.raw.gecode);
                outStream = new FileOutputStream(DATA_PATH + Geocode.DATABASE_NAME);
                byte[] buffer = new byte[1024 * 1024];
                int count = 0;
                while ((count = inputStream.read(buffer)) != -1) {
                    outStream.write(buffer, 0, count);
                }

                outStream.flush();
                if (DEBUG)
                    Log.d(TAG, "end copy database " + Geocode.DATABASE_NAME);
            } catch (IOException ex) {
                Log.e(TAG, "IOException msg: " + ex.toString());
                deleteFile();
                importDatabase();
                return;
            } catch (Exception ex) {
                Log.e(TAG, "Exception ex: " + ex.toString());
                deleteFile();
                importDatabase();
                return;
            } finally {
                try {
                    if (null != inputStream) {
                        inputStream.close();
                        inputStream = null;
                    }

                    if (null != outStream) {
                        outStream.close();
                        outStream = null;
                    }
                } catch (IOException ex) {
                    Log.e(TAG, "IOException msg: " + ex.toString());
                }
            }

            beginCopyDatabase();
        }

        private void deleteFile() {
            if (DEBUG)
                Log.d(TAG, "deleteFile...");

            File file = new File(DATA_PATH + Geocode.DATABASE_NAME);
            if (null != file && file.exists()) {
                file.delete();
            }
        }
    }

}

interface OnLoadComplete {
    public void onLoadComplete(Context context);
}
/**
 * @}
 */