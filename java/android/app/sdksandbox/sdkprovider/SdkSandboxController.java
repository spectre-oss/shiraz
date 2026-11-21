// SPDX-License-Identifier: GPL-3.0-only
package android.app.sdksandbox.sdkprovider;

import android.app.sdksandbox.AppOwnedSdkSandboxInterface;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class SdkSandboxController {
    public static final String SDK_SANDBOX_CONTROLLER_SERVICE = "sdk_sandbox_controller_service";

    private final Context mContext;
    private final Map<SdkSandboxActivityHandler, IBinder> mActivityHandlers =
            new LinkedHashMap<SdkSandboxActivityHandler, IBinder>();

    public SdkSandboxController(Context context) {
        mContext = context;
    }

    public List<AppOwnedSdkSandboxInterface> getAppOwnedSdkSandboxInterfaces() {
        return Collections.emptyList();
    }

    public List<SandboxedSdk> getSandboxedSdks() {
        return Collections.emptyList();
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
                        SdkSandboxManager.LOAD_SDK_SDK_SANDBOX_DISABLED,
                        "SDK sandbox is disabled"));
            }
        });
    }

    public SharedPreferences getClientSharedPreferences() {
        if (mContext == null) {
            throw new IllegalStateException("No context attached");
        }
        return mContext.getSharedPreferences("sdk_sandbox_client_prefs", Context.MODE_PRIVATE);
    }

    public synchronized IBinder registerSdkSandboxActivityHandler(
            SdkSandboxActivityHandler sdkSandboxActivityHandler) {
        if (sdkSandboxActivityHandler == null) {
            throw new NullPointerException("sdkSandboxActivityHandler");
        }
        IBinder existing = mActivityHandlers.get(sdkSandboxActivityHandler);
        if (existing != null) {
            return existing;
        }
        IBinder token = new Binder();
        mActivityHandlers.put(sdkSandboxActivityHandler, token);
        return token;
    }

    public synchronized void unregisterSdkSandboxActivityHandler(
            SdkSandboxActivityHandler sdkSandboxActivityHandler) {
        if (sdkSandboxActivityHandler == null) {
            throw new NullPointerException("sdkSandboxActivityHandler");
        }
        mActivityHandlers.remove(sdkSandboxActivityHandler);
    }

    public String getClientPackageName() {
        if (mContext == null) {
            throw new IllegalStateException("No context attached");
        }
        return mContext.getPackageName();
    }
}
