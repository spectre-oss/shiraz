// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/** Animated or single-frame raster movie supporting GIF87a/89a, PNG, JPEG, WebP, and BMP. */
public class Movie {
    static final class Frame {
        final Bitmap bitmap;
        final int delayMs;

        Frame(Bitmap bitmap, int delayMs) {
            this.bitmap = bitmap;
            this.delayMs = delayMs;
        }
    }

    private final int movieWidth;
    private final int movieHeight;
    private final boolean opaque;
    private final int totalDuration;
    private final Frame[] frames;
    private int currentFrameIndex;
    private int currentTimeMs = -1;

    Movie(int width, int height, boolean opaque, Frame[] frames) {
        this.movieWidth = width;
        this.movieHeight = height;
        this.opaque = opaque;
        this.frames = frames;
        int sum = 0;
        if (frames.length > 1) {
            for (int i = 0; i < frames.length; i++) {
                sum += Math.max(10, frames[i].delayMs);
            }
        }
        this.totalDuration = sum;
    }

    Frame[] shirazFrames() {
        return frames;
    }

    public int shirazFrameCount() {
        return frames.length;
    }

    public Bitmap shirazFrameBitmap(int index) {
        return frames[index].bitmap;
    }

    public int shirazFrameDelay(int index) {
        return frames[index].delayMs;
    }

    public int width() {
        return movieWidth;
    }

    public int height() {
        return movieHeight;
    }

    public boolean isOpaque() {
        return opaque;
    }

    public int duration() {
        return totalDuration;
    }

    public boolean setTime(int relativeMilliseconds) {
        if (relativeMilliseconds < 0) {
            relativeMilliseconds = 0;
        }
        int nextIndex = 0;
        if (frames.length > 1 && totalDuration > 0) {
            int t = relativeMilliseconds <= totalDuration
                    ? relativeMilliseconds
                    : (relativeMilliseconds % totalDuration);
            int acc = 0;
            for (int i = 0; i < frames.length; i++) {
                acc += Math.max(10, frames[i].delayMs);
                if (t < acc) {
                    nextIndex = i;
                    break;
                }
                nextIndex = i;
            }
        }
        boolean changed = (nextIndex != currentFrameIndex) || (currentTimeMs == -1);
        currentFrameIndex = nextIndex;
        currentTimeMs = relativeMilliseconds;
        return changed;
    }

    public void draw(Canvas canvas, float x, float y, Paint paint) {
        if (canvas == null) {
            throw new NullPointerException("canvas");
        }
        if (frames.length == 0) {
            return;
        }
        canvas.drawBitmap(frames[currentFrameIndex].bitmap, x, y, paint);
    }

    public void draw(Canvas canvas, float x, float y) {
        draw(canvas, x, y, null);
    }

    public static Movie decodeStream(InputStream is) {
        if (is == null) {
            return null;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                if (n == 0) {
                    int b = is.read();
                    if (b < 0) break;
                    out.write(b);
                } else {
                    out.write(buf, 0, n);
                }
                if (out.size() > 16 * 1024 * 1024) {
                    return null;
                }
            }
            byte[] data = out.toByteArray();
            return decodeByteArray(data, 0, data.length);
        } catch (IOException e) {
            return null;
        }
    }

    public static Movie decodeByteArray(byte[] data, int offset, int length) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        if ((offset | length) < 0 || offset > data.length - length) {
            throw new ArrayIndexOutOfBoundsException("Movie byte range");
        }
        if (length < 6) {
            return null;
        }
        if (isGif(data, offset, length)) {
            return decodeGif(data, offset, length);
        }
        if (isBmp(data, offset, length)) {
            Bitmap bmp = decodeBmp(data, offset, length);
            if (bmp == null) return null;
            return new Movie(bmp.getWidth(), bmp.getHeight(), !bmp.hasAlpha(),
                    new Frame[]{new Frame(bmp, 0)});
        }
        Bitmap bmp = BitmapFactory.decodeByteArray(data, offset, length);
        if (bmp == null) {
            return null;
        }
        boolean opaque = checkOpaque(bmp);
        return new Movie(bmp.getWidth(), bmp.getHeight(), opaque, new Frame[]{new Frame(bmp, 0)});
    }

    public static Movie decodeFile(String pathName) {
        if (pathName == null) {
            return null;
        }
        InputStream is = null;
        try {
            is = new FileInputStream(pathName);
            return decodeStream(is);
        } catch (IOException e) {
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    static boolean isGif(byte[] d, int off, int len) {
        return len >= 6
                && d[off] == 'G' && d[off + 1] == 'I' && d[off + 2] == 'F'
                && d[off + 3] == '8' && (d[off + 4] == '7' || d[off + 4] == '9')
                && d[off + 5] == 'a';
    }

    static boolean isBmp(byte[] d, int off, int len) {
        return len >= 26 && d[off] == 'B' && d[off + 1] == 'M';
    }

    private static boolean checkOpaque(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            if ((px[i] >>> 24) != 255) {
                return false;
            }
        }
        return true;
    }

    static Bitmap decodeBmp(byte[] d, int off, int len) {
        if (!isBmp(d, off, len) || len < 54) {
            return null;
        }
        int pixelOffset = readLe32(d, off + 10);
        int headerSize = readLe32(d, off + 14);
        if (headerSize < 40 || pixelOffset < 0 || pixelOffset > len) {
            return null;
        }
        int width = readLe32(d, off + 18);
        int rawHeight = readLe32(d, off + 22);
        boolean topDown = rawHeight < 0;
        int height = Math.abs(rawHeight);
        int bpp = readLe16(d, off + 28);
        int compression = readLe32(d, off + 30);
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192
                || (long) width * height > 16L * 1024 * 1024) {
            return null;
        }
        if (compression != 0 || (bpp != 24 && bpp != 32)) {
            return null;
        }
        int rowStride = ((width * bpp + 31) / 32) * 4;
        if ((long) pixelOffset + (long) rowStride * height > len) {
            return null;
        }
        int[] pixels = new int[width * height];
        boolean hasAlpha = bpp == 32;
        for (int row = 0; row < height; row++) {
            int srcRow = topDown ? row : (height - 1 - row);
            int srcPos = off + pixelOffset + srcRow * rowStride;
            int dstRow = row * width;
            for (int col = 0; col < width; col++) {
                int p = srcPos + col * (bpp / 8);
                int b = d[p] & 255;
                int g = d[p + 1] & 255;
                int r = d[p + 2] & 255;
                int a = hasAlpha ? (d[p + 3] & 255) : 255;
                pixels[dstRow + col] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        Bitmap bmp = Bitmap.fromPixels(width, height, pixels);
        bmp.setHasAlpha(hasAlpha);
        bmp.freeze();
        return bmp;
    }

    private static int readLe16(byte[] d, int p) {
        return (d[p] & 255) | ((d[p + 1] & 255) << 8);
    }

    private static int readLe32(byte[] d, int p) {
        return (d[p] & 255)
                | ((d[p + 1] & 255) << 8)
                | ((d[p + 2] & 255) << 16)
                | ((d[p + 3] & 255) << 24);
    }

    static Movie decodeGif(byte[] d, int off, int len) {
        int end = off + len;
        if (len < 13) return null;
        int width = readLe16(d, off + 6);
        int height = readLe16(d, off + 8);
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096
                || (long) width * height > 8L * 1024 * 1024) {
            return null;
        }
        int packed = d[off + 10] & 255;
        boolean gctFlag = (packed & 0x80) != 0;
        int gctSize = 2 << (packed & 7);
        int pos = off + 13;
        int[] gct = null;
        if (gctFlag) {
            if (pos + gctSize * 3 > end) return null;
            gct = readColorTable(d, pos, gctSize);
            pos += gctSize * 3;
        }
        int[] canvas = new int[width * height];
        ArrayList<Frame> frameList = new ArrayList<Frame>();
        int delayMs = 100;
        int disposal = 0;
        int transIndex = -1;
        boolean allOpaque = true;

        while (pos < end) {
            int blockId = d[pos++] & 255;
            if (blockId == 0x3B) {
                break;
            } else if (blockId == 0x21) {
                if (pos >= end) break;
                int label = d[pos++] & 255;
                if (label == 0xF9 && pos + 5 <= end) {
                    int blockSize = d[pos++] & 255;
                    if (blockSize >= 4 && pos + blockSize <= end) {
                        int gcePacked = d[pos] & 255;
                        disposal = (gcePacked >>> 2) & 7;
                        boolean transFlag = (gcePacked & 1) != 0;
                        int delayCs = readLe16(d, pos + 1);
                        delayMs = delayCs <= 1 ? 100 : delayCs * 10;
                        transIndex = transFlag ? (d[pos + 3] & 255) : -1;
                    }
                    pos += blockSize;
                    pos = skipSubBlocks(d, pos, end);
                } else {
                    pos = skipSubBlocks(d, pos, end);
                }
            } else if (blockId == 0x2C) {
                if (pos + 9 > end) return null;
                int fx = readLe16(d, pos);
                int fy = readLe16(d, pos + 2);
                int fw = readLe16(d, pos + 4);
                int fh = readLe16(d, pos + 6);
                int fPacked = d[pos + 8] & 255;
                pos += 9;
                boolean lctFlag = (fPacked & 0x80) != 0;
                boolean interlace = (fPacked & 0x40) != 0;
                int[] act = gct;
                if (lctFlag) {
                    int lctSize = 2 << (fPacked & 7);
                    if (pos + lctSize * 3 > end) return null;
                    act = readColorTable(d, pos, lctSize);
                    pos += lctSize * 3;
                }
                if (pos >= end || act == null || fw <= 0 || fh <= 0) return null;
                int minCodeSize = d[pos++] & 255;
                ByteArrayOutputStream compressed = new ByteArrayOutputStream();
                while (pos < end) {
                    int subLen = d[pos++] & 255;
                    if (subLen == 0) break;
                    if (pos + subLen > end) return null;
                    compressed.write(d, pos, subLen);
                    pos += subLen;
                }
                byte[] indices = lzwDecode(compressed.toByteArray(), minCodeSize, fw * fh);
                if (indices == null) return null;
                int[] savedPrev = disposal == 3 ? canvas.clone() : null;
                renderGifFrame(canvas, width, height, fx, fy, fw, fh, indices, act, transIndex, interlace);
                for (int i = 0; i < canvas.length; i++) {
                    if ((canvas[i] >>> 24) != 255) {
                        allOpaque = false;
                        break;
                    }
                }
                Bitmap frameBmp = Bitmap.fromPixels(width, height, canvas.clone());
                frameBmp.freeze();
                frameList.add(new Frame(frameBmp, delayMs));
                if (disposal == 2) {
                    for (int row = 0; row < fh; row++) {
                        int y = fy + row;
                        if (y < 0 || y >= height) continue;
                        for (int col = 0; col < fw; col++) {
                            int x = fx + col;
                            if (x >= 0 && x < width) canvas[y * width + x] = 0;
                        }
                    }
                } else if (disposal == 3 && savedPrev != null) {
                    System.arraycopy(savedPrev, 0, canvas, 0, canvas.length);
                }
                delayMs = 100;
                disposal = 0;
                transIndex = -1;
            } else {
                break;
            }
        }
        if (frameList.isEmpty()) return null;
        return new Movie(width, height, allOpaque, frameList.toArray(new Frame[frameList.size()]));
    }

    private static int[] readColorTable(byte[] d, int pos, int count) {
        int[] table = new int[count];
        for (int i = 0; i < count; i++) {
            int p = pos + i * 3;
            table[i] = 0xff000000
                    | ((d[p] & 255) << 16)
                    | ((d[p + 1] & 255) << 8)
                    | (d[p + 2] & 255);
        }
        return table;
    }

    private static int skipSubBlocks(byte[] d, int pos, int end) {
        while (pos < end) {
            int len = d[pos++] & 255;
            if (len == 0) break;
            pos = Math.min(end, pos + len);
        }
        return pos;
    }

    private static void renderGifFrame(
            int[] canvas, int cw, int ch,
            int fx, int fy, int fw, int fh,
            byte[] indices, int[] colorTable, int transIndex, boolean interlace) {
        int pass = 0;
        int inc = 8;
        int line = 0;
        for (int i = 0; i < fh; i++) {
            int srcRow = i;
            int dstRow = i;
            if (interlace) {
                if (line >= fh) {
                    pass++;
                    switch (pass) {
                        case 1: line = 4; inc = 8; break;
                        case 2: line = 2; inc = 4; break;
                        case 3: line = 1; inc = 2; break;
                        default: line = i; inc = 1; break;
                    }
                }
                dstRow = line;
                line += inc;
            }
            int y = fy + dstRow;
            if (y < 0 || y >= ch) continue;
            for (int col = 0; col < fw; col++) {
                int x = fx + col;
                if (x < 0 || x >= cw) continue;
                int idx = indices[srcRow * fw + col] & 255;
                if (idx == transIndex) continue;
                if (idx < colorTable.length) {
                    canvas[y * cw + x] = colorTable[idx];
                }
            }
        }
    }

    private static byte[] lzwDecode(byte[] data, int minCodeSize, int pixelCount) {
        if (minCodeSize < 2 || minCodeSize > 8 || pixelCount <= 0) {
            return null;
        }
        final int maxStack = 4096;
        int clearCode = 1 << minCodeSize;
        int endCode = clearCode + 1;
        int avail = clearCode + 2;
        int codeSize = minCodeSize + 1;
        int codeMask = (1 << codeSize) - 1;
        short[] prefix = new short[maxStack];
        byte[] suffix = new byte[maxStack];
        byte[] pixelStack = new byte[maxStack + 1];
        for (int code = 0; code < clearCode; code++) {
            prefix[code] = 0;
            suffix[code] = (byte) code;
        }
        byte[] pixels = new byte[pixelCount];
        int datum = 0, bits = 0, first = 0, top = 0, pi = 0, bi = 0;
        int oldCode = -1;
        while (pi < pixelCount) {
            if (top == 0) {
                if (bits < codeSize) {
                    if (bi >= data.length) break;
                    datum |= (data[bi++] & 255) << bits;
                    bits += 8;
                    continue;
                }
                int code = datum & codeMask;
                datum >>>= codeSize;
                bits -= codeSize;
                if (code > avail || code == endCode) {
                    break;
                }
                if (code == clearCode) {
                    codeSize = minCodeSize + 1;
                    codeMask = (1 << codeSize) - 1;
                    avail = clearCode + 2;
                    oldCode = -1;
                    continue;
                }
                if (oldCode == -1) {
                    pixelStack[top++] = suffix[code];
                    oldCode = code;
                    first = code;
                    continue;
                }
                int inCode = code;
                if (code == avail) {
                    pixelStack[top++] = (byte) first;
                    code = oldCode;
                }
                while (code > clearCode) {
                    if (top >= maxStack) return null;
                    pixelStack[top++] = suffix[code];
                    code = prefix[code] & 0xffff;
                }
                first = suffix[code] & 255;
                if (top >= maxStack) return null;
                pixelStack[top++] = (byte) first;
                if (avail < maxStack) {
                    prefix[avail] = (short) oldCode;
                    suffix[avail] = (byte) first;
                    avail++;
                    if ((avail & codeMask) == 0 && avail < maxStack) {
                        codeSize++;
                        codeMask += avail;
                    }
                }
                oldCode = inCode;
            }
            top--;
            pixels[pi++] = pixelStack[top];
        }
        return pixels;
    }
}
