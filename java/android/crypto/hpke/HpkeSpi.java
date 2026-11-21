// SPDX-License-Identifier: GPL-3.0-only
package android.crypto.hpke;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;

/** Service provider interface for Hybrid Public Key Encryption (RFC 9180). */
public interface HpkeSpi {
    void engineInitSender(
            PublicKey recipientKey,
            byte[] info,
            PrivateKey senderKey,
            byte[] psk,
            byte[] pskId) throws InvalidKeyException;

    void engineInitSenderWithSeed(
            PublicKey recipientKey,
            byte[] info,
            PrivateKey senderKey,
            byte[] psk,
            byte[] pskId,
            byte[] sKe) throws InvalidKeyException;

    void engineInitRecipient(
            byte[] encapsulated,
            PrivateKey recipientKey,
            byte[] info,
            PublicKey senderKey,
            byte[] psk,
            byte[] pskId) throws InvalidKeyException;

    byte[] engineSeal(byte[] plaintext, byte[] aad);

    byte[] engineOpen(byte[] ciphertext, byte[] aad) throws GeneralSecurityException;

    byte[] engineExport(int length, byte[] exporterContext);

    byte[] getEncapsulated();
}
