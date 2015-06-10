package com.sprd.providers.contacts;

import android.accounts.Account;
import android.util.Log;
import android.content.Context;
import android.content.ContentValues;
import java.lang.IllegalStateException;
import java.util.ArrayList;

/**
* SPRD:
* 
* @{
*/
public class ContactProxyManager {
    private static ContactProxyManager sInstance;
    private ArrayList<IContactProxy> mProxys = new ArrayList<IContactProxy>();

    private ContactProxyManager(Context context) {
        IContactProxy simContactProxy = new SimContactProxy(context);
        mProxys.add(simContactProxy);
        // add more IContactProxy here
    }

    synchronized public static ContactProxyManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ContactProxyManager(context);
        }
        return sInstance;
    }

    public IContactProxy getProxyForAccount(Account account) {
        for (IContactProxy proxy : mProxys) {
            if (proxy.isMyAccount(account)) {
                return proxy;
            }
        }
        return null;
    }

    public void onImport(Account account) {
        for (IContactProxy proxy : mProxys) {
            if (proxy.isMyAccount(account)) {
                proxy.onImport(account);
                return;
            }
        }
    }

    public ContentValues insert(long rawContactId, Account account) throws IllegalStateException {
        for (IContactProxy proxy : mProxys) {
            if (proxy.isMyAccount(account) && proxy.isMyContact(rawContactId)) {
                return proxy.insert(rawContactId, account);
            }
        }
        return null;
    }

    public void remove(long rawContactId) {
        for (IContactProxy proxy : mProxys) {
            if (proxy.isMyContact(rawContactId)) {
                proxy.remove(rawContactId);
                return;
            }
        }
    }

    public void update(long rawContactId) throws IllegalStateException {
        for (IContactProxy proxy : mProxys) {
            if (proxy.isMyContact(rawContactId)) {
                proxy.update(rawContactId);
                return;
            }
        }
    }

    public ContentValues insertGroup(long groupRowId, ContentValues values, Account account) {
        for (IContactProxy proxy : mProxys) {
            if (proxy.isMyAccount(account) && proxy.isMyContactGroup(groupRowId)) {
                return proxy.insertGroup(groupRowId, values, account);
            }
        }
        return null;
    }

    public void removeGroup(long groupRowId) {
        for (IContactProxy proxy : mProxys) {
            if (proxy.isMyContactGroup(groupRowId)) {
                proxy.removeGroup(groupRowId);
                return;
            }
        }
    }

    public void updateGroup(long groupRowId, ContentValues values) {
        for (IContactProxy proxy : mProxys) {
            if (proxy.isMyContactGroup(groupRowId)) {
                proxy.updateGroup(groupRowId, values);
                return;
            }
        }
    }

    // TODO: rewrite the method, refers to DataRowHandlerForXXX
    public void onDataUpdate(long rawContactId, ContentValues values, String mimeType) {
        for (IContactProxy proxy : mProxys) {
            if (proxy.isMyContact(rawContactId)) {
                proxy.onDataUpdate(rawContactId, values, mimeType);
                return;
            }
        }
    }

    public void addToNonSimContactCache(long rawContactId) {
        for (IContactProxy proxy : mProxys) {
            if (proxy instanceof SimContactProxy) {
                proxy.addToNonSimContactCache(rawContactId);
                return;
            }
        }
    }

}
/**
 * @}
 */