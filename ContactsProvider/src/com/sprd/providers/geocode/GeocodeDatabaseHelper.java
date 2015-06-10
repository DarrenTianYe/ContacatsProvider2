package com.sprd.providers.geocode;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.sprd.providers.geocode.GeocodeContract.Geocode;

/**
 * Create database.
 */
public class GeocodeDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "GeocodeDatabaseHelper";
    
    private static final boolean DEBUG = false;

    private static final int DATABASE_VERSION = 777;

    public GeocodeDatabaseHelper(Context context) {
        super(context, Geocode.DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        if (DEBUG) Log.d(TAG, "onCreate");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

}
