// SPDX-License-Identifier: GPL-3.0-only
package android.opengl;

import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * Pure-Java Ericsson Texture Compression (ETC1 / {@code OES_compressed_ETC1_RGB8_texture})
 * 4x4 block encoder/decoder and PKM 1.0 header parser/writer.
 */
public class ETC1 {
    public static final int ENCODED_BLOCK_SIZE = 8;
    public static final int DECODED_BLOCK_SIZE = 48;
    public static final int ETC_PKM_HEADER_SIZE = 16;
    public static final int ETC1_RGB8_OES = 0x8D64;

    private static final int[][] MODIFIER_TABLES = {
        {-8, -2, 2, 8},
        {-17, -5, 5, 17},
        {-29, -9, 9, 29},
        {-42, -13, 13, 42},
        {-60, -18, 18, 60},
        {-80, -24, 24, 80},
        {-106, -33, 33, 106},
        {-183, -47, 47, 183},
    };
    // Maps 2-bit pixel selector (msb<<1 | lsb) to MODIFIER_TABLES index:
    // 11 -> 0 (-large), 10 -> 1 (-small), 00 -> 2 (+small), 01 -> 3 (+large)
    private static final int[] SELECTOR_TO_INDEX = {2, 3, 1, 0};

    public ETC1() {}

    private static ByteBuffer asByteBuffer(Buffer buf, int minRemaining, boolean writable) {
        if (buf == null) {
            throw new IllegalArgumentException("Buffer must not be null");
        }
        if (!(buf instanceof ByteBuffer)) {
            throw new IllegalArgumentException("Buffer must be a ByteBuffer");
        }
        if (writable && buf.isReadOnly()) {
            throw new IllegalArgumentException("Output buffer is read-only");
        }
        if (buf.remaining() < minRemaining) {
            throw new IllegalArgumentException(
                    "Buffer remaining (" + buf.remaining() + ") < required (" + minRemaining + ")");
        }
        return (ByteBuffer) buf;
    }

    public static int getEncodedDataSize(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Negative dimensions");
        }
        return (((width + 3) & ~3) * ((height + 3) & ~3)) >> 1;
    }

    public static void formatHeader(Buffer header, int width, int height) {
        if (width < 0 || width > 0xFFFF || height < 0 || height > 0xFFFF) {
            throw new IllegalArgumentException("Dimensions out of 16-bit PKM range");
        }
        ByteBuffer out = asByteBuffer(header, ETC_PKM_HEADER_SIZE, true);
        int pos = out.position();
        int paddedW = (width + 3) & ~3;
        int paddedH = (height + 3) & ~3;
        out.put(pos + 0, (byte) 'P');
        out.put(pos + 1, (byte) 'K');
        out.put(pos + 2, (byte) 'M');
        out.put(pos + 3, (byte) ' ');
        out.put(pos + 4, (byte) '1');
        out.put(pos + 5, (byte) '0');
        out.put(pos + 6, (byte) 0);
        out.put(pos + 7, (byte) 0);
        out.put(pos + 8, (byte) ((paddedW >> 8) & 0xFF));
        out.put(pos + 9, (byte) (paddedW & 0xFF));
        out.put(pos + 10, (byte) ((paddedH >> 8) & 0xFF));
        out.put(pos + 11, (byte) (paddedH & 0xFF));
        out.put(pos + 12, (byte) ((width >> 8) & 0xFF));
        out.put(pos + 13, (byte) (width & 0xFF));
        out.put(pos + 14, (byte) ((height >> 8) & 0xFF));
        out.put(pos + 15, (byte) (height & 0xFF));
    }

    public static boolean isValid(Buffer header) {
        ByteBuffer in = asByteBuffer(header, ETC_PKM_HEADER_SIZE, false);
        int pos = in.position();
        if (in.get(pos + 0) != 'P'
                || in.get(pos + 1) != 'K'
                || in.get(pos + 2) != 'M'
                || in.get(pos + 3) != ' '
                || in.get(pos + 4) != '1'
                || in.get(pos + 5) != '0'
                || in.get(pos + 6) != 0
                || in.get(pos + 7) != 0) {
            return false;
        }
        int paddedW = u16be(in, pos + 8);
        int paddedH = u16be(in, pos + 10);
        int width = u16be(in, pos + 12);
        int height = u16be(in, pos + 14);
        return paddedW >= width && (paddedW - width) < 4
                && paddedH >= height && (paddedH - height) < 4;
    }

    public static int getWidth(Buffer header) {
        ByteBuffer in = asByteBuffer(header, ETC_PKM_HEADER_SIZE, false);
        return u16be(in, in.position() + 12);
    }

    public static int getHeight(Buffer header) {
        ByteBuffer in = asByteBuffer(header, ETC_PKM_HEADER_SIZE, false);
        return u16be(in, in.position() + 14);
    }

    private static int u16be(ByteBuffer buf, int idx) {
        return ((buf.get(idx) & 0xFF) << 8) | (buf.get(idx + 1) & 0xFF);
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static int extend4to8(int v4) {
        int c = v4 & 0xF;
        return (c << 4) | c;
    }

    private static int extend5to8(int v5) {
        int c = v5 & 0x1F;
        return (c << 3) | (c >> 2);
    }

    private static int signExtend3(int v3) {
        int c = v3 & 0x7;
        return (c & 0x4) != 0 ? (c - 8) : c;
    }

    /**
     * Decodes a single 8-byte ETC1 block into 48 bytes (4x4 RGB888, column-major x*4 + y per Khronos ETC1).
     */
    public static void decodeBlock(Buffer in, Buffer out) {
        ByteBuffer src = asByteBuffer(in, ENCODED_BLOCK_SIZE, false);
        ByteBuffer dst = asByteBuffer(out, DECODED_BLOCK_SIZE, true);
        byte[] block = new byte[8];
        int srcPos = src.position();
        for (int i = 0; i < 8; i++) {
            block[i] = src.get(srcPos + i);
        }
        byte[] rgb = new byte[DECODED_BLOCK_SIZE];
        decodeBlockBytes(block, rgb, true);
        int dstPos = dst.position();
        for (int i = 0; i < DECODED_BLOCK_SIZE; i++) {
            dst.put(dstPos + i, rgb[i]);
        }
    }

    private static void decodeBlockBytes(byte[] block, byte[] outRgb, boolean columnMajor) {
        int b0 = block[0] & 0xFF;
        int b1 = block[1] & 0xFF;
        int b2 = block[2] & 0xFF;
        int b3 = block[3] & 0xFF;
        boolean diff = (b3 & 0x02) != 0;
        boolean flip = (b3 & 0x01) != 0;
        int tableIdx1 = (b3 >> 5) & 0x7;
        int tableIdx2 = (b3 >> 2) & 0x7;

        int r1, g1, b1c, r2, g2, b2c;
        if (diff) {
            int br = (b0 >> 3) & 0x1F;
            int bg = (b1 >> 3) & 0x1F;
            int bb = (b2 >> 3) & 0x1F;
            int dr = signExtend3(b0);
            int dg = signExtend3(b1);
            int db = signExtend3(b2);
            r1 = extend5to8(br);
            g1 = extend5to8(bg);
            b1c = extend5to8(bb);
            r2 = extend5to8(clamp5(br + dr));
            g2 = extend5to8(clamp5(bg + dg));
            b2c = extend5to8(clamp5(bb + db));
        } else {
            r1 = extend4to8((b0 >> 4) & 0xF);
            r2 = extend4to8(b0 & 0xF);
            g1 = extend4to8((b1 >> 4) & 0xF);
            g2 = extend4to8(b1 & 0xF);
            b1c = extend4to8((b2 >> 4) & 0xF);
            b2c = extend4to8(b2 & 0xF);
        }

        int msbBits = ((block[4] & 0xFF) << 8) | (block[5] & 0xFF);
        int lsbBits = ((block[6] & 0xFF) << 8) | (block[7] & 0xFF);

        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                int bitIdx = x * 4 + y;
                int sel = (((msbBits >> bitIdx) & 1) << 1) | ((lsbBits >> bitIdx) & 1);
                boolean secondSubblock = flip ? (y >= 2) : (x >= 2);
                int mod = MODIFIER_TABLES[secondSubblock ? tableIdx2 : tableIdx1][SELECTOR_TO_INDEX[sel]];
                int pr = clamp255((secondSubblock ? r2 : r1) + mod);
                int pg = clamp255((secondSubblock ? g2 : g1) + mod);
                int pb = clamp255((secondSubblock ? b2c : b1c) + mod);
                int outIdx = (columnMajor ? (x * 4 + y) : (y * 4 + x)) * 3;
                outRgb[outIdx] = (byte) pr;
                outRgb[outIdx + 1] = (byte) pg;
                outRgb[outIdx + 2] = (byte) pb;
            }
        }
    }

    private static int clamp5(int v) {
        return v < 0 ? 0 : (v > 31 ? 31 : v);
    }

    /**
     * Encodes a 4x4 RGB888 block (48 bytes, column-major {@code x*4 + y}) into an 8-byte ETC1 block.
     */
    public static void encodeBlock(Buffer in, int validPixelMask, Buffer out) {
        if ((validPixelMask & ~0xFFFF) != 0) {
            throw new IllegalArgumentException("Invalid validPixelMask: " + validPixelMask);
        }
        ByteBuffer src = asByteBuffer(in, DECODED_BLOCK_SIZE, false);
        ByteBuffer dst = asByteBuffer(out, ENCODED_BLOCK_SIZE, true);
        byte[] pixels = new byte[DECODED_BLOCK_SIZE];
        int srcPos = src.position();
        for (int i = 0; i < DECODED_BLOCK_SIZE; i++) {
            pixels[i] = src.get(srcPos + i);
        }
        byte[] encoded = new byte[ENCODED_BLOCK_SIZE];
        encodeBlockBytes(pixels, validPixelMask == 0 ? 0xFFFF : validPixelMask, encoded);
        int dstPos = dst.position();
        for (int i = 0; i < ENCODED_BLOCK_SIZE; i++) {
            dst.put(dstPos + i, encoded[i]);
        }
    }

    private static void encodeBlockBytes(byte[] pixelsColMajor, int mask, byte[] outBlock) {
        // Evaluate flip=0 (2x4 vertical subblocks) in individual 4:4:4 mode with optimal modifier table search.
        int rSum1 = 0, gSum1 = 0, bSum1 = 0, n1 = 0;
        int rSum2 = 0, gSum2 = 0, bSum2 = 0, n2 = 0;
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                int idx = x * 4 + y;
                if (((mask >> idx) & 1) == 0) continue;
                int r = pixelsColMajor[idx * 3] & 0xFF;
                int g = pixelsColMajor[idx * 3 + 1] & 0xFF;
                int b = pixelsColMajor[idx * 3 + 2] & 0xFF;
                if (x < 2) {
                    rSum1 += r; gSum1 += g; bSum1 += b; n1++;
                } else {
                    rSum2 += r; gSum2 += g; bSum2 += b; n2++;
                }
            }
        }
        int r4_1 = n1 > 0 ? ((rSum1 / n1 + 8) * 15 / 255) : 0;
        int g4_1 = n1 > 0 ? ((gSum1 / n1 + 8) * 15 / 255) : 0;
        int b4_1 = n1 > 0 ? ((bSum1 / n1 + 8) * 15 / 255) : 0;
        int r4_2 = n2 > 0 ? ((rSum2 / n2 + 8) * 15 / 255) : r4_1;
        int g4_2 = n2 > 0 ? ((gSum2 / n2 + 8) * 15 / 255) : g4_1;
        int b4_2 = n2 > 0 ? ((bSum2 / n2 + 8) * 15 / 255) : b4_1;
        if (n1 == 0) { r4_1 = r4_2; g4_1 = g4_2; b4_1 = b4_2; }

        int baseR1 = extend4to8(r4_1), baseG1 = extend4to8(g4_1), baseB1 = extend4to8(b4_1);
        int baseR2 = extend4to8(r4_2), baseG2 = extend4to8(g4_2), baseB2 = extend4to8(b4_2);

        int[] sel1 = new int[16];
        int table1 = chooseTableAndSelectors(pixelsColMajor, mask, 0, 2, baseR1, baseG1, baseB1, sel1);
        int table2 = chooseTableAndSelectors(pixelsColMajor, mask, 2, 4, baseR2, baseG2, baseB2, sel1);

        int msb = 0, lsb = 0;
        for (int i = 0; i < 16; i++) {
            msb |= ((sel1[i] >> 1) & 1) << i;
            lsb |= (sel1[i] & 1) << i;
        }

        outBlock[0] = (byte) (((r4_1 & 0xF) << 4) | (r4_2 & 0xF));
        outBlock[1] = (byte) (((g4_1 & 0xF) << 4) | (g4_2 & 0xF));
        outBlock[2] = (byte) (((b4_1 & 0xF) << 4) | (b4_2 & 0xF));
        // table1 (bits 7..5), table2 (bits 4..2), diff=0 (bit 1), flip=0 (bit 0)
        outBlock[3] = (byte) (((table1 & 0x7) << 5) | ((table2 & 0x7) << 2));
        outBlock[4] = (byte) ((msb >> 8) & 0xFF);
        outBlock[5] = (byte) (msb & 0xFF);
        outBlock[6] = (byte) ((lsb >> 8) & 0xFF);
        outBlock[7] = (byte) (lsb & 0xFF);
    }

    private static final int[] INDEX_TO_SELECTOR = {3, 2, 0, 1};

    private static int chooseTableAndSelectors(
            byte[] pixelsColMajor, int mask, int xStart, int xEnd,
            int baseR, int baseG, int baseB, int[] outSelectors) {
        int bestTable = 0;
        long bestErr = Long.MAX_VALUE;
        int[] tmpSel = new int[16];
        for (int t = 0; t < 8; t++) {
            int[] mods = MODIFIER_TABLES[t];
            long errSum = 0;
            for (int x = xStart; x < xEnd; x++) {
                for (int y = 0; y < 4; y++) {
                    int idx = x * 4 + y;
                    if (((mask >> idx) & 1) == 0) {
                        tmpSel[idx] = 0;
                        continue;
                    }
                    int pr = pixelsColMajor[idx * 3] & 0xFF;
                    int pg = pixelsColMajor[idx * 3 + 1] & 0xFF;
                    int pb = pixelsColMajor[idx * 3 + 2] & 0xFF;
                    int bestM = 0;
                    int bestPixelErr = Integer.MAX_VALUE;
                    for (int m = 0; m < 4; m++) {
                        int dr = clamp255(baseR + mods[m]) - pr;
                        int dg = clamp255(baseG + mods[m]) - pg;
                        int db = clamp255(baseB + mods[m]) - pb;
                        int pe = dr * dr + dg * dg + db * db;
                        if (pe < bestPixelErr) {
                            bestPixelErr = pe;
                            bestM = m;
                        }
                    }
                    tmpSel[idx] = INDEX_TO_SELECTOR[bestM];
                    errSum += bestPixelErr;
                }
            }
            if (errSum < bestErr) {
                bestErr = errSum;
                bestTable = t;
                for (int x = xStart; x < xEnd; x++) {
                    for (int y = 0; y < 4; y++) {
                        int idx = x * 4 + y;
                        outSelectors[idx] = tmpSel[idx];
                    }
                }
            }
        }
        return bestTable;
    }

    public static void encodeImage(
            Buffer in, int width, int height, int pixelSize, int stride, Buffer out) {
        if (pixelSize != 2 && pixelSize != 3) {
            throw new IllegalArgumentException("pixelSize must be 2 (RGB565) or 3 (RGB888)");
        }
        if (width < 0 || height < 0 || stride < width * pixelSize) {
            throw new IllegalArgumentException("Invalid image dimensions or stride");
        }
        int inBytesNeeded = height == 0 ? 0 : (height - 1) * stride + width * pixelSize;
        int encodedSize = getEncodedDataSize(width, height);
        ByteBuffer src = asByteBuffer(in, inBytesNeeded, false);
        ByteBuffer dst = asByteBuffer(out, encodedSize, true);
        int srcBase = src.position();
        int dstPos = dst.position();

        byte[] blockPixels = new byte[DECODED_BLOCK_SIZE];
        byte[] encodedBlock = new byte[ENCODED_BLOCK_SIZE];
        for (int by = 0; by < height; by += 4) {
            for (int bx = 0; bx < width; bx += 4) {
                int mask = 0;
                for (int x = 0; x < 4; x++) {
                    for (int y = 0; y < 4; y++) {
                        int px = bx + x;
                        int py = by + y;
                        int idx = x * 4 + y;
                        if (px < width && py < height) {
                            mask |= (1 << idx);
                            int pOff = srcBase + py * stride + px * pixelSize;
                            if (pixelSize == 3) {
                                blockPixels[idx * 3] = src.get(pOff);
                                blockPixels[idx * 3 + 1] = src.get(pOff + 1);
                                blockPixels[idx * 3 + 2] = src.get(pOff + 2);
                            } else {
                                int lo = src.get(pOff) & 0xFF;
                                int hi = src.get(pOff + 1) & 0xFF;
                                int rgb565 = lo | (hi << 8);
                                int r5 = (rgb565 >> 11) & 0x1F;
                                int g6 = (rgb565 >> 5) & 0x3F;
                                int b5 = rgb565 & 0x1F;
                                blockPixels[idx * 3] = (byte) ((r5 << 3) | (r5 >> 2));
                                blockPixels[idx * 3 + 1] = (byte) ((g6 << 2) | (g6 >> 4));
                                blockPixels[idx * 3 + 2] = (byte) ((b5 << 3) | (b5 >> 2));
                            }
                        } else {
                            blockPixels[idx * 3] = 0;
                            blockPixels[idx * 3 + 1] = 0;
                            blockPixels[idx * 3 + 2] = 0;
                        }
                    }
                }
                encodeBlockBytes(blockPixels, mask, encodedBlock);
                for (int i = 0; i < ENCODED_BLOCK_SIZE; i++) {
                    dst.put(dstPos++, encodedBlock[i]);
                }
            }
        }
    }

    public static void decodeImage(
            Buffer in, Buffer out, int width, int height, int pixelSize, int stride) {
        if (pixelSize != 2 && pixelSize != 3) {
            throw new IllegalArgumentException("pixelSize must be 2 (RGB565) or 3 (RGB888)");
        }
        if (width < 0 || height < 0 || stride < width * pixelSize) {
            throw new IllegalArgumentException("Invalid image dimensions or stride");
        }
        int encodedSize = getEncodedDataSize(width, height);
        int outBytesNeeded = height == 0 ? 0 : (height - 1) * stride + width * pixelSize;
        ByteBuffer src = asByteBuffer(in, encodedSize, false);
        ByteBuffer dst = asByteBuffer(out, outBytesNeeded, true);
        int srcPos = src.position();
        int dstBase = dst.position();

        byte[] encodedBlock = new byte[ENCODED_BLOCK_SIZE];
        byte[] decodedRgb = new byte[DECODED_BLOCK_SIZE];
        for (int by = 0; by < height; by += 4) {
            for (int bx = 0; bx < width; bx += 4) {
                for (int i = 0; i < ENCODED_BLOCK_SIZE; i++) {
                    encodedBlock[i] = src.get(srcPos++);
                }
                decodeBlockBytes(encodedBlock, decodedRgb, true);
                for (int x = 0; x < 4; x++) {
                    int px = bx + x;
                    if (px >= width) continue;
                    for (int y = 0; y < 4; y++) {
                        int py = by + y;
                        if (py >= height) continue;
                        int idx = (x * 4 + y) * 3;
                        int r = decodedRgb[idx] & 0xFF;
                        int g = decodedRgb[idx + 1] & 0xFF;
                        int b = decodedRgb[idx + 2] & 0xFF;
                        int outOff = dstBase + py * stride + px * pixelSize;
                        if (pixelSize == 3) {
                            dst.put(outOff, (byte) r);
                            dst.put(outOff + 1, (byte) g);
                            dst.put(outOff + 2, (byte) b);
                        } else {
                            int rgb565 = ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3);
                            dst.put(outOff, (byte) (rgb565 & 0xFF));
                            dst.put(outOff + 1, (byte) ((rgb565 >> 8) & 0xFF));
                        }
                    }
                }
            }
        }
    }
}
