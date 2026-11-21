// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/** Exception indicating failure of a {@link CallEndpoint} switch operation. */
public final class CallEndpointException extends RuntimeException implements Parcelable {
    public static final int ERROR_ENDPOINT_DOES_NOT_EXIST = 1;
    public static final int ERROR_REQUEST_TIME_OUT = 2;
    public static final int ERROR_ANOTHER_REQUEST = 3;
    public static final int ERROR_UNSPECIFIED = 4;

    private final int mCode;
    private final String mMessage;

    public CallEndpointException(String message, int code) {
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

    public static final Parcelable.Creator<CallEndpointException> CREATOR =
            new Parcelable.Creator<CallEndpointException>() {
                @Override
                public CallEndpointException createFromParcel(Parcel source) {
                    return new CallEndpointException(source.readString(), source.readInt());
                }

                @Override
                public CallEndpointException[] newArray(int size) {
                    return new CallEndpointException[size];
                }
            };
}
