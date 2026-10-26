// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.fonts;
/** One variable-font axis. Buffer font loading does not apply axes yet. */
public final class FontVariationAxis {
    private final String tag;
    private final float styleValue;
    public FontVariationAxis(String tag, float styleValue) {
        if (tag == null || tag.length() != 4) throw new IllegalArgumentException("Font axis tag");
        this.tag = tag;
        this.styleValue = styleValue;
    }
    public String getTag() { return tag; }
    public float getStyleValue() { return styleValue; }
}
