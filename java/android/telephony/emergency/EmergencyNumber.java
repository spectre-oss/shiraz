// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.emergency;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class EmergencyNumber implements Parcelable, Comparable<EmergencyNumber> {
    public static final int EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED = 0;
    public static final int EMERGENCY_SERVICE_CATEGORY_POLICE = 1 << 0;
    public static final int EMERGENCY_SERVICE_CATEGORY_AMBULANCE = 1 << 1;
    public static final int EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE = 1 << 2;
    public static final int EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD = 1 << 3;
    public static final int EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE = 1 << 4;
    public static final int EMERGENCY_SERVICE_CATEGORY_MIEC = 1 << 5;
    public static final int EMERGENCY_SERVICE_CATEGORY_AIEC = 1 << 6;

    public static final int EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING = 1 << 0;
    public static final int EMERGENCY_NUMBER_SOURCE_SIM = 1 << 1;
    public static final int EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG = 1 << 2;
    public static final int EMERGENCY_NUMBER_SOURCE_DATABASE = 1 << 4;
    public static final int EMERGENCY_NUMBER_SOURCE_DEFAULT = 1 << 3;

    public static final int EMERGENCY_CALL_ROUTING_UNKNOWN = 0;
    public static final int EMERGENCY_CALL_ROUTING_EMERGENCY = 1;
    public static final int EMERGENCY_CALL_ROUTING_NORMAL = 2;

    private static final int[] ALL_CATEGORIES = new int[] {
        EMERGENCY_SERVICE_CATEGORY_POLICE,
        EMERGENCY_SERVICE_CATEGORY_AMBULANCE,
        EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE,
        EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD,
        EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE,
        EMERGENCY_SERVICE_CATEGORY_MIEC,
        EMERGENCY_SERVICE_CATEGORY_AIEC
    };

    private static final int[] ALL_SOURCES = new int[] {
        EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
        EMERGENCY_NUMBER_SOURCE_SIM,
        EMERGENCY_NUMBER_SOURCE_DATABASE,
        EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG,
        EMERGENCY_NUMBER_SOURCE_DEFAULT
    };

    private final String mNumber;
    private final String mCountryIso;
    private final String mMnc;
    private final int mEmergencyServiceCategoryBitmask;
    private final List<String> mEmergencyUrns;
    private final int mEmergencyNumberSourceBitmask;
    private final int mEmergencyCallRouting;

    EmergencyNumber(
            String number,
            String countryIso,
            String mnc,
            int emergencyServiceCategories,
            List<String> emergencyUrns,
            int emergencyNumberSources,
            int emergencyCallRouting) {
        mNumber = number != null ? number : "";
        mCountryIso = countryIso != null ? countryIso : "";
        mMnc = mnc != null ? mnc : "";
        mEmergencyServiceCategoryBitmask = emergencyServiceCategories;
        mEmergencyUrns = emergencyUrns != null
                ? Collections.unmodifiableList(new ArrayList<String>(emergencyUrns))
                : Collections.<String>emptyList();
        mEmergencyNumberSourceBitmask = emergencyNumberSources;
        mEmergencyCallRouting = emergencyCallRouting;
    }

    private EmergencyNumber(Parcel source) {
        mNumber = source.readString();
        mCountryIso = source.readString();
        mMnc = source.readString();
        mEmergencyServiceCategoryBitmask = source.readInt();
        String[] urns = source.createStringArray();
        if (urns != null) {
            ArrayList<String> list = new ArrayList<String>(urns.length);
            Collections.addAll(list, urns);
            mEmergencyUrns = Collections.unmodifiableList(list);
        } else {
            mEmergencyUrns = Collections.emptyList();
        }
        mEmergencyNumberSourceBitmask = source.readInt();
        mEmergencyCallRouting = source.readInt();
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mNumber);
        dest.writeString(mCountryIso);
        dest.writeString(mMnc);
        dest.writeInt(mEmergencyServiceCategoryBitmask);
        dest.writeStringArray(mEmergencyUrns.toArray(new String[mEmergencyUrns.size()]));
        dest.writeInt(mEmergencyNumberSourceBitmask);
        dest.writeInt(mEmergencyCallRouting);
    }

    public String getNumber() {
        return mNumber;
    }

    public String getCountryIso() {
        return mCountryIso;
    }

    public String getMnc() {
        return mMnc;
    }

    public List<Integer> getEmergencyServiceCategories() {
        List<Integer> categories = new ArrayList<Integer>();
        if (mEmergencyServiceCategoryBitmask == EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED) {
            categories.add(Integer.valueOf(EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED));
            return categories;
        }
        for (int i = 0; i < ALL_CATEGORIES.length; i++) {
            int cat = ALL_CATEGORIES[i];
            if ((mEmergencyServiceCategoryBitmask & cat) == cat) {
                categories.add(Integer.valueOf(cat));
            }
        }
        return categories;
    }

    public List<String> getEmergencyUrns() {
        return mEmergencyUrns;
    }

    public boolean isInEmergencyServiceCategories(int categories) {
        if (categories == EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED) {
            return mEmergencyServiceCategoryBitmask == EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED;
        }
        if (mEmergencyServiceCategoryBitmask == EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED) {
            return true;
        }
        return (mEmergencyServiceCategoryBitmask & categories) == categories;
    }

    public List<Integer> getEmergencyNumberSources() {
        List<Integer> sources = new ArrayList<Integer>();
        for (int i = 0; i < ALL_SOURCES.length; i++) {
            int s = ALL_SOURCES[i];
            if ((mEmergencyNumberSourceBitmask & s) == s) {
                sources.add(Integer.valueOf(s));
            }
        }
        return sources;
    }

    public boolean isFromSources(int sources) {
        return (mEmergencyNumberSourceBitmask & sources) == sources;
    }

    public int getEmergencyCallRouting() {
        return mEmergencyCallRouting;
    }

    public int describeContents() {
        return 0;
    }

    public String toString() {
        return "EmergencyNumber:"
                + "Number-" + mNumber
                + "|CountryIso-" + mCountryIso
                + "|Mnc-" + mMnc
                + "|ServiceCategories-" + Integer.toBinaryString(mEmergencyServiceCategoryBitmask)
                + "|Urns-" + mEmergencyUrns
                + "|Sources-" + Integer.toBinaryString(mEmergencyNumberSourceBitmask)
                + "|Routing-" + mEmergencyCallRouting;
    }

    public boolean equals(Object o) {
        if (!(o instanceof EmergencyNumber)) {
            return false;
        }
        EmergencyNumber other = (EmergencyNumber) o;
        return Objects.equals(mNumber, other.mNumber)
                && Objects.equals(mCountryIso, other.mCountryIso)
                && Objects.equals(mMnc, other.mMnc)
                && mEmergencyServiceCategoryBitmask == other.mEmergencyServiceCategoryBitmask
                && Objects.equals(mEmergencyUrns, other.mEmergencyUrns)
                && mEmergencyNumberSourceBitmask == other.mEmergencyNumberSourceBitmask
                && mEmergencyCallRouting == other.mEmergencyCallRouting;
    }

    public int hashCode() {
        return Objects.hash(
                mNumber,
                mCountryIso,
                mMnc,
                Integer.valueOf(mEmergencyServiceCategoryBitmask),
                mEmergencyUrns,
                Integer.valueOf(mEmergencyNumberSourceBitmask),
                Integer.valueOf(mEmergencyCallRouting));
    }

    private int getDisplayPriorityScore() {
        int score = 0;
        if (isFromSources(EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING)) score += 1 << 4;
        if (isFromSources(EMERGENCY_NUMBER_SOURCE_SIM)) score += 1 << 3;
        if (isFromSources(EMERGENCY_NUMBER_SOURCE_DATABASE)) score += 1 << 2;
        if (isFromSources(EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG)) score += 1 << 1;
        if (isFromSources(EMERGENCY_NUMBER_SOURCE_DEFAULT)) score += 1 << 0;
        return score;
    }

    public int compareTo(EmergencyNumber emergencyNumber) {
        if (getDisplayPriorityScore() != emergencyNumber.getDisplayPriorityScore()) {
            return getDisplayPriorityScore() > emergencyNumber.getDisplayPriorityScore() ? -1 : 1;
        }
        if (mNumber.compareTo(emergencyNumber.mNumber) != 0) {
            return mNumber.compareTo(emergencyNumber.mNumber);
        }
        if (mCountryIso.compareTo(emergencyNumber.mCountryIso) != 0) {
            return mCountryIso.compareTo(emergencyNumber.mCountryIso);
        }
        if (mMnc.compareTo(emergencyNumber.mMnc) != 0) {
            return mMnc.compareTo(emergencyNumber.mMnc);
        }
        if (mEmergencyServiceCategoryBitmask != emergencyNumber.mEmergencyServiceCategoryBitmask) {
            return mEmergencyServiceCategoryBitmask > emergencyNumber.mEmergencyServiceCategoryBitmask ? -1 : 1;
        }
        if (mEmergencyUrns.toString().compareTo(emergencyNumber.mEmergencyUrns.toString()) != 0) {
            return mEmergencyUrns.toString().compareTo(emergencyNumber.mEmergencyUrns.toString());
        }
        if (mEmergencyCallRouting != emergencyNumber.mEmergencyCallRouting) {
            return mEmergencyCallRouting > emergencyNumber.mEmergencyCallRouting ? -1 : 1;
        }
        return 0;
    }

    public static final Parcelable.Creator<EmergencyNumber> CREATOR =
            new Parcelable.Creator<EmergencyNumber>() {
                public EmergencyNumber createFromParcel(Parcel in) {
                    return new EmergencyNumber(in);
                }

                public EmergencyNumber[] newArray(int size) {
                    return new EmergencyNumber[size];
                }
            };
}
