// SPDX-License-Identifier: GPL-3.0-only
package android.service.controls;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.service.controls.actions.ControlAction;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

/**
 * Service implementation allowing applications to contribute device controls to the system UI.
 */
public abstract class ControlsProviderService extends Service {
    public static final String SERVICE_CONTROLS =
            "android.service.controls.ControlsProviderService";
    public static final String META_DATA_PANEL_ACTIVITY =
            "android.service.controls.META_DATA_PANEL_ACTIVITY";
    public static final String EXTRA_CONTROLS_SURFACE =
            "android.service.controls.extra.CONTROLS_SURFACE";
    public static final String EXTRA_LOCKSCREEN_ALLOW_TRIVIAL_CONTROLS =
            "android.service.controls.extra.LOCKSCREEN_ALLOW_TRIVIAL_CONTROLS";
    public static final int CONTROLS_SURFACE_ACTIVITY_PANEL = 0;
    public static final int CONTROLS_SURFACE_DREAM = 1;
    public static final String TAG = "ControlsProviderService";

    private static final String ACTION_ADD_CONTROL =
            "android.service.controls.action.ADD_CONTROL";
    private static final String EXTRA_CONTROL =
            "android.service.controls.extra.CONTROL";

    private final Binder mBinder = new Binder();

    public ControlsProviderService() {
    }

    public abstract Flow.Publisher<Control> createPublisherForAllAvailable();

    public Flow.Publisher<Control> createPublisherForSuggested() {
        return null;
    }

    public abstract Flow.Publisher<Control> createPublisherFor(List<String> controlIds);

    public abstract void performControlAction(
            String controlId, ControlAction action, Consumer<Integer> consumer);

    @Override
    public final IBinder onBind(Intent intent) {
        if (intent != null && SERVICE_CONTROLS.equals(intent.getAction())) {
            return mBinder;
        }
        return null;
    }

    @Override
    public final boolean onUnbind(Intent intent) {
        return false;
    }

    public static void requestAddControl(
            Context context, ComponentName componentName, Control control) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(componentName, "componentName must not be null");
        Objects.requireNonNull(control, "control must not be null");
        Intent intent = new Intent(ACTION_ADD_CONTROL);
        intent.putExtra(Intent.EXTRA_COMPONENT_NAME, componentName);
        intent.putExtra(EXTRA_CONTROL, control);
        context.sendBroadcast(intent);
    }
}
