// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.os.Parcelable;
public abstract class InputEvent implements Parcelable {
    public abstract void recycle();
    public abstract int getDeviceId();
    public final InputDevice getDevice() { return InputDevice.getDevice(getDeviceId()); }
    public abstract long getEventTime();
    public abstract int getSource();
    public abstract void setSource(int source);
    public boolean isFromSource(int source) { return (getSource() & source) == source; }
}
