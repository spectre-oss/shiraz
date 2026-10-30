// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.security.keystore.KeyGenParameterSpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Exportable app-private RSA keys, with ordinary self-signed certificates.
 * No hardware-backed protection, attestation, or Android authorization enforcement. */
public final class HostRsaKeyPairGenerator extends KeyPairGeneratorSpi {
    private KeyGenParameterSpec spec;
    private KeyPairGenerator delegate;

    @Override public void initialize(int size, SecureRandom random) {
        throw new InvalidParameterException("KeyGenParameterSpec required");
    }

    @Override public void initialize(AlgorithmParameterSpec parameters, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        spec = null;
        delegate = null;
        if (!(parameters instanceof KeyGenParameterSpec))
            throw new InvalidAlgorithmParameterException("KeyGenParameterSpec required");
        KeyGenParameterSpec next = (KeyGenParameterSpec) parameters;
        if (next.isStrongBoxBacked() || next.isUserAuthenticationRequired()
                || next.getAttestationChallenge() != null || next.getKeyValidityStart() != null
                || next.getKeyValidityForOriginationEnd() != null)
            throw new InvalidAlgorithmParameterException(
                "Hardware, authentication, attestation and validity policies unavailable in software key store");
        AlgorithmParameterSpec algorithm = next.getAlgorithmParameterSpec();
        if (algorithm != null && !(algorithm instanceof RSAKeyGenParameterSpec))
            throw new InvalidAlgorithmParameterException("RSAKeyGenParameterSpec required");
        int size = next.getKeySize();
        RSAKeyGenParameterSpec rsa = (RSAKeyGenParameterSpec) algorithm;
        if (rsa != null && size != -1 && size != rsa.getKeysize())
            throw new InvalidAlgorithmParameterException("Conflicting RSA key sizes");
        if (size == -1) size = rsa == null ? 2048 : rsa.getKeysize();
        if (size < 512 || size > 4096 || size % 8 != 0)
            throw new InvalidAlgorithmParameterException("RSA key size must be 512..4096 bits in multiples of eight");
        if (rsa == null) rsa = new RSAKeyGenParameterSpec(size, RSAKeyGenParameterSpec.F4);
        try {
            for (Provider provider : Security.getProviders()) {
                if (provider.getName().equals("AndroidKeyStore")) continue;
                try { delegate = KeyPairGenerator.getInstance("RSA", provider); break; }
                catch (NoSuchAlgorithmException ignored) { }
            }
            if (delegate == null) throw new NoSuchAlgorithmException("No software RSA generator");
            delegate.initialize(rsa, random == null ? new SecureRandom() : random);
            spec = next;
        } catch (GeneralSecurityException failure) {
            delegate = null;
            throw new InvalidAlgorithmParameterException("Cannot initialize software RSA generator", failure);
        }
    }

    @Override public KeyPair generateKeyPair() {
        if (spec == null) throw new IllegalStateException("Generator not initialized");
        try {
            KeyPair pair = delegate.generateKeyPair();
            Certificate certificate = certificate(pair, spec);
            HostKeyStoreProvider.savePair(spec.getKeystoreAlias(), pair.getPrivate(), certificate);
            return pair;
        } catch (GeneralSecurityException failure) {
            throw new ProviderException("Cannot generate software RSA key", failure);
        }
    }

    private static Certificate certificate(KeyPair pair, KeyGenParameterSpec spec)
            throws GeneralSecurityException {
        // sha256WithRSAEncryption, including the required NULL parameters.
        byte[] algorithm = der(0x30, new byte[]{6,9,42,(byte)134,72,(byte)134,(byte)247,13,1,1,11,5,0});
        byte[] subject = spec.getCertificateSubject().getEncoded();
        byte[] tbs = der(0x30, der(0x02, spec.getCertificateSerialNumber().toByteArray()),
            algorithm, subject, der(0x30, date(spec.getCertificateNotBefore()),
            date(spec.getCertificateNotAfter())), subject, pair.getPublic().getEncoded());
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(tbs);
        byte[] encoded = der(0x30, tbs, algorithm, der(0x03, new byte[]{0}, signer.sign()));
        return CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(encoded));
    }

    private static byte[] date(Date value) {
        SimpleDateFormat year = new SimpleDateFormat("yyyy", Locale.US);
        year.setTimeZone(TimeZone.getTimeZone("UTC"));
        int y = Integer.parseInt(year.format(value));
        boolean utc = y >= 1950 && y < 2050;
        SimpleDateFormat format = new SimpleDateFormat(utc ? "yyMMddHHmmss'Z'" : "yyyyMMddHHmmss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return der(utc ? 0x17 : 0x18, format.format(value).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static byte[] der(int tag, byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) length = Math.addExact(length, part.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        if (length < 128) out.write(length);
        else {
            int bytes = (32 - Integer.numberOfLeadingZeros(length) + 7) / 8;
            out.write(0x80 | bytes);
            for (int i = bytes - 1; i >= 0; i--) out.write((length >>> (8 * i)) & 255);
        }
        for (byte[] part : parts) out.write(part, 0, part.length);
        return out.toByteArray();
    }
}
