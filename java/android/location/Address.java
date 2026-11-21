// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Address implements Parcelable {
    private Locale mLocale;
    private String mFeatureName;
    private HashMap<Integer, String> mAddressLines;
    private int mMaxAddressLineIndex = -1;
    private String mAdminArea;
    private String mSubAdminArea;
    private String mLocality;
    private String mSubLocality;
    private String mThoroughfare;
    private String mSubThoroughfare;
    private String mPremises;
    private String mPostalCode;
    private String mCountryCode;
    private String mCountryName;
    private double mLatitude;
    private double mLongitude;
    private boolean mHasLatitude = false;
    private boolean mHasLongitude = false;
    private String mPhone;
    private String mUrl;
    private Bundle mExtras = null;

    public Address(Locale locale) {
        mLocale = locale;
    }

    public Locale getLocale() {
        return mLocale;
    }

    public int getMaxAddressLineIndex() {
        return mMaxAddressLineIndex;
    }

    public String getAddressLine(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index = " + index + " < 0");
        }
        return mAddressLines == null ? null : mAddressLines.get(index);
    }

    public void setAddressLine(int index, String line) {
        if (index < 0) {
            throw new IllegalArgumentException("index = " + index + " < 0");
        }
        if (mAddressLines == null) {
            mAddressLines = new HashMap<Integer, String>();
        }
        mAddressLines.put(index, line);

        if (line == null) {
            mMaxAddressLineIndex = -1;
            for (Integer i : mAddressLines.keySet()) {
                if (mAddressLines.get(i) != null) {
                    mMaxAddressLineIndex = Math.max(mMaxAddressLineIndex, i);
                }
            }
        } else {
            mMaxAddressLineIndex = Math.max(mMaxAddressLineIndex, index);
        }
    }

    public String getFeatureName() {
        return mFeatureName;
    }

    public void setFeatureName(String featureName) {
        mFeatureName = featureName;
    }

    public String getAdminArea() {
        return mAdminArea;
    }

    public void setAdminArea(String adminArea) {
        mAdminArea = adminArea;
    }

    public String getSubAdminArea() {
        return mSubAdminArea;
    }

    public void setSubAdminArea(String subAdminArea) {
        mSubAdminArea = subAdminArea;
    }

    public String getLocality() {
        return mLocality;
    }

    public void setLocality(String locality) {
        mLocality = locality;
    }

    public String getSubLocality() {
        return mSubLocality;
    }

    public void setSubLocality(String sublocality) {
        mSubLocality = sublocality;
    }

    public String getThoroughfare() {
        return mThoroughfare;
    }

    public void setThoroughfare(String thoroughfare) {
        mThoroughfare = thoroughfare;
    }

    public String getSubThoroughfare() {
        return mSubThoroughfare;
    }

    public void setSubThoroughfare(String subthoroughfare) {
        mSubThoroughfare = subthoroughfare;
    }

    public String getPremises() {
        return mPremises;
    }

    public void setPremises(String premises) {
        mPremises = premises;
    }

    public String getPostalCode() {
        return mPostalCode;
    }

    public void setPostalCode(String postalCode) {
        mPostalCode = postalCode;
    }

    public String getCountryCode() {
        return mCountryCode;
    }

    public void setCountryCode(String countryCode) {
        mCountryCode = countryCode;
    }

    public String getCountryName() {
        return mCountryName;
    }

    public void setCountryName(String countryName) {
        mCountryName = countryName;
    }

    public boolean hasLatitude() {
        return mHasLatitude;
    }

    public double getLatitude() {
        if (mHasLatitude) {
            return mLatitude;
        } else {
            throw new IllegalStateException("Latitude is not assigned");
        }
    }

    public void setLatitude(double latitude) {
        mLatitude = latitude;
        mHasLatitude = true;
    }

    public void clearLatitude() {
        mHasLatitude = false;
    }

    public boolean hasLongitude() {
        return mHasLongitude;
    }

    public double getLongitude() {
        if (mHasLongitude) {
            return mLongitude;
        } else {
            throw new IllegalStateException("Longitude is not assigned");
        }
    }

    public void setLongitude(double longitude) {
        mLongitude = longitude;
        mHasLongitude = true;
    }

    public void clearLongitude() {
        mHasLongitude = false;
    }

    public String getPhone() {
        return mPhone;
    }

    public void setPhone(String phone) {
        mPhone = phone;
    }

    public String getUrl() {
        return mUrl;
    }

    public void setUrl(String Url) {
        mUrl = Url;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    public void setExtras(Bundle extras) {
        mExtras = extras == null ? null : new Bundle(extras);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Address[addressLines=[");
        for (int i = 0; i <= mMaxAddressLineIndex; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(i);
            sb.append(':');
            String line = mAddressLines == null ? null : mAddressLines.get(i);
            if (line == null) {
                sb.append("null");
            } else {
                sb.append('\"');
                sb.append(line);
                sb.append('\"');
            }
        }
        sb.append(']');
        sb.append(",feature=").append(mFeatureName);
        sb.append(",admin=").append(mAdminArea);
        sb.append(",sub-admin=").append(mSubAdminArea);
        sb.append(",locality=").append(mLocality);
        sb.append(",thoroughfare=").append(mThoroughfare);
        sb.append(",postalCode=").append(mPostalCode);
        sb.append(",countryCode=").append(mCountryCode);
        sb.append(",countryName=").append(mCountryName);
        sb.append(",hasLatitude=").append(mHasLatitude);
        sb.append(",latitude=").append(mLatitude);
        sb.append(",hasLongitude=").append(mHasLongitude);
        sb.append(",longitude=").append(mLongitude);
        sb.append(",phone=").append(mPhone);
        sb.append(",url=").append(mUrl);
        sb.append(",extras=").append(mExtras);
        sb.append(']');
        return sb.toString();
    }

    public static final Parcelable.Creator<Address> CREATOR = new Parcelable.Creator<Address>() {
        @Override
        public Address createFromParcel(Parcel in) {
            String language = in.readString();
            String country = in.readString();
            String variant = in.readString();
            Locale locale = null;
            if (language != null) {
                locale = new Locale(language,
                        country != null ? country : "",
                        variant != null ? variant : "");
            }
            Address a = new Address(locale);
            int n = in.readInt();
            if (n > 0) {
                a.mAddressLines = new HashMap<Integer, String>(n);
                for (int i = 0; i < n; i++) {
                    int index = in.readInt();
                    String line = in.readString();
                    a.mAddressLines.put(index, line);
                    if (line != null) {
                        a.mMaxAddressLineIndex = Math.max(a.mMaxAddressLineIndex, index);
                    }
                }
            }
            a.mFeatureName = in.readString();
            a.mAdminArea = in.readString();
            a.mSubAdminArea = in.readString();
            a.mLocality = in.readString();
            a.mSubLocality = in.readString();
            a.mThoroughfare = in.readString();
            a.mSubThoroughfare = in.readString();
            a.mPremises = in.readString();
            a.mPostalCode = in.readString();
            a.mCountryCode = in.readString();
            a.mCountryName = in.readString();
            a.mHasLatitude = in.readBoolean();
            if (a.mHasLatitude) {
                a.mLatitude = in.readDouble();
            }
            a.mHasLongitude = in.readBoolean();
            if (a.mHasLongitude) {
                a.mLongitude = in.readDouble();
            }
            a.mPhone = in.readString();
            a.mUrl = in.readString();
            a.mExtras = in.readBundle();
            return a;
        }

        @Override
        public Address[] newArray(int size) {
            return new Address[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        if (mLocale != null) {
            parcel.writeString(mLocale.getLanguage());
            parcel.writeString(mLocale.getCountry());
            parcel.writeString(mLocale.getVariant());
        } else {
            parcel.writeString(null);
            parcel.writeString(null);
            parcel.writeString(null);
        }
        if (mAddressLines == null) {
            parcel.writeInt(0);
        } else {
            parcel.writeInt(mAddressLines.size());
            for (Map.Entry<Integer, String> e : mAddressLines.entrySet()) {
                parcel.writeInt(e.getKey());
                parcel.writeString(e.getValue());
            }
        }
        parcel.writeString(mFeatureName);
        parcel.writeString(mAdminArea);
        parcel.writeString(mSubAdminArea);
        parcel.writeString(mLocality);
        parcel.writeString(mSubLocality);
        parcel.writeString(mThoroughfare);
        parcel.writeString(mSubThoroughfare);
        parcel.writeString(mPremises);
        parcel.writeString(mPostalCode);
        parcel.writeString(mCountryCode);
        parcel.writeString(mCountryName);
        parcel.writeBoolean(mHasLatitude);
        if (mHasLatitude) {
            parcel.writeDouble(mLatitude);
        }
        parcel.writeBoolean(mHasLongitude);
        if (mHasLongitude) {
            parcel.writeDouble(mLongitude);
        }
        parcel.writeString(mPhone);
        parcel.writeString(mUrl);
        parcel.writeBundle(mExtras);
    }
}
