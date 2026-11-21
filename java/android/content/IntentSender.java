// SPDX-License-Identifier: GPL-3.0-only
package android.content;

import android.app.PendingIntent;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;
import java.util.Objects;

/** In-process and host-backed pending intent completion target. */
public class IntentSender implements Parcelable {
    private final PendingIntent token;

    public IntentSender(PendingIntent token) {
        this.token = token;
    }

    public void sendIntent(
            Context context,
            final int code,
            final Intent intent,
            final OnFinished finished,
            Handler handler)
            throws SendIntentException {
        sendIntent(context, code, intent, finished, handler, null);
    }

    public void sendIntent(
            Context context,
            final int code,
            final Intent intent,
            final OnFinished finished,
            Handler handler,
            String requiredPermission)
            throws SendIntentException {
        try {
            if (token != null) {
                token.shirazSend(intent);
            }
            if (finished != null) {
                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        finished.onSendFinished(IntentSender.this, intent, code, null, null);
                    }
                };
                if (handler == null) {
                    r.run();
                } else {
                    handler.post(r);
                }
            }
        } catch (PendingIntent.CanceledException e) {
            throw new SendIntentException(e);
        }
    }

    @Deprecated
    public String getTargetPackage() {
        return getCreatorPackage();
    }

    public String getCreatorPackage() {
        return token != null ? token.getCreatorPackage() : null;
    }

    public int getCreatorUid() {
        return token != null ? token.getCreatorUid() : -1;
    }

    public UserHandle getCreatorUserHandle() {
        int uid = getCreatorUid();
        return uid >= 0 ? UserHandle.getUserHandleForUid(uid) : null;
    }

    boolean isImmutable() {
        return token != null && token.isImmutable();
    }

    @Override
    public boolean equals(Object otherObj) {
        if (this == otherObj) return true;
        if (!(otherObj instanceof IntentSender)) return false;
        return Objects.equals(token, ((IntentSender) otherObj).token);
    }

    @Override
    public int hashCode() {
        return token != null ? token.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "IntentSender{" + Integer.toHexString(System.identityHashCode(this))
                + ": " + token + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeTypedObject(token, flags);
    }

    public static void writeIntentSenderOrNullToParcel(IntentSender sender, Parcel out) {
        out.writeTypedObject(sender != null ? sender.token : null, 0);
    }

    public static IntentSender readIntentSenderOrNullFromParcel(Parcel in) {
        PendingIntent pi = in.readTypedObject(PendingIntent.CREATOR);
        return pi != null ? new IntentSender(pi) : null;
    }

    public static final Parcelable.Creator<IntentSender> CREATOR =
            new Parcelable.Creator<IntentSender>() {
                @Override
                public IntentSender createFromParcel(Parcel in) {
                    PendingIntent pi = in.readTypedObject(PendingIntent.CREATOR);
                    return pi != null ? new IntentSender(pi) : null;
                }

                @Override
                public IntentSender[] newArray(int size) {
                    return new IntentSender[size];
                }
            };

    public interface OnFinished {
        void onSendFinished(
                IntentSender sender,
                Intent intent,
                int resultCode,
                String resultData,
                android.os.Bundle resultExtras);
    }

    public static class SendIntentException extends android.util.AndroidException {
        public SendIntentException() {
        }

        public SendIntentException(String name) {
            super(name);
        }

        public SendIntentException(Exception cause) {
            super(cause);
        }
    }
}
