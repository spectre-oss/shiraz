// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.mbms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MbmsDownloadReceiver extends BroadcastReceiver {
    public MbmsDownloadReceiver() {}

    public void onReceive(Context context, Intent intent) {
        setResultCode(1);
    }
}
