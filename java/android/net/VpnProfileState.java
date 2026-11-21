// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Current connection state of a provisioned {@link PlatformVpnProfile}. */
public final class VpnProfileState implements Parcelable {
    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_FAILED = 3;

    private final int mState;
    private final String mSessionKey;
    private final boolean mAlwaysOn;
    private final boolean mLockdown;

    public VpnProfileState(int state, String sessionKey, boolean alwaysOn, boolean lockdown) {
        this.mState = state;
        this.mSessionKey = sessionKey;
        this.mAlwaysOn = alwaysOn;
        this.mLockdown = lockdown;
    }

    public int getState() {
        return mState;
    }

    public String getSessionId() {
        return mSessionKey;
    }

    public boolean isAlwaysOn() {
        return mAlwaysOn;
    }

    public boolean isLockdownEnabled() {
        return mLockdown;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mState);
        out.writeString(mSessionKey);
        out.writeBoolean(mAlwaysOn);
        out.writeBoolean(mLockdown);
    }

    @Override
    public String toString() {
        return "VpnProfileState{state="
                + mState
                + ", session="
                + mSessionKey
                + ", alwaysOn="
                + mAlwaysOn
                + ", lockdown="
                + mLockdown
                + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof VpnProfileState)) {
            return false;
        }
        VpnProfileState that = (VpnProfileState) obj;
        return mState == that.mState
                && mAlwaysOn == that.mAlwaysOn
                && mLockdown == that.mLockdown
                && Objects.equals(mSessionKey, that.mSessionKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mState),
                mSessionKey,
                Boolean.valueOf(mAlwaysOn),
                Boolean.valueOf(mLockdown));
    }

    public static final Parcelable.Creator<VpnProfileState> CREATOR =
            new Parcelable.Creator<VpnProfileState>() {
                @Override
                public VpnProfileState createFromParcel(Parcel in) {
                    return new VpnProfileState(
                            in.readInt(),
                            in.readString(),
                            in.readBoolean(),
                            in.readBoolean());
                }

                @Override
                public VpnProfileState[] newArray(int size) {
                    return new VpnProfileState[size];
                }
            };
}
