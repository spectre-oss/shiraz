// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1, FILTER_BITMAP_FLAG = 2, DITHER_FLAG = 4, UNDERLINE_TEXT_FLAG = 8, STRIKE_THRU_TEXT_FLAG = 16, FAKE_BOLD_TEXT_FLAG = 32, LINEAR_TEXT_FLAG = 64, SUBPIXEL_TEXT_FLAG = 128, DEV_KERN_TEXT_FLAG = 256, EMBEDDED_BITMAP_TEXT_FLAG = 1024;
    public static final int HINTING_OFF=0,HINTING_ON=1;
    public static final int CURSOR_AFTER = 0, CURSOR_AT_OR_AFTER = 1, CURSOR_BEFORE = 2, CURSOR_AT_OR_BEFORE = 3, CURSOR_AT = 4;
    public static final int START_HYPHEN_EDIT_NO_EDIT = 0, START_HYPHEN_EDIT_INSERT_HYPHEN = 1, START_HYPHEN_EDIT_INSERT_ZWJ = 2;
    public static final int END_HYPHEN_EDIT_NO_EDIT = 0, END_HYPHEN_EDIT_REPLACE_WITH_HYPHEN = 1, END_HYPHEN_EDIT_INSERT_HYPHEN = 2, END_HYPHEN_EDIT_INSERT_ARMENIAN_HYPHEN = 3, END_HYPHEN_EDIT_INSERT_MAQAF = 4, END_HYPHEN_EDIT_INSERT_UCAS_HYPHEN = 5, END_HYPHEN_EDIT_INSERT_ZWJ_AND_HYPHEN = 6;
    public static final int TEXT_RUN_FLAG_LEFT_EDGE = 8192, TEXT_RUN_FLAG_RIGHT_EDGE = 16384;
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
    private String featureSettings;
    public String getFontFeatureSettings(){return featureSettings;}
    public void setFontFeatureSettings(String settings){
        if(settings!=null&&settings.length()>4096)throw new IllegalArgumentException("Font feature settings bound");
        featureSettings=settings==null||settings.isEmpty()?null:settings;
    }
    private Shader shader;
    public Shader getShader(){return shader;}
    public Shader setShader(Shader value){shader=value;return value;}
    private ColorFilter colorFilter;
    private Xfermode xfermode;
    private MaskFilter maskFilter;
    private float shadowRadius,shadowDx,shadowDy;private int shadowColor;
    private PathEffect pathEffect;
    private Align align = Align.LEFT;
    private boolean underline,strikeThru,elegantTextHeight;
    private float wordSpacing;
    private int startHyphenEdit,endHyphenEdit;
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
        shader=source.shader;underline=source.underline;strikeThru=source.strikeThru;shadowRadius=source.shadowRadius;shadowDx=source.shadowDx;shadowDy=source.shadowDy;shadowColor=source.shadowColor;featureSettings=source.featureSettings;variationSettings=source.variationSettings;variationTags=source.variationTags==null?null:source.variationTags.clone();variationValues=source.variationValues==null?null:source.variationValues.clone();xfermode=source.xfermode; pathEffect=source.pathEffect; maskFilter=source.maskFilter; typeface = source.typeface; colorFilter=source.colorFilter;
        bidiLevels=source.bidiLevels==null?null:source.bidiLevels.clone();
        linearText=source.linearText;subpixelText=source.subpixelText;hinting=source.hinting;textScaleX=source.textScaleX;
        elegantTextHeight=source.elegantTextHeight;wordSpacing=source.wordSpacing;startHyphenEdit=source.startHyphenEdit;endHyphenEdit=source.endHyphenEdit;
        style = source.style; cap = source.cap; join = source.join;
        strokeWidth = source.strokeWidth; strokeMiter = source.strokeMiter;
        letterSpacing=source.letterSpacing;textSkewX=source.textSkewX; dither=source.dither; fakeBold=source.fakeBold; align = source.align; antialias = source.antialias; filterBitmap = source.filterBitmap; textSize = source.textSize; color = source.color;
    }
    public void reset() { set(new Paint()); }
    public Paint(int flags) { setFlags(flags); }
    public int getFlags() { return (linearText ? LINEAR_TEXT_FLAG : 0) | (subpixelText ? SUBPIXEL_TEXT_FLAG : 0) | (strikeThru ? STRIKE_THRU_TEXT_FLAG : 0) | (underline ? UNDERLINE_TEXT_FLAG : 0) | (antialias ? ANTI_ALIAS_FLAG : 0) | (filterBitmap ? FILTER_BITMAP_FLAG : 0) | (fakeBold ? FAKE_BOLD_TEXT_FLAG : 0) | (dither ? DITHER_FLAG : 0); }
    public void setFlags(int flags) {
        if ((flags & ~(ANTI_ALIAS_FLAG | FILTER_BITMAP_FLAG | FAKE_BOLD_TEXT_FLAG | DITHER_FLAG | UNDERLINE_TEXT_FLAG | STRIKE_THRU_TEXT_FLAG | SUBPIXEL_TEXT_FLAG | LINEAR_TEXT_FLAG)) != 0) throw new UnsupportedOperationException("Paint flags are not implemented");
        linearText=(flags&LINEAR_TEXT_FLAG)!=0;subpixelText=(flags&SUBPIXEL_TEXT_FLAG)!=0;strikeThru=(flags&STRIKE_THRU_TEXT_FLAG)!=0;underline=(flags&UNDERLINE_TEXT_FLAG)!=0;dither = (flags & DITHER_FLAG) != 0; fakeBold = (flags & FAKE_BOLD_TEXT_FLAG) != 0; antialias = (flags & ANTI_ALIAS_FLAG) != 0; filterBitmap = (flags & FILTER_BITMAP_FLAG) != 0;
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
        if (!Float.isFinite(value) || value < 0 || value > 16384) throw new IllegalArgumentException("Text size must be in [0,16384]");
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
        int[] box=shiraz.runtime.NativeText.boundsSubpixelResolved(text.substring(start,end),textSize,shirazTypeface().shirazFamily(),shirazTypeface().getStyle(),variationTags,variationValues,letterSpacing,featureSettings,fakeBold,textSkewX,shirazTextHinting(),textScaleX,bidiLevels==null?null:java.util.Arrays.copyOfRange(bidiLevels,start,end),subpixelText);
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
    public float[] shirazTextClusters(String text){float[] result=shiraz.runtime.NativeText.clustersResolved(text,textSize,shirazTypeface().shirazFamily(),shirazTypeface().getStyle(),variationTags,variationValues,letterSpacing,featureSettings,bidiLevels);for(int i=0;i<result.length;i+=5){result[i+2]*=textScaleX;result[i+3]*=textScaleX;}return result;}
    public float measureText(CharSequence text,int start,int end){if(text==null)throw new IllegalArgumentException("text");if(start<0||end<start||end>text.length())throw new IndexOutOfBoundsException();return measureText(text.toString(),start,end);}
    public float measureText(String text) { if (text == null) throw new IllegalArgumentException("text"); return shiraz.runtime.NativeText.measureResolved(text, textSize, shirazTypeface().shirazFamily(), shirazTypeface().getStyle(),variationTags,variationValues,letterSpacing,featureSettings,bidiLevels)*textScaleX; }
    public float measureText(String text, int start, int end) { if(bidiLevels==null)return measureText(text.substring(start,end));Paint copy=new Paint(this);copy.bidiLevels=java.util.Arrays.copyOfRange(bidiLevels,start,end);return copy.measureText(text.substring(start,end)); }
    public int breakText(String text,boolean forwards,float maxWidth,float[] measuredWidth) {
        if(text==null)throw new IllegalArgumentException("text");
        if(measuredWidth!=null&&measuredWidth.length==0)throw new ArrayIndexOutOfBoundsException("measuredWidth");
        if(Float.isNaN(maxWidth))throw new IllegalArgumentException("maxWidth");
        if(text.length()==0)return 0;
        float limit=maxWidth<0?maxWidth:textScaleX==0?Float.POSITIVE_INFINITY:maxWidth/textScaleX;
        int count=shiraz.runtime.NativeText.breakResolved(text,textSize,shirazTypeface().shirazFamily(),shirazTypeface().getStyle(),variationTags,variationValues,letterSpacing,featureSettings,forwards,limit,measuredWidth,bidiLevels);
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
    public float getFontSpacing() { return getFontMetrics(null); }
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
    public Typeface setTypeface(Typeface value) { Typeface previous = typeface; typeface = value;variationSettings=null;variationTags=null;variationValues=null;if(value!=null&&value.shirazVariationSettings()!=null)setFontVariationSettings(value.shirazVariationSettings()); return previous; }
    public Typeface getTypeface() { return typeface; }
    Typeface shirazTypeface() { return typeface == null ? Typeface.DEFAULT : typeface; }
    public PathEffect getPathEffect() { return pathEffect; }
    public PathEffect setPathEffect(PathEffect value) { PathEffect.shirazValidate(value); pathEffect=value; return value; }
    public ColorFilter setColorFilter(ColorFilter value) { ColorFilter.shirazValidate(value); colorFilter=value; return value; }
    public void setShadowLayer(float radius,float dx,float dy,int color){if(!Float.isFinite(radius)||radius<0||radius>256||!Float.isFinite(dx)||!Float.isFinite(dy)||Math.abs(dx)>8192||Math.abs(dy)>8192)throw new IllegalArgumentException("Shadow geometry bounds");shadowRadius=radius;shadowDx=dx;shadowDy=dy;shadowColor=color;}
    public void clearShadowLayer(){setShadowLayer(0,0,0,0);}public boolean hasShadowLayer(){return shadowRadius>0;}
    public float getShadowLayerRadius(){return shadowRadius;}public float getShadowLayerDx(){return shadowDx;}public float getShadowLayerDy(){return shadowDy;}public int getShadowLayerColor(){return shadowColor;}
    public BlendMode getBlendMode(){return xfermode==null?null:BlendMode.fromLegacy(((PorterDuffXfermode)xfermode).mode);}
    public void setBlendMode(BlendMode mode){setXfermode(mode==null?null:new PorterDuffXfermode(mode.legacy()));}
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
    public MaskFilter getMaskFilter() { return maskFilter; }
    public MaskFilter setMaskFilter(MaskFilter maskfilter) { this.maskFilter = maskfilter; return maskfilter; }
    public long getColorLong() { return Color.valueOf(color).pack(); }
    public void setColor(long color) { this.color = SweepGradient.packedColorToArgb(color); }
    public void setShadowLayer(float radius, float dx, float dy, long shadowColor) {
        setShadowLayer(radius, dx, dy, SweepGradient.packedColorToArgb(shadowColor));
    }
    public long getShadowLayerColorLong() { return Color.valueOf(shadowColor).pack(); }
    public float getUnderlinePosition() { return textSize / 9.0f; }
    public float getUnderlineThickness() { return Math.max(1.0f, textSize / 18.0f); }
    public float getStrikeThruPosition() { return -textSize * 0.3f; }
    public float getStrikeThruThickness() { return Math.max(1.0f, textSize / 18.0f); }
    public boolean isElegantTextHeight() { return elegantTextHeight; }
    public void setElegantTextHeight(boolean elegant) { this.elegantTextHeight = elegant; }
    public float getWordSpacing() { return wordSpacing; }
    public void setWordSpacing(float wordSpacing) {
        if (!Float.isFinite(wordSpacing)) throw new IllegalArgumentException("Non-finite wordSpacing");
        this.wordSpacing = wordSpacing;
    }
    public int getStartHyphenEdit() { return startHyphenEdit; }
    public void setStartHyphenEdit(int startHyphen) { this.startHyphenEdit = startHyphen; }
    public int getEndHyphenEdit() { return endHyphenEdit; }
    public void setEndHyphenEdit(int endHyphen) { this.endHyphenEdit = endHyphen; }
    public boolean getFillPath(Path src, Path dst) {
        if (src == null || dst == null) throw new NullPointerException();
        Path applied = PathEffect.shirazApply(pathEffect, src);
        dst.set(applied);
        return style != Style.STROKE || strokeWidth > 0f;
    }
    public float measureText(char[] text, int index, int count) {
        if (text == null) throw new IllegalArgumentException("text cannot be null");
        if ((index | count) < 0 || index + count > text.length) throw new ArrayIndexOutOfBoundsException();
        if (count == 0) return 0f;
        return measureText(new String(text, index, count));
    }
    public void getTextBounds(CharSequence text, int start, int end, Rect bounds) {
        if (text == null || bounds == null) throw new NullPointerException();
        if ((start | end | (end - start) | (text.length() - end)) < 0) throw new IndexOutOfBoundsException();
        getTextBounds(text.subSequence(start, end).toString(), 0, end - start, bounds);
    }
    public int getTextWidths(char[] text, int index, int count, float[] widths) {
        if (text == null) throw new IllegalArgumentException("text cannot be null");
        if ((index | count) < 0 || index + count > text.length || count > widths.length) {
            throw new ArrayIndexOutOfBoundsException();
        }
        return getTextWidths(new String(text, index, count), 0, count, widths);
    }
    public int getTextWidths(CharSequence text, int start, int end, float[] widths) {
        if (text == null) throw new IllegalArgumentException("text cannot be null");
        if ((start | end | (end - start) | (text.length() - end)) < 0) throw new IndexOutOfBoundsException();
        if (end - start > widths.length) throw new ArrayIndexOutOfBoundsException();
        return getTextWidths(text.subSequence(start, end).toString(), 0, end - start, widths);
    }
    public int getTextWidths(String text, float[] widths) {
        if (text == null) throw new IllegalArgumentException("text cannot be null");
        return getTextWidths(text, 0, text.length(), widths);
    }
    public int getTextWidths(String text, int start, int end, float[] widths) {
        if (text == null) throw new IllegalArgumentException("text cannot be null");
        if ((start | end | (end - start) | (text.length() - end)) < 0) throw new IndexOutOfBoundsException();
        int len = end - start;
        if (len > widths.length) throw new ArrayIndexOutOfBoundsException();
        if (len == 0) return 0;
        String sub = text.substring(start, end);
        computeCharAdvances(sub, widths, 0);
        return len;
    }
    void computeCharAdvances(String sub, float[] advances, int outOffset) {
        int len = sub.length();
        for (int i = 0; i < len; i++) {
            advances[outOffset + i] = 0f;
        }
        if (len == 0) return;
        float[] clusters = shirazTextClusters(sub);
        if (clusters != null && clusters.length >= 5) {
            for (int c = 0; c + 4 < clusters.length; c += 5) {
                int cStart = Math.max(0, Math.min(len, Math.round(clusters[c])));
                int cEnd = Math.max(cStart, Math.min(len, Math.round(clusters[c + 1])));
                float cWidth = Math.max(0f, clusters[c + 3] - clusters[c + 2]);
                if (cStart < len) {
                    advances[outOffset + cStart] += cWidth;
                }
            }
        } else {
            for (int i = 0; i < len; ) {
                int cp = Character.codePointAt(sub, i);
                int charCount = Character.charCount(cp);
                advances[outOffset + i] = measureText(sub, i, i + charCount);
                i += charCount;
            }
        }
        if (wordSpacing != 0f) {
            for (int i = 0; i < len; i++) {
                if (sub.charAt(i) == ' ') advances[outOffset + i] += wordSpacing;
            }
        }
    }
    public void getFontMetricsForLocale(FontMetrics metrics) {
        getFontMetrics(metrics);
    }
    public void getFontMetricsIntForLocale(FontMetricsInt metrics) {
        getFontMetricsInt(metrics);
    }
    public void getFontMetricsInt(
            CharSequence text, int start, int count, int contextStart, int contextCount,
            boolean isRtl, FontMetricsInt outMetrics) {
        if (text == null || outMetrics == null) throw new NullPointerException();
        if (start < 0 || count < 0 || contextStart < 0 || contextCount < 0
                || start < contextStart || start + count > contextStart + contextCount
                || contextStart + contextCount > text.length()) {
            throw new IndexOutOfBoundsException();
        }
        getFontMetricsInt(outMetrics);
    }
    public void getFontMetricsInt(
            char[] text, int start, int count, int contextStart, int contextCount,
            boolean isRtl, FontMetricsInt outMetrics) {
        if (text == null || outMetrics == null) throw new NullPointerException();
        if (start < 0 || count < 0 || contextStart < 0 || contextCount < 0
                || start < contextStart || start + count > contextStart + contextCount
                || contextStart + contextCount > text.length) {
            throw new IndexOutOfBoundsException();
        }
        getFontMetricsInt(outMetrics);
    }
    public float getTextRunAdvances(
            char[] chars, int index, int count, int contextIndex, int contextCount,
            boolean isRtl, float[] advances, int advancesIndex) {
        if (chars == null) throw new IllegalArgumentException("text cannot be null");
        if ((index | count | contextIndex | contextCount | advancesIndex
                | (index - contextIndex) | (contextCount - count)
                | ((contextIndex + contextCount) - (index + count))
                | (chars.length - (contextIndex + contextCount))
                | (advances == null ? 0 : (advances.length - (advancesIndex + count)))) < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (count == 0) return 0f;
        String sub = new String(chars, index, count);
        float[] local = new float[count];
        computeCharAdvances(sub, local, 0);
        float total = 0f;
        for (int i = 0; i < count; i++) {
            total += local[i];
            if (advances != null) advances[advancesIndex + i] = local[i];
        }
        return total;
    }
    public int getTextRunCursor(
            char[] text, int contextStart, int contextLength, boolean dir, int offset, int cursorOpt) {
        int contextEnd = contextStart + contextLength;
        if (((contextStart | contextEnd | offset | (contextEnd - contextStart)
                | (offset - contextStart) | (contextEnd - offset)
                | (text.length - contextEnd) | cursorOpt) < 0)
                || cursorOpt > CURSOR_AT) {
            throw new IndexOutOfBoundsException();
        }
        return computeCursor(new String(text, contextStart, contextLength), contextStart, offset, cursorOpt);
    }
    public int getTextRunCursor(
            CharSequence text, int contextStart, int contextEnd, boolean dir, int offset, int cursorOpt) {
        if (text == null) throw new NullPointerException();
        if (((contextStart | contextEnd | offset | (contextEnd - contextStart)
                | (offset - contextStart) | (contextEnd - offset)
                | (text.length() - contextEnd) | cursorOpt) < 0)
                || cursorOpt > CURSOR_AT) {
            throw new IndexOutOfBoundsException();
        }
        return computeCursor(
                text.subSequence(contextStart, contextEnd).toString(), contextStart, offset, cursorOpt);
    }
    private static int computeCursor(String sub, int base, int offset, int cursorOpt) {
        int local = offset - base;
        int len = sub.length();
        switch (cursorOpt) {
            case CURSOR_AFTER:
                return local >= len ? base + len : base + sub.offsetByCodePoints(local, 1);
            case CURSOR_AT_OR_AFTER:
                if (local < len && local > 0 && Character.isLowSurrogate(sub.charAt(local))
                        && Character.isHighSurrogate(sub.charAt(local - 1))) {
                    return base + local + 1;
                }
                return offset;
            case CURSOR_BEFORE:
                return local <= 0 ? base : base + sub.offsetByCodePoints(local, -1);
            case CURSOR_AT_OR_BEFORE:
                if (local < len && local > 0 && Character.isLowSurrogate(sub.charAt(local))
                        && Character.isHighSurrogate(sub.charAt(local - 1))) {
                    return base + local - 1;
                }
                return offset;
            default:
                if (local < len && local > 0 && Character.isLowSurrogate(sub.charAt(local))
                        && Character.isHighSurrogate(sub.charAt(local - 1))) {
                    return -1;
                }
                return offset;
        }
    }
    public void getTextPath(char[] text, int index, int count, float x, float y, Path path) {
        if ((index | count) < 0 || index + count > text.length) throw new ArrayIndexOutOfBoundsException();
        getTextPath(new String(text, index, count), 0, count, x, y, path);
    }
    public void getTextPath(String text, int start, int end, float x, float y, Path path) {
        if ((start | end | (end - start) | (text.length() - end)) < 0) throw new IndexOutOfBoundsException();
        if (path == null) throw new NullPointerException("path");
        path.reset();
        if (start == end) return;
        Rect bounds = new Rect();
        getTextBounds(text, start, end, bounds);
        if (!bounds.isEmpty()) {
            path.addRect(x + bounds.left, y + bounds.top, x + bounds.right, y + bounds.bottom, Path.Direction.CW);
        }
    }
    public float getRunAdvance(
            char[] text, int start, int end, int contextStart, int contextEnd, boolean isRtl, int offset) {
        return getRunCharacterAdvance(text, start, end, contextStart, contextEnd, isRtl, offset, null, 0);
    }
    public float getRunAdvance(
            CharSequence text, int start, int end, int contextStart, int contextEnd, boolean isRtl, int offset) {
        return getRunCharacterAdvance(text, start, end, contextStart, contextEnd, isRtl, offset, null, 0);
    }
    public float getRunCharacterAdvance(
            char[] text, int start, int end, int contextStart, int contextEnd,
            boolean isRtl, int offset, float[] advances, int advancesIndex) {
        if (text == null) throw new IllegalArgumentException("text cannot be null");
        if (contextStart < 0 || start < contextStart || offset < start || end < offset
                || contextEnd < end || text.length < contextEnd) {
            throw new IndexOutOfBoundsException();
        }
        int runLen = end - start;
        if (advances != null && (advancesIndex < 0 || advances.length - advancesIndex < offset - start)) {
            throw new IndexOutOfBoundsException();
        }
        if (runLen == 0) return 0f;
        float[] all = new float[runLen];
        computeCharAdvances(new String(text, start, runLen), all, 0);
        float sum = 0f;
        int limit = offset - start;
        for (int i = 0; i < limit; i++) {
            sum += all[i];
            if (advances != null) advances[advancesIndex + i] = all[i];
        }
        return sum;
    }
    public float getRunCharacterAdvance(
            CharSequence text, int start, int end, int contextStart, int contextEnd,
            boolean isRtl, int offset, float[] advances, int advancesIndex) {
        if (text == null) throw new IllegalArgumentException("text cannot be null");
        if (contextStart < 0 || start < contextStart || offset < start || end < offset
                || contextEnd < end || text.length() < contextEnd) {
            throw new IndexOutOfBoundsException();
        }
        char[] buf = new char[contextEnd - contextStart];
        for (int i = contextStart; i < contextEnd; i++) buf[i - contextStart] = text.charAt(i);
        return getRunCharacterAdvance(
                buf, start - contextStart, end - contextStart, 0, contextEnd - contextStart,
                isRtl, offset - contextStart, advances, advancesIndex);
    }
    public int getOffsetForAdvance(
            char[] text, int start, int end, int contextStart, int contextEnd,
            boolean isRtl, float advance) {
        if (text == null) throw new IllegalArgumentException("text cannot be null");
        if (contextStart < 0 || start < contextStart || end < start
                || contextEnd < end || text.length < contextEnd) {
            throw new IndexOutOfBoundsException();
        }
        int runLen = end - start;
        if (runLen == 0 || advance <= 0f) return isRtl ? end : start;
        float[] widths = new float[runLen];
        computeCharAdvances(new String(text, start, runLen), widths, 0);
        float acc = 0f;
        if (!isRtl) {
            for (int i = 0; i < runLen; i++) {
                float w = widths[i];
                if (advance < acc + w * 0.5f) return start + i;
                acc += w;
            }
            return end;
        } else {
            for (int i = runLen - 1; i >= 0; i--) {
                float w = widths[i];
                if (advance < acc + w * 0.5f) return start + i + 1;
                acc += w;
            }
            return start;
        }
    }
    public int getOffsetForAdvance(
            CharSequence text, int start, int end, int contextStart, int contextEnd,
            boolean isRtl, float advance) {
        if (text == null) throw new IllegalArgumentException("text cannot be null");
        if (contextStart < 0 || start < contextStart || end < start
                || contextEnd < end || text.length() < contextEnd) {
            throw new IndexOutOfBoundsException();
        }
        char[] buf = new char[contextEnd - contextStart];
        for (int i = contextStart; i < contextEnd; i++) buf[i - contextStart] = text.charAt(i);
        return contextStart + getOffsetForAdvance(
                buf, start - contextStart, end - contextStart, 0, contextEnd - contextStart,
                isRtl, advance);
    }
    public boolean equalsForTextMeasurement(Paint other) {
        if (other == null) return false;
        if (this == other) return true;
        return Float.compare(textSize, other.textSize) == 0
                && Float.compare(textScaleX, other.textScaleX) == 0
                && Float.compare(textSkewX, other.textSkewX) == 0
                && Float.compare(letterSpacing, other.letterSpacing) == 0
                && Float.compare(wordSpacing, other.wordSpacing) == 0
                && hinting == other.hinting
                && fakeBold == other.fakeBold
                && linearText == other.linearText
                && subpixelText == other.subpixelText
                && elegantTextHeight == other.elegantTextHeight
                && startHyphenEdit == other.startHyphenEdit
                && endHyphenEdit == other.endHyphenEdit
                && java.util.Objects.equals(shirazTypeface(), other.shirazTypeface())
                && java.util.Objects.equals(textLocales, other.textLocales)
                && java.util.Objects.equals(featureSettings, other.featureSettings)
                && java.util.Objects.equals(variationSettings, other.variationSettings);
    }
}
