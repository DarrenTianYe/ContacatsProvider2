
package com.sprd.providers.contacts;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AccountManagerCallback;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.ContactsContract.RawContacts;
import android.provider.Telephony;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;


import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IIccPhoneBook;

import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.providers.contacts.AccountWithDataSet;
import com.android.providers.contacts.ContactsDatabaseHelper.Tables;
import com.android.providers.contacts.ContactsDatabaseHelper;
import com.android.providers.contacts.R;
import com.sprd.providers.contacts.ContactProxyManager;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
* SPRD:
* 
* @{
*/
class FSA {
    interface Event {
        int ICC_LOADED = 1;
        int FDN_ENABLED = 2;
    }

    interface Action {
        int BOOT_COMPLETED = 8;
        int BOOTSTRAP = 9;

        int REMOVE_ACCOUNT = 10;
        int ADD_ACCOUNT = 11;
        int PURGE_CONTACT = 12;
        int IMPORT_CONTACT = 13;
    }

    interface State {
        int INIT = 0;
        int BOOT_COMPLETED = 1;
        int ACCOUNT_REMOVED = 3;
        int CONTACT_PURGED = 5;
        int ACCOUNT_ADDED = 7;
        int CONTACT_IMPORTED = 9;
    }

    private static final String TAG = FSA.class.getSimpleName();
    private static final boolean DEBUG = Debug.isDebug();
    final static String PHONE_ACCOUNT_TYPE = "sprd.com.android.account.phone";
    final static String SIM_ACCOUNT_TYPE = "sprd.com.android.account.sim";
    final static String USIM_ACCOUNT_TYPE = "sprd.com.android.account.usim";

    private int mEvent;

    private int mPhoneId;
    private Account mAccount = null;

    private int mState = State.INIT;
    private Handler mHandler;
    private Context mContext;
    private TelephonyManager mTelephonyManager;

    private static Map<Integer, FSA> sInstance = new HashMap<Integer, FSA>();

    public synchronized static FSA getInstance(Context context, int phoneId) {
        FSA ret = sInstance.get(phoneId);
        if (ret == null) {
            ret = new FSA(context, phoneId);
            sInstance.put(phoneId, ret);
        }
        return ret;
    }

    private boolean isEventOn(int event) {
        return (mEvent & (1 << event)) == 0 ? false : true;
    }

    // state:
    // init->boot_completed->purge_contact->remove_account->add_account->import_contact
    private FSA(Context context, int phoneId) {
        mPhoneId = phoneId;
        mContext = context.getApplicationContext();
        // mAccount = phoneToAccount(mPhoneId);
        mTelephonyManager = (TelephonyManager) context.getSystemService(TelephonyManager
                .getServiceName(Context.TELEPHONY_SERVICE, phoneId));

        // FDN_ENABLED is initialized to true, we will rely on ICC_LOADED event
        // to reset it to the right state later.
        mEvent |= (1 << Event.FDN_ENABLED);

        HandlerThread handlerThread = new HandlerThread("FSA_" + phoneId,
                Process.THREAD_PRIORITY_LOWEST);
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                int event = msg.what;
                switch (event) {
                    case Event.ICC_LOADED:
                        if (isEventOn(Event.ICC_LOADED)) {
                            onEvent(Event.FDN_ENABLED,
                                    mTelephonyManager.getIccFdnEnabled());
                        }
                        // NOTE: fall-through
                    case Event.FDN_ENABLED: {
                        if (mState == State.ACCOUNT_REMOVED) {
                            if (isEventOn(Event.ICC_LOADED)
                                    && !isEventOn(Event.FDN_ENABLED)) {
                                addAccount();
                                mState = State.ACCOUNT_ADDED;
                                onAction(Action.IMPORT_CONTACT);
                            }
                        }

                        if (mState == State.CONTACT_IMPORTED) {
                            markSyncableIfNecessary();
                        }
                    }
                        break;

                    case Action.BOOT_COMPLETED:
                        if (mState != State.INIT) {
                            return;
                        }
                        mState = State.BOOT_COMPLETED;
                        onAction(Action.PURGE_CONTACT);
                        break;

                    case Action.BOOTSTRAP: {
                        mState = State.INIT;
                        onAction(Action.BOOT_COMPLETED);
                    }
                        break;

                    case Action.PURGE_CONTACT:
                        if (mState != State.BOOT_COMPLETED) {
                            return;
                        }
                        mAccount = ContactAccountManager.getInstance(mContext).getSimAccount(
                                mPhoneId);
                        log("PURGE_CONTACT mAccount = " + mAccount);
                        if (mAccount != null) {
                            ContentResolver cr = mContext.getContentResolver();
                            cr.setIsSyncable(mAccount, ContactsContract.AUTHORITY, 0);
                        }
                        purgeContact();
                        mState = State.CONTACT_PURGED;

                        onAction(Action.REMOVE_ACCOUNT);
                        break;

                    case Action.REMOVE_ACCOUNT:
                        if (mState != State.CONTACT_PURGED) {
                            return;
                        }

                        addPhoneAccount();
                        removeAccount();
                        break;

                    case Action.ADD_ACCOUNT:
                        if (mState != State.ACCOUNT_REMOVED) {
                            return;
                        }
                        if (isEventOn(Event.ICC_LOADED)
                                && !isEventOn(Event.FDN_ENABLED)) {
                            addAccount();
                            mState = State.ACCOUNT_ADDED;
                            onAction(Action.IMPORT_CONTACT);
                        }
                        break;

                    case Action.IMPORT_CONTACT: {
                        if (mState != State.ACCOUNT_ADDED) {
                            return;
                        }
                        if (DEBUG)
                            log("startImport for " + mAccount);
                        updateProviderStatus(mContext, true);
                        ContactProxyManager.getInstance(mContext)
                                .onImport(mAccount);
                        updateProviderStatus(mContext, false);
                        mState = State.CONTACT_IMPORTED;

                        markSyncableIfNecessary();
                    }
                        break;
                    default:
                        break;
                }
            }
        };
        onEvent(Event.ICC_LOADED,
                mTelephonyManager.getSimState() == TelephonyManager.SIM_STATE_READY);
    }

    private static AtomicInteger sImportingCounter = new AtomicInteger(0);

    private static void updateProviderStatus(Context context, boolean importing) {
        int counter = 0;
        if (importing) {
            sImportingCounter.incrementAndGet();
        } else {
            sImportingCounter.decrementAndGet();
        }
        ContentResolver cr = context.getContentResolver();
        do {
            counter = sImportingCounter.get();
            if (DEBUG)
                Log.d(TAG, "sImportingCounter=" + counter);
            ContentValues status = new ContentValues();
            if (counter > 0) {
                status.put(ProviderStatus.STATUS, ProviderStatus.STATUS_IMPORTING);
            } else {
                status.put(ProviderStatus.STATUS, ProviderStatus.STATUS_NORMAL);
            }
            cr.update(ProviderStatus.CONTENT_URI, status, null, null);
        } while (sImportingCounter.get() != counter);
    }

    private void markSyncableIfNecessary() {
        ContentResolver cr = mContext.getContentResolver();
        if (mAccount != null) {
            // int syncable=0;
            // if (isEventOn(Event.ICC_LOADED)
            // &&!isEventOn(Event.FDN_ENABLED)) {
            // syncable=1;
            // }
            int syncable = 1;
            if (mTelephonyManager.getIccFdnEnabled()) {
                syncable = 0;
            }
            if (DEBUG)
                log("setIsSyncable: " + mAccount + " " + syncable);
            cr.setIsSyncable(mAccount, ContactsContract.AUTHORITY, syncable);
        }
    }

    /* *
     * Remove this function and replace it w/ {@link
     * ContactAccountManager.getPhoneAccount}. private Account
     * phoneToAccount(int phoneId) { AccountManager am =
     * AccountManager.get(mContext); Account[] simAccounts = am.getAccounts();
     * ContentResolver cr = mContext.getContentResolver(); Account ret = null;
     * for (Account account : simAccounts) { String slot =
     * am.getUserDataPrivileged(account, "slot"); if (slot != null) { int i =
     * Integer.parseInt(slot); if (i == phoneId) { ret = account; } } }
     * Log.e(TAG, "phoneToAccount:" + phoneId + ":" + ret); return ret; }
     */

    private void purgeContact() {
        if (DEBUG)
            log(">>>purgeContact:" + mPhoneId + ":" + mAccount);
        if (mAccount == null) {
            if (DEBUG)
                log("mAccount is null");
            return;
        }

        Long accountId = ContactsDatabaseHelper.getInstance(mContext).getAccountIdOrNull(
                new AccountWithDataSet(mAccount.name, mAccount.type, null));
        if (accountId == null) {
            return;
        }
        SQLiteDatabase db = ContactsDatabaseHelper.getInstance(mContext).getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL(
                    "DELETE FROM " + Tables.GROUPS + " WHERE account_id = " + accountId);

            db.execSQL(
                    "DELETE FROM " + Tables.RAW_CONTACTS + " WHERE account_id = " + accountId);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        if (DEBUG)
            log("<<<purgeContact:" + mPhoneId);
    }

    private void addAccount() {
        if (DEBUG)
            log("addAccount for " + mPhoneId);
        ContentResolver cr = mContext.getContentResolver();
//        TelephonyManager t = (TelephonyManager) mContext.getSystemService(TelephonyManager
//                .getServiceName(mContext.TELEPHONY_SERVICE, mPhoneId));

        IIccPhoneBook ipb = IIccPhoneBook.Stub.asInterface(
                ServiceManager.getService(TelephonyManager.getServiceName("simphonebook", mPhoneId)));

        boolean isUsim = false;
        boolean isSim = false;
        try {
            if (ipb != null) {
                isUsim = ipb.isApplicationOnIcc(IccCardApplicationStatus.AppType.APPTYPE_USIM.ordinal());
                isSim = ipb.isApplicationOnIcc(IccCardApplicationStatus.AppType.APPTYPE_SIM.ordinal());
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        String accountType = null;
        String accountNameTmpl = null;
        if (isUsim) {
            if (DEBUG)
                log("addAccount: USIM");
            accountType = USIM_ACCOUNT_TYPE;
            accountNameTmpl = "SIM";
        } else if (isSim) {
            if (DEBUG)
                log("addAccount: SIM");
            accountType = SIM_ACCOUNT_TYPE;
            accountNameTmpl = "SIM";
        } else {
            if (DEBUG)
                log("addAccount: can't detetct sim type,assume it to be SIM");
            accountType = SIM_ACCOUNT_TYPE;
            accountNameTmpl = "SIM";
        }

        AccountManager am = AccountManager.get(mContext);
        Account account;

        boolean isSingleSim = TelephonyManager.getPhoneCount() == 1 ? true : false;
        if (isSingleSim) {
            account = new Account(accountNameTmpl, accountType);
        } else {
            account = new Account(accountNameTmpl + (mPhoneId + 1), accountType);
        }

        am.addAccountExplicitly(account, null, makeUserData(mPhoneId, isSingleSim));
        mAccount = account;
        cr.setIsSyncable(account, ContactsContract.AUTHORITY, 0);
    }

    private Bundle makeUserData(int phoneId, boolean isSingleSim) {
        Bundle ret = new Bundle();
        if (!isSingleSim) {
            // `identifier` , as a text, will be drawn directly on the top-right
            // of the contact avatar
            ret.putString("identifier", Integer.toString(phoneId + 1));
        }
        ret.putString("slot", Integer.toString(phoneId));
        // set icc uri
        String iccUri = "";
        String iccGroupUri = "";

        if (isSingleSim) {
            iccUri = "content://icc/adn";
            iccGroupUri = "content://icc/gas";
        } else {
            iccUri = "content://icc/" + phoneId + "/adn";
            iccGroupUri = "content://icc/" + phoneId + "/gas";
        }

        ret.putString("icc_uri", iccUri);
        ret.putString("icc_gas_uri", iccGroupUri);

        // set account restriction
        int nameLength = SimUtils.getSimContactNameLength(phoneId);
        if (nameLength != -1) {
            ret.putString(StructuredName.CONTENT_ITEM_TYPE + "_length",
                    Integer.toString(nameLength));
        }

        int phoneLength = SimUtils.getSimContactPhoneLength(phoneId);
        if (phoneLength != -1) {
            ret.putString(Phone.CONTENT_ITEM_TYPE + "_length", Integer.toString(phoneLength));
        }

        int emailLength = SimUtils.getSimContactEmailLength(phoneId);
        if (emailLength != -1) {
            ret.putString(Email.CONTENT_ITEM_TYPE + "_length", Integer.toString(emailLength));
        }

        int simCardLength = SimUtils.getSimCardLength(phoneId);
        if (simCardLength != -1) {
            ret.putString("capacity", Integer.toString(simCardLength));
        }

        int emailCapacity = SimUtils.getSimContactEmailCapacity(phoneId);
        if (emailCapacity != -1) {
            ret.putString(Email.CONTENT_ITEM_TYPE + "_capacity", Integer.toString(emailCapacity));
        }

        int phoneTypeOverallMax = SimUtils.getSimContactPhoneTypeOverallMax(phoneId);
        if (phoneTypeOverallMax != -1) {
            ret.putString(Phone.CONTENT_ITEM_TYPE + "_typeoverallmax",
                    Integer.toString(phoneTypeOverallMax));
        }

        int emailTypeOverallMax = SimUtils.getSimContactEmailTypeOverallMax(phoneId);
        if (emailTypeOverallMax != -1) {
            ret.putString(Email.CONTENT_ITEM_TYPE + "_typeoverallmax",
                    Integer.toString(emailTypeOverallMax));
        }

        int usimGroupNameMaxLen = SimUtils.getUsimGroupNameMaxLen(phoneId);
        if (usimGroupNameMaxLen != 0) {
            ret.putString(GroupMembership.CONTENT_ITEM_TYPE + "_length",
                    Integer.toString(usimGroupNameMaxLen));
        }

        if (DEBUG)
            log("account: userdata:" + ret.toString());
        return ret;
    }

    private void removeAccount() {
        if (DEBUG)
            log(">>>remove sim account:" + mPhoneId);
        if (mAccount == null) {
            if (DEBUG)
                log("mAccount is null");
            mState = State.ACCOUNT_REMOVED;
            onAction(Action.ADD_ACCOUNT);
        } else {
            AccountManager am = AccountManager.get(mContext);
            am.removeAccount(mAccount, new AccountManagerCallback<Boolean>() {
                public void run(AccountManagerFuture<Boolean> future) {
                    mState = State.ACCOUNT_REMOVED;
                    onAction(Action.ADD_ACCOUNT);
                }
            }, new Handler(Looper.myLooper()));
        }
        if (DEBUG)
            log("<<<remove sim account:" + mPhoneId);
        return;
    }

    private void addPhoneAccount() {
        if (DEBUG)
            log(">>>addPhoneAccount");
        AccountManager am = AccountManager.get(mContext);
        Account[] accounts = am.getAccountsByType(PHONE_ACCOUNT_TYPE);
        if (accounts != null && accounts.length != 0) {
            if (DEBUG)
                log("phone account already exists");
            return;
        }
        Account account = new Account((String) mContext.getResources().getText(R.string.phone),
                PHONE_ACCOUNT_TYPE);
        am.addAccountExplicitly(account, null, null);
        // set is syncable
        ContentResolver cr = mContext.getContentResolver();
        cr.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
        if (DEBUG)
            log("<<<addPhoneAccount");
    }

    public void onEvent(int event, boolean on) {
        if (DEBUG)
            log("onEvent:" + mPhoneId + ":" + event + ":" + on);
        if (on) {
            mEvent |= (1 << event);
        } else {
            mEvent &= ~(1 << event);
        }

        mHandler.sendMessage(mHandler.obtainMessage(event));
    }

    public void onAction(int action) {
        if (DEBUG)
            log("onAction:" + mPhoneId + ":" + action);
        mHandler.sendMessage(mHandler.obtainMessage(action));
    }

    private void log(String msg) {
        Log.d(TAG + mPhoneId, msg);
    }
}

public class SyncSimAccountsReceiver extends BroadcastReceiver {

    private static final String TAG = SyncSimAccountsReceiver.class.getSimpleName();
    private static final boolean DEBUG = Debug.isDebug();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        Log.w(TAG, "action = " + action);
        if (TextUtils.isEmpty(action)) {
            return;
        }

        //context.startService(new Intent(context, SyncSimAccountsWatchdog.class));
        if (action.startsWith(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
            String state = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            int phoneId = intent.getIntExtra(IccCardConstants.INTENT_KEY_PHONE_ID, 0);
            Log.e(TAG, "SIM_STATE_CHANGED: state: " + state + " phoneId: " + phoneId);
            if (!TextUtils.isEmpty(state)) {
                if (IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(state)) {
                    FSA.getInstance(context, phoneId).onEvent(FSA.Event.ICC_LOADED, true);
                } else if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(state)) {
                    FSA.getInstance(context, phoneId).onEvent(FSA.Event.ICC_LOADED, false);
                    FSA.getInstance(context, phoneId).onAction(FSA.Action.BOOTSTRAP);
                } else {
                    FSA.getInstance(context, phoneId).onEvent(FSA.Event.ICC_LOADED, false);
                }
            }
        } else if (action.startsWith("android.intent.action.FDN_STATE_CHANGED")) {
            boolean isFdnEnabled = intent.getBooleanExtra("fdn_status", false);
            int phoneId = intent.getIntExtra("phone_id", 0);
            Log.e(TAG, "FDN_STATE_CHANGED: state: " + isFdnEnabled + " phoneId: " + phoneId);
            FSA.getInstance(context, phoneId).onEvent(FSA.Event.FDN_ENABLED, isFdnEnabled);
        } else if (action.equals("sync_sim_fake_boot_completed")) {
            Log.e(TAG, "sync_sim_fake_boot_completed!!! call remove");
            for (int i = 0; i < TelephonyManager.getPhoneCount(); ++i) {
                FSA.getInstance(context, i).onAction(FSA.Action.BOOT_COMPLETED);
            }
        } else if (action.equals(TelephonyIntents.ACTION_STK_REFRESH_SIM_CONTACTS)) {
            int phoneId = intent.getIntExtra("phone_id", 0);
            FSA.getInstance(context, phoneId).onAction(FSA.Action.BOOTSTRAP);
        } else if (action.equals(TelephonyIntents.ACTION_PHONE_START)) {
            if (DEBUG)
                Log.d(TAG, "syncSim:ACTION_PHONE_START");
            for (int i = 0; i < TelephonyManager.getPhoneCount(); ++i) {
                FSA.getInstance(context, i).onAction(FSA.Action.BOOTSTRAP);
            }
        }
    }
}
/**
 * @}
 */
