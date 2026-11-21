// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2;

import android.graphics.Bitmap;
import android.location.Location;
import android.media.Image;
import android.util.Size;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class DngCreator implements AutoCloseable {
    public static final int MAX_THUMBNAIL_DIMENSION = 256;

    private static final int TYPE_BYTE = 1;
    private static final int TYPE_ASCII = 2;
    private static final int TYPE_SHORT = 3;
    private static final int TYPE_LONG = 4;

    private final CameraCharacteristics mCharacteristics;
    private final CaptureResult mMetadata;
    private int mOrientation = 1;
    private String mDescription;
    private Location mLocation;
    private boolean mClosed;

    public DngCreator(CameraCharacteristics characteristics, CaptureResult metadata) {
        if (characteristics == null || metadata == null) {
            throw new IllegalArgumentException("Null argument to DngCreator constructor");
        }
        mCharacteristics = characteristics;
        mMetadata = metadata;
    }

    private void ensureOpen() {
        if (mClosed) {
            throw new IllegalStateException("DngCreator has already been closed");
        }
    }

    public DngCreator setOrientation(int orientation) {
        ensureOpen();
        if (orientation < 0 || orientation > 8) {
            throw new IllegalArgumentException("Orientation " + orientation + " is not a valid EXIF orientation");
        }
        mOrientation = orientation == 0 ? 1 : orientation;
        return this;
    }

    public DngCreator setThumbnail(Bitmap pixels) {
        ensureOpen();
        if (pixels == null) {
            throw new IllegalArgumentException("Null argument to setThumbnail");
        }
        if (pixels.getWidth() > MAX_THUMBNAIL_DIMENSION
                || pixels.getHeight() > MAX_THUMBNAIL_DIMENSION) {
            throw new IllegalArgumentException(
                    "Thumbnail dimensions exceed " + MAX_THUMBNAIL_DIMENSION);
        }
        return this;
    }

    public DngCreator setThumbnail(Image pixels) {
        ensureOpen();
        if (pixels == null) {
            throw new IllegalArgumentException("Null argument to setThumbnail");
        }
        if (pixels.getWidth() > MAX_THUMBNAIL_DIMENSION
                || pixels.getHeight() > MAX_THUMBNAIL_DIMENSION) {
            throw new IllegalArgumentException(
                    "Thumbnail dimensions exceed " + MAX_THUMBNAIL_DIMENSION);
        }
        return this;
    }

    public DngCreator setLocation(Location location) {
        ensureOpen();
        if (location == null) {
            throw new IllegalArgumentException("Null location passed to setLocation");
        }
        mLocation = new Location(location);
        return this;
    }

    public DngCreator setDescription(String description) {
        ensureOpen();
        if (description == null) {
            throw new IllegalArgumentException("Null description passed to setDescription");
        }
        mDescription = description;
        return this;
    }

    public void writeInputStream(
            OutputStream dngOutput,
            Size size,
            InputStream pixels,
            long offset) throws IOException {
        ensureOpen();
        if (dngOutput == null || size == null || pixels == null) {
            throw new IllegalArgumentException("Null argument to writeInputStream");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Negative offset passed to writeInputStream");
        }
        int width = size.getWidth();
        int height = size.getHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Size with invalid width/height passed to writeInputStream");
        }
        long remainingSkip = offset;
        while (remainingSkip > 0) {
            long skipped = pixels.skip(remainingSkip);
            if (skipped <= 0) {
                if (pixels.read() < 0) {
                    throw new EOFException("EOF reached before offset " + offset);
                }
                skipped = 1;
            }
            remainingSkip -= skipped;
        }
        int byteCount = width * height * 2;
        byte[] raw = new byte[byteCount];
        int pos = 0;
        while (pos < byteCount) {
            int n = pixels.read(raw, pos, byteCount - pos);
            if (n < 0) {
                throw new IOException("Insufficient pixel data in InputStream");
            }
            pos += n;
        }
        writeTiffDng(dngOutput, width, height, raw);
    }

    public void writeByteBuffer(
            OutputStream dngOutput,
            Size size,
            ByteBuffer pixels,
            long offset) throws IOException {
        ensureOpen();
        if (dngOutput == null || size == null || pixels == null) {
            throw new IllegalArgumentException("Null argument to writeByteBuffer");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Negative offset passed to writeByteBuffer");
        }
        int width = size.getWidth();
        int height = size.getHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Size with invalid width/height passed to writeByteBuffer");
        }
        int byteCount = width * height * 2;
        if (offset + byteCount > pixels.limit()) {
            throw new IllegalArgumentException("ByteBuffer capacity too small for requested size + offset");
        }
        ByteBuffer dup = pixels.duplicate();
        dup.position((int) offset);
        byte[] raw = new byte[byteCount];
        dup.get(raw);
        writeTiffDng(dngOutput, width, height, raw);
    }

    public void writeImage(OutputStream dngOutput, Image pixels) throws IOException {
        ensureOpen();
        if (dngOutput == null || pixels == null) {
            throw new IllegalArgumentException("Null argument to writeImage");
        }
        Image.Plane[] planes = pixels.getPlanes();
        if (planes == null || planes.length == 0) {
            throw new IllegalArgumentException("Image has no planes");
        }
        ByteBuffer buf = planes[0].getBuffer();
        int width = pixels.getWidth();
        int height = pixels.getHeight();
        int needed = width * height * 2;
        ByteBuffer dup = buf.duplicate();
        byte[] raw = new byte[needed];
        int avail = Math.min(needed, dup.remaining());
        dup.get(raw, 0, avail);
        writeTiffDng(dngOutput, width, height, raw);
    }

    private void writeTiffDng(OutputStream out, int width, int height, byte[] rawPixels)
            throws IOException {
        byte[] descBytes = null;
        if (mDescription != null) {
            byte[] utf = mDescription.getBytes(StandardCharsets.UTF_8);
            descBytes = new byte[utf.length + 1];
            System.arraycopy(utf, 0, descBytes, 0, utf.length);
        }

        int entryCount = descBytes != null ? 12 : 11;
        int ifdSize = 2 + entryCount * 12 + 4;
        int extraDataOffset = 8 + ifdSize;
        int descOffset = extraDataOffset;
        int stripOffset = extraDataOffset + (descBytes != null ? ((descBytes.length + 1) & ~1) : 0);

        ByteBuffer header = ByteBuffer.allocate(stripOffset).order(ByteOrder.LITTLE_ENDIAN);
        header.put((byte) 'I');
        header.put((byte) 'I');
        header.putShort((short) 42);
        header.putInt(8);

        header.putShort((short) entryCount);
        putIfdEntry(header, 256, TYPE_LONG, 1, width);          // ImageWidth
        putIfdEntry(header, 257, TYPE_LONG, 1, height);         // ImageLength
        putIfdEntry(header, 258, TYPE_SHORT, 1, 16);            // BitsPerSample
        putIfdEntry(header, 259, TYPE_SHORT, 1, 1);             // Compression = Uncompressed
        putIfdEntry(header, 262, TYPE_SHORT, 1, 32803);         // PhotometricInterpretation = CFA
        if (descBytes != null) {
            int valOrOffset = descBytes.length <= 4 ? packBytes(descBytes) : descOffset;
            putIfdEntry(header, 270, TYPE_ASCII, descBytes.length, valOrOffset); // ImageDescription
        }
        putIfdEntry(header, 273, TYPE_LONG, 1, stripOffset);    // StripOffsets
        putIfdEntry(header, 274, TYPE_SHORT, 1, mOrientation);  // Orientation
        putIfdEntry(header, 277, TYPE_SHORT, 1, 1);             // SamplesPerPixel
        putIfdEntry(header, 278, TYPE_LONG, 1, height);         // RowsPerStrip
        putIfdEntry(header, 279, TYPE_LONG, 1, rawPixels.length); // StripByteCounts
        putIfdEntry(header, 50706, TYPE_BYTE, 4, 0x00000401);   // DNGVersion = 1.4.0.0
        header.putInt(0); // next IFD = 0

        if (descBytes != null && descBytes.length > 4) {
            header.position(descOffset);
            header.put(descBytes);
        }

        ByteArrayOutputStream combined = new ByteArrayOutputStream(stripOffset + rawPixels.length);
        combined.write(header.array(), 0, stripOffset);
        combined.write(rawPixels);
        combined.writeTo(out);
        out.flush();
    }

    private static void putIfdEntry(ByteBuffer buf, int tag, int type, int count, int valueOrOffset) {
        buf.putShort((short) tag);
        buf.putShort((short) type);
        buf.putInt(count);
        buf.putInt(valueOrOffset);
    }

    private static int packBytes(byte[] b) {
        int v = 0;
        for (int i = 0; i < b.length && i < 4; i++) {
            v |= (b[i] & 0xff) << (i * 8);
        }
        return v;
    }

    @Override
    public void close() {
        mClosed = true;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }
}
