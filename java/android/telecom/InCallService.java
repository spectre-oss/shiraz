// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.view.Surface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/** Base service implemented by UI apps that present ongoing calls. */
public abstract class InCallService extends Service {
    public static final String SERVICE_INTERFACE = "android.telecom.InCallService";

    public abstract static class VideoCall {
        public abstract static class Callback {
            public Callback() {
            }

            public abstract void onSessionModifyRequestReceived(VideoProfile videoProfile);

            public abstract void onSessionModifyResponseReceived(
                    int status, VideoProfile requestedProfile, VideoProfile responseProfile);

            public abstract void onCallSessionEvent(int event);

            public abstract void onPeerDimensionsChanged(int width, int height);

            public abstract void onVideoQualityChanged(int videoQuality);

            public abstract void onCallDataUsageChanged(long dataUsage);

            public abstract void onCameraCapabilitiesChanged(
                    VideoProfile.CameraCapabilities cameraCapabilities);
        }

        public VideoCall() {
        }

        public abstract void registerCallback(VideoCall.Callback callback);

        public abstract void registerCallback(VideoCall.Callback callback, Handler handler);

        public abstract void unregisterCallback(VideoCall.Callback callback);

        public abstract void setCamera(String cameraId);

        public abstract void setPreviewSurface(Surface surface);

        public abstract void setDisplaySurface(Surface surface);

        public abstract void setDeviceOrientation(int rotation);

        public abstract void setZoom(float value);

        public abstract void sendSessionModifyRequest(VideoProfile requestProfile);

        public abstract void sendSessionModifyResponse(VideoProfile responseProfile);

        public abstract void requestCameraCapabilities();

        public abstract void requestCallDataUsage();

        public abstract void setPauseImage(Uri uri);
    }

    private final IBinder mBinder = new Binder();
    private final List<Call> mCalls = new CopyOnWriteArrayList<Call>();
    private volatile boolean mCanAddCall = true;
    private volatile CallAudioState mCallAudioState =
            new CallAudioState(
                    false,
                    CallAudioState.ROUTE_EARPIECE,
                    CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER);
    private volatile CallEndpoint mCurrentCallEndpoint =
            new CallEndpoint("Earpiece", CallEndpoint.TYPE_EARPIECE);

    public InCallService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    public final List<Call> getCalls() {
        return Collections.unmodifiableList(new ArrayList<Call>(mCalls));
    }

    public final boolean canAddCall() {
        return mCanAddCall;
    }

    public final CallAudioState getCallAudioState() {
        return mCallAudioState;
    }

    public final void setMuted(boolean state) {
        CallAudioState prev = mCallAudioState;
        mCallAudioState =
                new CallAudioState(
                        state,
                        prev.getRoute(),
                        prev.getSupportedRouteMask(),
                        prev.getActiveBluetoothDevice(),
                        prev.getSupportedBluetoothDevices());
        onCallAudioStateChanged(mCallAudioState);
        onMuteStateChanged(state);
    }

    public final void setAudioRoute(int route) {
        CallAudioState prev = mCallAudioState;
        int supported = prev.getSupportedRouteMask() | route;
        mCallAudioState =
                new CallAudioState(
                        prev.isMuted(),
                        route,
                        supported,
                        prev.getActiveBluetoothDevice(),
                        prev.getSupportedBluetoothDevices());
        onCallAudioStateChanged(mCallAudioState);
    }

    public final void requestBluetoothAudio(BluetoothDevice bluetoothDevice) {
        Objects.requireNonNull(bluetoothDevice, "bluetoothDevice");
        CallAudioState prev = mCallAudioState;
        List<BluetoothDevice> devices =
                new ArrayList<BluetoothDevice>(prev.getSupportedBluetoothDevices());
        if (!devices.contains(bluetoothDevice)) {
            devices.add(bluetoothDevice);
        }
        mCallAudioState =
                new CallAudioState(
                        prev.isMuted(),
                        CallAudioState.ROUTE_BLUETOOTH,
                        prev.getSupportedRouteMask() | CallAudioState.ROUTE_BLUETOOTH,
                        bluetoothDevice,
                        devices);
        onCallAudioStateChanged(mCallAudioState);
    }

    public final void requestCallEndpointChange(
            CallEndpoint endpoint,
            Executor executor,
            final OutcomeReceiver<Void, CallEndpointException> callback) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        mCurrentCallEndpoint = endpoint;
        onCallEndpointChanged(endpoint);
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(null);
                    }
                });
    }

    public final CallEndpoint getCurrentCallEndpoint() {
        return mCurrentCallEndpoint;
    }

    public void onCallAudioStateChanged(CallAudioState audioState) {
    }

    public void onCallEndpointChanged(CallEndpoint callEndpoint) {
    }

    public void onAvailableCallEndpointsChanged(List<CallEndpoint> availableEndpoints) {
    }

    public void onMuteStateChanged(boolean isMuted) {
    }

    public void onBringToForeground(boolean showDialpad) {
    }

    public void onCallAdded(Call call) {
    }

    public void onCallRemoved(Call call) {
    }

    public void onCanAddCallChanged(boolean canAddCall) {
    }

    public void onSilenceRinger() {
    }

    public void onConnectionEvent(Call call, String event, Bundle extras) {
    }

    void addCallInternal(Call call) {
        if (call != null && !mCalls.contains(call)) {
            mCalls.add(call);
            onCallAdded(call);
        }
    }

    void removeCallInternal(Call call) {
        if (call != null && mCalls.remove(call)) {
            onCallRemoved(call);
        }
    }
}
