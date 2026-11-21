// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.rtt;

import android.location.Address;
import android.location.Location;
import android.net.MacAddress;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ResponderLocation implements Parcelable {
    public static final int ALTITUDE_UNDEFINED = 0;
    public static final int ALTITUDE_METERS = 1;
    public static final int ALTITUDE_FLOORS = 2;

    public static final int DATUM_UNDEFINED = 0;
    public static final int DATUM_WGS84 = 1;
    public static final int DATUM_NAD83_NAV88 = 2;
    public static final int DATUM_NAD83_MLLW = 3;

    public static final int LCI_VERSION_1 = 1;

    public static final int LOCATION_FIXED = 0;
    public static final int LOCATION_VARIABLE = 1;
    public static final int LOCATION_MOVEMENT_UNKNOWN = 2;
    public static final int LOCATION_RESERVED = 3;

    public static final Creator<ResponderLocation> CREATOR =
            new Creator<ResponderLocation>() {
                @Override
                public ResponderLocation createFromParcel(Parcel in) {
                    return new ResponderLocation(in);
                }

                @Override
                public ResponderLocation[] newArray(int size) {
                    return new ResponderLocation[size];
                }
            };

    private boolean mIsValid = false;
    private boolean mIsLciValid = false;
    private boolean mIsZValid = false;
    private boolean mIsUsageRulesValid = false;
    private boolean mIsBssidListValid = false;
    private boolean mIsLocationCivicValid = false;
    private boolean mIsMapImageValid = false;

    private double mLatitudeUncertainty;
    private double mLatitude;
    private double mLongitudeUncertainty;
    private double mLongitude;
    private int mAltitudeType;
    private double mAltitudeUncertainty;
    private double mAltitude;
    private int mDatum;
    private boolean mLciRegisteredLocationAgreement;
    private int mLciVersion = LCI_VERSION_1;

    private int mExpectedToMove;
    private double mFloorNumber;
    private double mHeightAboveFloorMeters;
    private double mHeightAboveFloorUncertaintyMeters;

    private List<MacAddress> mBssidList = Collections.emptyList();
    private String mCivicCountryCode;
    private SparseArray<String> mCivicAddressElements = new SparseArray<String>();
    private String mImageMimeType;
    private Uri mMapImageUri;

    ResponderLocation(byte[] lciBuffer, byte[] lcrBuffer) {
        // Minimal safe parser: empty/null buffers remain invalid subelements
        if (lciBuffer != null && lciBuffer.length >= 18) {
            mIsValid = true;
        }
        if (lcrBuffer != null && lcrBuffer.length >= 4) {
            mIsValid = true;
        }
    }

    private ResponderLocation(Parcel in) {
        mIsValid = in.readByte() != 0;
        mIsLciValid = in.readByte() != 0;
        mIsZValid = in.readByte() != 0;
        mIsUsageRulesValid = in.readByte() != 0;
        mIsBssidListValid = in.readByte() != 0;
        mIsLocationCivicValid = in.readByte() != 0;
        mIsMapImageValid = in.readByte() != 0;

        mLatitudeUncertainty = in.readDouble();
        mLatitude = in.readDouble();
        mLongitudeUncertainty = in.readDouble();
        mLongitude = in.readDouble();
        mAltitudeType = in.readInt();
        mAltitudeUncertainty = in.readDouble();
        mAltitude = in.readDouble();
        mDatum = in.readInt();
        mLciRegisteredLocationAgreement = in.readByte() != 0;
        mLciVersion = in.readInt();

        mExpectedToMove = in.readInt();
        mFloorNumber = in.readDouble();
        mHeightAboveFloorMeters = in.readDouble();
        mHeightAboveFloorUncertaintyMeters = in.readDouble();

        int bssidCount = in.readInt();
        if (bssidCount > 0) {
            List<MacAddress> list = new ArrayList<MacAddress>(bssidCount);
            for (int i = 0; i < bssidCount; i++) {
                MacAddress mac = in.readTypedObject(MacAddress.CREATOR);
                if (mac != null) {
                    list.add(mac);
                }
            }
            mBssidList = Collections.unmodifiableList(list);
        }

        mCivicCountryCode = in.readString();
        int civicCount = in.readInt();
        for (int i = 0; i < civicCount; i++) {
            int key = in.readInt();
            String val = in.readString();
            mCivicAddressElements.put(key, val);
        }

        mImageMimeType = in.readString();
        String uriStr = in.readString();
        mMapImageUri = uriStr == null ? null : Uri.parse(uriStr);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeByte(mIsValid ? (byte) 1 : (byte) 0);
        parcel.writeByte(mIsLciValid ? (byte) 1 : (byte) 0);
        parcel.writeByte(mIsZValid ? (byte) 1 : (byte) 0);
        parcel.writeByte(mIsUsageRulesValid ? (byte) 1 : (byte) 0);
        parcel.writeByte(mIsBssidListValid ? (byte) 1 : (byte) 0);
        parcel.writeByte(mIsLocationCivicValid ? (byte) 1 : (byte) 0);
        parcel.writeByte(mIsMapImageValid ? (byte) 1 : (byte) 0);

        parcel.writeDouble(mLatitudeUncertainty);
        parcel.writeDouble(mLatitude);
        parcel.writeDouble(mLongitudeUncertainty);
        parcel.writeDouble(mLongitude);
        parcel.writeInt(mAltitudeType);
        parcel.writeDouble(mAltitudeUncertainty);
        parcel.writeDouble(mAltitude);
        parcel.writeInt(mDatum);
        parcel.writeByte(mLciRegisteredLocationAgreement ? (byte) 1 : (byte) 0);
        parcel.writeInt(mLciVersion);

        parcel.writeInt(mExpectedToMove);
        parcel.writeDouble(mFloorNumber);
        parcel.writeDouble(mHeightAboveFloorMeters);
        parcel.writeDouble(mHeightAboveFloorUncertaintyMeters);

        parcel.writeInt(mBssidList.size());
        for (int i = 0; i < mBssidList.size(); i++) {
            parcel.writeTypedObject(mBssidList.get(i), flags);
        }

        parcel.writeString(mCivicCountryCode);
        parcel.writeInt(mCivicAddressElements.size());
        for (int i = 0; i < mCivicAddressElements.size(); i++) {
            parcel.writeInt(mCivicAddressElements.keyAt(i));
            parcel.writeString(mCivicAddressElements.valueAt(i));
        }

        parcel.writeString(mImageMimeType);
        parcel.writeString(mMapImageUri == null ? null : mMapImageUri.toString());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ResponderLocation)) {
            return false;
        }
        ResponderLocation lhs = (ResponderLocation) obj;
        return mIsValid == lhs.mIsValid
                && mIsLciValid == lhs.mIsLciValid
                && mIsZValid == lhs.mIsZValid
                && mIsUsageRulesValid == lhs.mIsUsageRulesValid
                && mIsBssidListValid == lhs.mIsBssidListValid
                && mIsLocationCivicValid == lhs.mIsLocationCivicValid
                && mIsMapImageValid == lhs.mIsMapImageValid
                && Double.compare(mLatitudeUncertainty, lhs.mLatitudeUncertainty) == 0
                && Double.compare(mLatitude, lhs.mLatitude) == 0
                && Double.compare(mLongitudeUncertainty, lhs.mLongitudeUncertainty) == 0
                && Double.compare(mLongitude, lhs.mLongitude) == 0
                && mAltitudeType == lhs.mAltitudeType
                && Double.compare(mAltitudeUncertainty, lhs.mAltitudeUncertainty) == 0
                && Double.compare(mAltitude, lhs.mAltitude) == 0
                && mDatum == lhs.mDatum
                && mLciRegisteredLocationAgreement == lhs.mLciRegisteredLocationAgreement
                && mLciVersion == lhs.mLciVersion
                && mExpectedToMove == lhs.mExpectedToMove
                && Double.compare(mFloorNumber, lhs.mFloorNumber) == 0
                && Double.compare(mHeightAboveFloorMeters, lhs.mHeightAboveFloorMeters) == 0
                && Double.compare(
                                mHeightAboveFloorUncertaintyMeters,
                                lhs.mHeightAboveFloorUncertaintyMeters)
                        == 0
                && Objects.equals(mBssidList, lhs.mBssidList)
                && Objects.equals(mCivicCountryCode, lhs.mCivicCountryCode)
                && Objects.equals(mImageMimeType, lhs.mImageMimeType)
                && Objects.equals(mMapImageUri, lhs.mMapImageUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Boolean.valueOf(mIsValid),
                Boolean.valueOf(mIsLciValid),
                Boolean.valueOf(mIsZValid),
                Double.valueOf(mLatitude),
                Double.valueOf(mLongitude),
                Integer.valueOf(mAltitudeType),
                Double.valueOf(mAltitude),
                Integer.valueOf(mDatum),
                Integer.valueOf(mExpectedToMove),
                Double.valueOf(mFloorNumber),
                mBssidList,
                mCivicCountryCode,
                mImageMimeType,
                mMapImageUri);
    }

    public boolean isLciSubelementValid() {
        return mIsLciValid;
    }

    private void ensureLciValid() {
        if (!mIsLciValid) {
            throw new IllegalStateException(
                    "get*: invoked on an invalid ResponderLocation (LCI subelement is invalid)");
        }
    }

    private void ensureZValid() {
        if (!mIsZValid) {
            throw new IllegalStateException(
                    "get*: invoked on an invalid ResponderLocation (Z subelement is invalid)");
        }
    }

    public double getLatitudeUncertainty() {
        ensureLciValid();
        return mLatitudeUncertainty;
    }

    public double getLatitude() {
        ensureLciValid();
        return mLatitude;
    }

    public double getLongitudeUncertainty() {
        ensureLciValid();
        return mLongitudeUncertainty;
    }

    public double getLongitude() {
        ensureLciValid();
        return mLongitude;
    }

    public int getAltitudeType() {
        ensureLciValid();
        return mAltitudeType;
    }

    public double getAltitudeUncertainty() {
        ensureLciValid();
        return mAltitudeUncertainty;
    }

    public double getAltitude() {
        ensureLciValid();
        return mAltitude;
    }

    public int getDatum() {
        ensureLciValid();
        return mDatum;
    }

    public boolean getRegisteredLocationAgreementIndication() {
        ensureLciValid();
        return mLciRegisteredLocationAgreement;
    }

    public int getLciVersion() {
        ensureLciValid();
        return mLciVersion;
    }

    public Location toLocation() {
        ensureLciValid();
        Location loc = new Location("wifi");
        loc.setLatitude(mLatitude);
        loc.setLongitude(mLongitude);
        loc.setAccuracy((float) ((mLatitudeUncertainty + mLongitudeUncertainty) / 2.0));
        if (mAltitudeType == ALTITUDE_METERS) {
            loc.setAltitude(mAltitude);
            loc.setVerticalAccuracyMeters((float) mAltitudeUncertainty);
        }
        loc.setTime(System.currentTimeMillis());
        return loc;
    }

    public boolean isZaxisSubelementValid() {
        return mIsZValid;
    }

    public int getExpectedToMove() {
        ensureZValid();
        return mExpectedToMove;
    }

    public double getFloorNumber() {
        ensureZValid();
        return mFloorNumber;
    }

    public double getHeightAboveFloorMeters() {
        ensureZValid();
        return mHeightAboveFloorMeters;
    }

    public double getHeightAboveFloorUncertaintyMeters() {
        ensureZValid();
        return mHeightAboveFloorUncertaintyMeters;
    }

    public List<MacAddress> getColocatedBssids() {
        return Collections.unmodifiableList(mBssidList);
    }

    public Address toCivicLocationAddress() {
        if (!mIsLocationCivicValid) {
            return null;
        }
        Address address = new Address(Locale.getDefault());
        address.setCountryCode(mCivicCountryCode);
        String state = mCivicAddressElements.get(CivicLocationKeys.STATE);
        if (state != null) {
            address.setAdminArea(state);
        }
        String county = mCivicAddressElements.get(CivicLocationKeys.COUNTY);
        if (county != null) {
            address.setSubAdminArea(county);
        }
        String city = mCivicAddressElements.get(CivicLocationKeys.CITY);
        if (city != null) {
            address.setLocality(city);
        }
        String postal = mCivicAddressElements.get(CivicLocationKeys.POSTAL_CODE);
        if (postal != null) {
            address.setPostalCode(postal);
        }
        String road = mCivicAddressElements.get(CivicLocationKeys.PRIMARY_ROAD_NAME);
        if (road != null) {
            address.setThoroughfare(road);
        }
        return address;
    }

    public SparseArray<String> toCivicLocationSparseArray() {
        if (!mIsLocationCivicValid) {
            return null;
        }
        SparseArray<String> copy = new SparseArray<String>(mCivicAddressElements.size());
        for (int i = 0; i < mCivicAddressElements.size(); i++) {
            copy.put(mCivicAddressElements.keyAt(i), mCivicAddressElements.valueAt(i));
        }
        return copy;
    }

    public String getMapImageMimeType() {
        if (!mIsMapImageValid) {
            return null;
        }
        return mImageMimeType;
    }

    public Uri getMapImageUri() {
        if (!mIsMapImageValid) {
            return null;
        }
        return mMapImageUri;
    }
}
