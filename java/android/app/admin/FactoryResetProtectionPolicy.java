// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FactoryResetProtectionPolicy implements Parcelable {
    private final List<String> mFactoryResetProtectionAccounts;
    private final boolean mFactoryResetProtectionEnabled;

    private FactoryResetProtectionPolicy(
            List<String> factoryResetProtectionAccounts, boolean factoryResetProtectionEnabled) {
        mFactoryResetProtectionAccounts = factoryResetProtectionAccounts == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(factoryResetProtectionAccounts));
        mFactoryResetProtectionEnabled = factoryResetProtectionEnabled;
    }

    FactoryResetProtectionPolicy(Parcel in) {
        int count = in.readInt();
        List<String> list = new ArrayList<String>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            list.add(in.readString());
        }
        mFactoryResetProtectionAccounts = Collections.unmodifiableList(list);
        mFactoryResetProtectionEnabled = in.readInt() != 0;
    }

    public List<String> getFactoryResetProtectionAccounts() {
        return mFactoryResetProtectionAccounts;
    }

    public boolean isFactoryResetProtectionEnabled() {
        return mFactoryResetProtectionEnabled;
    }

    @Override
    public String toString() {
        return "FactoryResetProtectionPolicy{"
                + "mFactoryResetProtectionAccounts=" + mFactoryResetProtectionAccounts
                + ", mFactoryResetProtectionEnabled=" + mFactoryResetProtectionEnabled
                + '}';
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mFactoryResetProtectionAccounts.size());
        for (String account : mFactoryResetProtectionAccounts) {
            dest.writeString(account);
        }
        dest.writeInt(mFactoryResetProtectionEnabled ? 1 : 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<FactoryResetProtectionPolicy> CREATOR =
            new Parcelable.Creator<FactoryResetProtectionPolicy>() {
                @Override
                public FactoryResetProtectionPolicy createFromParcel(Parcel in) {
                    return new FactoryResetProtectionPolicy(in);
                }

                @Override
                public FactoryResetProtectionPolicy[] newArray(int size) {
                    return new FactoryResetProtectionPolicy[size];
                }
            };

    public static class Builder {
        private List<String> mFactoryResetProtectionAccounts;
        private boolean mFactoryResetProtectionEnabled = true;

        public Builder() {
        }

        public Builder setFactoryResetProtectionAccounts(List<String> factoryResetProtectionAccounts) {
            if (factoryResetProtectionAccounts == null) {
                throw new NullPointerException("accounts cannot be null");
            }
            mFactoryResetProtectionAccounts = new ArrayList<String>(factoryResetProtectionAccounts);
            return this;
        }

        public Builder setFactoryResetProtectionEnabled(boolean factoryResetProtectionEnabled) {
            mFactoryResetProtectionEnabled = factoryResetProtectionEnabled;
            return this;
        }

        public FactoryResetProtectionPolicy build() {
            return new FactoryResetProtectionPolicy(
                    mFactoryResetProtectionAccounts, mFactoryResetProtectionEnabled);
        }
    }
}
