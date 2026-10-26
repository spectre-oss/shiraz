// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
import android.content.res.AssetManager;
import java.io.File;
import java.util.Objects;

/** Immutable host font request. Fontconfig resolves installed family/style fallbacks. */
public class Typeface {
    public static final int NORMAL = 0, BOLD = 1, ITALIC = 2, BOLD_ITALIC = 3;
    public static final Typeface DEFAULT = new Typeface("sans-serif", NORMAL);
    public static final Typeface DEFAULT_BOLD = new Typeface("sans-serif", BOLD);
    public static final Typeface SANS_SERIF = DEFAULT;
    public static final Typeface SERIF = new Typeface("serif", NORMAL);
    public static final Typeface MONOSPACE = new Typeface("monospace", NORMAL);
    private final String family;
    private final int style;
    private Typeface(String family, int style) { this.family = family; this.style = style; }
    public static Typeface create(String familyName, int style) {
        if (style < NORMAL || style > BOLD_ITALIC) style = NORMAL;
        if (familyName == null || familyName.isEmpty()) familyName = "sans-serif";
        if (familyName.length() > 256 || familyName.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid font family");
        if (familyName.equals("sans-serif")) return style == NORMAL ? DEFAULT : style == BOLD ? DEFAULT_BOLD : new Typeface(familyName, style);
        return new Typeface(familyName, style);
    }
    public static Typeface create(Typeface family, int style) { return create(family == null ? null : family.family, style); }
    public static Typeface defaultFromStyle(int style) { return create((String)null, style); }
    public int getStyle() { return style; }
    public boolean isBold() { return (style & BOLD) != 0; }
    public boolean isItalic() { return (style & ITALIC) != 0; }
    public String shirazFamily() { return family; }
    public static Typeface createFromFamiliesWithDefault(FontFamily[] families, int weight, int italic) {
        return createFromFamiliesWithDefault(families, null, weight, italic);
    }
    public static Typeface createFromFamiliesWithDefault(FontFamily[] families, String fallbackName, int weight, int italic) {
        if (families == null) throw new NullPointerException("families");
        int style = (weight >= 700 ? BOLD : 0) | (italic != 0 ? ITALIC : 0);
        for (FontFamily family : families) {
            int id = family == null ? 0 : family.frozenId();
            if (id > 0) return new Typeface("\u0001" + id, style);
        }
        return create(fallbackName, style);
    }
    public static Typeface createFromAsset(AssetManager assets, String path) {
        if (assets == null || path == null) throw new NullPointerException("font asset");
        return faceFrom(assets, path, true);
    }
    public static Typeface createFromFile(File file) {
        if (file == null) throw new NullPointerException("font file");
        return createFromFile(file.getPath());
    }
    public static Typeface createFromFile(String path) {
        if (path == null) throw new NullPointerException("font file");
        return faceFrom(null, path, false);
    }
    private static Typeface faceFrom(AssetManager assets, String path, boolean asset) {
        FontFamily family = new FontFamily();
        if (!family.addFontFromAssetManager(assets, path, 0, asset, 0, 400, 0, null) || !family.freeze()) throw new RuntimeException("Font not found");
        return createFromFamiliesWithDefault(new FontFamily[]{family}, 400, 0);
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof Typeface)) return false;
        Typeface face = (Typeface)other; return style == face.style && family.equals(face.family);
    }
    @Override public int hashCode() { return Objects.hash(family, style); }
}
