/* SPRD: created */

package com.sprd.providers.geocode;

import android.os.SystemProperties;

public class GeocodeUtils {

    private static final String KEY = "ro.device.support.geocode";
    private static final boolean SUPPORT_SPRD_GROCODE = SystemProperties.getBoolean(KEY, true);

    public static boolean isSupportSprdGeocode() {
        return SUPPORT_SPRD_GROCODE;
    }

    public static boolean isSupportGoogleGeocode() {
        return !isSupportSprdGeocode();
    }
}
