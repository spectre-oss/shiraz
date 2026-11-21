// SPDX-License-Identifier: GPL-3.0-only
package android.service.chooser;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import java.util.List;

public abstract class ChooserTargetService extends Service {
    public static final String BIND_PERMISSION =
            "android.permission.BIND_CHOOSER_TARGET_SERVICE";
    public static final String META_DATA_NAME =
            "android.service.chooser.chooser_target_service";
    public static final String SERVICE_INTERFACE =
            "android.service.chooser.ChooserTargetService";

    private final IBinder mBinder = new Binder();

    public ChooserTargetService() {
    }

    public abstract List<ChooserTarget> onGetChooserTargets(ComponentName targetActivityName,
            IntentFilter matchedFilter);

    @Override
    public IBinder onBind(Intent intent) {
        if (!SERVICE_INTERFACE.equals(intent != null ? intent.getAction() : null)) {
            return null;
        }
        return mBinder;
    }
}
