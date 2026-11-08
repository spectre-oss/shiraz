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
    private final int weight;
    private final String variations;
    private Typeface(String family, int style) { this(family,style,(style&BOLD)!=0?700:400,null); }
    private Typeface(String family,int style,int weight,String variations){this.family=family;this.style=style;this.weight=weight;this.variations=variations;}
    public static Typeface create(String familyName, int style) {
        if (style < NORMAL || style > BOLD_ITALIC) style = NORMAL;
        if (familyName == null || familyName.isEmpty()) familyName = "sans-serif";
        if (familyName.length() > 256 || familyName.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid font family");
        if (familyName.equals("sans-serif")) return style == NORMAL ? DEFAULT : style == BOLD ? DEFAULT_BOLD : new Typeface(familyName, style);
        return new Typeface(familyName, style);
    }
    public static Typeface create(Typeface family, int style) { if(family==null)return create((String)null,style);if(style<0||style>3)style=0;return new Typeface(family.family,style,(style&BOLD)!=0?700:400,family.variations); }
    public static Typeface create(Typeface family,int weight,boolean italic){if(weight<1||weight>1000)throw new IllegalArgumentException("Font weight");Typeface base=family==null?DEFAULT:family;return new Typeface(base.family,(weight>=700?BOLD:0)|(italic?ITALIC:0),weight,base.variations);}
    public static Typeface defaultFromStyle(int style) { return create((String)null, style); }
    public int getStyle() { return style; }
    public int getWeight(){return weight;}
    String shirazVariationSettings(){return variations;}
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
    public static final class CustomFallbackBuilder {
        private final java.util.ArrayList<android.graphics.fonts.FontFamily> families=new java.util.ArrayList<android.graphics.fonts.FontFamily>();
        private android.graphics.fonts.FontStyle style=new android.graphics.fonts.FontStyle();
        private String fallback="sans-serif";
        public CustomFallbackBuilder(android.graphics.fonts.FontFamily family){addCustomFallback(family);}
        public static int getMaxCustomFallbackCount(){return 64;}
        public CustomFallbackBuilder addCustomFallback(android.graphics.fonts.FontFamily family){if(family==null)throw new NullPointerException("family");if(families.size()>=getMaxCustomFallbackCount())throw new IllegalArgumentException("Fallback family limit");families.add(family);return this;}
        public CustomFallbackBuilder setStyle(android.graphics.fonts.FontStyle value){style=Objects.requireNonNull(value);return this;}
        public CustomFallbackBuilder setSystemFallback(String name){if(name==null)throw new NullPointerException("fallback");create(name,NORMAL);fallback=name;return this;}
        public Typeface build(){
            // The current text engine accepts one custom face plus its system fallback.
            if(families.size()!=1||!fallback.equals("sans-serif"))throw new UnsupportedOperationException("Multiple or named custom fallback families are not implemented");
            android.graphics.fonts.FontFamily family=families.get(0);android.graphics.fonts.Font selected=null;int score=Integer.MAX_VALUE;
            for(int i=0;i<family.getSize();i++){android.graphics.fonts.Font font=family.getFont(i);int candidate=style.getMatchScore(font.getStyle());if(candidate<score){selected=font;score=candidate;}}
            int bits=(style.getWeight()>=700?BOLD:0)|(style.getSlant()==1?ITALIC:0);
            return selected==null?create(fallback,bits):create(selected.shirazTypeface(),bits);
        }
    }
    public static final class Builder {
        private final AssetManager assets;private final String path;private final java.io.FileDescriptor descriptor;
        private int weight=400,ttcIndex;private boolean italic;private String variations,fallback;
        public Builder(AssetManager assets,String path){if(assets==null||path==null)throw new NullPointerException("font asset");this.assets=assets;this.path=path;descriptor=null;}
        public Builder(java.io.FileDescriptor descriptor){if(descriptor==null)throw new NullPointerException("font descriptor");this.descriptor=descriptor;assets=null;path=null;}
        public Builder(String path){if(path==null)throw new NullPointerException("font path");this.path=path;assets=null;descriptor=null;}
        public Builder(File file){this(java.util.Objects.requireNonNull(file,"font file").getPath());}
        public Builder setWeight(int weight){if(weight<1||weight>1000)throw new IllegalArgumentException("Font weight");this.weight=weight;return this;}
        public Builder setItalic(boolean italic){this.italic=italic;return this;}
        public Builder setTtcIndex(int index){if(index<0)throw new IllegalArgumentException("TTC index");ttcIndex=index;return this;}
        public Builder setFontVariationSettings(String settings){android.graphics.fonts.FontVariationAxis.fromFontVariationSettings(settings);variations=settings;return this;}
        public Builder setFontVariationSettings(android.graphics.fonts.FontVariationAxis[] axes){if(axes==null||axes.length==0){variations=null;return this;}StringBuilder text=new StringBuilder();for(android.graphics.fonts.FontVariationAxis axis:axes){if(axis==null)throw new NullPointerException("font axis");if(text.length()>0)text.append(',');text.append('\'').append(axis.getTag()).append("' ").append(axis.getStyleValue());}return setFontVariationSettings(text.toString());}
        public Builder setFallback(String name){fallback=name;return this;}
        private Typeface failed(){return fallback==null?null:Typeface.create(fallback,(weight>=700?BOLD:0)|(italic?ITALIC:0));}
        public Typeface build(){
            if(ttcIndex!=0)return failed();
            FontFamily family=new FontFamily();boolean added;
            try{
                if(descriptor!=null){long size=shiraz.runtime.NativeFd.size(descriptor);if(size<1||size>16*1024*1024)return failed();byte[] bytes=shiraz.runtime.NativeFd.readRegion(descriptor,0,size);added=family.addFontFromBuffer(java.nio.ByteBuffer.wrap(bytes),0,null,weight,italic?1:0);}
                else added=family.addFontFromAssetManager(assets,path,0,assets!=null,0,weight,italic?1:0,null);
            }catch(java.io.IOException|IllegalArgumentException failure){return failed();}
            if(!added||!family.freeze())return failed();
            return new Typeface("\u0001"+family.frozenId(),(weight>=700?BOLD:0)|(italic?ITALIC:0),weight,variations);
        }
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof Typeface)) return false;
        Typeface face = (Typeface)other; return style == face.style && weight==face.weight&&Objects.equals(variations,face.variations)&&family.equals(face.family);
    }
    @Override public int hashCode() { return Objects.hash(family, style,weight,variations); }
}
