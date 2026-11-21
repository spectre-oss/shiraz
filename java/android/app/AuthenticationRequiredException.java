// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class AuthenticationRequiredException extends SecurityException implements Parcelable {
    private final PendingIntent mUserAction;

    public AuthenticationRequiredException(Throwable throwable, PendingIntent userAction) {
        super(throwable != null ? throwable.getMessage() : null, throwable);
        mUserAction = Objects.requireNonNull(userAction, "userAction");
    }

    private AuthenticationRequiredException(Parcel in) {
        this(new SecurityException(in.readString()), PendingIntent.CREATOR.createFromParcel(in));
    }

    public PendingIntent getUserAction() {
        return mUserAction;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(getMessage());
        mUserAction.writeToParcel(dest, flags);
    }

    public static final Parcelable.Creator<AuthenticationRequiredException> CREATOR =
            new Parcelable.Creator<AuthenticationRequiredException>() {
                @Override
                public AuthenticationRequiredException createFromParcel(Parcel in) {
                    return new AuthenticationRequiredException(in);
                }

                @Override
                public AuthenticationRequiredException[] newArray(int size) {
                    return new AuthenticationRequiredException[size];
                }
            };
}
