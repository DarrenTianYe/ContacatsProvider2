
package com.sprd.providers.contacts;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.TelephonyIntents;
/**
* SPRD:
* 
* @{
*/
public class SyncSimAccountsWatchdog extends Service {

    private static final String TAG = SyncSimAccountsWatchdog.class.getSimpleName();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            bootstrap();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
    }

    private void bootstrap() {
        Log.e(TAG, "SyncSimAccountsWatchdog: bootstrap");
        sendBroadcast(new Intent("sync_sim_fake_boot_completed"));
    }
}
/**
 * @}
 */