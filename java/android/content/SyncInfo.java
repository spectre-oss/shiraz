// SPDX-License-Identifier: GPL-3.0-only
package android.content;

import android.accounts.Account;
import android.os.Parcel;
import android.os.Parcelable;

/** Information about an active sync operation. */
public class SyncInfo implements Parcelable {
    public final int authorityId;
    public final Account account;
    public final String authority;
    public final long startTime;

    public SyncInfo(int authorityId, Account account, String authority, long startTime) {
        this.authorityId = authorityId;
        this.account = account;
        this.authority = authority;
        this.startTime = startTime;
    }

    public SyncInfo(SyncInfo other) {
        this.authorityId = other.authorityId;
        this.account = other.account != null ? new Account(other.account.name, other.account.type) : null;
        this.authority = other.authority;
        this.startTime = other.startTime;
    }

    SyncInfo(Parcel parcel) {
        authorityId = parcel.readInt();
        account = parcel.readInt() != 0 ? Account.CREATOR.createFromParcel(parcel) : null;
        authority = parcel.readString();
        startTime = parcel.readLong();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(authorityId);
        if (account != null) {
            parcel.writeInt(1);
            account.writeToParcel(parcel, flags);
        } else {
            parcel.writeInt(0);
        }
        parcel.writeString(authority);
        parcel.writeLong(startTime);
    }

    public static final Creator<SyncInfo> CREATOR = new Creator<SyncInfo>() {
        @Override
        public SyncInfo createFromParcel(Parcel in) {
            return new SyncInfo(in);
        }

        @Override
        public SyncInfo[] newArray(int size) {
            return new SyncInfo[size];
        }
    };
}
