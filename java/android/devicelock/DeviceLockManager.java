// SPDX-License-Identifier: GPL-3.0-only
package android.devicelock;

import android.os.OutcomeReceiver;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executor;

public final class DeviceLockManager {
    public static final int DEVICE_LOCK_ROLE_FINANCING = 0;

    /** @hide */
    public DeviceLockManager() {
    }

    private static void checkArgs(Executor executor, OutcomeReceiver<?, ?> callback) {
        if (executor == null) {
            throw new NullPointerException("executor");
        }
        if (callback == null) {
            throw new NullPointerException("callback");
        }
    }

    public void lockDevice(
            Executor executor, final OutcomeReceiver<Void, Exception> callback) {
        checkArgs(executor, callback);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onError(new SecurityException(
                        "DeviceLock financing controller is not provisioned on desktop host"));
            }
        });
    }

    public void unlockDevice(
            Executor executor, final OutcomeReceiver<Void, Exception> callback) {
        checkArgs(executor, callback);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onError(new SecurityException(
                        "DeviceLock financing controller is not provisioned on desktop host"));
            }
        });
    }

    public void isDeviceLocked(
            Executor executor, final OutcomeReceiver<Boolean, Exception> callback) {
        checkArgs(executor, callback);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onResult(Boolean.FALSE);
            }
        });
    }

    public void getDeviceId(
            Executor executor, final OutcomeReceiver<DeviceId, Exception> callback) {
        checkArgs(executor, callback);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onError(new SecurityException(
                        "No cellular IMEI/MEID identifier available on desktop host"));
            }
        });
    }

    public void getKioskApps(
            Executor executor,
            final OutcomeReceiver<Map<Integer, String>, Exception> callback) {
        checkArgs(executor, callback);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onResult(Collections.<Integer, String>emptyMap());
            }
        });
    }
}
