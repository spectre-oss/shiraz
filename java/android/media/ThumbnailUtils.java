// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.CancellationSignal;
import android.util.Size;
import java.io.File;
import java.io.IOException;

public class ThumbnailUtils {
    public static final int OPTIONS_RECYCLE_INPUT = 2;

    private static final int MINI_KIND = 1;
    private static final int MICRO_KIND = 3;

    public ThumbnailUtils() {
    }

    public static Bitmap createAudioThumbnail(String filePath, int kind) {
        if (filePath == null) {
            return null;
        }
        try {
            return createAudioThumbnail(new File(filePath), sizeForKind(kind), null);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static Bitmap createAudioThumbnail(File file, Size size, CancellationSignal signal)
            throws IOException {
        if (file == null || size == null) {
            throw new NullPointerException("file and size must not be null");
        }
        if (signal != null) {
            signal.throwIfCanceled();
        }
        if (!file.isFile()) {
            throw new IOException("Audio file does not exist: " + file);
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getPath());
            if (signal != null) {
                signal.throwIfCanceled();
            }
            byte[] raw = retriever.getEmbeddedPicture();
            if (raw == null || raw.length == 0) {
                throw new IOException("No embedded artwork found in " + file);
            }
            Bitmap decoded = BitmapFactory.decodeByteArray(raw, 0, raw.length);
            if (decoded == null) {
                throw new IOException("Failed to decode embedded artwork in " + file);
            }
            return extractThumbnail(decoded, size.getWidth(), size.getHeight(), OPTIONS_RECYCLE_INPUT);
        } catch (RuntimeException e) {
            if (signal != null) {
                signal.throwIfCanceled();
            }
            throw new IOException("Failed to extract audio thumbnail: " + file, e);
        } finally {
            retriever.release();
        }
    }

    public static Bitmap createImageThumbnail(String filePath, int kind) {
        if (filePath == null) {
            return null;
        }
        try {
            return createImageThumbnail(new File(filePath), sizeForKind(kind), null);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static Bitmap createImageThumbnail(File file, Size size, CancellationSignal signal)
            throws IOException {
        if (file == null || size == null) {
            throw new NullPointerException("file and size must not be null");
        }
        if (signal != null) {
            signal.throwIfCanceled();
        }
        if (!file.isFile()) {
            throw new IOException("Image file does not exist: " + file);
        }
        Bitmap bitmap = BitmapFactory.decodeFile(file.getPath());
        if (bitmap == null) {
            throw new IOException("Failed to decode image: " + file);
        }
        if (signal != null) {
            signal.throwIfCanceled();
        }
        try {
            ExifInterface exif = new ExifInterface(file);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int degrees = exifOrientationDegrees(orientation);
            if (degrees != 0) {
                Matrix m = new Matrix();
                m.postRotate(degrees);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
                if (rotated != bitmap) {
                    bitmap.recycle();
                    bitmap = rotated;
                }
            }
        } catch (IOException ignored) {
        }
        return extractThumbnail(bitmap, size.getWidth(), size.getHeight(), OPTIONS_RECYCLE_INPUT);
    }

    public static Bitmap createVideoThumbnail(String filePath, int kind) {
        if (filePath == null) {
            return null;
        }
        try {
            return createVideoThumbnail(new File(filePath), sizeForKind(kind), null);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static Bitmap createVideoThumbnail(File file, Size size, CancellationSignal signal)
            throws IOException {
        if (file == null || size == null) {
            throw new NullPointerException("file and size must not be null");
        }
        if (signal != null) {
            signal.throwIfCanceled();
        }
        if (!file.isFile()) {
            throw new IOException("Video file does not exist: " + file);
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getPath());
            if (signal != null) {
                signal.throwIfCanceled();
            }
            Bitmap frame = retriever.getFrameAtTime(-1);
            if (frame == null) {
                byte[] art = retriever.getEmbeddedPicture();
                if (art != null && art.length > 0) {
                    frame = BitmapFactory.decodeByteArray(art, 0, art.length);
                }
            }
            if (frame == null) {
                throw new IOException("Failed to retrieve video frame from " + file);
            }
            return extractThumbnail(frame, size.getWidth(), size.getHeight(), OPTIONS_RECYCLE_INPUT);
        } catch (RuntimeException e) {
            if (signal != null) {
                signal.throwIfCanceled();
            }
            throw new IOException("Failed to create video thumbnail: " + file, e);
        } finally {
            retriever.release();
        }
    }

    public static Bitmap extractThumbnail(Bitmap source, int width, int height) {
        return extractThumbnail(source, width, height, 0);
    }

    public static Bitmap extractThumbnail(Bitmap source, int width, int height, int options) {
        if (source == null) {
            return null;
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be > 0");
        }
        int srcW = source.getWidth();
        int srcH = source.getHeight();
        if (srcW == width && srcH == height) {
            return source;
        }
        float scale = Math.max((float) width / (float) srcW, (float) height / (float) srcH);
        int cropW = Math.max(1, Math.min(srcW, Math.round(width / scale)));
        int cropH = Math.max(1, Math.min(srcH, Math.round(height / scale)));
        int cropX = Math.max(0, (srcW - cropW) / 2);
        int cropY = Math.max(0, (srcH - cropH) / 2);

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        Rect srcRect = new Rect(cropX, cropY, cropX + cropW, cropY + cropH);
        Rect dstRect = new Rect(0, 0, width, height);
        canvas.drawBitmap(source, srcRect, dstRect, paint);

        if ((options & OPTIONS_RECYCLE_INPUT) != 0 && output != source) {
            source.recycle();
        }
        return output;
    }

    private static Size sizeForKind(int kind) {
        if (kind == MICRO_KIND) {
            return new Size(96, 96);
        }
        if (kind == MINI_KIND) {
            return new Size(512, 384);
        }
        return new Size(320, 240);
    }

    private static int exifOrientationDegrees(int orientation) {
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }
}
