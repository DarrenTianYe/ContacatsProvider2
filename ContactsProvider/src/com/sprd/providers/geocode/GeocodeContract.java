package com.sprd.providers.geocode;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

/**
 * create the column key.
 */
public final class GeocodeContract {

    /** The authority for the GecodeSearch provider */
    public static final String AUTHORITY = "gecode_location";
    /** A content:// style uri to the authority for the GecodeSearch provider */
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    public static class Geocode {

        public Geocode() {
        }

        public static final String DATABASE_NAME = "gecode.db";

        /**
         * The content:// style URI for this table
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "gecode");

        /**
         * The MIME type of a directory of gecode.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/gecode";

        /**
         * The MIME type of a a single gecode.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/gecode";

        public static final String TABLE_NAME = "gecode";

        /**
         * table column, number、province、city、operator.
         */
        public static final String SEGMENT_NUMBER = "number";

        public static final String SEGMENT_PROVINCE = "province";

        public static final String SEGMENT_CITY = "city";

        public static final String OPERATOR_NAME = "operator";

        /**
         * query the location information.
         * @param number for example 1881049XXXX
         * @return the province and city for example Beijing
         */
        public static String getGeocodedLocation(Context context, String number) {
            String location = "";
            number = PhoneNumberUtils.stripSeparators(number);
            if (TextUtils.isEmpty(number)) {
                return location;
            }

            Cursor cursor = context.getContentResolver().query(Geocode.CONTENT_URI, new String[] {
                    Geocode.SEGMENT_PROVINCE, Geocode.SEGMENT_CITY
            }, number, null, null);

            if (null == cursor) {
                return location;
            }

            cursor.moveToPosition(-1);
            if (cursor.moveToNext()) {
                String procince = cursor.getString(0);
                String city = cursor.getString(1);

                if (procince.equals(city)) { // Municipality
                    location = procince;
                } else {
                    location = procince + city;
                }
            }

            if (null != cursor) {
                cursor.close();
                cursor = null;
            }

            return location;
        }
    }

}
