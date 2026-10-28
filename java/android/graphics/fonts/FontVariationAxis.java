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
    public static FontVariationAxis[] fromFontVariationSettings(String settings) {
        if(settings==null||settings.trim().isEmpty())return null;
        java.util.ArrayList<FontVariationAxis> result=new java.util.ArrayList<FontVariationAxis>();
        for(String item:settings.split(",",-1)) {
            item=item.trim();
            if(item.length()<7||(item.charAt(0)!='\''&&item.charAt(0)!='"')||item.charAt(5)!=item.charAt(0))throw new IllegalArgumentException("Invalid font variation settings");
            String tag=item.substring(1,5);
            for(int j=0;j<4;j++)if(tag.charAt(j)<32||tag.charAt(j)>126)throw new IllegalArgumentException("Invalid font variation tag");
            float value=Float.parseFloat(item.substring(6).trim());
            result.add(new FontVariationAxis(tag,value));
        }
        return result.toArray(new FontVariationAxis[result.size()]);
    }
}
