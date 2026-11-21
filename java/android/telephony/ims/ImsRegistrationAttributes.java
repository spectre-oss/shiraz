// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.ims;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class ImsRegistrationAttributes implements Parcelable {
    public static final int ATTR_EPDG_OVER_CELL_INTERNET = 1 << 0;

    private final int mRegistrationTech;
    private final int mTransportType;
    private final int mImsAttributeFlags;
    private final Set<String> mFeatureTags;
    private final SipDetails mSipDetails;

    ImsRegistrationAttributes(
            int registrationTech,
            int transportType,
            int imsAttributeFlags,
            Set<String> featureTags,
            SipDetails details) {
        mRegistrationTech = registrationTech;
        mTransportType = transportType;
        mImsAttributeFlags = imsAttributeFlags;
        mFeatureTags = featureTags != null
                ? Collections.unmodifiableSet(new HashSet<String>(featureTags))
                : Collections.<String>emptySet();
        mSipDetails = details;
    }

    private ImsRegistrationAttributes(Parcel source) {
        mRegistrationTech = source.readInt();
        mTransportType = source.readInt();
        mImsAttributeFlags = source.readInt();
        String[] tags = source.createStringArray();
        if (tags != null) {
            HashSet<String> set = new HashSet<String>(tags.length);
            Collections.addAll(set, tags);
            mFeatureTags = Collections.unmodifiableSet(set);
        } else {
            mFeatureTags = Collections.emptySet();
        }
        mSipDetails = source.readTypedObject(SipDetails.CREATOR);
    }

    public int getTransportType() {
        return mTransportType;
    }

    public int getAttributeFlags() {
        return mImsAttributeFlags;
    }

    public Set<String> getFeatureTags() {
        return mFeatureTags;
    }

    public SipDetails getSipDetails() {
        return mSipDetails;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRegistrationTech);
        dest.writeInt(mTransportType);
        dest.writeInt(mImsAttributeFlags);
        dest.writeStringArray(mFeatureTags.toArray(new String[mFeatureTags.size()]));
        dest.writeTypedObject(mSipDetails, flags);
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImsRegistrationAttributes)) return false;
        ImsRegistrationAttributes that = (ImsRegistrationAttributes) o;
        return mRegistrationTech == that.mRegistrationTech
                && mTransportType == that.mTransportType
                && mImsAttributeFlags == that.mImsAttributeFlags
                && Objects.equals(mFeatureTags, that.mFeatureTags)
                && Objects.equals(mSipDetails, that.mSipDetails);
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mRegistrationTech),
                Integer.valueOf(mTransportType),
                Integer.valueOf(mImsAttributeFlags),
                mFeatureTags,
                mSipDetails);
    }

    public String toString() {
        return "ImsRegistrationAttributes { transportType= " + mTransportType
                + ", attributeFlags=" + mImsAttributeFlags
                + ", featureTags=[" + mFeatureTags + "]"
                + ", SipDetails=" + mSipDetails + "}";
    }

    public static final Parcelable.Creator<ImsRegistrationAttributes> CREATOR =
            new Parcelable.Creator<ImsRegistrationAttributes>() {
                public ImsRegistrationAttributes createFromParcel(Parcel source) {
                    return new ImsRegistrationAttributes(source);
                }

                public ImsRegistrationAttributes[] newArray(int size) {
                    return new ImsRegistrationAttributes[size];
                }
            };
}
