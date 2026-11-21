// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/** Exception reported during transactional {@link CallControl} and {@link TelecomManager#addCall} operations. */
public final class CallException extends RuntimeException implements Parcelable {
    public static final int CODE_ERROR_UNKNOWN = 1;
    public static final int CODE_CANNOT_HOLD_CURRENT_ACTIVE_CALL = 2;
    public static final int CODE_CALL_IS_NOT_BEING_TRACKED = 3;
    public static final int CODE_CALL_CANNOT_BE_SET_TO_ACTIVE = 4;
    public static final int CODE_CALL_NOT_PERMITTED_AT_PRESENT_TIME = 5;
    public static final int CODE_OPERATION_TIMED_OUT = 6;

    private final int mCode;
    private final String mMessage;

    public CallException(String message, int code) {
        super(getMessage(message, code));
        this.mCode = code;
        this.mMessage = message;
    }

    private static String getMessage(String message, int code) {
        if (!TextUtils.isEmpty(message)) {
            return message + " (code: " + code + ")";
        }
        return "code: " + code;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mMessage);
        dest.writeInt(mCode);
    }

    public int getCode() {
        return mCode;
    }

    public static final Parcelable.Creator<CallException> CREATOR =
            new Parcelable.Creator<CallException>() {
                @Override
                public CallException createFromParcel(Parcel source) {
                    return new CallException(source.readString(), source.readInt());
                }

                @Override
                public CallException[] newArray(int size) {
                    return new CallException[size];
                }
            };
}
