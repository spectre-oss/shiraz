// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import android.content.ComponentName;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.view.SurfaceControlViewHost;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Manager for invoking On-Device Personalization isolated services; disabled on the host. */
public class OnDevicePersonalizationManager {
    OnDevicePersonalizationManager() {
    }

    public static class ExecuteResult {
        private final SurfacePackageToken mSurfacePackageToken;
        private final byte[] mOutputData;

        ExecuteResult(SurfacePackageToken surfacePackageToken, byte[] outputData) {
            this.mSurfacePackageToken = surfacePackageToken;
            this.mOutputData = outputData == null ? null : outputData.clone();
        }

        public SurfacePackageToken getSurfacePackageToken() {
            return mSurfacePackageToken;
        }

        public byte[] getOutputData() {
            return mOutputData == null ? null : mOutputData.clone();
        }
    }

    public void execute(
            ComponentName service,
            PersistableBundle params,
            Executor callbackExecutor,
            final OutcomeReceiver<ExecuteResult, Exception> receiver) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        Objects.requireNonNull(receiver, "receiver");
        callbackExecutor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        receiver.onError(
                                new OnDevicePersonalizationException(
                                        OnDevicePersonalizationException
                                                .ERROR_PERSONALIZATION_DISABLED,
                                        "OnDevicePersonalization is disabled on this host"));
                    }
                });
    }

    public void requestSurfacePackage(
            SurfacePackageToken surfacePackageToken,
            IBinder surfaceHostToken,
            int displayId,
            int width,
            int height,
            Executor callbackExecutor,
            final OutcomeReceiver<SurfaceControlViewHost.SurfacePackage, Exception> receiver) {
        Objects.requireNonNull(surfacePackageToken, "surfacePackageToken");
        Objects.requireNonNull(surfaceHostToken, "surfaceHostToken");
        Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        Objects.requireNonNull(receiver, "receiver");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be positive");
        }
        callbackExecutor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        receiver.onError(
                                new OnDevicePersonalizationException(
                                        OnDevicePersonalizationException
                                                .ERROR_PERSONALIZATION_DISABLED,
                                        "OnDevicePersonalization is disabled on this host"));
                    }
                });
    }
}
