// SPDX-License-Identifier: GPL-3.0-only
import com.android.apksig.ApkVerifier;
import java.io.File;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.TreeSet;
/** Host-side verification using Google's apksig; not part of the Android framework. */
public final class VerifyApk {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("APK path required");
        ApkVerifier.Result result = new ApkVerifier.Builder(new File(args[0])).build().verify();
        if (!result.isVerified()) throw new SecurityException("APK signature verification failed");
        TreeSet<String> digests = new TreeSet<String>();
        for (X509Certificate certificate : result.getSignerCertificates()) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format("%02x", value & 255));
            digests.add(hex.toString());
        }
        if (digests.isEmpty() || digests.size() > 16) throw new SecurityException("Signer count");
        for (String digest : digests) System.out.println(digest);
    }
}
