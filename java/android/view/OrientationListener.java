// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.content.Context;
import android.hardware.SensorListener;
import android.hardware.SensorManager;

@Deprecated
public abstract class OrientationListener implements SensorListener {
    public static final int ORIENTATION_UNKNOWN = -1;

    private final OrientationEventListener mOrientationEventListener;

    public OrientationListener(Context context) {
        this(context, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public OrientationListener(Context context, int rate) {
        mOrientationEventListener = new OrientationEventListener(context, rate) {
            @Override
            public void onOrientationChanged(int orientation) {
                OrientationListener.this.onOrientationChanged(orientation);
            }
        };
    }

    public void enable() {
        mOrientationEventListener.enable();
    }

    public void disable() {
        mOrientationEventListener.disable();
    }

    @Override
    public void onAccuracyChanged(int sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(int sensor, float[] values) {
    }

    public abstract void onOrientationChanged(int orientation);
}
