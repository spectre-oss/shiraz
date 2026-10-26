// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/** Streaming RGBA PNG writer; extra storage is one scanline and one output block. */
final class PngEncoder {
    private static final byte[] IHDR = {73,72,68,82}, IDAT = {73,68,65,84}, IEND = {73,69,78,68};
    static boolean write(int width, int height, int[] pixels, boolean alphaOnly, OutputStream target) {
        Deflater compressor = new Deflater();
        try {
            DataOutputStream out = new DataOutputStream(target);
            out.write(new byte[]{(byte)137,80,78,71,13,10,26,10});
            byte[] header = new byte[13];
            putInt(header, 0, width); putInt(header, 4, height);
            header[8] = 8; header[9] = 6; // Eight-bit RGBA, no interlacing.
            chunk(out, IHDR, header, header.length);
            byte[] row = new byte[1 + width * 4];
            byte[] compressed = new byte[32768];
            for (int y = 0; y < height; y++) {
                // Filter zero keeps encoding independent of the previous row.
                for (int x = 0; x < width; x++) {
                    int color = pixels[y * width + x], offset = 1 + x * 4;
                    row[offset] = alphaOnly ? 0 : (byte)(color >>> 16);
                    row[offset + 1] = alphaOnly ? 0 : (byte)(color >>> 8);
                    row[offset + 2] = alphaOnly ? 0 : (byte)color;
                    row[offset + 3] = (byte)(color >>> 24);
                }
                compressor.setInput(row);
                while (!compressor.needsInput()) {
                    int count = compressor.deflate(compressed);
                    if (count != 0) chunk(out, IDAT, compressed, count);
                }
            }
            compressor.finish();
            while (!compressor.finished()) {
                int count = compressor.deflate(compressed);
                if (count != 0) chunk(out, IDAT, compressed, count);
            }
            chunk(out, IEND, new byte[0], 0);
            return true;
        } catch (IOException failure) {
            return false;
        } finally {
            compressor.end();
        }
    }
    private static void putInt(byte[] data, int offset, int value) {
        for (int i = 0; i < 4; i++) data[offset + i] = (byte)(value >>> (24 - i * 8));
    }
    private static void chunk(DataOutputStream out, byte[] type, byte[] bytes, int count) throws IOException {
        out.writeInt(count); out.write(type); out.write(bytes, 0, count);
        CRC32 crc = new CRC32(); crc.update(type); crc.update(bytes, 0, count);
        out.writeInt((int)crc.getValue());
    }
}
