// SPDX-License-Identifier: GPL-3.0-only
package android.media.midi;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class MidiUmpDeviceService extends Service {
    public static final String SERVICE_INTERFACE = "android.media.midi.MidiUmpDeviceService";

    private MidiDeviceInfo mDeviceInfo;
    private MidiOutputPort[] mOutputPorts = new MidiOutputPort[0];
    private final IBinder mBinder = new Binder();

    public MidiUmpDeviceService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        List<MidiReceiver> inputReceivers = onGetInputPortReceivers();
        int inputCount = inputReceivers != null ? inputReceivers.size() : 0;
        if (mDeviceInfo == null) {
            Bundle props = new Bundle();
            props.putString(MidiDeviceInfo.PROPERTY_NAME, getClass().getSimpleName());
            mDeviceInfo = new MidiDeviceInfo(
                    MidiDeviceInfo.TYPE_VIRTUAL,
                    System.identityHashCode(this),
                    inputCount,
                    inputCount > 0 ? inputCount : 1,
                    null,
                    null,
                    props,
                    false,
                    MidiDeviceInfo.PROTOCOL_UMP_MIDI_2_0);
        }
        int outputCount = mDeviceInfo.getOutputPortCount();
        mOutputPorts = new MidiOutputPort[outputCount];
        for (int i = 0; i < outputCount; i++) {
            mOutputPorts[i] = new MidiOutputPort(i);
        }
    }

    public abstract List<MidiReceiver> onGetInputPortReceivers();

    public final List<MidiReceiver> getOutputPortReceivers() {
        List<MidiReceiver> receivers = new ArrayList<MidiReceiver>(mOutputPorts.length);
        for (int i = 0; i < mOutputPorts.length; i++) {
            final MidiOutputPort port = mOutputPorts[i];
            receivers.add(new MidiReceiver() {
                @Override
                public void onSend(byte[] msg, int offset, int count, long timestamp)
                        throws java.io.IOException {
                    port.dispatchSend(msg, offset, count, timestamp);
                }

                @Override
                public void onFlush() throws java.io.IOException {
                    port.dispatchFlush();
                }
            });
        }
        return Collections.unmodifiableList(receivers);
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
