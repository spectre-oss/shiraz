// SPDX-License-Identifier: GPL-3.0-only
package android.media.midi;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MidiDevice implements Closeable {
    public class MidiConnection implements Closeable {
        private final MidiInputPort mInputPort;
        private final MidiOutputPort mOutputPort;
        private boolean mIsClosed;

        MidiConnection(MidiInputPort inputPort, MidiOutputPort outputPort) {
            mInputPort = inputPort;
            mOutputPort = outputPort;
        }

        @Override
        public void close() throws IOException {
            synchronized (this) {
                if (mIsClosed) {
                    return;
                }
                mIsClosed = true;
            }
            if (mOutputPort != null) {
                if (mInputPort != null) {
                    mOutputPort.disconnect(mInputPort);
                }
                mOutputPort.close();
            }
            if (mInputPort != null) {
                mInputPort.close();
            }
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                close();
            } finally {
                super.finalize();
            }
        }
    }

    private final MidiDeviceInfo mDeviceInfo;
    private final MidiManager mManager;
    private final boolean[] mInputPortOpen;
    private final int[] mOutputPortOpenCount;
    private final List<List<MidiOutputPort>> mActiveOutputPorts;
    private final MidiReceiver[] mInputTargetReceivers;
    private boolean mIsClosed;

    MidiDevice(MidiDeviceInfo deviceInfo, MidiManager manager) {
        this(deviceInfo, manager, null);
    }

    MidiDevice(MidiDeviceInfo deviceInfo, MidiManager manager, MidiReceiver[] inputReceivers) {
        mDeviceInfo = deviceInfo;
        mManager = manager;
        int inCount = deviceInfo != null ? deviceInfo.getInputPortCount() : 0;
        int outCount = deviceInfo != null ? deviceInfo.getOutputPortCount() : 0;
        mInputPortOpen = new boolean[inCount];
        mOutputPortOpenCount = new int[outCount];
        mActiveOutputPorts = new ArrayList<List<MidiOutputPort>>(outCount);
        for (int i = 0; i < outCount; i++) {
            mActiveOutputPorts.add(new ArrayList<MidiOutputPort>());
        }
        mInputTargetReceivers = inputReceivers;
    }

    public MidiDeviceInfo getInfo() {
        return mDeviceInfo;
    }

    public MidiInputPort openInputPort(int portNumber) {
        synchronized (this) {
            if (mIsClosed || portNumber < 0 || portNumber >= mInputPortOpen.length) {
                return null;
            }
            if (mInputPortOpen[portNumber]) {
                return null;
            }
            mInputPortOpen[portNumber] = true;
            MidiReceiver target = (mInputTargetReceivers != null
                    && portNumber < mInputTargetReceivers.length)
                    ? mInputTargetReceivers[portNumber] : null;
            notifyStatusChangedLocked();
            return new MidiInputPort(this, portNumber, target);
        }
    }

    public MidiOutputPort openOutputPort(int portNumber) {
        synchronized (this) {
            if (mIsClosed || portNumber < 0 || portNumber >= mOutputPortOpenCount.length) {
                return null;
            }
            MidiOutputPort port = new MidiOutputPort(this, portNumber);
            mActiveOutputPorts.get(portNumber).add(port);
            mOutputPortOpenCount[portNumber]++;
            notifyStatusChangedLocked();
            return port;
        }
    }

    public MidiConnection connectPorts(MidiInputPort inputPort, int outputPortNumber) {
        if (inputPort == null) {
            throw new NullPointerException("inputPort must not be null");
        }
        if (outputPortNumber < 0 || outputPortNumber >= mOutputPortOpenCount.length) {
            throw new IllegalArgumentException("Invalid outputPortNumber: " + outputPortNumber);
        }
        synchronized (this) {
            if (mIsClosed) {
                return null;
            }
        }
        MidiOutputPort outputPort = openOutputPort(outputPortNumber);
        if (outputPort == null) {
            return null;
        }
        outputPort.connect(inputPort);
        return new MidiConnection(inputPort, outputPort);
    }

    void onInputPortClosed(int portNumber) {
        synchronized (this) {
            if (portNumber >= 0 && portNumber < mInputPortOpen.length) {
                mInputPortOpen[portNumber] = false;
                notifyStatusChangedLocked();
            }
        }
    }

    void onOutputPortClosed(int portNumber) {
        synchronized (this) {
            if (portNumber >= 0 && portNumber < mOutputPortOpenCount.length) {
                if (mOutputPortOpenCount[portNumber] > 0) {
                    mOutputPortOpenCount[portNumber]--;
                }
                notifyStatusChangedLocked();
            }
        }
    }

    void dispatchFromInputPort(int portNumber, byte[] msg, int offset, int count, long timestamp)
            throws IOException {
        List<MidiOutputPort> ports;
        synchronized (this) {
            if (mIsClosed || portNumber < 0 || portNumber >= mActiveOutputPorts.size()) {
                return;
            }
            ports = new ArrayList<MidiOutputPort>(mActiveOutputPorts.get(portNumber));
        }
        for (int i = 0; i < ports.size(); i++) {
            ports.get(i).dispatchSend(msg, offset, count, timestamp);
        }
    }

    void dispatchFlushFromInputPort(int portNumber) throws IOException {
        List<MidiOutputPort> ports;
        synchronized (this) {
            if (mIsClosed || portNumber < 0 || portNumber >= mActiveOutputPorts.size()) {
                return;
            }
            ports = new ArrayList<MidiOutputPort>(mActiveOutputPorts.get(portNumber));
        }
        for (int i = 0; i < ports.size(); i++) {
            ports.get(i).dispatchFlush();
        }
    }

    private void notifyStatusChangedLocked() {
        if (mManager != null && mDeviceInfo != null) {
            mManager.notifyDeviceStatusChanged(
                    new MidiDeviceStatus(mDeviceInfo, mInputPortOpen, mOutputPortOpenCount));
        }
    }

    @Override
    public void close() throws IOException {
        List<MidiOutputPort> toClose = new ArrayList<MidiOutputPort>();
        synchronized (this) {
            if (mIsClosed) {
                return;
            }
            mIsClosed = true;
            for (int i = 0; i < mActiveOutputPorts.size(); i++) {
                toClose.addAll(mActiveOutputPorts.get(i));
                mActiveOutputPorts.get(i).clear();
                mOutputPortOpenCount[i] = 0;
            }
            for (int i = 0; i < mInputPortOpen.length; i++) {
                mInputPortOpen[i] = false;
            }
        }
        for (int i = 0; i < toClose.size(); i++) {
            toClose.get(i).close();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    @Override
    public String toString() {
        return "MidiDevice: " + mDeviceInfo;
    }
}
