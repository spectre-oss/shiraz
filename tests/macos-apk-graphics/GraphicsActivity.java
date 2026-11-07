// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.macosgraphics;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Text, vector paths and decoded PNG/WebP/JPEG images at fixed dp positions in
 * a 360x640 dp frame. Geometry and colours match scripts/macos_graphics_images.py
 * and the graphics profile of scripts/macos_runtime_smoke.py. Every stage prints
 * a marker; any failure (including UnsatisfiedLinkError for a missing native
 * library) propagates and fails the run. No lambdas or method references: the
 * darwin ART interpreter does not run invoke-custom.
 */
public final class GraphicsActivity extends Activity {
    static final int BACKGROUND = 0xff102030;
    static final int LATIN_COLOR = 0xffffffff;
    static final int PERSIAN_COLOR = 0xffffd54f;
    static final int HEBREW_COLOR = 0xff80deea;
    static final int CIRCLE_COLOR = 0xffe53935;
    static final int STROKE_COLOR = 0xff43a047;
    static final String LATIN = "Shiraz";
    /** Persian "Shiraz": joined Arabic-script letters, right to left. */
    static final String PERSIAN = "شیراز";
    /** Hebrew "shalom", right to left. */
    static final String HEBREW = "שלום";
    static final int[] QUADRANTS_PNG = {0xff3366cc, 0xffcc6633, 0xff66cc33, 0xffcccc00};
    static final int[] QUADRANTS_WEBP = {0xffe04010, 0xff20c010, 0xff2040f0, 0xffe0c0f0};
    static final int JPEG_RGB = 0x3090d0;
    static final int JPEG_TOLERANCE = 8;
    static final int ROUNDTRIP_JPEG_RGB = 0x8040c0;

    Bitmap png, webp, jpeg, roundtripPng, roundtripWebp;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        float density = getResources().getDisplayMetrics().density;
        byte[] pngBytes = asset("quadrants.png"), webpBytes = asset("quadrants.webp"), jpegBytes = asset("flat.jpg");
        System.out.println("MACOS_GRAPHICS_ASSETS_READ png=" + pngBytes.length + " webp=" + webpBytes.length
                + " jpeg=" + jpegBytes.length + " density=" + density);

        Paint latin = textPaint(56 * density, LATIN_COLOR), hebrew = textPaint(32 * density, HEBREW_COLOR);
        // Same size as the Persian TextView below. The smoke compares these
        // widths (in dp) with the bundled Noto fonts' advances, so a host font
        // or .notdef boxes fail the run.
        Paint persianPaint = textPaint(36 * density, PERSIAN_COLOR);
        float width = latin.measureText(LATIN), rtlWidth = hebrew.measureText(HEBREW);
        float persianWidth = persianPaint.measureText(PERSIAN);
        if (!(width > 0) || !(rtlWidth > 0) || !(persianWidth > 0))
            throw new IllegalStateException("text measured " + width + " / " + rtlWidth + " / " + persianWidth);
        System.out.println("MACOS_GRAPHICS_TEXT_MEASURED width=" + width + " rtl_width=" + rtlWidth
                + " persian_width=" + persianWidth);

        png = decodeAsset("quadrants.png");
        webp = decodeAsset("quadrants.webp");
        jpeg = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
        checkQuadrants("png", png, QUADRANTS_PNG);
        checkQuadrants("webp", webp, QUADRANTS_WEBP);
        if (jpeg == null || jpeg.getWidth() != 16 || jpeg.getHeight() != 16) throw new IllegalStateException("jpeg decode " + jpeg);
        int jpegDelta = delta(jpeg.getPixel(8, 8), JPEG_RGB);
        if ((jpeg.getPixel(8, 8) >>> 24) != 0xff || jpegDelta > JPEG_TOLERANCE)
            throw new IllegalStateException("jpeg centre " + hex(jpeg.getPixel(8, 8)) + " delta " + jpegDelta);
        System.out.println("MACOS_GRAPHICS_IMAGES_DECODED png=" + size(png) + " webp=" + size(webp) + " jpeg=" + size(jpeg)
                + " png_tl=" + hex(png.getPixel(0, 0)) + " webp_tl=" + hex(webp.getPixel(0, 0))
                + " jpeg_centre=" + hex(jpeg.getPixel(8, 8)) + " jpeg_delta=" + jpegDelta);

        roundtrip();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BACKGROUND);
        root.addView(new GraphicsView(this, latin, hebrew), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView persian = new TextView(this);
        persian.setText(PERSIAN);
        persian.setTextColor(PERSIAN_COLOR);
        persian.setTextSize(TypedValue.COMPLEX_UNIT_PX, 36 * density);
        persian.setIncludeFontPadding(false);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = Math.round(24 * density);
        params.topMargin = Math.round(104 * density);
        root.addView(persian, params);
        setContentView(root);
        System.out.println("MACOS_GRAPHICS_CONTENT_SET");
    }

    static Paint textPaint(float size, int color) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(size);
        paint.setColor(color);
        return paint;
    }

    byte[] asset(String name) {
        try (InputStream stream = getAssets().open(name)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("asset " + name, failure);
        }
    }

    Bitmap decodeAsset(String name) {
        try (InputStream stream = getAssets().open(name)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (bitmap == null) throw new IllegalStateException("BitmapFactory.decodeStream returned null for " + name);
            return bitmap;
        } catch (IOException failure) {
            throw new IllegalStateException("asset " + name, failure);
        }
    }

    static void checkQuadrants(String name, Bitmap bitmap, int[] expected) {
        if (bitmap.getWidth() != 8 || bitmap.getHeight() != 8) throw new IllegalStateException(name + " size " + size(bitmap));
        int[][] probes = {{0, 0}, {7, 0}, {0, 7}, {7, 7}, {3, 3}, {4, 3}, {3, 4}, {4, 4}};
        int[] quadrant = {0, 1, 2, 3, 0, 1, 2, 3};
        for (int i = 0; i < probes.length; i++) {
            int actual = bitmap.getPixel(probes[i][0], probes[i][1]);
            if (actual != expected[quadrant[i]])
                throw new IllegalStateException(name + " pixel " + probes[i][0] + "," + probes[i][1] + " " + hex(actual)
                        + " != " + hex(expected[quadrant[i]]));
        }
    }

    /** 4x4 source; pixel 5 is fully transparent, the rest opaque and distinct. */
    static int[] roundtripPixels() {
        int[] pixels = new int[16];
        for (int i = 0; i < 16; i++)
            pixels[i] = i == 5 ? 0 : 0xff000000 | ((i * 16 + 8) << 16) | ((255 - i * 16) << 8) | ((i * 37 + 11) & 0xff);
        return pixels;
    }

    void roundtrip() {
        int[] pixels = roundtripPixels();
        Bitmap source = Bitmap.createBitmap(pixels, 4, 4, Bitmap.Config.ARGB_8888);
        byte[] pngEncoded = compress(source, Bitmap.CompressFormat.PNG, 100);
        // WEBP_LOSSLESS is API 30; Shiraz implements it and WEBP at quality 100 is lossy there.
        byte[] webpEncoded = compress(source, Bitmap.CompressFormat.valueOf("WEBP_LOSSLESS"), 100);
        roundtripPng = decodeExact("png", pngEncoded, pixels);
        roundtripWebp = decodeExact("webp", webpEncoded, pixels);
        if (webpEncoded.length < 12 || webpEncoded[0] != 'R' || webpEncoded[8] != 'W')
            throw new IllegalStateException("webp compress did not produce RIFF/WEBP");
        int[] flat = new int[256];
        java.util.Arrays.fill(flat, 0xff000000 | ROUNDTRIP_JPEG_RGB);
        byte[] jpegEncoded = compress(Bitmap.createBitmap(flat, 16, 16, Bitmap.Config.ARGB_8888), Bitmap.CompressFormat.JPEG, 95);
        Bitmap decoded = BitmapFactory.decodeByteArray(jpegEncoded, 0, jpegEncoded.length);
        if (decoded == null || decoded.getWidth() != 16 || decoded.getHeight() != 16) throw new IllegalStateException("jpeg roundtrip decode " + decoded);
        int worst = 0;
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int value = decoded.getPixel(x, y);
            if ((value >>> 24) != 0xff) throw new IllegalStateException("jpeg roundtrip alpha " + hex(value));
            worst = Math.max(worst, delta(value, ROUNDTRIP_JPEG_RGB));
        }
        if (worst > JPEG_TOLERANCE) throw new IllegalStateException("jpeg roundtrip delta " + worst);
        System.out.println("MACOS_GRAPHICS_ROUNDTRIP_OK png=" + pngEncoded.length + " webp=" + webpEncoded.length
                + " jpeg=" + jpegEncoded.length + " jpeg_delta=" + worst);
    }

    static byte[] compress(Bitmap bitmap, Bitmap.CompressFormat format, int quality) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!bitmap.compress(format, quality, output)) throw new IllegalStateException("Bitmap.compress " + format + " returned false");
        if (output.size() == 0) throw new IllegalStateException("Bitmap.compress " + format + " wrote nothing");
        return output.toByteArray();
    }

    static Bitmap decodeExact(String name, byte[] encoded, int[] expected) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(encoded, 0, encoded.length);
        if (bitmap == null || bitmap.getWidth() != 4 || bitmap.getHeight() != 4) throw new IllegalStateException(name + " roundtrip decode " + bitmap);
        for (int i = 0; i < 16; i++) {
            int actual = bitmap.getPixel(i % 4, i / 4);
            if (actual != expected[i])
                throw new IllegalStateException(name + " roundtrip pixel " + i + " " + hex(actual) + " != " + hex(expected[i]));
        }
        return bitmap;
    }

    static int delta(int argb, int rgb) {
        int worst = 0;
        for (int shift = 0; shift <= 16; shift += 8)
            worst = Math.max(worst, Math.abs(((argb >> shift) & 0xff) - ((rgb >> shift) & 0xff)));
        return worst;
    }

    static String hex(int value) {
        return "0x" + Integer.toHexString(value);
    }

    static String size(Bitmap bitmap) {
        return bitmap.getWidth() + "x" + bitmap.getHeight();
    }

    /** Draws at dp positions scaled by the display density. */
    final class GraphicsView extends View {
        final Paint latin, hebrew;
        final Paint fill = new Paint(), stroke = new Paint(), image = new Paint();
        final float density;
        boolean reported;

        GraphicsView(Activity context, Paint latin, Paint hebrew) {
            super(context);
            this.latin = latin;
            this.hebrew = hebrew;
            density = context.getResources().getDisplayMetrics().density;
            fill.setAntiAlias(true);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(CIRCLE_COLOR);
            stroke.setAntiAlias(true);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(8 * density);
            stroke.setColor(STROKE_COLOR);
            image.setFilterBitmap(false);
        }

        float dp(float value) {
            return value * density;
        }

        RectF rect(float left, float top, float right, float bottom) {
            return new RectF(dp(left), dp(top), dp(right), dp(bottom));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawText(LATIN, dp(24), dp(76), latin);
            canvas.drawText(HEBREW, dp(24), dp(212), hebrew);
            canvas.drawCircle(dp(96), dp(304), dp(56), fill);
            canvas.drawRoundRect(rect(196, 248, 332, 360), dp(24), dp(24), stroke);
            canvas.drawBitmap(png, null, rect(24, 384, 104, 464), image);
            canvas.drawBitmap(webp, null, rect(140, 384, 220, 464), image);
            canvas.drawBitmap(jpeg, null, rect(256, 384, 336, 464), image);
            canvas.drawBitmap(roundtripWebp, null, rect(24, 488, 104, 568), image);
            canvas.drawBitmap(roundtripPng, null, rect(140, 488, 220, 568), image);
            if (!reported) {
                reported = true;
                System.out.println("MACOS_GRAPHICS_DRAWN width=" + getWidth() + " height=" + getHeight());
            }
        }
    }
}
