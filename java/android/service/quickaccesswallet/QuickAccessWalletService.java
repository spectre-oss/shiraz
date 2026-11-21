// SPDX-License-Identifier: GPL-3.0-only
package android.service.quickaccesswallet;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

public abstract class QuickAccessWalletService extends Service {
    public static final String ACTION_VIEW_WALLET =
            "android.service.quickaccesswallet.action.VIEW_WALLET";
    public static final String ACTION_VIEW_WALLET_SETTINGS =
            "android.service.quickaccesswallet.action.VIEW_WALLET_SETTINGS";
    public static final String SERVICE_INTERFACE =
            "android.service.quickaccesswallet.QuickAccessWalletService";
    public static final String SERVICE_META_DATA = "android.quickaccesswallet";

    private final IBinder mBinder = new Binder();

    public QuickAccessWalletService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (!SERVICE_INTERFACE.equals(intent != null ? intent.getAction() : null)) {
            return null;
        }
        return mBinder;
    }

    public abstract void onWalletCardsRequested(GetWalletCardsRequest request,
            GetWalletCardsCallback callback);

    public abstract void onWalletCardSelected(SelectWalletCardRequest request);

    public abstract void onWalletDismissed();

    public final void sendWalletServiceEvent(WalletServiceEvent serviceEvent) {
    }

    public PendingIntent getTargetActivityPendingIntent() {
        return null;
    }
}
