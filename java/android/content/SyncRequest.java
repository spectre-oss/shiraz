// SPDX-License-Identifier: GPL-3.0-only
package android.content;

import android.accounts.Account;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/** Encapsulates parameters for a one-off or periodic content sync request. */
public class SyncRequest implements Parcelable {
    private final Account mAccountToSync;
    private final String mAuthority;
    private final Bundle mExtras;
    private final boolean mDisallowMetered;
    private final long mSyncFlexTimeSecs;
    private final long mSyncRunTimeSecs;
    private final boolean mIsPeriodic;
    private final boolean mIsAuthority;
    private final boolean mIsExpedited;
    private final boolean mIsScheduledAsExpeditedJob;

    SyncRequest(Builder b) {
        mSyncFlexTimeSecs = b.mSyncFlexTimeSecs;
        mSyncRunTimeSecs = b.mSyncRunTimeSecs;
        mAccountToSync = b.mAccount;
        mAuthority = b.mAuthority;
        mIsPeriodic = (b.mSyncType == Builder.SYNC_TYPE_PERIODIC);
        mIsAuthority = (b.mSyncTarget == Builder.SYNC_TARGET_ADAPTER);
        mIsExpedited = b.mExpedited;
        mIsScheduledAsExpeditedJob = b.mScheduleAsExpeditedJob;
        mExtras = new Bundle(b.mCustomExtras);
        mExtras.putAll(b.mSyncConfigExtras);
        mDisallowMetered = b.mDisallowMetered;
    }

    private SyncRequest(Parcel in) {
        mExtras = in.readBundle();
        mSyncFlexTimeSecs = in.readLong();
        mSyncRunTimeSecs = in.readLong();
        mIsPeriodic = (in.readInt() != 0);
        mDisallowMetered = (in.readInt() != 0);
        mIsAuthority = (in.readInt() != 0);
        mIsExpedited = (in.readInt() != 0);
        mIsScheduledAsExpeditedJob = (in.readInt() != 0);
        mAccountToSync = in.readInt() != 0 ? Account.CREATOR.createFromParcel(in) : null;
        mAuthority = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeBundle(mExtras);
        parcel.writeLong(mSyncFlexTimeSecs);
        parcel.writeLong(mSyncRunTimeSecs);
        parcel.writeInt(mIsPeriodic ? 1 : 0);
        parcel.writeInt(mDisallowMetered ? 1 : 0);
        parcel.writeInt(mIsAuthority ? 1 : 0);
        parcel.writeInt(mIsExpedited ? 1 : 0);
        parcel.writeInt(mIsScheduledAsExpeditedJob ? 1 : 0);
        if (mAccountToSync != null) {
            parcel.writeInt(1);
            mAccountToSync.writeToParcel(parcel, flags);
        } else {
            parcel.writeInt(0);
        }
        parcel.writeString(mAuthority);
    }

    public static final Creator<SyncRequest> CREATOR = new Creator<SyncRequest>() {
        @Override
        public SyncRequest createFromParcel(Parcel in) {
            return new SyncRequest(in);
        }

        @Override
        public SyncRequest[] newArray(int size) {
            return new SyncRequest[size];
        }
    };

    public static class Builder {
        static final int SYNC_TYPE_UNKNOWN = 0;
        static final int SYNC_TYPE_PERIODIC = 1;
        static final int SYNC_TYPE_ONCE = 2;

        static final int SYNC_TARGET_UNKNOWN = 0;
        static final int SYNC_TARGET_ADAPTER = 2;

        long mSyncFlexTimeSecs;
        long mSyncRunTimeSecs;
        Bundle mCustomExtras;
        Bundle mSyncConfigExtras;
        boolean mDisallowMetered;
        boolean mIsManual;
        boolean mNoRetry;
        boolean mIgnoreBackoff;
        boolean mIgnoreSettings;
        boolean mExpedited;
        boolean mScheduleAsExpeditedJob;
        boolean mRequiresCharging;
        int mSyncType = SYNC_TYPE_UNKNOWN;
        int mSyncTarget = SYNC_TARGET_UNKNOWN;
        Account mAccount;
        String mAuthority;

        public Builder() {}

        public Builder syncOnce() {
            if (mSyncType != SYNC_TYPE_UNKNOWN) {
                throw new IllegalArgumentException("Sync type has already been defined.");
            }
            mSyncType = SYNC_TYPE_ONCE;
            mSyncRunTimeSecs = 0;
            mSyncFlexTimeSecs = 0;
            return this;
        }

        public Builder syncPeriodic(long pollFrequency, long beforeSeconds) {
            if (mSyncType != SYNC_TYPE_UNKNOWN) {
                throw new IllegalArgumentException("Sync type has already been defined.");
            }
            mSyncType = SYNC_TYPE_PERIODIC;
            if (beforeSeconds < 0 || pollFrequency < 0 || beforeSeconds > pollFrequency) {
                throw new IllegalArgumentException("Invalid periodic sync interval/flex.");
            }
            mSyncRunTimeSecs = pollFrequency;
            mSyncFlexTimeSecs = beforeSeconds;
            return this;
        }

        public Builder setDisallowMetered(boolean disallow) {
            if (mIgnoreSettings && disallow) {
                throw new IllegalArgumentException("setDisallowMetered(true) after having specified that settings are ignored.");
            }
            mDisallowMetered = disallow;
            return this;
        }

        public Builder setRequiresCharging(boolean requiresCharging) {
            mRequiresCharging = requiresCharging;
            return this;
        }

        public Builder setSyncAdapter(Account account, String authority) {
            if (mSyncTarget != SYNC_TARGET_UNKNOWN) {
                throw new IllegalArgumentException("Sync target has already been defined.");
            }
            if (authority != null && authority.length() == 0) {
                throw new IllegalArgumentException("Authority must be non-empty");
            }
            mSyncTarget = SYNC_TARGET_ADAPTER;
            mAccount = account;
            mAuthority = authority;
            return this;
        }

        public Builder setExtras(Bundle bundle) {
            mCustomExtras = bundle;
            return this;
        }

        public Builder setNoRetry(boolean noRetry) {
            mNoRetry = noRetry;
            return this;
        }

        public Builder setIgnoreSettings(boolean ignoreSettings) {
            if (mDisallowMetered && ignoreSettings) {
                throw new IllegalArgumentException("setIgnoreSettings(true) after having specified sync settings with this builder.");
            }
            mIgnoreSettings = ignoreSettings;
            return this;
        }

        public Builder setIgnoreBackoff(boolean ignoreBackoff) {
            mIgnoreBackoff = ignoreBackoff;
            return this;
        }

        public Builder setManual(boolean isManual) {
            mIsManual = isManual;
            return this;
        }

        public Builder setExpedited(boolean expedited) {
            mExpedited = expedited;
            return this;
        }

        public Builder setScheduleAsExpeditedJob(boolean scheduleAsExpeditedJob) {
            mScheduleAsExpeditedJob = scheduleAsExpeditedJob;
            return this;
        }

        public SyncRequest build() {
            if (mCustomExtras == null) {
                mCustomExtras = new Bundle();
            }
            mSyncConfigExtras = new Bundle();
            if (mIgnoreBackoff) {
                mSyncConfigExtras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_BACKOFF, true);
            }
            if (mDisallowMetered) {
                mSyncConfigExtras.putBoolean("allow_metered", false);
            }
            if (mRequiresCharging) {
                mSyncConfigExtras.putBoolean("require_charging", true);
            }
            if (mIgnoreSettings) {
                mSyncConfigExtras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true);
            }
            if (mNoRetry) {
                mSyncConfigExtras.putBoolean(ContentResolver.SYNC_EXTRAS_DO_NOT_RETRY, true);
            }
            if (mExpedited) {
                mSyncConfigExtras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
            }
            if (mIsManual) {
                mSyncConfigExtras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_BACKOFF, true);
                mSyncConfigExtras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true);
            }
            if (mSyncType == SYNC_TYPE_PERIODIC && (mExpedited || mScheduleAsExpeditedJob)) {
                throw new IllegalArgumentException("Expedited syncs cannot be periodic");
            }
            if (mSyncTarget == SYNC_TARGET_UNKNOWN) {
                throw new IllegalArgumentException("Must specify an adapter with setSyncAdapter(Account, String)");
            }
            if (mSyncType == SYNC_TYPE_UNKNOWN) {
                throw new IllegalArgumentException("Must call either syncOnce() or syncPeriodic()");
            }
            return new SyncRequest(this);
        }
    }
}
