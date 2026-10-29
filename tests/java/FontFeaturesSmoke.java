// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import java.util.Arrays;

public final class FontFeaturesSmoke {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static int[] render(Paint paint) {
        Bitmap bitmap = Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888);
        new Canvas(bitmap).drawText("ffi", 10, 75, paint);
        int[] pixels = new int[300 * 100];
        bitmap.getPixels(pixels, 0, 300, 0, 0, 300, 100);
        return pixels;
    }
    public static void main(String[] args) {
        Paint enabled = new Paint(Paint.ANTI_ALIAS_FLAG);
        enabled.setTypeface(android.graphics.Typeface.createFromFile(".local/share/shiraz/fonts/NotoSerif-Regular.ttf"));
        enabled.setTextSize(64);
        enabled.setFontFeatureSettings("'liga' 1, 'clig' 1");
        Paint disabled = new Paint(enabled);
        disabled.setFontFeatureSettings("'liga' 0, 'clig' 0");
        require("'liga' 1, 'clig' 1".equals(enabled.getFontFeatureSettings()), "copy must keep independent state");
        float joined = enabled.measureText("ffi"), separate = disabled.measureText("ffi");
        require(Math.abs(joined - separate) > .1f, "ligature must affect measured advance");
        require(enabled.shirazTextClusters("ffi").length < disabled.shirazTextClusters("ffi").length, "ligature cluster boundaries");
        require(!Arrays.equals(render(enabled), render(disabled)), "feature must affect Canvas pixels");
        float[] width = new float[1];
        require(disabled.breakText("ffi", true, separate + .01f, width) == 3 && Math.abs(width[0] - separate) < .01f, "breakText must use features");
        Rect a = new Rect(), b = new Rect();
        enabled.getTextBounds("ffi", 0, 3, a);
        disabled.getTextBounds("ffi", 0, 3, b);
        require(a.right != b.right || a.left != b.left, "bounds must use features");
        Paint copy = new Paint(); copy.set(disabled);
        require(Arrays.equals(render(copy), render(disabled)), "Paint.set must copy features");
        copy.reset(); require(copy.getFontFeatureSettings() == null, "reset features");
        disabled.setFontFeatureSettings(""); require(disabled.getFontFeatureSettings() == null, "empty resets features");
        disabled.setFontFeatureSettings("'liga' 1");
        require(Math.abs(disabled.measureText("ffi") - joined) < .01f, "feature change must invalidate shaping state");
        try { disabled.setFontFeatureSettings(new String(new char[4097])); throw new AssertionError("feature bound"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("FONT_FEATURES_OK");
    }
}
