
package com.sprd.providers.geocode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.android.providers.contacts.R;
import com.sprd.providers.geocode.GeocodeContract.Geocode;

public class GeocodeDatabaseLoader {
    private final static String TAG = "LoadDatabase";
    private static final boolean DBG = Debug.isDebug();

    private static final int MSG_RESET_COPY = 1;
    private static final int MSG_COMPLETE = 2;

    private static final String PREFEREBCE_NAME = "status";

    private final static String DATA_PATH = "data/data/com.android.providers.contacts/databases";
    private final static String FILE_PATH = "data/data/com.android.providers.contacts/databases"
            + File.separator + Geocode.DATABASE_NAME;

    private OnLoadComplete mOnLoadListener;
    private Context mContext;
    private HandlerThread mHandlerThread;
    private AsyncThread mAsyncThread;

    public GeocodeDatabaseLoader(Context context, OnLoadComplete listener) {
        if (listener == null) {
            throw new NullPointerException("OnLoadComplete can not be null");
        }
        mContext = context;
        mOnLoadListener = listener;
    }

    public void excute() {
        if (DBG)Log.d(TAG, "excute...");
        int msg = MSG_RESET_COPY;

        File file = new File(FILE_PATH);
        if (file != null && file.exists()) {
            boolean status = checkDatabaseOK();
            if (status) {
                if (DBG) Log.d(TAG, "database " + Geocode.DATABASE_NAME + "have exist.");
                msg = MSG_COMPLETE;
            } else {
                Log.w(TAG, "Error, the DB is a broken file, now do delete and reload.");
                msg = MSG_RESET_COPY;
                setDatabaseOk(false);
            }
        }

        mHandler.sendEmptyMessage(msg);
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_COMPLETE:
                    notifyLoadComplete();
                    break;
                case MSG_RESET_COPY:
                    notifyLoadStart();
                    break;
                default:
                    Log.e(TAG, "An error message.");
                    return;
            }
        };
    };

    private void notifyLoadComplete() {
        setDatabaseOk(true);
        if (mHandlerThread != null) {
            mHandlerThread.quit();
            mHandlerThread = null;
        }
        if (mAsyncThread != null) {
            mAsyncThread.removeMessages(0);
            mAsyncThread = null;
        }
        mOnLoadListener.onLoadComplete(mContext);
    }

    private void notifyLoadStart() {
        if (DBG) Log.d(TAG, "now to import Database...");
        if (mHandlerThread == null) {
            mHandlerThread = new HandlerThread("");
            mHandlerThread.start();
            mAsyncThread = new AsyncThread(mHandlerThread.getLooper());
            mAsyncThread.removeMessages(0);
            mAsyncThread.sendEmptyMessage(0);
        }
    }

    private class AsyncThread extends Handler {
        public AsyncThread(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            copyDatabase();
        };
    }

    private void dealWithResult(boolean success) {
        int msg = success ? MSG_COMPLETE : MSG_RESET_COPY;
        long delay = success ? 0 : 30000;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessageDelayed(msg, delay);
    }

    private void copyDatabase() {
        FileOutputStream outStream = null;
        InputStream inputStream = null;
        boolean success = false;
        try {
            File path = new File(DATA_PATH);
            if (null != path || !path.exists()) {
                path.mkdir();
            }

            File file = new File(FILE_PATH);
            if (file != null && file.exists()) {
                boolean s = file.delete();
                if (!s) {
                    Log.e(TAG, "delete file error.");
                    dealWithResult(success);
                    return;
                }
            }
            Log.i(TAG, "start copy database " + Geocode.DATABASE_NAME);
            inputStream = mContext.getResources().openRawResource(R.raw.gecode);
            outStream = new FileOutputStream(FILE_PATH);
            byte[] buffer = new byte[1024 * 1024];
            int count = 0;
            while ((count = inputStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, count);
            }

            outStream.flush();
            success = true;
            Log.i(TAG, "end copy database " + Geocode.DATABASE_NAME);
        } catch (IOException ex) {
            Log.e(TAG, "IOException msg: " + ex.toString());
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
                Log.e(TAG, "close stream err : " + ex.toString());
            }
        }
        dealWithResult(success);
    }

    private boolean checkDatabaseOK() {
        SharedPreferences sp = mContext.getSharedPreferences(PREFEREBCE_NAME, Context.MODE_PRIVATE);
        String status = sp.getString("status", null);
        Log.w(TAG, "database status = " + status);
        return "OK".equals(status);
    }

    private void setDatabaseOk(boolean ok) {
        SharedPreferences sp = mContext.getSharedPreferences(PREFEREBCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putString("status", ok ? "OK" : "ERROR");
        ed.apply();
    }

}

interface OnLoadComplete {
    public void onLoadComplete(Context context);
}
