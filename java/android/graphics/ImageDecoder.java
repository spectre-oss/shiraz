// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.net.Uri;
import android.util.Size;
import android.util.TypedValue;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.Callable;

/** Modern Android image decoder supporting Bitmap and Drawable targets with header/post callbacks. */
public final class ImageDecoder implements AutoCloseable {
    public static final int ALLOCATOR_DEFAULT = 0;
    public static final int ALLOCATOR_SOFTWARE = 1;
    public static final int ALLOCATOR_SHARED_MEMORY = 2;
    public static final int ALLOCATOR_HARDWARE = 3;

    public static final int MEMORY_POLICY_LOW_RAM = 0;
    public static final int MEMORY_POLICY_DEFAULT = 1;

    public abstract static class Source {
        Source() {}

        abstract byte[] readBytes() throws IOException;

        Resources getResources() {
            return null;
        }

        int getSourceDensity() {
            return Bitmap.DENSITY_NONE;
        }
    }

    public static class ImageInfo {
        private final Size size;
        private final String mimeType;
        private final boolean animated;
        private final ColorSpace colorSpace;

        ImageInfo(Size size, String mimeType, boolean animated, ColorSpace colorSpace) {
            this.size = size;
            this.mimeType = mimeType;
            this.animated = animated;
            this.colorSpace = colorSpace;
        }

        public Size getSize() {
            return size;
        }

        public String getMimeType() {
            return mimeType;
        }

        public boolean isAnimated() {
            return animated;
        }

        public ColorSpace getColorSpace() {
            return colorSpace;
        }
    }

    public interface OnHeaderDecodedListener {
        void onHeaderDecoded(ImageDecoder decoder, ImageInfo info, Source source);
    }

    public interface OnPartialImageListener {
        boolean onPartialImage(DecodeException exception);
    }

    public static final class DecodeException extends IOException {
        public static final int SOURCE_EXCEPTION = 1;
        public static final int SOURCE_INCOMPLETE = 2;
        public static final int SOURCE_MALFORMED_DATA = 3;

        private final int error;
        private final Source source;

        DecodeException(int error, String msg, Throwable cause, Source source) {
            super(msg, cause);
            this.error = error;
            this.source = source;
        }

        public int getError() {
            return error;
        }

        public Source getSource() {
            return source;
        }
    }

    private final Source source;
    private final int intrinsicWidth;
    private final int intrinsicHeight;
    private final String mimeType;
    private final boolean animated;
    private int desiredWidth;
    private int desiredHeight;
    private int allocator = ALLOCATOR_DEFAULT;
    private boolean unpremultipliedRequired;
    private PostProcessor postProcessor;
    private OnPartialImageListener onPartialImageListener;
    private Rect cropRect;
    private boolean mutableRequired;
    private int memorySizePolicy = MEMORY_POLICY_DEFAULT;
    private boolean decodeAsAlphaMask;
    private ColorSpace targetColorSpace;
    private boolean closed;

    private ImageDecoder(
            Source source, int width, int height, String mimeType, boolean animated) {
        this.source = source;
        this.intrinsicWidth = width;
        this.intrinsicHeight = height;
        this.desiredWidth = width;
        this.desiredHeight = height;
        this.mimeType = mimeType;
        this.animated = animated;
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("ImageDecoder is closed");
        }
    }

    public static boolean isMimeTypeSupported(String mimeType) {
        if (mimeType == null) {
            throw new NullPointerException("mimeType");
        }
        String lower = mimeType.toLowerCase(Locale.US);
        return "image/png".equals(lower)
                || "image/jpeg".equals(lower)
                || "image/webp".equals(lower)
                || "image/gif".equals(lower)
                || "image/bmp".equals(lower)
                || "image/x-ms-bmp".equals(lower);
    }

    public static Source createSource(final Resources res, final int resId) {
        if (res == null) {
            throw new NullPointerException("res");
        }
        return new Source() {
            private int density = Bitmap.DENSITY_NONE;

            @Override
            byte[] readBytes() throws IOException {
                try {
                    TypedValue value = new TypedValue();
                    byte[] bytes = res.shirazReadResourceFile(resId, value);
                    density = value.density == TypedValue.DENSITY_NONE
                            ? Bitmap.DENSITY_NONE
                            : (value.density == 0 ? 160 : value.density);
                    return bytes;
                } catch (RuntimeException e) {
                    throw new IOException("Failed to read resource " + resId, e);
                }
            }

            @Override
            Resources getResources() {
                return res;
            }

            @Override
            int getSourceDensity() {
                return density;
            }
        };
    }

    public static Source createSource(final ContentResolver cr, final Uri uri) {
        if (cr == null || uri == null) {
            throw new NullPointerException("ContentResolver/Uri");
        }
        return new Source() {
            @Override
            byte[] readBytes() throws IOException {
                InputStream is = cr.openInputStream(uri);
                if (is == null) {
                    throw new IOException("Unable to open URI: " + uri);
                }
                try {
                    return drainStream(is);
                } finally {
                    is.close();
                }
            }
        };
    }

    public static Source createSource(final AssetManager assets, final String fileName) {
        if (assets == null || fileName == null) {
            throw new NullPointerException("assets/fileName");
        }
        return new Source() {
            @Override
            byte[] readBytes() throws IOException {
                InputStream is = assets.open(fileName);
                try {
                    return drainStream(is);
                } finally {
                    is.close();
                }
            }
        };
    }

    public static Source createSource(final byte[] data, final int offset, final int length)
            throws ArrayIndexOutOfBoundsException {
        if (data == null) {
            throw new NullPointerException("data");
        }
        if (offset < 0 || length < 0 || offset >= data.length || offset > data.length - length) {
            if (data.length == 0 && offset == 0 && length == 0) {
                // Allow empty slice; decode will fail with DecodeException.
            } else if (offset < 0 || length < 0 || offset > data.length - length) {
                throw new ArrayIndexOutOfBoundsException("ImageDecoder byte range");
            }
        }
        return new Source() {
            @Override
            byte[] readBytes() {
                byte[] slice = new byte[length];
                System.arraycopy(data, offset, slice, 0, length);
                return slice;
            }
        };
    }

    public static Source createSource(byte[] data) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        return createSource(data, 0, data.length);
    }

    public static Source createSource(final ByteBuffer buffer) {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        final ByteBuffer dup = buffer.slice();
        return new Source() {
            @Override
            byte[] readBytes() {
                ByteBuffer copy = dup.duplicate();
                byte[] bytes = new byte[copy.remaining()];
                copy.get(bytes);
                return bytes;
            }
        };
    }

    public static Source createSource(final File file) {
        if (file == null) {
            throw new NullPointerException("file");
        }
        return new Source() {
            @Override
            byte[] readBytes() throws IOException {
                InputStream is = new FileInputStream(file);
                try {
                    return drainStream(is);
                } finally {
                    is.close();
                }
            }
        };
    }

    public static Source createSource(final Callable<AssetFileDescriptor> callable) {
        if (callable == null) {
            throw new NullPointerException("callable");
        }
        return new Source() {
            @Override
            byte[] readBytes() throws IOException {
                AssetFileDescriptor afd;
                try {
                    afd = callable.call();
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IOException("Callable failed", e);
                }
                if (afd == null) {
                    throw new IOException("Callable returned null AssetFileDescriptor");
                }
                try {
                    InputStream is = afd.createInputStream();
                    try {
                        return drainStream(is);
                    } finally {
                        is.close();
                    }
                } finally {
                    afd.close();
                }
            }
        };
    }

    private static byte[] drainStream(InputStream is) throws IOException {
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
            if (out.size() > 32 * 1024 * 1024) {
                throw new IOException("Image stream exceeds 32MiB limit");
            }
        }
        return out.toByteArray();
    }

    public void setTargetSize(int width, int height) {
        checkNotClosed();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive: " + width + "x" + height);
        }
        this.desiredWidth = width;
        this.desiredHeight = height;
    }

    public void setTargetSampleSize(int sampleSize) {
        checkNotClosed();
        if (sampleSize < 1) {
            throw new IllegalArgumentException("sampleSize must be >= 1");
        }
        this.desiredWidth = Math.max(1, intrinsicWidth / sampleSize);
        this.desiredHeight = Math.max(1, intrinsicHeight / sampleSize);
    }

    public void setAllocator(int allocator) {
        checkNotClosed();
        if (allocator < ALLOCATOR_DEFAULT || allocator > ALLOCATOR_HARDWARE) {
            throw new IllegalArgumentException("Invalid allocator: " + allocator);
        }
        this.allocator = allocator;
    }

    public int getAllocator() {
        return allocator;
    }

    public void setUnpremultipliedRequired(boolean unpremultipliedRequired) {
        checkNotClosed();
        this.unpremultipliedRequired = unpremultipliedRequired;
    }

    public boolean isUnpremultipliedRequired() {
        return unpremultipliedRequired;
    }

    public void setPostProcessor(PostProcessor postProcessor) {
        checkNotClosed();
        this.postProcessor = postProcessor;
    }

    public PostProcessor getPostProcessor() {
        return postProcessor;
    }

    public void setOnPartialImageListener(OnPartialImageListener listener) {
        checkNotClosed();
        this.onPartialImageListener = listener;
    }

    public OnPartialImageListener getOnPartialImageListener() {
        return onPartialImageListener;
    }

    public void setCrop(Rect subset) {
        checkNotClosed();
        this.cropRect = subset == null ? null : new Rect(subset);
    }

    public Rect getCrop() {
        return cropRect == null ? null : new Rect(cropRect);
    }

    public void setMutableRequired(boolean mutable) {
        checkNotClosed();
        this.mutableRequired = mutable;
    }

    public boolean isMutableRequired() {
        return mutableRequired;
    }

    public void setMemorySizePolicy(int policy) {
        checkNotClosed();
        if (policy != MEMORY_POLICY_LOW_RAM && policy != MEMORY_POLICY_DEFAULT) {
            throw new IllegalArgumentException("Invalid memory policy: " + policy);
        }
        this.memorySizePolicy = policy;
    }

    public int getMemorySizePolicy() {
        return memorySizePolicy;
    }

    public void setDecodeAsAlphaMaskEnabled(boolean enabled) {
        checkNotClosed();
        this.decodeAsAlphaMask = enabled;
    }

    public boolean isDecodeAsAlphaMaskEnabled() {
        return decodeAsAlphaMask;
    }

    public void setTargetColorSpace(ColorSpace colorSpace) {
        checkNotClosed();
        this.targetColorSpace = colorSpace;
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    public static Drawable decodeDrawable(Source src) throws IOException {
        return decodeDrawableImpl(src, null);
    }

    public static Drawable decodeDrawable(Source src, OnHeaderDecodedListener listener)
            throws IOException {
        if (listener == null) {
            throw new NullPointerException("listener");
        }
        return decodeDrawableImpl(src, listener);
    }

    public static Bitmap decodeBitmap(Source src) throws IOException {
        return decodeBitmapImpl(src, null);
    }

    public static Bitmap decodeBitmap(Source src, OnHeaderDecodedListener listener)
            throws IOException {
        if (listener == null) {
            throw new NullPointerException("listener");
        }
        return decodeBitmapImpl(src, listener);
    }

    private static Drawable decodeDrawableImpl(Source src, OnHeaderDecodedListener listener)
            throws IOException {
        if (src == null) {
            throw new NullPointerException("src");
        }
        byte[] bytes = readSourceBytes(src);
        DecodedData decoded = inspectAndDecode(src, bytes);
        ImageDecoder decoder = new ImageDecoder(
                src, decoded.width, decoded.height, decoded.mimeType, decoded.animated);
        try {
            if (listener != null) {
                ImageInfo info = new ImageInfo(
                        new Size(decoded.width, decoded.height),
                        decoded.mimeType,
                        decoded.animated,
                        ColorSpace.get(ColorSpace.Named.SRGB));
                listener.onHeaderDecoded(decoder, info, src);
            }
            if (decoder.mutableRequired) {
                throw new IllegalStateException("Cannot decode a mutable Drawable");
            }
            decoder.validateConfiguration();
            if (decoded.animated && decoded.movie != null) {
                AnimatedImageDrawable anim = new AnimatedImageDrawable();
                anim.shirazConfigure(
                        decoded.movie, decoder.desiredWidth, decoder.desiredHeight,
                        decoder.cropRect, decoder.postProcessor);
                return anim;
            }
            Bitmap bmp = decoder.transformBitmap(decoded.bitmap, false);
            byte[] chunk = bmp.getNinePatchChunk();
            Resources res = src.getResources();
            if (chunk != null && NinePatch.isNinePatchChunk(chunk)) {
                Rect padding = new Rect();
                new NinePatch(bmp, chunk, null).shirazPadding(padding);
                return new NinePatchDrawable(res, bmp, chunk, padding, null);
            }
            return res != null ? new BitmapDrawable(res, bmp) : new BitmapDrawable(bmp);
        } finally {
            decoder.close();
        }
    }

    private static Bitmap decodeBitmapImpl(Source src, OnHeaderDecodedListener listener)
            throws IOException {
        if (src == null) {
            throw new NullPointerException("src");
        }
        byte[] bytes = readSourceBytes(src);
        DecodedData decoded = inspectAndDecode(src, bytes);
        ImageDecoder decoder = new ImageDecoder(
                src, decoded.width, decoded.height, decoded.mimeType, decoded.animated);
        try {
            if (listener != null) {
                ImageInfo info = new ImageInfo(
                        new Size(decoded.width, decoded.height),
                        decoded.mimeType,
                        decoded.animated,
                        ColorSpace.get(ColorSpace.Named.SRGB));
                listener.onHeaderDecoded(decoder, info, src);
            }
            decoder.validateConfiguration();
            return decoder.transformBitmap(decoded.bitmap, decoder.mutableRequired);
        } finally {
            decoder.close();
        }
    }

    private static byte[] readSourceBytes(Source src) throws IOException {
        try {
            byte[] data = src.readBytes();
            if (data == null || data.length == 0) {
                throw new DecodeException(
                        DecodeException.SOURCE_INCOMPLETE, "Empty image data", null, src);
            }
            return data;
        } catch (DecodeException e) {
            throw e;
        } catch (IOException e) {
            throw new DecodeException(DecodeException.SOURCE_EXCEPTION, e.getMessage(), e, src);
        }
    }

    private static final class DecodedData {
        final Bitmap bitmap;
        final Movie movie;
        final int width;
        final int height;
        final String mimeType;
        final boolean animated;

        DecodedData(
                Bitmap bitmap, Movie movie, int width, int height,
                String mimeType, boolean animated) {
            this.bitmap = bitmap;
            this.movie = movie;
            this.width = width;
            this.height = height;
            this.mimeType = mimeType;
            this.animated = animated;
        }
    }

    private static DecodedData inspectAndDecode(Source src, byte[] bytes) throws DecodeException {
        if (Movie.isGif(bytes, 0, bytes.length)) {
            Movie movie = Movie.decodeGif(bytes, 0, bytes.length);
            if (movie == null || movie.shirazFrames().length == 0) {
                throw new DecodeException(
                        DecodeException.SOURCE_MALFORMED_DATA, "Malformed GIF stream", null, src);
            }
            Movie.Frame[] frames = movie.shirazFrames();
            return new DecodedData(
                    frames[0].bitmap, movie, movie.width(), movie.height(),
                    "image/gif", frames.length > 1);
        }
        if (Movie.isBmp(bytes, 0, bytes.length)) {
            Bitmap bmp = Movie.decodeBmp(bytes, 0, bytes.length);
            if (bmp == null) {
                throw new DecodeException(
                        DecodeException.SOURCE_MALFORMED_DATA, "Malformed BMP stream", null, src);
            }
            return new DecodedData(bmp, null, bmp.getWidth(), bmp.getHeight(), "image/bmp", false);
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;
        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        if (bmp == null || opts.outWidth <= 0 || opts.outHeight <= 0) {
            throw new DecodeException(
                    DecodeException.SOURCE_MALFORMED_DATA, "Unsupported or malformed image", null, src);
        }
        int srcDensity = src.getSourceDensity();
        if (srcDensity != Bitmap.DENSITY_NONE) {
            bmp.setDensity(srcDensity);
        }
        String mime = opts.outMimeType != null ? opts.outMimeType : "image/png";
        return new DecodedData(bmp, null, bmp.getWidth(), bmp.getHeight(), mime, false);
    }

    private void validateConfiguration() {
        if (unpremultipliedRequired && postProcessor != null) {
            throw new IllegalStateException("Cannot have unpremultiplied pixels and PostProcessor");
        }
        if (unpremultipliedRequired) {
            throw new UnsupportedOperationException("Unpremultiplied Bitmap storage is not supported");
        }
        if (allocator == ALLOCATOR_HARDWARE && mutableRequired) {
            throw new IllegalStateException("Cannot make mutable HARDWARE Bitmap");
        }
        if (cropRect != null) {
            if (cropRect.width() <= 0 || cropRect.height() <= 0
                    || cropRect.left < 0 || cropRect.top < 0
                    || cropRect.right > desiredWidth || cropRect.bottom > desiredHeight) {
                throw new IllegalStateException("Crop rectangle out of target bounds: " + cropRect);
            }
        }
    }

    private Bitmap transformBitmap(Bitmap raw, boolean wantMutable) {
        Bitmap current = raw;
        if (desiredWidth != current.getWidth() || desiredHeight != current.getHeight()) {
            current = Bitmap.createScaledBitmap(current, desiredWidth, desiredHeight, true);
        }
        if (cropRect != null) {
            current = Bitmap.createBitmap(
                    current, cropRect.left, cropRect.top, cropRect.width(), cropRect.height());
        }
        Bitmap.Config targetCfg = decodeAsAlphaMask
                ? Bitmap.Config.ALPHA_8
                : (memorySizePolicy == MEMORY_POLICY_LOW_RAM && !current.hasAlpha()
                        ? Bitmap.Config.RGB_565
                        : Bitmap.Config.ARGB_8888);
        if (postProcessor != null) {
            Bitmap mutableWork = current.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(mutableWork);
            postProcessor.onPostProcess(canvas);
            current = mutableWork;
        }
        if (current.getConfig() != targetCfg || current.isMutable() != wantMutable) {
            current = current.copy(targetCfg, wantMutable);
        }
        Resources res = source.getResources();
        if (res != null && source.getSourceDensity() != Bitmap.DENSITY_NONE
                && desiredWidth == intrinsicWidth && desiredHeight == intrinsicHeight
                && cropRect == null) {
            int srcDpi = source.getSourceDensity();
            int dstDpi = res.getDisplayMetrics().densityDpi;
            if (srcDpi > 0 && dstDpi > 0 && srcDpi != dstDpi) {
                int sw = Math.max(1, (current.getWidth() * dstDpi + srcDpi / 2) / srcDpi);
                int sh = Math.max(1, (current.getHeight() * dstDpi + srcDpi / 2) / srcDpi);
                Bitmap scaled = Bitmap.createScaledBitmap(current, sw, sh, true);
                scaled.setDensity(dstDpi);
                current = scaled.copy(targetCfg, wantMutable);
                current.setDensity(dstDpi);
            }
        }
        return current;
    }
}
