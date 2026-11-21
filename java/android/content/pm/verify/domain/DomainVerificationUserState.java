// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm.verify.domain;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Per-user App Links domain verification state for a package. */
public final class DomainVerificationUserState implements Parcelable {
    public static final int DOMAIN_STATE_NONE = 0;
    public static final int DOMAIN_STATE_SELECTED = 1;
    public static final int DOMAIN_STATE_VERIFIED = 2;

    private final String mPackageName;
    private final UserHandle mUser;
    private final boolean mLinkHandlingAllowed;
    private final Map<String, Integer> mHostToStateMap;

    public DomainVerificationUserState(
            String packageName,
            UserHandle user,
            boolean linkHandlingAllowed,
            Map<String, Integer> hostToStateMap) {
        mPackageName = Objects.requireNonNull(packageName);
        mUser = Objects.requireNonNull(user);
        mLinkHandlingAllowed = linkHandlingAllowed;
        mHostToStateMap = Collections.unmodifiableMap(
                new LinkedHashMap<String, Integer>(
                        hostToStateMap != null ? hostToStateMap : Collections.<String, Integer>emptyMap()));
    }

    DomainVerificationUserState(Parcel in) {
        mPackageName = in.readString();
        mUser = UserHandle.readFromParcel(in);
        mLinkHandlingAllowed = in.readInt() != 0;
        int size = in.readInt();
        Map<String, Integer> map = new LinkedHashMap<String, Integer>(Math.max(0, size));
        for (int i = 0; i < size; i++) {
            String host = in.readString();
            int state = in.readInt();
            map.put(host, Integer.valueOf(state));
        }
        mHostToStateMap = Collections.unmodifiableMap(map);
    }

    public String getPackageName() {
        return mPackageName;
    }

    public UserHandle getUser() {
        return mUser;
    }

    public boolean isLinkHandlingAllowed() {
        return mLinkHandlingAllowed;
    }

    public Map<String, Integer> getHostToStateMap() {
        return mHostToStateMap;
    }

    @Override
    public String toString() {
        return "DomainVerificationUserState { packageName = " + mPackageName
                + ", user = " + mUser
                + ", linkHandlingAllowed = " + mLinkHandlingAllowed
                + ", hostToStateMap = " + mHostToStateMap + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DomainVerificationUserState)) return false;
        DomainVerificationUserState that = (DomainVerificationUserState) o;
        return mLinkHandlingAllowed == that.mLinkHandlingAllowed
                && Objects.equals(mPackageName, that.mPackageName)
                && Objects.equals(mUser, that.mUser)
                && Objects.equals(mHostToStateMap, that.mHostToStateMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mPackageName, mUser, Boolean.valueOf(mLinkHandlingAllowed), mHostToStateMap);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mPackageName);
        UserHandle.writeToParcel(mUser, dest);
        dest.writeInt(mLinkHandlingAllowed ? 1 : 0);
        dest.writeInt(mHostToStateMap.size());
        for (Map.Entry<String, Integer> entry : mHostToStateMap.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeInt(entry.getValue().intValue());
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<DomainVerificationUserState> CREATOR =
            new Creator<DomainVerificationUserState>() {
                @Override
                public DomainVerificationUserState createFromParcel(Parcel in) {
                    return new DomainVerificationUserState(in);
                }

                @Override
                public DomainVerificationUserState[] newArray(int size) {
                    return new DomainVerificationUserState[size];
                }
            };
}
