// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1, FILTER_BITMAP_FLAG = 2, DITHER_FLAG = 4, UNDERLINE_TEXT_FLAG = 8, FAKE_BOLD_TEXT_FLAG = 32, LINEAR_TEXT_FLAG = 64, SUBPIXEL_TEXT_FLAG = 128;
    public static final int HINTING_OFF=0,HINTING_ON=1;
    private android.os.LocaleList textLocales=android.os.LocaleList.getDefault();
    public java.util.Locale getTextLocale(){return textLocales.get(0);}
    public android.os.LocaleList getTextLocales(){return textLocales;}
    public void setTextLocale(java.util.Locale locale){setTextLocales(new android.os.LocaleList(locale));}
    public void setTextLocales(android.os.LocaleList locales){if(locales==null||locales.isEmpty())throw new IllegalArgumentException("Text locales");textLocales=locales;}
    private int hinting=HINTING_ON;
    public int getHinting(){return hinting;}
    public void setHinting(int value){if(value!=HINTING_OFF&&value!=HINTING_ON)throw new IllegalArgumentException("Paint hinting");hinting=value;}
    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public enum Align { LEFT, CENTER, RIGHT }
    public enum Cap { BUTT, ROUND, SQUARE }
    public enum Join { MITER, ROUND, BEVEL }
    private Style style = Style.FILL;
    private Cap cap = Cap.BUTT;
    private Join join = Join.MITER;
    private float strokeWidth, strokeMiter = 4;
    private Typeface typeface;
    private String variationSettings;private int[] variationTags;private float[] variationValues;
    public String getFontFeatureSettings(){return null;}
    public void setFontFeatureSettings(String settings){
        if(settings!=null&&!settings.isEmpty())throw new UnsupportedOperationException("Explicit OpenType font features are not implemented");
    }
    private Shader shader;
    public Shader getShader(){return shader;}
    public Shader setShader(Shader value){shader=value;return value;}
    private ColorFilter colorFilter;
    private Xfermode xfermode;
    private float shadowRadius,shadowDx,shadowDy;private int shadowColor;
    private PathEffect pathEffect;
    private Align align = Align.LEFT;
    private boolean underline,strikeThru;
    public void setStrikeThruText(boolean value){strikeThru=value;}
    public boolean isStrikeThruText(){return strikeThru;}
    public void setUnderlineText(boolean value){underline=value;}
    public boolean isUnderlineText(){return underline;}
    private boolean linearText;
    public boolean isLinearText(){return linearText;}
    public void setLinearText(boolean value){linearText=value;}
    public int shirazTextHinting(){return linearText?HINTING_OFF:hinting;}
    private boolean subpixelText;
    public boolean isSubpixelText(){return subpixelText;}
    public void setSubpixelText(boolean value){subpixelText=value;}
    private boolean antialias, filterBitmap, fakeBold, dither;
    private float textSize = 16, textSkewX,letterSpacing,textScaleX=1;
    public float getTextScaleX(){return textScaleX;}
    public void setTextScaleX(float value){if(!Float.isFinite(value)||value<0||value>16)throw new IllegalArgumentException("Text scale must be in [0,16]");textScaleX=value;}
    private int color = 0xff000000;
    public Paint() { }
    public Paint(Paint source) { set(source); }
    public void set(Paint source) {
        textLocales=source.textLocales;
        if (source == null) throw new NullPointerException("paint");
        shader=source.shader;underline=source.underline;strikeThru=source.strikeThru;shadowRadius=source.shadowRadius;shadowDx=source.shadowDx;shadowDy=source.shadowDy;shadowColor=source.shadowColor;variationSettings=source.variationSettings;variationTags=source.variationTags==null?null:source.variationTags.clone();variationValues=source.variationValues==null?null:source.variationValues.clone();xfermode=source.xfermode; pathEffect=source.pathEffect; typeface = source.typeface; colorFilter=source.colorFilter;
        bidiLevels=source.bidiLevels==null?null:source.bidiLevels.clone();
        linearText=source.linearText;subpixelText=source.subpixelText;hinting=source.hinting;textScaleX=source.textScaleX;
        style = source.style; cap = source.cap; join = source.join;
        strokeWidth = source.strokeWidth; strokeMiter = source.strokeMiter;
        letterSpacing=source.letterSpacing;textSkewX=source.textSkewX; dither=source.dither; fakeBold=source.fakeBold; align = source.align; antialias = source.antialias; filterBitmap = source.filterBitmap; textSize = source.textSize; color = source.color;
    }
    public void reset() { set(new Paint()); }
    public Paint(int flags) { setFlags(flags); }
    public int getFlags() { return (linearText ? LINEAR_TEXT_FLAG : 0) | (subpixelText ? SUBPIXEL_TEXT_FLAG : 0) | (underline ? UNDERLINE_TEXT_FLAG : 0) | (antialias ? ANTI_ALIAS_FLAG : 0) | (filterBitmap ? FILTER_BITMAP_FLAG : 0) | (fakeBold ? FAKE_BOLD_TEXT_FLAG : 0) | (dither ? DITHER_FLAG : 0); }
    public void setFlags(int flags) {
        if ((flags & ~(ANTI_ALIAS_FLAG | FILTER_BITMAP_FLAG | FAKE_BOLD_TEXT_FLAG | DITHER_FLAG | UNDERLINE_TEXT_FLAG | SUBPIXEL_TEXT_FLAG | LINEAR_TEXT_FLAG)) != 0) throw new UnsupportedOperationException("Paint flags are not implemented");
        linearText=(flags&LINEAR_TEXT_FLAG)!=0;subpixelText=(flags&SUBPIXEL_TEXT_FLAG)!=0;underline=(flags&UNDERLINE_TEXT_FLAG)!=0;dither = (flags & DITHER_FLAG) != 0; fakeBold = (flags & FAKE_BOLD_TEXT_FLAG) != 0; antialias = (flags & ANTI_ALIAS_FLAG) != 0; filterBitmap = (flags & FILTER_BITMAP_FLAG) != 0;
    }
    // Dithering is a quantized-output hint. The current ARGB_8888-only target retains all 8-bit channels.
    public void setDither(boolean value) { dither=value; }
    public boolean isDither() { return dither; }
    public void setFakeBoldText(boolean value) { fakeBold=value; }
    public boolean isFakeBoldText() { return fakeBold; }
    public void setFilterBitmap(boolean value) { filterBitmap = value; }
    public boolean isFilterBitmap() { return filterBitmap; }
    public void setAntiAlias(boolean value) { antialias = value; }
    public boolean isAntiAlias() { return antialias; }
    public void setTextSize(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 512) throw new IllegalArgumentException("Text size must be in [0,512]");
        textSize = value;
    }
    public void setTextSkewX(float value){if(!Float.isFinite(value)||Math.abs(value)>16)throw new IllegalArgumentException("Text skew must be in [-16,16]");textSkewX=value;}
    public float getTextSkewX(){return textSkewX;}
    public float getLetterSpacing(){return letterSpacing;}
    public void setLetterSpacing(float value){if(!Float.isFinite(value)||Math.abs(value)>16)throw new IllegalArgumentException("Letter spacing must be finite and within 16 em");letterSpacing=value;}
    public float getTextSize() { return textSize; }
    public void setTextAlign(Align value) { if (value == null) throw new NullPointerException("align"); align = value; }
    public Align getTextAlign() { return align; }
    public void getTextBounds(String text,int start,int end,Rect bounds){
        if(text==null||bounds==null)throw new NullPointerException();
        if(start<0||end<start||end>text.length())throw new IndexOutOfBoundsException();
        int[] box=shiraz.runtime.NativeText.boundsSubpixelResolved(text.substring(start,end),textSize,shirazTypeface().shirazFamily(),shirazTypeface().getStyle(),variationTags,variationValues,letterSpacing,fakeBold,textSkewX,shirazTextHinting(),textScaleX,bidiLevels==null?null:java.util.Arrays.copyOfRange(bidiLevels,start,end),subpixelText);
        bounds.set(box[0],box[1],box[2],box[3]);
    }
    public void getTextBounds(char[] text,int index,int count,Rect bounds){
        if(text==null||bounds==null)throw new NullPointerException();
        if(index<0||count<0||index>text.length-count)throw new IndexOutOfBoundsException();
        getTextBounds(new String(text,index,count),0,count,bounds);
    }
    private byte[] bidiLevels;
    public void shirazSetBidiLevels(byte[] levels){bidiLevels=levels==null?null:levels.clone();}
    public byte[] shirazBidiLevels(){return bidiLevels==null?null:bidiLevels.clone();}
    public float[] shirazTextClusters(String text){float[] result=shiraz.runtime.NativeText.clustersResolved(text,textSize,shirazTypeface().shirazFamily(),shirazTypeface().getStyle(),variationTags,variationValues,letterSpacing,bidiLevels);for(int i=0;i<result.length;i+=5){result[i+2]*=textScaleX;result[i+3]*=textScaleX;}return result;}
    public float measureText(CharSequence text,int start,int end){if(text==null)throw new IllegalArgumentException("text");if(start<0||end<start||end>text.length())throw new IndexOutOfBoundsException();return measureText(text.toString(),start,end);}
    public float measureText(String text) { if (text == null) throw new IllegalArgumentException("text"); return shiraz.runtime.NativeText.measureResolved(text, textSize, shirazTypeface().shirazFamily(), shirazTypeface().getStyle(),variationTags,variationValues,letterSpacing,bidiLevels)*textScaleX; }
    public float measureText(String text, int start, int end) { if(bidiLevels==null)return measureText(text.substring(start,end));Paint copy=new Paint(this);copy.bidiLevels=java.util.Arrays.copyOfRange(bidiLevels,start,end);return copy.measureText(text.substring(start,end)); }
    public int breakText(String text,boolean forwards,float maxWidth,float[] measuredWidth) {
        if(text==null)throw new IllegalArgumentException("text");
        if(measuredWidth!=null&&measuredWidth.length==0)throw new ArrayIndexOutOfBoundsException("measuredWidth");
        if(Float.isNaN(maxWidth))throw new IllegalArgumentException("maxWidth");
        if(text.length()==0)return 0;
        float limit=maxWidth<0?maxWidth:textScaleX==0?Float.POSITIVE_INFINITY:maxWidth/textScaleX;
        int count=shiraz.runtime.NativeText.breakResolved(text,textSize,shirazTypeface().shirazFamily(),shirazTypeface().getStyle(),variationTags,variationValues,letterSpacing,forwards,limit,measuredWidth,bidiLevels);
        if(measuredWidth!=null)measuredWidth[0]*=textScaleX;
        return count;
    }
    public int breakText(CharSequence text,int start,int end,boolean forwards,float maxWidth,float[] measuredWidth) {
        if(text==null)throw new IllegalArgumentException("text");
        if(start<0||end<start||end>text.length())throw new IndexOutOfBoundsException("text range");
        return breakText(text.subSequence(start,end).toString(),forwards,maxWidth,measuredWidth);
    }
    public int breakText(char[] text,int index,int count,float maxWidth,float[] measuredWidth) {
        if(text==null)throw new IllegalArgumentException("text");
        long length=Math.abs((long)count);if(index<0||index>text.length||length>text.length-index)throw new ArrayIndexOutOfBoundsException("text range");
        return breakText(new String(text,index,(int)length),count>=0,maxWidth,measuredWidth);
    }
    public boolean hasGlyph(String string) {
        return string != null && !string.isEmpty() && measureText(string) > 0f;
    }
    public static class FontMetrics { public float top, ascent, descent, bottom, leading; }
    public static class FontMetricsInt { public int top, ascent, descent, bottom, leading; }
    public FontMetrics getFontMetrics() { FontMetrics value = new FontMetrics(); getFontMetrics(value); return value; }
    public float getFontMetrics(FontMetrics value) {
        float[] metrics = shiraz.runtime.NativeText.metricsVariable(textSize, shirazTypeface().shirazFamily(), shirazTypeface().getStyle(),variationTags,variationValues);
        if (value != null) { value.top = metrics[0]; value.ascent = metrics[1]; value.descent = metrics[2]; value.bottom = metrics[3]; value.leading = metrics[4]; }
        return metrics[2] - metrics[1] + metrics[4];
    }
    public FontMetricsInt getFontMetricsInt() { FontMetricsInt value = new FontMetricsInt(); getFontMetricsInt(value); return value; }
    public int getFontMetricsInt(FontMetricsInt value) {
        FontMetrics metrics = getFontMetrics();
        int ascent = (int) Math.floor(metrics.ascent), descent = (int) Math.ceil(metrics.descent), leading = (int) Math.ceil(metrics.leading);
        if (value != null) { value.top = (int) Math.floor(metrics.top); value.ascent = ascent; value.descent = descent; value.bottom = (int) Math.ceil(metrics.bottom); value.leading = leading; }
        return descent - ascent + leading;
    }
    public float ascent() { return getFontMetrics().ascent; }
    public float descent() { return getFontMetrics().descent; }
    public String getFontVariationSettings(){return variationSettings;}
    public int[] shirazVariationTags(){return variationTags==null?null:variationTags.clone();}
    public float[] shirazVariationValues(){return variationValues==null?null:variationValues.clone();}
    public boolean setFontVariationSettings(String settings){
        if(settings==null||settings.isEmpty()){variationSettings=null;variationTags=null;variationValues=null;return true;}
        if(settings.equals(variationSettings))return true;if(settings.length()>4096)throw new IllegalArgumentException("Font variation settings length");
        String[] fields=settings.split(",",-1);if(fields.length>64)throw new IllegalArgumentException("Font variation axis count");
        java.util.regex.Pattern pattern=java.util.regex.Pattern.compile("\\s*(['\"])([ -~]{4})\\1\\s*([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)\\s*");
        int[] requested=new int[fields.length];float[] coords=new float[fields.length];for(int i=0;i<fields.length;i++){java.util.regex.Matcher match=pattern.matcher(fields[i]);if(!match.matches())throw new IllegalArgumentException("Font variation syntax");String tag=match.group(2);int packed=0;for(int j=0;j<4;j++)packed=(packed<<8)|tag.charAt(j);requested[i]=packed;coords[i]=Float.parseFloat(match.group(3));if(!Float.isFinite(coords[i]))throw new IllegalArgumentException("Nonfinite font variation");}
        Typeface face=shirazTypeface();int[] supported=shiraz.runtime.NativeText.variationAxes(face.shirazFamily(),face.getStyle());int count=0;for(int i=0;i<requested.length;i++)for(int axis:supported)if(axis==requested[i]){requested[count]=requested[i];coords[count++]=coords[i];break;}
        if(count==0)return false;variationTags=java.util.Arrays.copyOf(requested,count);variationValues=java.util.Arrays.copyOf(coords,count);variationSettings=settings;return true;
    }
    public Typeface setTypeface(Typeface value) { Typeface previous = typeface; typeface = value;variationSettings=null;variationTags=null;variationValues=null; return previous; }
    public Typeface getTypeface() { return typeface; }
    Typeface shirazTypeface() { return typeface == null ? Typeface.DEFAULT : typeface; }
    public PathEffect getPathEffect() { return pathEffect; }
    public PathEffect setPathEffect(PathEffect value) { PathEffect.shirazValidate(value); pathEffect=value; return value; }
    public ColorFilter setColorFilter(ColorFilter value) { ColorFilter.shirazValidate(value); colorFilter=value; return value; }
    public void setShadowLayer(float radius,float dx,float dy,int color){if(!Float.isFinite(radius)||radius<0||radius>256||!Float.isFinite(dx)||!Float.isFinite(dy)||Math.abs(dx)>8192||Math.abs(dy)>8192)throw new IllegalArgumentException("Shadow geometry bounds");shadowRadius=radius;shadowDx=dx;shadowDy=dy;shadowColor=color;}
    public void clearShadowLayer(){setShadowLayer(0,0,0,0);}public boolean hasShadowLayer(){return shadowRadius>0;}
    public float getShadowLayerRadius(){return shadowRadius;}public float getShadowLayerDx(){return shadowDx;}public float getShadowLayerDy(){return shadowDy;}public int getShadowLayerColor(){return shadowColor;}
    public Xfermode getXfermode(){return xfermode;}
    public Xfermode setXfermode(Xfermode value){if(value!=null&&!(value instanceof PorterDuffXfermode))throw new UnsupportedOperationException("Custom Xfermode");xfermode=value;return value;}
    public ColorFilter getColorFilter() { return colorFilter; }
    int shirazFilteredColor() { return ColorFilter.shirazApply(colorFilter,color); }
    public void setColor(int color) { this.color = color; }
    public void setARGB(int alpha,int red,int green,int blue) { setColor(Color.argb(alpha,red,green,blue)); }
    public int getColor() { return color; }
    public void setAlpha(int alpha) {
        if (alpha < 0 || alpha > 255) throw new IllegalArgumentException("alpha");
        color = (color & 0xffffff) | (alpha << 24);
    }
    public int getAlpha() { return color >>> 24; }
    public void setStyle(Style style) {
        if (style == null) throw new NullPointerException("style");
        this.style = style;
    }
    public Style getStyle() { return style; }
    public void setStrokeWidth(float width) {
        if (!Float.isFinite(width) || width < 0) throw new IllegalArgumentException("Stroke width must be finite and nonnegative");
        strokeWidth = width;
    }
    public float getStrokeWidth() { return strokeWidth; }
    public void setStrokeMiter(float miter) {
        if (!Float.isFinite(miter) || miter < 0) throw new IllegalArgumentException("Stroke miter must be finite and nonnegative");
        strokeMiter = miter;
    }
    public float getStrokeMiter() { return strokeMiter; }
    public void setStrokeCap(Cap value) { if (value == null) throw new NullPointerException("cap"); cap = value; }
    public Cap getStrokeCap() { return cap; }
    public void setStrokeJoin(Join value) { if (value == null) throw new NullPointerException("join"); join = value; }
    public Join getStrokeJoin() { return join; }
}
