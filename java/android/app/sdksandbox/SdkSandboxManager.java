// SPDX-License-Identifier: GPL-3.0-only
package android.app.sdksandbox;

import android.app.Activity;
import android.os.Bundle;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

public final class SdkSandboxManager {
    public static final String SDK_SANDBOX_SERVICE = "sdk_sandbox";
    public static final int SDK_SANDBOX_STATE_DISABLED = 0;
    public static final int SDK_SANDBOX_STATE_ENABLED_PROCESS_ISOLATION = 2;

    public static final int LOAD_SDK_NOT_FOUND = 100;
    public static final int LOAD_SDK_ALREADY_LOADED = 101;
    public static final int LOAD_SDK_SDK_DEFINED_ERROR = 102;
    public static final int LOAD_SDK_SDK_SANDBOX_DISABLED = 103;
    public static final int LOAD_SDK_INTERNAL_ERROR = 500;
    public static final int SDK_SANDBOX_PROCESS_NOT_AVAILABLE = 503;

    public static final int REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR = 700;
    public static final int REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED = 701;

    public static final String EXTRA_SURFACE_PACKAGE = "android.app.sdksandbox.extra.SURFACE_PACKAGE";
    public static final String EXTRA_WIDTH_IN_PIXELS = "android.app.sdksandbox.extra.WIDTH_IN_PIXELS";
    public static final String EXTRA_HEIGHT_IN_PIXELS = "android.app.sdksandbox.extra.HEIGHT_IN_PIXELS";
    public static final String EXTRA_DISPLAY_ID = "android.app.sdksandbox.extra.DISPLAY_ID";
    public static final String EXTRA_HOST_TOKEN = "android.app.sdksandbox.extra.HOST_TOKEN";

    private final Map<String, AppOwnedSdkSandboxInterface> mAppInterfaces =
            new LinkedHashMap<String, AppOwnedSdkSandboxInterface>();
    private final Map<SdkSandboxProcessDeathCallback, Executor> mDeathCallbacks =
            new LinkedHashMap<SdkSandboxProcessDeathCallback, Executor>();
    private final Set<String> mSyncedKeys = new LinkedHashSet<String>();

    public SdkSandboxManager() {
    }

    public static int getSdkSandboxState() {
        return SDK_SANDBOX_STATE_DISABLED;
    }

    public synchronized void addSdkSandboxProcessDeathCallback(
            Executor callbackExecutor, SdkSandboxProcessDeathCallback callback) {
        if (callbackExecutor == null) throw new NullPointerException("callbackExecutor");
        if (callback == null) throw new NullPointerException("callback");
        mDeathCallbacks.put(callback, callbackExecutor);
    }

    public synchronized void removeSdkSandboxProcessDeathCallback(
            SdkSandboxProcessDeathCallback callback) {
        if (callback == null) throw new NullPointerException("callback");
        mDeathCallbacks.remove(callback);
    }

    public synchronized void registerAppOwnedSdkSandboxInterface(
            AppOwnedSdkSandboxInterface appOwnedSdkSandboxInterface) {
        if (appOwnedSdkSandboxInterface == null) {
            throw new NullPointerException("appOwnedSdkSandboxInterface");
        }
        String name = appOwnedSdkSandboxInterface.getName();
        if (mAppInterfaces.containsKey(name)) {
            throw new IllegalStateException("Interface already registered: " + name);
        }
        mAppInterfaces.put(name, appOwnedSdkSandboxInterface);
    }

    public synchronized void unregisterAppOwnedSdkSandboxInterface(String name) {
        if (name == null) throw new NullPointerException("name");
        mAppInterfaces.remove(name);
    }

    public synchronized List<AppOwnedSdkSandboxInterface> getAppOwnedSdkSandboxInterfaces() {
        return new ArrayList<AppOwnedSdkSandboxInterface>(mAppInterfaces.values());
    }

    public void loadSdk(
            String sdkName,
            Bundle params,
            Executor executor,
            final OutcomeReceiver<SandboxedSdk, LoadSdkException> receiver) {
        if (sdkName == null) throw new NullPointerException("sdkName");
        if (params == null) throw new NullPointerException("params");
        if (executor == null) throw new NullPointerException("executor");
        if (receiver == null) throw new NullPointerException("receiver");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                receiver.onError(new LoadSdkException(
                        LOAD_SDK_SDK_SANDBOX_DISABLED, "SDK sandbox is disabled"));
            }
        });
    }

    public List<SandboxedSdk> getSandboxedSdks() {
        return Collections.emptyList();
    }

    public void unloadSdk(String sdkName) {
        if (sdkName == null) throw new NullPointerException("sdkName");
    }

    public void requestSurfacePackage(
            String sdkName,
            Bundle params,
            Executor callbackExecutor,
            final OutcomeReceiver<Bundle, RequestSurfacePackageException> receiver) {
        if (sdkName == null) throw new NullPointerException("sdkName");
        if (params == null) throw new NullPointerException("params");
        if (callbackExecutor == null) throw new NullPointerException("callbackExecutor");
        if (receiver == null) throw new NullPointerException("receiver");
        callbackExecutor.execute(new Runnable() {
            @Override
            public void run() {
                receiver.onError(new RequestSurfacePackageException(
                        REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED, "SDK not loaded"));
            }
        });
    }

    public void startSdkSandboxActivity(Activity fromActivity, IBinder sdkActivityToken) {
        if (fromActivity == null) throw new NullPointerException("fromActivity");
        if (sdkActivityToken == null) throw new NullPointerException("sdkActivityToken");
        throw new UnsupportedOperationException("SDK sandbox process unavailable");
    }

    public synchronized void addSyncedSharedPreferencesKeys(Set<String> keys) {
        if (keys == null) throw new NullPointerException("keys");
        for (String k : keys) {
            if (k == null || k.isEmpty()) {
                throw new IllegalArgumentException("Key cannot be null or empty");
            }
            mSyncedKeys.add(k);
        }
    }

    public synchronized void removeSyncedSharedPreferencesKeys(Set<String> keys) {
        if (keys == null) throw new NullPointerException("keys");
        mSyncedKeys.removeAll(keys);
    }

    public synchronized Set<String> getSyncedSharedPreferencesKeys() {
        return new LinkedHashSet<String>(mSyncedKeys);
    }

    public interface SdkSandboxProcessDeathCallback {
        void onSdkSandboxDied();
    }
}
