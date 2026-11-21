// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class SyncNotedAppOp implements Parcelable {
    private final int mOpCode;
    private final String mOp;
    private final String mAttributionTag;

    public SyncNotedAppOp(int opCode, String attributionTag) {
        mOpCode = opCode;
        mOp = opCodeToName(opCode);
        mAttributionTag = attributionTag;
    }

    SyncNotedAppOp(String op, String attributionTag) {
        mOpCode = -1;
        mOp = Objects.requireNonNull(op, "op");
        mAttributionTag = attributionTag;
    }

    private SyncNotedAppOp(Parcel in) {
        mOpCode = in.readInt();
        mOp = in.readString();
        mAttributionTag = in.readString();
    }

    private static String opCodeToName(int code) {
        switch (code) {
            case 0: return AppOpsManager.OPSTR_COARSE_LOCATION;
            case 1: return AppOpsManager.OPSTR_FINE_LOCATION;
            case 4: return "android:read_contacts";
            case 5: return "android:write_contacts";
            case 6: return "android:read_call_log";
            case 7: return "android:write_call_log";
            case 8: return "android:read_calendar";
            case 9: return "android:write_calendar";
            case 13: return "android:call_phone";
            case 14: return "android:read_sms";
            case 16: return "android:receive_sms";
            case 20: return "android:send_sms";
            case 23: return "android:write_settings";
            case 24: return AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW;
            case 26: return AppOpsManager.OPSTR_CAMERA;
            case 27: return AppOpsManager.OPSTR_RECORD_AUDIO;
            default: return "android:op_" + code;
        }
    }

    public String getOp() {
        return mOp;
    }

    public String getAttributionTag() {
        return mAttributionTag;
    }

    @Override
    public String toString() {
        return "SyncNotedAppOp(op=" + mOp + ", attributionTag=" + mAttributionTag + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SyncNotedAppOp)) {
            return false;
        }
        SyncNotedAppOp that = (SyncNotedAppOp) o;
        return Objects.equals(mOp, that.mOp)
                && Objects.equals(mAttributionTag, that.mAttributionTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mOp, mAttributionTag);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mOpCode);
        dest.writeString(mOp);
        dest.writeString(mAttributionTag);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<SyncNotedAppOp> CREATOR =
            new Parcelable.Creator<SyncNotedAppOp>() {
                @Override
                public SyncNotedAppOp createFromParcel(Parcel in) {
                    return new SyncNotedAppOp(in);
                }

                @Override
                public SyncNotedAppOp[] newArray(int size) {
                    return new SyncNotedAppOp[size];
                }
            };
}
