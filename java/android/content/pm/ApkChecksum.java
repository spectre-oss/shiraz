// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;
import java.io.ByteArrayInputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.Objects;

/** Cryptographic checksum of an installed APK or split APK, optionally signed by an installer. */
public final class ApkChecksum implements Parcelable {
    private final String mSplitName;
    private final Checksum mChecksum;
    private final String mInstallerPackageName;
    private final byte[] mInstallerCertificate;

    public ApkChecksum(String splitName, int type, byte[] value) {
        this(splitName, new Checksum(type, value), null, null);
    }

    public ApkChecksum(
            String splitName,
            int type,
            byte[] value,
            String sourcePackageName,
            Certificate sourceCertificate)
            throws CertificateException {
        this(
                splitName,
                new Checksum(type, value),
                sourcePackageName,
                sourceCertificate != null ? sourceCertificate.getEncoded() : null);
    }

    ApkChecksum(
            String splitName,
            Checksum checksum,
            String installerPackageName,
            byte[] installerCertificate) {
        mSplitName = splitName;
        mChecksum = Objects.requireNonNull(checksum);
        mInstallerPackageName = installerPackageName;
        mInstallerCertificate = installerCertificate;
    }

    ApkChecksum(Parcel in) {
        mSplitName = in.readString();
        mChecksum = Checksum.CREATOR.createFromParcel(in);
        mInstallerPackageName = in.readString();
        mInstallerCertificate = in.createByteArray();
    }

    public int getType() {
        return mChecksum.getType();
    }

    public byte[] getValue() {
        return mChecksum.getValue();
    }

    public Certificate getInstallerCertificate() throws CertificateException {
        if (mInstallerCertificate == null) {
            return null;
        }
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return cf.generateCertificate(new ByteArrayInputStream(mInstallerCertificate));
    }

    public String getSplitName() {
        return mSplitName;
    }

    public String getInstallerPackageName() {
        return mInstallerPackageName;
    }

    @Override
    public String toString() {
        return "ApkChecksum { splitName = " + mSplitName
                + ", checksum = " + mChecksum
                + ", installerPackageName = " + mInstallerPackageName
                + ", installerCertificate = " + Arrays.toString(mInstallerCertificate) + " }";
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mSplitName);
        mChecksum.writeToParcel(dest, flags);
        dest.writeString(mInstallerPackageName);
        dest.writeByteArray(mInstallerCertificate);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ApkChecksum> CREATOR = new Creator<ApkChecksum>() {
        @Override
        public ApkChecksum createFromParcel(Parcel in) {
            return new ApkChecksum(in);
        }

        @Override
        public ApkChecksum[] newArray(int size) {
            return new ApkChecksum[size];
        }
    };
}
