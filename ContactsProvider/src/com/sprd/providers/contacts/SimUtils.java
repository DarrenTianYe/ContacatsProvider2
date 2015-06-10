package com.sprd.providers.contacts;

import android.accounts.Account;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Debug;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContacts;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.IccConstants;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
* SPRD:
* 
* @{
*/
public class SimUtils {
    private static final String LOG_TAG = "SimUtils";

    private static final boolean DEBUG = Debug.isDebug();

    private static final String ACCOUNT_TYPE_GOOGLE = "com.google";

    private static final String GOOGLE_MY_CONTACTS_GROUP = "System Group: My Contacts";

    private static final ContentValues sEmptyContentValues = new ContentValues();

    // return -1 on error
    public static int getSimCardLength(int phoneId) {
        int ret = -1;
        try {
            IIccPhoneBook iccIpb = getIccPhoneBook(phoneId);
            if (iccIpb != null) {
                int[] sizes = iccIpb.getAdnRecordsSize(IccConstants.EF_ADN);
                if (sizes != null) {
                    if (sizes.length == 3) {
                        ret = sizes[2];
                    } else if (sizes.length == 2) {
                        ret = sizes[1] / sizes[0];
                    }
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        return ret;
    }

    // return -1 on error
    // FIXME: return 12....
    public static int getSimContactNameLength(int phoneId) {
        int ret = -1;
        try {
            IIccPhoneBook iccIpb = getIccPhoneBook(phoneId);
            if (iccIpb != null) {
                int[] sizes = iccIpb.getAdnRecordsSize(IccConstants.EF_ADN);
                int size = -1;
                if (sizes != null && sizes.length > 0) {
                    size = sizes[0] - 14;
                    if (size < 0) {
                        // get length of sim contactor's Name fail
                        return 12;
                    }
                } else {
                    return 12;
                }
                ret = size;
            } else {
                return 12;
            }
        } catch (RemoteException ex) {
            return 12;
        } catch (SecurityException ex) {
            return 12;
        }

        return ret;
    }

    private static IIccPhoneBook getIccPhoneBook(int phoneId) {
        return IIccPhoneBook.Stub.asInterface(ServiceManager
                .getService(TelephonyManager.getServiceName("simphonebook", phoneId)));
    }

    // return -1 on error, and [0,..] on ok
    public static int getSimContactPhoneLength(int phoneId) {
        int ret = -1;
        IIccPhoneBook iccIpb = getIccPhoneBook(phoneId);
        if (iccIpb != null) {
            try {
                ret = iccIpb.getPhoneNumMaxLen();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        if (ret <= 0) {
            ret = -1;
        }
        return ret;
    }

    // return -1 on error, and [0,..] on ok
    public static int getSimContactEmailLength(int phoneId) {
        int ret = -1;
        IIccPhoneBook iccIpb = getIccPhoneBook(phoneId);
        if (iccIpb != null) {
            try {
                ret = iccIpb.getEmailMaxLen();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        if (ret <= 0) {
            ret = -1;
        }
        return ret;
    }

    // return -1 on error, and [0,..] on ok
    public static int getSimContactEmailCapacity(int phoneId) {
        int[] sizes = null;
        IIccPhoneBook iccIpb = getIccPhoneBook(phoneId);
        if (iccIpb != null) {
            try {
                sizes = iccIpb.getEmailRecordsSize();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        int ret = -1;
        if (sizes != null) {
            ret = sizes[2];
        }
        if (ret < 0) {
            ret = -1;
        }

        return ret;
    }

    public static int getSimContactPhoneTypeOverallMax(int phoneId) {
        IIccPhoneBook iccIpb = getIccPhoneBook(phoneId);
        int ret = 0;
        if (iccIpb != null) {
            try {
                ret = iccIpb.getAnrNum();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        ret++;
        return ret;
    }

    public static int getSimContactEmailTypeOverallMax(int phoneId) {
        IIccPhoneBook iccIpb = getIccPhoneBook(phoneId);
        int ret = 0;
        if (iccIpb != null) {
            try {
                ret = iccIpb.getEmailNum();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        if (DEBUG)
            Log.d(LOG_TAG, "EmailTypeOverallMax = " + ret);
        return ret;
    }

    public static byte[] getSimRecordBytes(String record) {
        byte[] bytes = null;
        if (record == null) {
            record = "";
        }
        try {
            bytes = GsmAlphabet.isAsciiStringToGsm8BitUnpackedField(record);
        } catch (EncodeException e) {
            try {
                bytes = record.getBytes("utf-16be");
            } catch (UnsupportedEncodingException e1) {
            }
        }
        return bytes;
    }

    public static int getUsimGroupNameMaxLen(int phoneId) {
        IIccPhoneBook iccIpb = getIccPhoneBook(phoneId);
        int ret = 0;
        if (iccIpb != null) {
            try {
                ret = iccIpb.getUsimGroupNameMaxLen();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        if (DEBUG)
            Log.d(LOG_TAG, "UsimGroupNameMaxLen = " + ret);
        return ret;
    }

}
/**
 * @}
 */