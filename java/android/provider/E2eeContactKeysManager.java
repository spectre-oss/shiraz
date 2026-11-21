// SPDX-License-Identifier: GPL-3.0-only

package android.provider;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class E2eeContactKeysManager {
    public static final int VERIFICATION_STATE_UNVERIFIED = 0;
    public static final int VERIFICATION_STATE_VERIFICATION_FAILED = 1;
    public static final int VERIFICATION_STATE_VERIFIED = 2;

    private static final int MAX_KEY_SIZE_BYTES = 5000;

    private final String mOwnerPackageName;
    private final Object mLock = new Object();
    private final Map<String, E2eeContactKey> mContactKeys =
            new LinkedHashMap<String, E2eeContactKey>();
    private final Map<String, E2eeSelfKey> mSelfKeys =
            new LinkedHashMap<String, E2eeSelfKey>();

    E2eeContactKeysManager(Context context) {
        if (context == null) {
            throw new NullPointerException("context must not be null");
        }
        String pkg = context.getPackageName();
        mOwnerPackageName = pkg != null ? pkg : "android";
    }

    E2eeContactKeysManager() {
        mOwnerPackageName = "android";
    }

    public static int getMaxKeySizeBytes() {
        return MAX_KEY_SIZE_BYTES;
    }

    private static void validateKeyBytes(byte[] keyValue) {
        if (keyValue == null) {
            throw new NullPointerException("keyValue must not be null");
        }
        if (keyValue.length == 0 || keyValue.length > MAX_KEY_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Key value length must be in (0, " + MAX_KEY_SIZE_BYTES + "]");
        }
    }

    private static void validateState(int state) {
        if (state < VERIFICATION_STATE_UNVERIFIED || state > VERIFICATION_STATE_VERIFIED) {
            throw new IllegalArgumentException("Invalid verification state: " + state);
        }
    }

    private String contactMapKey(String lookupKey, String deviceId, String accountId) {
        Objects.requireNonNull(lookupKey, "lookupKey");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(accountId, "accountId");
        return lookupKey + "\u0000" + deviceId + "\u0000" + accountId;
    }

    private String selfMapKey(String deviceId, String accountId) {
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(accountId, "accountId");
        return deviceId + "\u0000" + accountId;
    }

    public void updateOrInsertE2eeContactKey(
            String lookupKey, String deviceId, String accountId, byte[] keyValue) {
        String mapKey = contactMapKey(lookupKey, deviceId, accountId);
        validateKeyBytes(keyValue);
        synchronized (mLock) {
            E2eeContactKey prev = mContactKeys.get(mapKey);
            int localState = prev != null ? prev.getLocalVerificationState()
                    : VERIFICATION_STATE_UNVERIFIED;
            int remoteState = prev != null ? prev.getRemoteVerificationState()
                    : VERIFICATION_STATE_UNVERIFIED;
            mContactKeys.put(mapKey, new E2eeContactKey(
                    deviceId,
                    accountId,
                    mOwnerPackageName,
                    System.currentTimeMillis(),
                    keyValue,
                    localState,
                    remoteState,
                    null,
                    null,
                    null));
        }
    }

    public E2eeContactKey getE2eeContactKey(
            String lookupKey, String deviceId, String accountId) {
        String mapKey = contactMapKey(lookupKey, deviceId, accountId);
        synchronized (mLock) {
            return mContactKeys.get(mapKey);
        }
    }

    public List<E2eeContactKey> getAllE2eeContactKeys(String lookupKey) {
        Objects.requireNonNull(lookupKey, "lookupKey");
        String prefix = lookupKey + "\u0000";
        synchronized (mLock) {
            List<E2eeContactKey> list = new ArrayList<E2eeContactKey>();
            for (Map.Entry<String, E2eeContactKey> e : mContactKeys.entrySet()) {
                if (e.getKey().startsWith(prefix)) {
                    list.add(e.getValue());
                }
            }
            return Collections.unmodifiableList(list);
        }
    }

    public List<E2eeContactKey> getOwnerE2eeContactKeys(String lookupKey) {
        return getAllE2eeContactKeys(lookupKey);
    }

    public boolean updateE2eeContactKeyLocalVerificationState(
            String lookupKey,
            String deviceId,
            String accountId,
            int localVerificationState) {
        String mapKey = contactMapKey(lookupKey, deviceId, accountId);
        validateState(localVerificationState);
        synchronized (mLock) {
            E2eeContactKey cur = mContactKeys.get(mapKey);
            if (cur == null) return false;
            mContactKeys.put(mapKey, new E2eeContactKey(
                    cur.getDeviceId(),
                    cur.getAccountId(),
                    cur.getOwnerPackageName(),
                    System.currentTimeMillis(),
                    cur.getKeyValue(),
                    localVerificationState,
                    cur.getRemoteVerificationState(),
                    cur.getDisplayName(),
                    cur.getPhoneNumber(),
                    cur.getEmailAddress()));
            return true;
        }
    }

    public boolean updateE2eeContactKeyRemoteVerificationState(
            String lookupKey,
            String deviceId,
            String accountId,
            int remoteVerificationState) {
        String mapKey = contactMapKey(lookupKey, deviceId, accountId);
        validateState(remoteVerificationState);
        synchronized (mLock) {
            E2eeContactKey cur = mContactKeys.get(mapKey);
            if (cur == null) return false;
            mContactKeys.put(mapKey, new E2eeContactKey(
                    cur.getDeviceId(),
                    cur.getAccountId(),
                    cur.getOwnerPackageName(),
                    System.currentTimeMillis(),
                    cur.getKeyValue(),
                    cur.getLocalVerificationState(),
                    remoteVerificationState,
                    cur.getDisplayName(),
                    cur.getPhoneNumber(),
                    cur.getEmailAddress()));
            return true;
        }
    }

    public boolean removeE2eeContactKey(String lookupKey, String deviceId, String accountId) {
        String mapKey = contactMapKey(lookupKey, deviceId, accountId);
        synchronized (mLock) {
            return mContactKeys.remove(mapKey) != null;
        }
    }

    public boolean updateOrInsertE2eeSelfKey(
            String deviceId, String accountId, byte[] keyValue) {
        String mapKey = selfMapKey(deviceId, accountId);
        validateKeyBytes(keyValue);
        synchronized (mLock) {
            E2eeSelfKey prev = mSelfKeys.get(mapKey);
            if (prev != null && Arrays.equals(prev.getKeyValue(), keyValue)) {
                return false;
            }
            int remoteState = prev != null ? prev.getRemoteVerificationState()
                    : VERIFICATION_STATE_UNVERIFIED;
            mSelfKeys.put(mapKey, new E2eeSelfKey(
                    deviceId,
                    accountId,
                    mOwnerPackageName,
                    System.currentTimeMillis(),
                    keyValue,
                    remoteState));
            return true;
        }
    }

    public boolean updateE2eeSelfKeyRemoteVerificationState(
            String deviceId, String accountId, int remoteVerificationState) {
        String mapKey = selfMapKey(deviceId, accountId);
        validateState(remoteVerificationState);
        synchronized (mLock) {
            E2eeSelfKey cur = mSelfKeys.get(mapKey);
            if (cur == null) return false;
            mSelfKeys.put(mapKey, new E2eeSelfKey(
                    cur.getDeviceId(),
                    cur.getAccountId(),
                    cur.getOwnerPackageName(),
                    System.currentTimeMillis(),
                    cur.getKeyValue(),
                    remoteVerificationState));
            return true;
        }
    }

    public E2eeSelfKey getE2eeSelfKey(String deviceId, String accountId) {
        String mapKey = selfMapKey(deviceId, accountId);
        synchronized (mLock) {
            return mSelfKeys.get(mapKey);
        }
    }

    public List<E2eeSelfKey> getAllE2eeSelfKeys() {
        synchronized (mLock) {
            return Collections.unmodifiableList(new ArrayList<E2eeSelfKey>(mSelfKeys.values()));
        }
    }

    public List<E2eeSelfKey> getOwnerE2eeSelfKeys() {
        return getAllE2eeSelfKeys();
    }

    public boolean removeE2eeSelfKey(String deviceId, String accountId) {
        String mapKey = selfMapKey(deviceId, accountId);
        synchronized (mLock) {
            return mSelfKeys.remove(mapKey) != null;
        }
    }

    public static final class E2eeContactKey implements Parcelable {
        private final String mDeviceId;
        private final String mAccountId;
        private final String mOwnerPackageName;
        private final long mTimeUpdated;
        private final byte[] mKeyValue;
        private final int mLocalVerificationState;
        private final int mRemoteVerificationState;
        private final String mDisplayName;
        private final String mPhoneNumber;
        private final String mEmailAddress;

        E2eeContactKey(
                String deviceId,
                String accountId,
                String ownerPackageName,
                long timeUpdated,
                byte[] keyValue,
                int localVerificationState,
                int remoteVerificationState,
                String displayName,
                String phoneNumber,
                String emailAddress) {
            mDeviceId = deviceId;
            mAccountId = accountId;
            mOwnerPackageName = ownerPackageName;
            mTimeUpdated = timeUpdated;
            mKeyValue = keyValue != null ? keyValue.clone() : null;
            mLocalVerificationState = localVerificationState;
            mRemoteVerificationState = remoteVerificationState;
            mDisplayName = displayName;
            mPhoneNumber = phoneNumber;
            mEmailAddress = emailAddress;
        }

        private E2eeContactKey(Parcel in) {
            mDeviceId = in.readString();
            mAccountId = in.readString();
            mOwnerPackageName = in.readString();
            mTimeUpdated = in.readLong();
            int len = in.readInt();
            if (len >= 0) {
                mKeyValue = new byte[len];
                in.readByteArray(mKeyValue);
            } else {
                mKeyValue = null;
            }
            mLocalVerificationState = in.readInt();
            mRemoteVerificationState = in.readInt();
            mDisplayName = in.readString();
            mPhoneNumber = in.readString();
            mEmailAddress = in.readString();
        }

        public int getLocalVerificationState() {
            return mLocalVerificationState;
        }

        public String getDisplayName() {
            return mDisplayName;
        }

        public String getPhoneNumber() {
            return mPhoneNumber;
        }

        public String getEmailAddress() {
            return mEmailAddress;
        }

        public String getDeviceId() {
            return mDeviceId;
        }

        public String getOwnerPackageName() {
            return mOwnerPackageName;
        }

        public String getAccountId() {
            return mAccountId;
        }

        public long getTimeUpdated() {
            return mTimeUpdated;
        }

        public byte[] getKeyValue() {
            return mKeyValue != null ? mKeyValue.clone() : null;
        }

        public int getRemoteVerificationState() {
            return mRemoteVerificationState;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    mDeviceId,
                    mAccountId,
                    mOwnerPackageName,
                    Long.valueOf(mTimeUpdated),
                    Integer.valueOf(Arrays.hashCode(mKeyValue)),
                    Integer.valueOf(mLocalVerificationState),
                    Integer.valueOf(mRemoteVerificationState),
                    mDisplayName,
                    mPhoneNumber,
                    mEmailAddress);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof E2eeContactKey)) return false;
            E2eeContactKey other = (E2eeContactKey) obj;
            return mTimeUpdated == other.mTimeUpdated
                    && mLocalVerificationState == other.mLocalVerificationState
                    && mRemoteVerificationState == other.mRemoteVerificationState
                    && Objects.equals(mDeviceId, other.mDeviceId)
                    && Objects.equals(mAccountId, other.mAccountId)
                    && Objects.equals(mOwnerPackageName, other.mOwnerPackageName)
                    && Arrays.equals(mKeyValue, other.mKeyValue)
                    && Objects.equals(mDisplayName, other.mDisplayName)
                    && Objects.equals(mPhoneNumber, other.mPhoneNumber)
                    && Objects.equals(mEmailAddress, other.mEmailAddress);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mDeviceId);
            dest.writeString(mAccountId);
            dest.writeString(mOwnerPackageName);
            dest.writeLong(mTimeUpdated);
            if (mKeyValue != null) {
                dest.writeInt(mKeyValue.length);
                dest.writeByteArray(mKeyValue);
            } else {
                dest.writeInt(-1);
            }
            dest.writeInt(mLocalVerificationState);
            dest.writeInt(mRemoteVerificationState);
            dest.writeString(mDisplayName);
            dest.writeString(mPhoneNumber);
            dest.writeString(mEmailAddress);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Parcelable.Creator<E2eeContactKey> CREATOR =
                new Parcelable.Creator<E2eeContactKey>() {
                    @Override
                    public E2eeContactKey createFromParcel(Parcel source) {
                        return new E2eeContactKey(source);
                    }

                    @Override
                    public E2eeContactKey[] newArray(int size) {
                        return new E2eeContactKey[size];
                    }
                };
    }

    public static final class E2eeSelfKey implements Parcelable {
        private final String mDeviceId;
        private final String mAccountId;
        private final String mOwnerPackageName;
        private final long mTimeUpdated;
        private final byte[] mKeyValue;
        private final int mRemoteVerificationState;

        E2eeSelfKey(
                String deviceId,
                String accountId,
                String ownerPackageName,
                long timeUpdated,
                byte[] keyValue,
                int remoteVerificationState) {
            mDeviceId = deviceId;
            mAccountId = accountId;
            mOwnerPackageName = ownerPackageName;
            mTimeUpdated = timeUpdated;
            mKeyValue = keyValue != null ? keyValue.clone() : null;
            mRemoteVerificationState = remoteVerificationState;
        }

        private E2eeSelfKey(Parcel in) {
            mDeviceId = in.readString();
            mAccountId = in.readString();
            mOwnerPackageName = in.readString();
            mTimeUpdated = in.readLong();
            int len = in.readInt();
            if (len >= 0) {
                mKeyValue = new byte[len];
                in.readByteArray(mKeyValue);
            } else {
                mKeyValue = null;
            }
            mRemoteVerificationState = in.readInt();
        }

        public String getDeviceId() {
            return mDeviceId;
        }

        public String getOwnerPackageName() {
            return mOwnerPackageName;
        }

        public String getAccountId() {
            return mAccountId;
        }

        public long getTimeUpdated() {
            return mTimeUpdated;
        }

        public byte[] getKeyValue() {
            return mKeyValue != null ? mKeyValue.clone() : null;
        }

        public int getRemoteVerificationState() {
            return mRemoteVerificationState;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    mDeviceId,
                    mAccountId,
                    mOwnerPackageName,
                    Long.valueOf(mTimeUpdated),
                    Integer.valueOf(Arrays.hashCode(mKeyValue)),
                    Integer.valueOf(mRemoteVerificationState));
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof E2eeSelfKey)) return false;
            E2eeSelfKey other = (E2eeSelfKey) obj;
            return mTimeUpdated == other.mTimeUpdated
                    && mRemoteVerificationState == other.mRemoteVerificationState
                    && Objects.equals(mDeviceId, other.mDeviceId)
                    && Objects.equals(mAccountId, other.mAccountId)
                    && Objects.equals(mOwnerPackageName, other.mOwnerPackageName)
                    && Arrays.equals(mKeyValue, other.mKeyValue);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mDeviceId);
            dest.writeString(mAccountId);
            dest.writeString(mOwnerPackageName);
            dest.writeLong(mTimeUpdated);
            if (mKeyValue != null) {
                dest.writeInt(mKeyValue.length);
                dest.writeByteArray(mKeyValue);
            } else {
                dest.writeInt(-1);
            }
            dest.writeInt(mRemoteVerificationState);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Parcelable.Creator<E2eeSelfKey> CREATOR =
                new Parcelable.Creator<E2eeSelfKey>() {
                    @Override
                    public E2eeSelfKey createFromParcel(Parcel source) {
                        return new E2eeSelfKey(source);
                    }

                    @Override
                    public E2eeSelfKey[] newArray(int size) {
                        return new E2eeSelfKey[size];
                    }
                };
    }
}
