// SPDX-License-Identifier: GPL-3.0-only
package android.media.midi;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

public abstract class MidiDeviceService extends Service {
    public static final String SERVICE_INTERFACE = "android.media.midi.MidiDeviceService";

    private MidiDeviceInfo mDeviceInfo;
    private MidiOutputPort[] mOutputPorts = new MidiOutputPort[0];
    private final IBinder mBinder = new Binder();

    public MidiDeviceService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        MidiReceiver[] inputReceivers = onGetInputPortReceivers();
        int inputCount = inputReceivers != null ? inputReceivers.length : 0;
        if (mDeviceInfo == null) {
            Bundle props = new Bundle();
            props.putString(MidiDeviceInfo.PROPERTY_NAME, getClass().getSimpleName());
            mDeviceInfo = new MidiDeviceInfo(
                    MidiDeviceInfo.TYPE_VIRTUAL,
                    System.identityHashCode(this),
                    inputCount,
                    1,
                    null,
                    null,
                    props,
                    false,
                    MidiDeviceInfo.PROTOCOL_UNKNOWN);
        }
        int outputCount = mDeviceInfo.getOutputPortCount();
        mOutputPorts = new MidiOutputPort[outputCount];
        for (int i = 0; i < outputCount; i++) {
            mOutputPorts[i] = new MidiOutputPort(i);
        }
    }

    public abstract MidiReceiver[] onGetInputPortReceivers();

    public final MidiReceiver[] getOutputPortReceivers() {
        MidiReceiver[] receivers = new MidiReceiver[mOutputPorts.length];
        for (int i = 0; i < mOutputPorts.length; i++) {
            final MidiOutputPort port = mOutputPorts[i];
            receivers[i] = new MidiReceiver() {
                @Override
                public void onSend(byte[] msg, int offset, int count, long timestamp)
                        throws java.io.IOException {
                    port.dispatchSend(msg, offset, count, timestamp);
                }

                @Override
                public void onFlush() throws java.io.IOException {
                    port.dispatchFlush();
                }
            };
        }
        return receivers;
    }

    public final MidiDeviceInfo getDeviceInfo() {
        return mDeviceInfo;
    }

    public void onDeviceStatusChanged(MidiDeviceStatus status) {
    }

    public void onClose() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null && SERVICE_INTERFACE.equals(intent.getAction())) {
            return mBinder;
        }
        return null;
    }
}
