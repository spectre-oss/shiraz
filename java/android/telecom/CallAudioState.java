// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/** Current audio routing and mute state of a call. */
public final class CallAudioState implements Parcelable {
    public static final int ROUTE_EARPIECE = 0x00000001;
    public static final int ROUTE_BLUETOOTH = 0x00000002;
    public static final int ROUTE_WIRED_HEADSET = 0x00000004;
    public static final int ROUTE_SPEAKER = 0x00000008;
    public static final int ROUTE_WIRED_OR_EARPIECE = ROUTE_EARPIECE | ROUTE_WIRED_HEADSET;
    public static final int ROUTE_STREAMING = 0x00000010;

    private final boolean mIsMuted;
    private final int mRoute;
    private final int mSupportedRouteMask;
    private final BluetoothDevice mActiveBluetoothDevice;
    private final Collection<BluetoothDevice> mSupportedBluetoothDevices;

    public CallAudioState(boolean muted, int route, int supportedRouteMask) {
        this(muted, route, supportedRouteMask, null, Collections.<BluetoothDevice>emptyList());
    }

    CallAudioState(
            boolean isMuted,
            int route,
            int supportedRouteMask,
            BluetoothDevice activeBluetoothDevice,
            Collection<BluetoothDevice> supportedBluetoothDevices) {
        this.mIsMuted = isMuted;
        this.mRoute = route;
        this.mSupportedRouteMask = supportedRouteMask;
        this.mActiveBluetoothDevice = activeBluetoothDevice;
        this.mSupportedBluetoothDevices =
                supportedBluetoothDevices == null
                        ? Collections.<BluetoothDevice>emptyList()
                        : Collections.unmodifiableList(
                                new ArrayList<BluetoothDevice>(supportedBluetoothDevices));
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CallAudioState)) {
            return false;
        }
        CallAudioState other = (CallAudioState) obj;
        if (mSupportedBluetoothDevices.size() != other.mSupportedBluetoothDevices.size()) {
            return false;
        }
        for (BluetoothDevice device : mSupportedBluetoothDevices) {
            if (!other.mSupportedBluetoothDevices.contains(device)) {
                return false;
            }
        }
        return Objects.equals(mActiveBluetoothDevice, other.mActiveBluetoothDevice)
                && mIsMuted == other.mIsMuted
                && mRoute == other.mRoute
                && mSupportedRouteMask == other.mSupportedRouteMask;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Boolean.valueOf(mIsMuted),
                Integer.valueOf(mRoute),
                Integer.valueOf(mSupportedRouteMask),
                mActiveBluetoothDevice);
    }

    @Override
    public String toString() {
        return "[AudioState isMuted: "
                + mIsMuted
                + ", route: "
                + audioRouteToString(mRoute)
                + ", supportedRouteMask: "
                + audioRouteToString(mSupportedRouteMask)
                + ", activeBluetoothDevice: ["
                + mActiveBluetoothDevice
                + "], supportedBluetoothDevices: ["
                + mSupportedBluetoothDevices
                + "]]";
    }

    public boolean isMuted() {
        return mIsMuted;
    }

    public int getRoute() {
        return mRoute;
    }

    public int getSupportedRouteMask() {
        return mSupportedRouteMask;
    }

    public BluetoothDevice getActiveBluetoothDevice() {
        return mActiveBluetoothDevice;
    }

    public Collection<BluetoothDevice> getSupportedBluetoothDevices() {
        return mSupportedBluetoothDevices;
    }

    public static String audioRouteToString(int route) {
        if (route == 0 || (route & ~0x1f) != 0) {
            return "UNKNOWN";
        }
        StringBuilder buffer = new StringBuilder();
        if ((route & ROUTE_EARPIECE) == ROUTE_EARPIECE) {
            listAppend(buffer, "EARPIECE");
        }
        if ((route & ROUTE_BLUETOOTH) == ROUTE_BLUETOOTH) {
            listAppend(buffer, "BLUETOOTH");
        }
        if ((route & ROUTE_WIRED_HEADSET) == ROUTE_WIRED_HEADSET) {
            listAppend(buffer, "WIRED_HEADSET");
        }
        if ((route & ROUTE_SPEAKER) == ROUTE_SPEAKER) {
            listAppend(buffer, "SPEAKER");
        }
        if ((route & ROUTE_STREAMING) == ROUTE_STREAMING) {
            listAppend(buffer, "STREAMING");
        }
        return buffer.toString();
    }

    private static void listAppend(StringBuilder buffer, String str) {
        if (buffer.length() > 0) {
            buffer.append(", ");
        }
        buffer.append(str);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel destination, int flags) {
        destination.writeBoolean(mIsMuted);
        destination.writeInt(mRoute);
        destination.writeInt(mSupportedRouteMask);
        writeBluetoothDevice(mActiveBluetoothDevice, destination);
        destination.writeInt(mSupportedBluetoothDevices.size());
        for (BluetoothDevice d : mSupportedBluetoothDevices) {
            writeBluetoothDevice(d, destination);
        }
    }

    private static void writeBluetoothDevice(BluetoothDevice d, Parcel out) {
        if (d == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeString(d.getAddress());
            out.writeString(d.getName());
            out.writeInt(d.getType());
        }
    }

    private static BluetoothDevice readBluetoothDevice(Parcel in) {
        if (!in.readBoolean()) {
            return null;
        }
        String address = in.readString();
        String name = in.readString();
        int type = in.readInt();
        return BluetoothDevice.shirazObserved(address, name, type);
    }

    public static final Parcelable.Creator<CallAudioState> CREATOR =
            new Parcelable.Creator<CallAudioState>() {
                @Override
                public CallAudioState createFromParcel(Parcel source) {
                    boolean isMuted = source.readBoolean();
                    int route = source.readInt();
                    int supportedRouteMask = source.readInt();
                    BluetoothDevice active = readBluetoothDevice(source);
                    int count = source.readInt();
                    ArrayList<BluetoothDevice> supported = new ArrayList<BluetoothDevice>(count);
                    for (int i = 0; i < count; i++) {
                        BluetoothDevice d = readBluetoothDevice(source);
                        if (d != null) {
                            supported.add(d);
                        }
                    }
                    return new CallAudioState(
                            isMuted, route, supportedRouteMask, active, supported);
                }

                @Override
                public CallAudioState[] newArray(int size) {
                    return new CallAudioState[size];
                }
            };
}
