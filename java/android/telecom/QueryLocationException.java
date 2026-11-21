// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/** Exception thrown when emergency call location querying fails. */
public final class QueryLocationException extends RuntimeException implements Parcelable {
    public static final int ERROR_REQUEST_TIME_OUT = 1;
    public static final int ERROR_PREVIOUS_REQUEST_EXISTS = 2;
    public static final int ERROR_NOT_PERMITTED = 3;
    public static final int ERROR_NOT_ALLOWED_FOR_NON_EMERGENCY_CONNECTIONS = 4;
    public static final int ERROR_SERVICE_UNAVAILABLE = 5;
    public static final int ERROR_UNSPECIFIED = 6;

    private final int mCode;
    private final String mMessage;

    public QueryLocationException(String message) {
        this(message, ERROR_UNSPECIFIED);
    }

    public QueryLocationException(String message, int code) {
        super(getMessage(message, code));
        this.mCode = code;
        this.mMessage = message;
    }

    public QueryLocationException(String message, int code, Throwable cause) {
        super(getMessage(message, code), cause);
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

    public static final Parcelable.Creator<QueryLocationException> CREATOR =
            new Parcelable.Creator<QueryLocationException>() {
                @Override
                public QueryLocationException createFromParcel(Parcel source) {
                    return new QueryLocationException(source.readString(), source.readInt());
                }

                @Override
                public QueryLocationException[] newArray(int size) {
                    return new QueryLocationException[size];
                }
            };
}
