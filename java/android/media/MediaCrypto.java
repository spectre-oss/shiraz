// SPDX-License-Identifier: GPL-3.0-only
package android.media;
public final class MediaCrypto {
 public MediaCrypto(java.util.UUID uuid,byte[] data){throw new UnsupportedOperationException("Encrypted media is unsupported");}
 public static boolean isCryptoSchemeSupported(java.util.UUID uuid){return false;}
 public void release(){}
}
