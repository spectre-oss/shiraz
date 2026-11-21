// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Cause code, user-visible labels, and tone describing why a call disconnected. */
public final class DisconnectCause implements Parcelable {
    public static final int UNKNOWN = 0;
    public static final int ERROR = 1;
    public static final int LOCAL = 2;
    public static final int REMOTE = 3;
    public static final int CANCELED = 4;
    public static final int MISSED = 5;
    public static final int REJECTED = 6;
    public static final int BUSY = 7;
    public static final int RESTRICTED = 8;
    public static final int OTHER = 9;
    public static final int CONNECTION_MANAGER_NOT_SUPPORTED = 10;
    public static final int ANSWERED_ELSEWHERE = 11;
    public static final int CALL_PULLED = 12;

    public static final String REASON_WIFI_ON_BUT_WFC_OFF = "REASON_WIFI_ON_BUT_WFC_OFF";
    public static final String REASON_IMS_ACCESS_BLOCKED = "REASON_IMS_ACCESS_BLOCKED";
    public static final String REASON_EMULATING_SINGLE_CALL = "EMULATING_SINGLE_CALL";
    public static final String REASON_EMERGENCY_CALL_PLACED = "REASON_EMERGENCY_CALL_PLACED";

    private static final int TONE_UNKNOWN = -1;

    private final int mDisconnectCode;
    private final CharSequence mDisconnectLabel;
    private final CharSequence mDisconnectDescription;
    private final String mDisconnectReason;
    private final int mToneToPlay;

    public DisconnectCause(int code) {
        this(code, null, null, null, TONE_UNKNOWN);
    }

    public DisconnectCause(int code, String reason) {
        this(code, null, null, reason, TONE_UNKNOWN);
    }

    public DisconnectCause(
            int code, CharSequence label, CharSequence description, String reason) {
        this(code, label, description, reason, TONE_UNKNOWN);
    }

    public DisconnectCause(
            int code,
            CharSequence label,
            CharSequence description,
            String reason,
            int toneToPlay) {
        this.mDisconnectCode = code;
        this.mDisconnectLabel = label;
        this.mDisconnectDescription = description;
        this.mDisconnectReason = reason;
        this.mToneToPlay = toneToPlay;
    }

    private DisconnectCause(Parcel in) {
        this.mDisconnectCode = in.readInt();
        this.mDisconnectLabel = in.readString();
        this.mDisconnectDescription = in.readString();
        this.mDisconnectReason = in.readString();
        this.mToneToPlay = in.readInt();
    }

    public int getCode() {
        return mDisconnectCode;
    }

    public CharSequence getLabel() {
        return mDisconnectLabel;
    }

    public CharSequence getDescription() {
        return mDisconnectDescription;
    }

    public String getReason() {
        return mDisconnectReason;
    }

    public int getTone() {
        return mToneToPlay;
    }

    @Override
    public void writeToParcel(Parcel destination, int flags) {
        destination.writeInt(mDisconnectCode);
        destination.writeString(
                mDisconnectLabel == null ? null : mDisconnectLabel.toString());
        destination.writeString(
                mDisconnectDescription == null ? null : mDisconnectDescription.toString());
        destination.writeString(mDisconnectReason);
        destination.writeInt(mToneToPlay);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mDisconnectCode),
                mDisconnectLabel == null ? null : mDisconnectLabel.toString(),
                mDisconnectDescription == null ? null : mDisconnectDescription.toString(),
                mDisconnectReason,
                Integer.valueOf(mToneToPlay));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DisconnectCause)) {
            return false;
        }
        DisconnectCause d = (DisconnectCause) o;
        return mDisconnectCode == d.mDisconnectCode
                && Objects.equals(
                        mDisconnectLabel == null ? null : mDisconnectLabel.toString(),
                        d.mDisconnectLabel == null ? null : d.mDisconnectLabel.toString())
                && Objects.equals(
                        mDisconnectDescription == null
                                ? null
                                : mDisconnectDescription.toString(),
                        d.mDisconnectDescription == null
                                ? null
                                : d.mDisconnectDescription.toString())
                && Objects.equals(mDisconnectReason, d.mDisconnectReason)
                && mToneToPlay == d.mToneToPlay;
    }

    @Override
    public String toString() {
        return "DisconnectCause [ Code: ("
                + codeToString(mDisconnectCode)
                + ") Label: ("
                + (mDisconnectLabel == null ? "" : mDisconnectLabel)
                + ") Description: ("
                + (mDisconnectDescription == null ? "" : mDisconnectDescription)
                + ") Reason: ("
                + (mDisconnectReason == null ? "" : mDisconnectReason)
                + ") Tone: ("
                + mToneToPlay
                + ") ]";
    }

    private static String codeToString(int code) {
        switch (code) {
            case UNKNOWN:
                return "UNKNOWN";
            case ERROR:
                return "ERROR";
            case LOCAL:
                return "LOCAL";
            case REMOTE:
                return "REMOTE";
            case CANCELED:
                return "CANCELED";
            case MISSED:
                return "MISSED";
            case REJECTED:
                return "REJECTED";
            case BUSY:
                return "BUSY";
            case RESTRICTED:
                return "RESTRICTED";
            case OTHER:
                return "OTHER";
            case CONNECTION_MANAGER_NOT_SUPPORTED:
                return "CONNECTION_MANAGER_NOT_SUPPORTED";
            case ANSWERED_ELSEWHERE:
                return "ANSWERED_ELSEWHERE";
            case CALL_PULLED:
                return "CALL_PULLED";
            default:
                return "INVALID: " + code;
        }
    }

    public static final Parcelable.Creator<DisconnectCause> CREATOR =
            new Parcelable.Creator<DisconnectCause>() {
                @Override
                public DisconnectCause createFromParcel(Parcel source) {
                    return new DisconnectCause(source);
                }

                @Override
                public DisconnectCause[] newArray(int size) {
                    return new DisconnectCause[size];
                }
            };
}
