// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.GeneralSecurityException;
import java.security.SignatureException;

/** OTA package verification and recovery partition control APIs. */
public class RecoverySystem {
    public interface ProgressListener {
        void onProgress(int progress);
    }

    public RecoverySystem() {
    }

    public static void verifyPackage(
            File packageFile, ProgressListener listener, File deviceCertsZipFile)
            throws IOException, GeneralSecurityException {
        if (packageFile == null) {
            throw new NullPointerException("packageFile");
        }
        long fileLen = packageFile.length();
        RandomAccessFile raf = new RandomAccessFile(packageFile, "r");
        try {
            if (listener != null) {
                listener.onProgress(0);
            }
            if (fileLen < 22) {
                throw new SignatureException("package is too short to be a signedota zip");
            }
            raf.seek(fileLen - 6);
            byte[] footer = new byte[6];
            raf.readFully(footer);
            if ((footer[2] & 0xff) != 0xff || (footer[3] & 0xff) != 0xff) {
                throw new SignatureException("no signature in file (no footer)");
            }
            throw new SignatureException(
                    "OTA whole-file PKCS#7 verification requires recovery keystore certificates");
        } finally {
            raf.close();
        }
    }

    public static void installPackage(Context context, File packageFile) throws IOException {
        throw new IOException("Recovery OTA installation is unsupported on desktop host");
    }

    public static void rebootWipeUserData(Context context) throws IOException {
        throw new IOException("Recovery userdata wipe is unsupported on desktop host");
    }

    public static void rebootWipeCache(Context context) throws IOException {
        throw new IOException("Recovery cache wipe is unsupported on desktop host");
    }
}
