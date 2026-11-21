// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import java.nio.ByteBuffer;

public final class MediaDescrambler implements AutoCloseable {
    public static final byte SCRAMBLE_CONTROL_UNSCRAMBLED = 0;
    public static final byte SCRAMBLE_CONTROL_RESERVED = 1;
    public static final byte SCRAMBLE_CONTROL_EVEN_KEY = 2;
    public static final byte SCRAMBLE_CONTROL_ODD_KEY = 3;
    public static final byte SCRAMBLE_FLAG_PES_HEADER = 1;

    public MediaDescrambler(int CA_system_id) throws MediaCasException.UnsupportedCasException {
        throw new MediaCasException.UnsupportedCasException("Unsupported CA_system_id " + CA_system_id);
    }

    public boolean requiresSecureDecoderComponent(String mime) {
        return false;
    }

    public void setMediaCasSession(MediaCas.Session session) {
        throw new IllegalStateException("MediaDescrambler unavailable on desktop host");
    }

    public int descramble(ByteBuffer srcBuf, ByteBuffer dstBuf, MediaCodec.CryptoInfo cryptoInfo) {
        throw new IllegalStateException("MediaDescrambler unavailable on desktop host");
    }

    @Override
    public void close() {
    }

    @Override
    protected void finalize() {
        close();
    }
}
