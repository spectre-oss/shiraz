// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.Objects;

public final class RecoverableSecurityException extends SecurityException implements Parcelable {
    private final CharSequence mUserMessage;
    private final RemoteAction mUserAction;

    public RecoverableSecurityException(Throwable cause, CharSequence userMessage, RemoteAction userAction) {
        super(cause != null ? cause.getMessage() : null, cause);
        mUserMessage = Objects.requireNonNull(userMessage, "userMessage");
        mUserAction = Objects.requireNonNull(userAction, "userAction");
    }

    private RecoverableSecurityException(Parcel in) {
        this(new SecurityException(in.readString()),
                TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in),
                RemoteAction.CREATOR.createFromParcel(in));
    }

    public CharSequence getUserMessage() {
        return mUserMessage;
    }

    public RemoteAction getUserAction() {
        return mUserAction;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(getMessage());
        TextUtils.writeToParcel(mUserMessage, dest, flags);
        mUserAction.writeToParcel(dest, flags);
    }

    public static final Parcelable.Creator<RecoverableSecurityException> CREATOR =
            new Parcelable.Creator<RecoverableSecurityException>() {
                @Override
                public RecoverableSecurityException createFromParcel(Parcel source) {
                    return new RecoverableSecurityException(source);
                }

                @Override
                public RecoverableSecurityException[] newArray(int size) {
                    return new RecoverableSecurityException[size];
                }
            };
}
