
package com.sprd.providers.geocode;

import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Debug;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.sprd.providers.geocode.GeocodeContract.Geocode;

/**
 * Operate the database of geocode information.
 */
public class GeocodeProvider extends ContentProvider {

    private static final String TAG = "GeocodeProvider";
    
    private static final boolean DEBUG = Debug.isDebug();

    private GeocodeDatabaseHelper mDbHelper;

    private final static int QUERY_LOCATION = 0;

    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sURIMatcher.addURI(GeocodeContract.AUTHORITY, "gecode", QUERY_LOCATION);
    }

    private final static HashMap<String, String> sGeocodeProjectionMap;
    static {
        sGeocodeProjectionMap = new HashMap<String, String>();
        sGeocodeProjectionMap.put(Geocode.SEGMENT_NUMBER, Geocode.SEGMENT_NUMBER);
        sGeocodeProjectionMap.put(Geocode.SEGMENT_PROVINCE, Geocode.SEGMENT_PROVINCE);
        sGeocodeProjectionMap.put(Geocode.SEGMENT_CITY, Geocode.SEGMENT_CITY);
    }

    OnLoadComplete mLoadCompleteListener = new OnLoadComplete() {
        public void onLoadComplete(Context context) {
            mDbHelper = new GeocodeDatabaseHelper(context);
        }
    };

    private static final String[] NUMBER_SEGMENT = new String[] {
        "17951", "12593", "17911", "10193", "17909", "+86"
    };

    @Override
    public boolean onCreate() {
        if (DEBUG) Log.d(TAG, "onCreate...");
        Context context = getContext();
        if (GeocodeUtils.isSupportSprdGeocode()) {
            GeocodeDatabaseLoader load = new GeocodeDatabaseLoader(context, mLoadCompleteListener);
            load.excute();
        }
        return false;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (mDbHelper == null) {
            Log.e(TAG, "insert database have not been loaded complete!");
            return null;
        }

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        long id = db.insert(Geocode.TABLE_NAME, Geocode.SEGMENT_NUMBER, values);
        if (id > 0) {
            return ContentUris.withAppendedId(uri, id);
        }

        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        if (mDbHelper == null) {
            Log.e(TAG, "query database have not been loaded complete!");
            return null;
        }
        if (selection == null) {
            Log.e(TAG, "selection is null");
            return null;
        }

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        final SQLiteDatabase db = mDbHelper.getReadableDatabase();

        int match = sURIMatcher.match(uri);
        if (DEBUG) Log.d(TAG, "match: " + match);
        switch (match) {
            case QUERY_LOCATION:
                if (DEBUG) Log.d(TAG, "selection: " + selection);
                selection = PhoneNumberUtils.stripSeparators(selection);
                selection = clearIpOrCountryCode(selection);

                int length = selection.length();
                StringBuffer sb = new StringBuffer();
                sb.append("");
                if (selection.startsWith("0") && length > 3) { // fixed number
                    String tmpNumber = selection.substring(1, selection.length());
                    sb.append(Geocode.SEGMENT_NUMBER);
                    sb.append("= '");
                    sb.append(tmpNumber.substring(0, 2));
                    sb.append("' OR ");
                    sb.append(Geocode.SEGMENT_NUMBER);
                    sb.append("= '");
                    sb.append(tmpNumber.substring(0, 3));
                    sb.append("'");
                } else if (selection.startsWith("1") && length >= 11) { // mobile number
                    sb.append(Geocode.SEGMENT_NUMBER);
                    sb.append("=");
                    sb.append(selection.substring(0, 7));
                } else {
                    return null;
                }

                selection = sb.toString();
                qb.setTables(Geocode.TABLE_NAME);
                qb.setProjectionMap(sGeocodeProjectionMap);
                break;
            default:
                throw new IllegalArgumentException("Unknown URL " + uri);
        }
        try {
            return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder, null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (mDbHelper == null) {
            Log.e(TAG, "delete database have not been loaded complete!");
            return 0;
        }

        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (mDbHelper == null) {
            Log.e(TAG, "update database have not been loaded complete!");
            return 0;
        }

        return 0;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    private String clearIpOrCountryCode(String number) {
        for (String segment : NUMBER_SEGMENT) {
            if (number.startsWith(segment)) {
                return number.substring(segment.length(), number.length());
            }
        }

        return number;
    }

}
