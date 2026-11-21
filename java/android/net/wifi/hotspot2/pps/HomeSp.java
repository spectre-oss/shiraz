// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.hotspot2.pps;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public final class HomeSp implements Parcelable {
    public static final Creator<HomeSp> CREATOR =
            new Creator<HomeSp>() {
                @Override
                public HomeSp createFromParcel(Parcel in) {
                    HomeSp sp = new HomeSp();
                    sp.mFqdn = in.readString();
                    sp.mFriendlyName = in.readString();
                    sp.mRoamingConsortiumOis = in.createLongArray();
                    sp.mMatchAllOis = in.createLongArray();
                    sp.mMatchAnyOis = in.createLongArray();
                    String[] partners = in.createStringArray();
                    sp.mOtherHomePartners =
                            partners == null
                                    ? Collections.<String>emptyList()
                                    : Collections.unmodifiableList(Arrays.asList(partners));
                    return sp;
                }

                @Override
                public HomeSp[] newArray(int size) {
                    return new HomeSp[size];
                }
            };

    private String mFqdn;
    private String mFriendlyName;
    private long[] mRoamingConsortiumOis;
    private long[] mMatchAllOis;
    private long[] mMatchAnyOis;
    private Collection<String> mOtherHomePartners = Collections.emptyList();

    public HomeSp() {}

    public HomeSp(HomeSp source) {
        if (source != null) {
            this.mFqdn = source.mFqdn;
            this.mFriendlyName = source.mFriendlyName;
            this.mRoamingConsortiumOis =
                    source.mRoamingConsortiumOis == null
                            ? null
                            : source.mRoamingConsortiumOis.clone();
            this.mMatchAllOis = source.mMatchAllOis == null ? null : source.mMatchAllOis.clone();
            this.mMatchAnyOis = source.mMatchAnyOis == null ? null : source.mMatchAnyOis.clone();
            this.mOtherHomePartners =
                    source.mOtherHomePartners == null
                            ? Collections.<String>emptyList()
                            : Collections.unmodifiableList(
                                    new ArrayList<String>(source.mOtherHomePartners));
        }
    }

    public void setFqdn(String fqdn) {
        this.mFqdn = fqdn;
    }

    public String getFqdn() {
        return mFqdn;
    }

    public void setFriendlyName(String friendlyName) {
        this.mFriendlyName = friendlyName;
    }

    public String getFriendlyName() {
        return mFriendlyName;
    }

    public void setMatchAllOis(long[] matchAllOis) {
        this.mMatchAllOis = matchAllOis == null ? null : matchAllOis.clone();
    }

    public long[] getMatchAllOis() {
        return mMatchAllOis == null ? null : mMatchAllOis.clone();
    }

    public void setMatchAnyOis(long[] matchAnyOis) {
        this.mMatchAnyOis = matchAnyOis == null ? null : matchAnyOis.clone();
    }

    public long[] getMatchAnyOis() {
        return mMatchAnyOis == null ? null : mMatchAnyOis.clone();
    }

    public void setOtherHomePartnersList(Collection<String> otherHomePartners) {
        if (otherHomePartners == null) {
            return;
        }
        this.mOtherHomePartners =
                Collections.unmodifiableList(new ArrayList<String>(otherHomePartners));
    }

    public Collection<String> getOtherHomePartnersList() {
        return mOtherHomePartners;
    }

    public void setRoamingConsortiumOis(long[] roamingConsortiumOis) {
        this.mRoamingConsortiumOis =
                roamingConsortiumOis == null ? null : roamingConsortiumOis.clone();
    }

    public long[] getRoamingConsortiumOis() {
        return mRoamingConsortiumOis == null ? null : mRoamingConsortiumOis.clone();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mFqdn);
        dest.writeString(mFriendlyName);
        dest.writeLongArray(mRoamingConsortiumOis);
        dest.writeLongArray(mMatchAllOis);
        dest.writeLongArray(mMatchAnyOis);
        dest.writeStringArray(
                mOtherHomePartners == null
                        ? null
                        : mOtherHomePartners.toArray(new String[0]));
    }

    @Override
    public boolean equals(Object thatObject) {
        if (this == thatObject) {
            return true;
        }
        if (!(thatObject instanceof HomeSp)) {
            return false;
        }
        HomeSp that = (HomeSp) thatObject;
        return Objects.equals(mFqdn, that.mFqdn)
                && Objects.equals(mFriendlyName, that.mFriendlyName)
                && Arrays.equals(mRoamingConsortiumOis, that.mRoamingConsortiumOis)
                && Arrays.equals(mMatchAllOis, that.mMatchAllOis)
                && Arrays.equals(mMatchAnyOis, that.mMatchAnyOis)
                && Objects.equals(mOtherHomePartners, that.mOtherHomePartners);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mFqdn,
                mFriendlyName,
                Integer.valueOf(Arrays.hashCode(mRoamingConsortiumOis)),
                Integer.valueOf(Arrays.hashCode(mMatchAllOis)),
                Integer.valueOf(Arrays.hashCode(mMatchAnyOis)),
                mOtherHomePartners);
    }

    @Override
    public String toString() {
        return "HomeSp{fqdn=" + mFqdn + ", friendlyName=" + mFriendlyName + "}";
    }
}
