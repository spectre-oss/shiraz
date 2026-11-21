// SPDX-License-Identifier: GPL-3.0-only
package android.content;

import android.accounts.Account;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Value type describing a periodic sync schedule for an Account and authority. */
public class PeriodicSync implements Parcelable {
    public final Account account;
    public final String authority;
    public final Bundle extras;
    public final long period;

    public PeriodicSync(Account account, String authority, Bundle extras, long periodInSeconds) {
        this.account = account;
        this.authority = authority;
        this.extras = extras == null ? new Bundle() : new Bundle(extras);
        this.period = periodInSeconds;
    }

    private PeriodicSync(Parcel in) {
        this.account = in.readInt() != 0 ? Account.CREATOR.createFromParcel(in) : null;
        this.authority = in.readString();
        this.extras = in.readBundle();
        this.period = in.readLong();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (account != null) {
            dest.writeInt(1);
            account.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        dest.writeString(authority);
        dest.writeBundle(extras);
        dest.writeLong(period);
    }

    public static final Parcelable.Creator<PeriodicSync> CREATOR =
            new Parcelable.Creator<PeriodicSync>() {
                @Override
                public PeriodicSync createFromParcel(Parcel source) {
                    return new PeriodicSync(source);
                }

                @Override
                public PeriodicSync[] newArray(int size) {
                    return new PeriodicSync[size];
                }
            };

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof PeriodicSync)) return false;
        final PeriodicSync other = (PeriodicSync) o;
        return Objects.equals(account, other.account)
                && Objects.equals(authority, other.authority)
                && period == other.period
                && syncExtrasEquals(extras, other.extras);
    }

    private static boolean syncExtrasEquals(Bundle b1, Bundle b2) {
        if (b1 == b2) return true;
        if (b1 == null || b2 == null) return false;
        if (b1.size() != b2.size()) return false;
        for (String key : b1.keySet()) {
            if (!b2.containsKey(key)) return false;
            if (!Objects.equals(b1.get(key), b2.get(key))) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "account: " + account + ", authority: " + authority
                + ". period: " + period + "s, extras: " + extras;
    }
}
