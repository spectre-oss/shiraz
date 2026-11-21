// SPDX-License-Identifier: GPL-3.0-only
package android.app.blob;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.Arrays;
import java.util.Objects;

public final class BlobHandle implements Parcelable {
    private static final String ALGO_SHA_256 = "SHA-256";
    private static final int LIMIT_BLOB_LABEL_LENGTH = 100;
    private static final int LIMIT_BLOB_TAG_LENGTH = 128;

    private final String mAlgorithm;
    private final byte[] mDigest;
    private final CharSequence mLabel;
    private final long mExpiryTimeMillis;
    private final String mTag;

    private BlobHandle(String algorithm, byte[] digest, CharSequence label, long expiryTimeMillis, String tag) {
        if (algorithm == null) throw new NullPointerException("algorithm");
        if (digest == null) throw new NullPointerException("digest");
        if (label == null) throw new NullPointerException("label");
        if (tag == null) throw new NullPointerException("tag");
        if (ALGO_SHA_256.equals(algorithm) && digest.length != 32) {
            throw new IllegalArgumentException("SHA-256 digest must be 32 bytes");
        }
        if (label.length() > LIMIT_BLOB_LABEL_LENGTH) {
            throw new IllegalArgumentException("label too long");
        }
        if (expiryTimeMillis < 0) {
            throw new IllegalArgumentException("expiryTimeMillis must not be negative");
        }
        if (tag.length() > LIMIT_BLOB_TAG_LENGTH) {
            throw new IllegalArgumentException("tag too long");
        }
        mAlgorithm = algorithm;
        mDigest = digest.clone();
        mLabel = label;
        mExpiryTimeMillis = expiryTimeMillis;
        mTag = tag;
    }

    BlobHandle(Parcel in) {
        mAlgorithm = in.readString();
        mDigest = in.createByteArray();
        mLabel = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        mExpiryTimeMillis = in.readLong();
        mTag = in.readString();
    }

    public static BlobHandle createWithSha256(
            byte[] digest, CharSequence label, long expiryTimeMillis, String tag) {
        return new BlobHandle(ALGO_SHA_256, digest, label, expiryTimeMillis, tag);
    }

    public byte[] getSha256Digest() {
        return mDigest.clone();
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    public long getExpiryTimeMillis() {
        return mExpiryTimeMillis;
    }

    public String getTag() {
        return mTag;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mAlgorithm);
        dest.writeByteArray(mDigest);
        TextUtils.writeToParcel(mLabel, dest, flags);
        dest.writeLong(mExpiryTimeMillis);
        dest.writeString(mTag);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BlobHandle)) return false;
        BlobHandle other = (BlobHandle) obj;
        String thisLabel = mLabel == null ? null : mLabel.toString();
        String otherLabel = other.mLabel == null ? null : other.mLabel.toString();
        return Objects.equals(mAlgorithm, other.mAlgorithm)
                && Arrays.equals(mDigest, other.mDigest)
                && Objects.equals(thisLabel, otherLabel)
                && mExpiryTimeMillis == other.mExpiryTimeMillis
                && Objects.equals(mTag, other.mTag);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                mAlgorithm,
                mLabel == null ? null : mLabel.toString(),
                Long.valueOf(mExpiryTimeMillis),
                mTag);
        return 31 * result + Arrays.hashCode(mDigest);
    }

    @Override
    public String toString() {
        return "BlobHandle{algo=" + mAlgorithm + ",label=" + mLabel
                + ",expiryMillis=" + mExpiryTimeMillis + ",tag=" + mTag + "}";
    }

    public static final Parcelable.Creator<BlobHandle> CREATOR = new Parcelable.Creator<BlobHandle>() {
        @Override
        public BlobHandle createFromParcel(Parcel in) {
            return new BlobHandle(in);
        }

        @Override
        public BlobHandle[] newArray(int size) {
            return new BlobHandle[size];
        }
    };
}
