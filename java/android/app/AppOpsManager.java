// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/** App-op checks delegate to the loaded application's existing permission policy. */
public class AppOpsManager {
    public static final int MODE_ALLOWED = 0;
    public static final int MODE_IGNORED = 1;
    public static final int MODE_ERRORED = 2;
    public static final int MODE_DEFAULT = 3;
    public static final int MODE_FOREGROUND = 4;

    public static final int WATCH_FOREGROUND_CHANGES = 1;

    public static final String OPSTR_ADD_VOICEMAIL = "android:add_voicemail";
    public static final String OPSTR_ANSWER_PHONE_CALLS = "android:answer_phone_calls";
    public static final String OPSTR_BODY_SENSORS = "android:body_sensors";
    public static final String OPSTR_CALL_PHONE = "android:call_phone";
    public static final String OPSTR_CAMERA = "android:camera";
    public static final String OPSTR_COARSE_LOCATION = "android:coarse_location";
    public static final String OPSTR_FINE_LOCATION = "android:fine_location";
    public static final String OPSTR_GET_USAGE_STATS = "android:get_usage_stats";
    public static final String OPSTR_MOCK_LOCATION = "android:mock_location";
    public static final String OPSTR_MONITOR_HIGH_POWER_LOCATION = "android:monitor_location_high_power";
    public static final String OPSTR_MONITOR_LOCATION = "android:monitor_location";
    public static final String OPSTR_PICTURE_IN_PICTURE = "android:picture_in_picture";
    public static final String OPSTR_PROCESS_OUTGOING_CALLS = "android:process_outgoing_calls";
    public static final String OPSTR_READ_CALENDAR = "android:read_calendar";
    public static final String OPSTR_READ_CALL_LOG = "android:read_call_log";
    public static final String OPSTR_READ_CELL_BROADCASTS = "android:read_cell_broadcasts";
    public static final String OPSTR_READ_CONTACTS = "android:read_contacts";
    public static final String OPSTR_READ_EXTERNAL_STORAGE = "android:read_external_storage";
    public static final String OPSTR_READ_PHONE_NUMBERS = "android:read_phone_numbers";
    public static final String OPSTR_READ_PHONE_STATE = "android:read_phone_state";
    public static final String OPSTR_READ_SMS = "android:read_sms";
    public static final String OPSTR_RECEIVE_MMS = "android:receive_mms";
    public static final String OPSTR_RECEIVE_SMS = "android:receive_sms";
    public static final String OPSTR_RECEIVE_WAP_PUSH = "android:receive_wap_push";
    public static final String OPSTR_RECORD_AUDIO = "android:record_audio";
    public static final String OPSTR_SEND_SMS = "android:send_sms";
    public static final String OPSTR_SYSTEM_ALERT_WINDOW = "android:system_alert_window";
    public static final String OPSTR_USE_FINGERPRINT = "android:use_fingerprint";
    public static final String OPSTR_USE_SIP = "android:use_sip";
    public static final String OPSTR_WRITE_CALENDAR = "android:write_calendar";
    public static final String OPSTR_WRITE_CALL_LOG = "android:write_call_log";
    public static final String OPSTR_WRITE_CONTACTS = "android:write_contacts";
    public static final String OPSTR_WRITE_EXTERNAL_STORAGE = "android:write_external_storage";
    public static final String OPSTR_WRITE_SETTINGS = "android:write_settings";

    public interface OnOpChangedListener {
        void onOpChanged(String op, String packageName);

        default void onOpChanged(String op, String packageName, int userId, String persistentDeviceId) {
            onOpChanged(op, packageName);
        }
    }

    public interface OnOpActiveChangedListener {
        void onOpActiveChanged(String op, int uid, String packageName, boolean active);

        default void onOpActiveChanged(
                String op,
                int uid,
                String packageName,
                String attributionTag,
                int virtualDeviceId,
                boolean active,
                int attributionFlags,
                int attributionChainId) {
            onOpActiveChanged(op, uid, packageName, active);
        }
    }

    public static abstract class OnOpNotedCallback {
        public OnOpNotedCallback() {}
        public abstract void onNoted(SyncNotedAppOp op);
        public abstract void onSelfNoted(SyncNotedAppOp op);
        public abstract void onAsyncNoted(AsyncNotedAppOp asyncOp);
    }

    private static final String[] OPS = {
        OPSTR_CAMERA,
        OPSTR_RECORD_AUDIO,
        OPSTR_FINE_LOCATION,
        OPSTR_COARSE_LOCATION,
        OPSTR_SYSTEM_ALERT_WINDOW
    };
    private static final java.util.ArrayList<java.lang.ref.WeakReference<AppOpsManager>> MANAGERS =
            new java.util.ArrayList<java.lang.ref.WeakReference<AppOpsManager>>();
    private final java.util.ArrayList<Watch> watches = new java.util.ArrayList<Watch>();
    private final CopyOnWriteArrayList<OnOpActiveChangedListener> activeListeners =
            new CopyOnWriteArrayList<OnOpActiveChangedListener>();
    private volatile Executor notedExecutor;
    private volatile OnOpNotedCallback notedCallback;
    private volatile android.os.Handler handler;
    private final Object notificationLock = new Object();
    private boolean notificationPending;

    private static final class Watch {
        final String op;
        final String pkg;
        final OnOpChangedListener listener;
        final java.util.HashMap<String, Integer> modes = new java.util.HashMap<String, Integer>();

        Watch(String op, String pkg, OnOpChangedListener listener) {
            this.op = op;
            this.pkg = pkg;
            this.listener = listener;
        }
    }

    private final android.content.Context context;

    public AppOpsManager(android.content.Context context) {
        if (context == null) throw new NullPointerException("context");
        this.context = context;
        synchronized (MANAGERS) {
            for (int i = MANAGERS.size() - 1; i >= 0; i--) {
                if (MANAGERS.get(i).get() == null) MANAGERS.remove(i);
            }
            if (MANAGERS.size() >= 1024) throw new IllegalStateException("AppOps context limit");
            MANAGERS.add(new java.lang.ref.WeakReference<AppOpsManager>(this));
        }
    }

    public static String permissionToOp(String permission) {
        if ("android.permission.CAMERA".equals(permission)) return OPSTR_CAMERA;
        if ("android.permission.RECORD_AUDIO".equals(permission)) return OPSTR_RECORD_AUDIO;
        if ("android.permission.ACCESS_FINE_LOCATION".equals(permission)) return OPSTR_FINE_LOCATION;
        if ("android.permission.ACCESS_COARSE_LOCATION".equals(permission)) return OPSTR_COARSE_LOCATION;
        if ("android.permission.SYSTEM_ALERT_WINDOW".equals(permission)) return OPSTR_SYSTEM_ALERT_WINDOW;
        return null;
    }

    private static String permission(String op) {
        if (OPSTR_CAMERA.equals(op)) return "android.permission.CAMERA";
        if (OPSTR_RECORD_AUDIO.equals(op)) return "android.permission.RECORD_AUDIO";
        if (OPSTR_FINE_LOCATION.equals(op)) return "android.permission.ACCESS_FINE_LOCATION";
        if (OPSTR_COARSE_LOCATION.equals(op)) return "android.permission.ACCESS_COARSE_LOCATION";
        if (OPSTR_SYSTEM_ALERT_WINDOW.equals(op)) return "android.permission.SYSTEM_ALERT_WINDOW";
        return null;
    }

    public void checkPackage(int uid, String packageName) {
        if (uid != android.os.Process.myUid() || !context.getPackageName().equals(packageName)) {
            throw new SecurityException("Package does not belong to UID");
        }
    }

    public synchronized void startWatchingMode(String op, String packageName, OnOpChangedListener callback) {
        if (callback == null) throw new NullPointerException("listener");
        if (op != null && permission(op) == null) {
            throw new IllegalArgumentException("Unsupported app operation: " + op);
        }
        for (Watch watch : watches) {
            if (watch.listener == callback
                    && Objects.equals(op, watch.op)
                    && Objects.equals(packageName, watch.pkg)) {
                return;
            }
        }
        if (watches.size() >= 1024) throw new IllegalStateException("AppOps watch limit");
        if (handler == null) {
            android.os.Looper main = android.os.Looper.getMainLooper();
            if (main == null) throw new IllegalStateException("AppOps watches need a main Looper");
            handler = new android.os.Handler(main);
        }
        Watch watch = new Watch(op, packageName, callback);
        for (String known : OPS) {
            if (op == null || op.equals(known)) {
                watch.modes.put(
                        known,
                        checkOpNoThrow(
                                known,
                                android.os.Process.myUid(),
                                packageName == null ? context.getPackageName() : packageName));
            }
        }
        watches.add(watch);
    }

    public void startWatchingMode(String op, String packageName, int flags, OnOpChangedListener callback) {
        startWatchingMode(op, packageName, callback);
    }

    public synchronized void stopWatchingMode(OnOpChangedListener callback) {
        for (int i = watches.size() - 1; i >= 0; i--) {
            if (watches.get(i).listener == callback) watches.remove(i);
        }
    }

    public void startWatchingActive(String[] ops, Executor executor, OnOpActiveChangedListener callback) {
        Objects.requireNonNull(ops, "ops");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        activeListeners.addIfAbsent(callback);
    }

    public void stopWatchingActive(OnOpActiveChangedListener callback) {
        if (callback != null) {
            activeListeners.remove(callback);
        }
    }

    public static void shirazPermissionChanged(final String permission) {
        java.util.ArrayList<AppOpsManager> targets = new java.util.ArrayList<AppOpsManager>();
        synchronized (MANAGERS) {
            for (java.lang.ref.WeakReference<AppOpsManager> reference : MANAGERS) {
                AppOpsManager manager = reference.get();
                if (manager != null) targets.add(manager);
            }
        }
        for (AppOpsManager manager : targets) manager.queueChanges();
    }

    private void queueChanges() {
        android.os.Handler target = handler;
        if (target == null) return;
        synchronized (notificationLock) {
            if (notificationPending) return;
            notificationPending = true;
        }
        if (!target.post(new Runnable() {
            public void run() {
                synchronized (notificationLock) {
                    notificationPending = false;
                }
                dispatchChanges(null);
            }
        })) {
            synchronized (notificationLock) {
                notificationPending = false;
            }
            android.util.Log.w("AppOps", "Mode change notification could not be queued");
        }
    }

    private void dispatchChanges(String changed) {
        java.util.ArrayList<Watch> snapshot;
        synchronized (this) {
            snapshot = new java.util.ArrayList<Watch>(watches);
        }
        for (Watch watch : snapshot) {
            for (String op : OPS) {
                if ((watch.op == null || watch.op.equals(op)) && (changed == null || changed.equals(permission(op)))) {
                    String pkg = watch.pkg == null ? context.getPackageName() : watch.pkg;
                    int mode = checkOpNoThrow(op, android.os.Process.myUid(), pkg);
                    boolean notify;
                    synchronized (this) {
                        Integer previous = watch.modes.get(op);
                        notify = watches.contains(watch) && previous != null && previous.intValue() != mode;
                        if (notify) watch.modes.put(op, mode);
                    }
                    if (notify) watch.listener.onOpChanged(op, pkg);
                }
            }
        }
    }

    public int checkOpNoThrow(String op, int uid, String packageName) {
        String permission = permission(op);
        if (permission == null || uid != android.os.Process.myUid() || !context.getPackageName().equals(packageName)) {
            return MODE_ERRORED;
        }
        return context.checkPermission(permission, android.os.Process.myPid(), uid)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED
                ? MODE_ALLOWED
                : MODE_IGNORED;
    }

    @Deprecated
    public int checkOp(String op, int uid, String packageName) {
        int result = checkOpNoThrow(op, uid, packageName);
        if (result == MODE_ERRORED) throw new SecurityException("App operation errored: " + op);
        return result;
    }

    public int unsafeCheckOp(String op, int uid, String packageName) {
        return checkOp(op, uid, packageName);
    }

    public int unsafeCheckOpNoThrow(String op, int uid, String packageName) {
        return checkOpNoThrow(op, uid, packageName);
    }

    public int unsafeCheckOpRaw(String op, int uid, String packageName) {
        return checkOp(op, uid, packageName);
    }

    public int unsafeCheckOpRawNoThrow(String op, int uid, String packageName) {
        return checkOpNoThrow(op, uid, packageName);
    }

    @Deprecated
    public int noteOp(String op, int uid, String packageName) {
        return noteOp(op, uid, packageName, null, null);
    }

    public int noteOp(String op, int uid, String packageName, String attributionTag, String message) {
        int result = noteOpNoThrow(op, uid, packageName, attributionTag, message);
        if (result == MODE_ERRORED) throw new SecurityException("Unknown app operation");
        return result;
    }

    @Deprecated
    public int noteOpNoThrow(String op, int uid, String packageName) {
        return checkOpNoThrow(op, uid, packageName);
    }

    public int noteOpNoThrow(String op, int uid, String packageName, String attributionTag, String message) {
        return checkOpNoThrow(op, uid, packageName);
    }

    @Deprecated
    public int noteProxyOp(String op, String proxiedPackageName) {
        int result = noteProxyOpNoThrow(op, proxiedPackageName);
        if (result == MODE_ERRORED) throw new SecurityException("Unknown app operation");
        return result;
    }

    public int noteProxyOp(
            String op,
            String proxiedPackageName,
            int proxiedUid,
            String attributionTag,
            String message) {
        int result = noteProxyOpNoThrow(op, proxiedPackageName, proxiedUid, attributionTag, message);
        if (result == MODE_ERRORED) throw new SecurityException("Unknown app operation");
        return result;
    }

    @Deprecated
    public int noteProxyOpNoThrow(String op, String proxiedPackageName) {
        return checkOpNoThrow(op, android.os.Process.myUid(), proxiedPackageName);
    }

    @Deprecated
    public int noteProxyOpNoThrow(String op, String proxiedPackageName, int proxiedUid) {
        return checkOpNoThrow(op, proxiedUid, proxiedPackageName);
    }

    public int noteProxyOpNoThrow(
            String op,
            String proxiedPackageName,
            int proxiedUid,
            String attributionTag,
            String message) {
        return checkOpNoThrow(op, proxiedUid, proxiedPackageName);
    }

    @Deprecated
    public int startOp(String op, int uid, String packageName) {
        return startOp(op, uid, packageName, null, null);
    }

    public int startOp(String op, int uid, String packageName, String attributionTag, String message) {
        int result = startOpNoThrow(op, uid, packageName, attributionTag, message);
        if (result == MODE_ERRORED) throw new SecurityException("Unknown app operation");
        return result;
    }

    @Deprecated
    public int startOpNoThrow(String op, int uid, String packageName) {
        return checkOpNoThrow(op, uid, packageName);
    }

    public int startOpNoThrow(String op, int uid, String packageName, String attributionTag, String message) {
        return checkOpNoThrow(op, uid, packageName);
    }

    public int startProxyOp(
            String op,
            int proxiedUid,
            String proxiedPackageName,
            String proxiedAttributionTag,
            String message) {
        int result = startProxyOpNoThrow(op, proxiedUid, proxiedPackageName, proxiedAttributionTag, message);
        if (result == MODE_ERRORED) throw new SecurityException("Unknown app operation");
        return result;
    }

    public int startProxyOpNoThrow(
            String op,
            int proxiedUid,
            String proxiedPackageName,
            String proxiedAttributionTag,
            String message) {
        return checkOpNoThrow(op, proxiedUid, proxiedPackageName);
    }

    @Deprecated
    public void finishOp(String op, int uid, String packageName) {
    }

    public void finishOp(String op, int uid, String packageName, String attributionTag) {
    }

    public void finishProxyOp(String op, int proxiedUid, String proxiedPackageName, String proxiedAttributionTag) {
    }

    public boolean isOpActive(String op, int uid, String packageName) {
        return false;
    }

    public void setOnOpNotedCallback(Executor asyncExecutor, OnOpNotedCallback callback) {
        if (callback != null && asyncExecutor == null) {
            throw new NullPointerException("asyncExecutor");
        }
        this.notedExecutor = asyncExecutor;
        this.notedCallback = callback;
    }
}
