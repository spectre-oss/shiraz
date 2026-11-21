// SPDX-License-Identifier: GPL-3.0-only
package android.nfc;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Tag implements Parcelable {
    /** @hide */
    public static final int TECH_NFC_A = 1;
    /** @hide */
    public static final int TECH_NFC_B = 2;
    /** @hide */
    public static final int TECH_ISO_DEP = 3;
    /** @hide */
    public static final int TECH_NFC_F = 4;
    /** @hide */
    public static final int TECH_NFC_V = 5;
    /** @hide */
    public static final int TECH_NDEF = 6;
    /** @hide */
    public static final int TECH_NDEF_FORMATABLE = 7;
    /** @hide */
    public static final int TECH_MIFARE_CLASSIC = 8;
    /** @hide */
    public static final int TECH_MIFARE_ULTRALIGHT = 9;
    /** @hide */
    public static final int TECH_NFC_BARCODE = 10;

    private static final Map<Integer, String> TECH_CODE_TO_NAME = new HashMap<Integer, String>();
    private static final Map<String, Integer> TECH_NAME_TO_CODE = new HashMap<String, Integer>();

    static {
        registerTech(TECH_ISO_DEP, "android.nfc.tech.IsoDep");
        registerTech(TECH_NFC_A, "android.nfc.tech.NfcA");
        registerTech(TECH_NFC_B, "android.nfc.tech.NfcB");
        registerTech(TECH_NFC_F, "android.nfc.tech.NfcF");
        registerTech(TECH_NFC_V, "android.nfc.tech.NfcV");
        registerTech(TECH_NDEF, "android.nfc.tech.Ndef");
        registerTech(TECH_NDEF_FORMATABLE, "android.nfc.tech.NdefFormatable");
        registerTech(TECH_MIFARE_CLASSIC, "android.nfc.tech.MifareClassic");
        registerTech(TECH_MIFARE_ULTRALIGHT, "android.nfc.tech.MifareUltralight");
        registerTech(TECH_NFC_BARCODE, "android.nfc.tech.NfcBarcode");
    }

    private static void registerTech(int code, String name) {
        TECH_CODE_TO_NAME.put(Integer.valueOf(code), name);
        TECH_NAME_TO_CODE.put(name, Integer.valueOf(code));
    }

    private final byte[] mId;
    private final int[] mTechList;
    private final String[] mTechStringList;
    private final Bundle[] mTechExtras;
    private int mConnectedTechnology = -1;

    /** @hide */
    public Tag(byte[] id, int[] techList, Bundle[] techListExtras) {
        if (techList == null) {
            throw new IllegalArgumentException("rawTargets cannot be null");
        }
        mId = id != null ? id.clone() : new byte[0];
        mTechList = techList.clone();
        mTechStringList = generateTechStringList(mTechList);
        mTechExtras = new Bundle[mTechList.length];
        if (techListExtras != null) {
            for (int i = 0; i < Math.min(mTechList.length, techListExtras.length); i++) {
                mTechExtras[i] = techListExtras[i] != null
                        ? new Bundle(techListExtras[i])
                        : null;
            }
        }
    }

    /** @hide */
    public static Tag createMockTag(byte[] id, int[] techList, Bundle[] techListExtras) {
        return new Tag(id, techList, techListExtras);
    }

    private static String[] generateTechStringList(int[] techCodes) {
        String[] strings = new String[techCodes.length];
        for (int i = 0; i < techCodes.length; i++) {
            String name = TECH_CODE_TO_NAME.get(Integer.valueOf(techCodes[i]));
            if (name == null) {
                throw new IllegalArgumentException("Unknown tech type " + techCodes[i]);
            }
            strings[i] = name;
        }
        return strings;
    }

    public byte[] getId() {
        return mId.clone();
    }

    public String[] getTechList() {
        return mTechStringList.clone();
    }

    /** @hide */
    public boolean hasTech(int techType) {
        for (int i = 0; i < mTechList.length; i++) {
            if (mTechList[i] == techType) {
                return true;
            }
        }
        return false;
    }

    /** @hide */
    public Bundle getTechExtras(int tech) {
        for (int i = 0; i < mTechList.length; i++) {
            if (mTechList[i] == tech) {
                return mTechExtras[i];
            }
        }
        return null;
    }

    /** @hide */
    public synchronized int getConnectedTechnology() {
        return mConnectedTechnology;
    }

    /** @hide */
    public synchronized void setConnectedTechnology(int technology) {
        if (mConnectedTechnology != -1 && mConnectedTechnology != technology) {
            throw new IllegalStateException("Call close() first on other technology!");
        }
        mConnectedTechnology = technology;
    }

    /** @hide */
    public synchronized void setTechnologyDisconnected() {
        mConnectedTechnology = -1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TAG: Tech [");
        for (int i = 0; i < mTechStringList.length; i++) {
            sb.append(mTechStringList[i]);
            if (i < mTechStringList.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(mId);
        dest.writeIntArray(mTechList);
        dest.writeTypedArray(mTechExtras, 0);
    }

    public static final Parcelable.Creator<Tag> CREATOR =
            new Parcelable.Creator<Tag>() {
                @Override
                public Tag createFromParcel(Parcel in) {
                    byte[] id = in.createByteArray();
                    int[] techList = in.createIntArray();
                    Bundle[] techExtras = in.createTypedArray(Bundle.CREATOR);
                    return new Tag(
                            id,
                            techList != null ? techList : new int[0],
                            techExtras);
                }

                @Override
                public Tag[] newArray(int size) {
                    return new Tag[size];
                }
            };
}
