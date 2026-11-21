// SPDX-License-Identifier: GPL-3.0-only
import com.android.apksig.ApkSigner;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
/** Test-only signing; generated material stays in a temporary build directory. */
public final class SignApkFixture {
    public static void main(String[] args) throws Exception {
        char[] password = new String(Files.readAllBytes(Paths.get(args[3])), StandardCharsets.UTF_8).trim().toCharArray();
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (FileInputStream input = new FileInputStream(args[2])) { store.load(input, password); }
        ApkSigner.SignerConfig signer = new ApkSigner.SignerConfig.Builder("fixture",
            (PrivateKey) store.getKey("fixture", password),
            Collections.singletonList((X509Certificate) store.getCertificate("fixture"))).build();
        new ApkSigner.Builder(Collections.singletonList(signer))
            .setInputApk(new File(args[0])).setOutputApk(new File(args[1]))
            .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(args.length<5||!"v2-only".equals(args[4]))
            .setV4SigningEnabled(false).build().sign();
    }
}
