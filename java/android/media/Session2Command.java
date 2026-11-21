// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.Objects;

public final class Session2Command implements Parcelable {
    public static final int COMMAND_CODE_CUSTOM = 0;

    private final int mCommandCode;
    private final String mCustomAction;
    private final Bundle mCustomExtras;

    public Session2Command(int commandCode) {
        if (commandCode == COMMAND_CODE_CUSTOM) {
            throw new IllegalArgumentException("commandCode shouldn't be COMMAND_CODE_CUSTOM");
        }
        mCommandCode = commandCode;
        mCustomAction = null;
        mCustomExtras = null;
    }

    public Session2Command(String action, Bundle extras) {
        if (action == null) {
            throw new IllegalArgumentException("action shouldn't be null");
        }
        mCommandCode = COMMAND_CODE_CUSTOM;
        mCustomAction = action;
        mCustomExtras = extras != null ? new Bundle(extras) : null;
    }

    Session2Command(Parcel in) {
        mCommandCode = in.readInt();
        mCustomAction = in.readString();
        mCustomExtras = in.readBundle();
    }

    public int getCommandCode() {
        return mCommandCode;
    }

    public String getCustomAction() {
        return mCustomAction;
    }

    public Bundle getCustomExtras() {
        return mCustomExtras != null ? new Bundle(mCustomExtras) : null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeInt(mCommandCode);
        dest.writeString(mCustomAction);
        dest.writeBundle(mCustomExtras);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Session2Command)) return false;
        Session2Command other = (Session2Command) obj;
        return mCommandCode == other.mCommandCode
                && TextUtils.equals(mCustomAction, other.mCustomAction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mCustomAction, Integer.valueOf(mCommandCode));
    }

    public static final Parcelable.Creator<Session2Command> CREATOR =
            new Parcelable.Creator<Session2Command>() {
                @Override
                public Session2Command createFromParcel(Parcel in) {
                    return new Session2Command(in);
                }

                @Override
                public Session2Command[] newArray(int size) {
                    return new Session2Command[size];
                }
            };

    public static final class Result {
        public static final int RESULT_ERROR_UNKNOWN_ERROR = -1;
        public static final int RESULT_SUCCESS = 0;
        public static final int RESULT_INFO_SKIPPED = 1;

        private final int mResultCode;
        private final Bundle mResultData;

        public Result(int resultCode, Bundle resultData) {
            mResultCode = resultCode;
            mResultData = resultData != null ? new Bundle(resultData) : null;
        }

        public int getResultCode() {
            return mResultCode;
        }

        public Bundle getResultData() {
            return mResultData != null ? new Bundle(mResultData) : null;
        }
    }
}
