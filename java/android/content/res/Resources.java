// SPDX-License-Identifier: GPL-3.0-only
package android.content.res;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Original-APK resources on an initial API-27/mdpi virtual display. */
public class Resources {
    static { System.loadLibrary("shiraz-resources"); }
    private volatile long handle;
    private final String apkPath;
    private Resources parent;
    private Configuration override, parentConfiguration;
    private int depth;
    private java.util.concurrent.atomic.AtomicInteger familySize = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.ArrayList<Resources> children = new java.util.ArrayList<Resources>();
    private final boolean processSystem;
    private static Resources system;
    private static int systemWidth=720,systemHeight=1280,systemDensity=160;
    public static synchronized void shirazSetSystemDisplay(int width,int height) {shirazSetSystemDisplay(width,height,systemDensity);}
    public static synchronized void shirazSetSystemDisplay(int width,int height,int density) {
        if(width<1||height<1||width>8192||height>8192||(long)width*height>16*1024*1024)throw new IllegalArgumentException("System display geometry");
        if(density<1||density>1280)throw new IllegalArgumentException("System display density");
        if(system!=null)system.shirazResizeDisplay(width,height,density);
        systemWidth=width;systemHeight=height;systemDensity=density;
        android.graphics.Bitmap.shirazSetDefaultDensity(density);
    }
    public static synchronized Resources getSystem() {
        if(system==null){String framework=System.getProperty("shiraz.frameworkResources");if(framework==null||framework.isEmpty())throw new NotFoundException("Framework resources are not configured");system=new Resources(framework,systemWidth,systemHeight,Locale.getDefault(),true,systemDensity);}
        return system;
    }
    private Configuration configuration;
    private android.util.DisplayMetrics metrics;
    private final android.util.DisplayMetrics baseMetrics;
    private int width, height;
    private AssetManager assets;
    private Resources(String apk, int width, int height, Locale locale,boolean processSystem) {
        this(apk,width,height,locale,processSystem,160);
    }
    private Resources(String apk,int width,int height,Locale locale,boolean processSystem,int density) {
        if(density<1||density>1280)throw new IllegalArgumentException("Display density");
        this.processSystem=processSystem; this.apkPath=apk;
        this.width = width; this.height = height;
        configuration = new Configuration(); configuration.fontScale=1; configuration.setLocale(locale);
        configuration.densityDpi = density; configuration.screenWidthDp = Math.max(1,Math.round(width*160f/density)); configuration.screenHeightDp = Math.max(1,Math.round(height*160f/density));
        configuration.smallestScreenWidthDp = Math.min(configuration.screenWidthDp, configuration.screenHeightDp);
        configuration.screenLayout = displayScreenLayout(configuration.screenLayout, configuration.screenWidthDp, configuration.screenHeightDp);
        configuration.orientation = width > height ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
        configuration.uiMode = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_NO;
        metrics = new android.util.DisplayMetrics(); metrics.widthPixels = width; metrics.heightPixels = height;
        metrics.densityDpi=density;metrics.density=metrics.scaledDensity=density/160f;metrics.xdpi=metrics.ydpi=density;
        baseMetrics = new android.util.DisplayMetrics(); baseMetrics.setTo(metrics);
        String framework = System.getProperty("shiraz.frameworkResources");
        handle = processSystem ? nativeOpenSystem(apk.getBytes(StandardCharsets.UTF_8),locale.toLanguageTag(),width,height) : nativeOpen(apk.getBytes(StandardCharsets.UTF_8), framework == null ? null : framework.getBytes(StandardCharsets.UTF_8), locale.toLanguageTag(), width, height);
        if (handle == 0) throw new IllegalStateException("Cannot open APK resource manager");
        try { configure(configuration, metrics); } catch (Throwable error) { nativeClose(handle); handle = 0; throw error; }
    }
    public static Resources shirazOpen(String apk,int width,int height,int density) {
        return new Resources(apk,width,height,Locale.getDefault(),false,density);
    }
    public static Resources shirazOpen(String apk, int width, int height) {
        return new Resources(apk, width, height, Locale.getDefault(),false);
    }
    public synchronized Resources shirazWithConfiguration(Configuration changes) {
        if(changes==null)throw new NullPointerException("overrideConfiguration");
        requireOpen();
        for(java.util.Iterator<Resources> it=children.iterator();it.hasNext();)if(it.next().handle==0)it.remove();
        if(depth>=32)throw new IllegalStateException("Configuration context nesting limit");
        if(familySize.incrementAndGet()>128){familySize.decrementAndGet();throw new IllegalStateException("Configuration resource limit");}
        Resources child=null;
        try {
            child=new Resources(apkPath,width,height,configuration.locale,processSystem);
            child.updateConfiguration(configuration,metrics);
            child.updateConfiguration(new Configuration(changes),null);
            child.override=new Configuration(changes);child.parentConfiguration=new Configuration(configuration);
            child.parent=this;child.depth=depth+1;child.familySize=familySize;
            children.add(child);return child;
        } catch(Throwable failure) {boolean counted=child!=null&&child.parent!=null;if(child!=null)child.closeOwned();if(!counted)familySize.decrementAndGet();throw failure;}
    }
    public void shirazClose() {
        if(processSystem&&parent==null)throw new IllegalStateException("System resources belong to the process");
        closeOwned();
    }
    private void closeOwned() {
        java.util.ArrayList<Resources> owned;
        synchronized(this) {
            if(handle==0)return;
            nativeClose(handle);handle=0;valueCache.clear();styleCache.clear();clearXmlCache();
            owned=new java.util.ArrayList<Resources>(children);children.clear();
            if(parent!=null)familySize.decrementAndGet();
        }
        for(Resources child:owned)child.closeOwned();
    }
    /** App resource size qualifiers, excluding the framework APK, for activity resize policy. */
    public synchronized Configuration[] getSizeConfigurations(){
        requireOpen();int[] values=nativeSizeConfigurations(handle);
        if(values==null||values.length%3!=0||values.length>32768*3)throw new IllegalStateException("Invalid resource size configurations");
        Configuration[] result=new Configuration[values.length/3];
        for(int i=0;i<result.length;i++){Configuration value=new Configuration();value.screenWidthDp=values[i*3];value.screenHeightDp=values[i*3+1];value.smallestScreenWidthDp=values[i*3+2];result[i]=value;}
        return result;
    }
    public synchronized AssetManager getAssets() { requireOpen(); if (assets == null) assets = new AssetManager(this); return assets; }
    synchronized String shirazAssetArchive(){requireOpen();return apkPath;}
    synchronized long[] shirazOpenAssetStream(String name) throws java.io.IOException {
        requireOpen(); return nativeAssetStream(handle, ("assets/" + name).getBytes(StandardCharsets.UTF_8));
    }
    synchronized long[] shirazOpenAssetFd(String name) throws java.io.IOException {
        requireOpen(); return nativeAssetFd(handle, ("assets/" + name).getBytes(StandardCharsets.UTF_8));
    }
    // Native resource change bits differ from ActivityInfo's public Java masks.
    private static int javaConfigurationFlags(int nativeFlags) {
        int result=nativeFlags & 255;
        if ((nativeFlags & 0x100) != 0) result |= 0x1000;
        if ((nativeFlags & 0x200) != 0) result |= 0x400;
        if ((nativeFlags & 0x800) != 0) result |= 0x100;
        if ((nativeFlags & 0x1000) != 0) result |= 0x200;
        if ((nativeFlags & 0x2000) != 0) result |= 0x800;
        if ((nativeFlags & 0x4000) != 0) result |= 0x2000;
        if ((nativeFlags & 0x10000) != 0) result |= 0x4000;
        if ((nativeFlags & 0x20000) != 0) result |= 0x8000;
        return result;
    }
    // Resource values depend on this manager's configuration, never on a Theme.
    // Keep scalar/bag reference queries separate and bound retained APK strings.
    private final java.util.LinkedHashMap<Long,android.util.TypedValue> styleCache=new java.util.LinkedHashMap<Long,android.util.TypedValue>(64,.75f,true);
    private final java.util.LinkedHashMap<Long,android.util.TypedValue> valueCache=new java.util.LinkedHashMap<Long,android.util.TypedValue>(64,.75f,true);
    private void resourceValue(int id,int density,boolean resolve,boolean allowBag,android.util.TypedValue out){
        long key=((id&0xffffffffL)<<18)|((long)density<<2)|(resolve?2:0)|(allowBag?1:0);
        android.util.TypedValue found=valueCache.get(key);if(found!=null){out.setTo(found);return;}
        int[] fields=new int[6];String[] text=new String[1];nativeValue(handle,id,density,resolve,allowBag,fields,text);assignValue(out,fields,text[0]);
        if(text[0]==null||text[0].length()<=1024){android.util.TypedValue copy=new android.util.TypedValue();copy.setTo(out);valueCache.put(key,copy);if(valueCache.size()>512)valueCache.remove(valueCache.keySet().iterator().next());}
    }
    private static void assignValue(android.util.TypedValue out,int[] value,String text) {
        out.type=value[0]; out.data=value[1]; out.assetCookie=value[2]; out.changingConfigurations=javaConfigurationFlags(value[3]);
        out.resourceId=value[4]; out.density=value[5]; out.string=text;
    }
    public void getValue(int id,android.util.TypedValue out,boolean resolveRefs) { getValueForDensity(id,0,out,resolveRefs); }
    public void getValue(String name,android.util.TypedValue out,boolean resolveRefs) { getValue(getIdentifier(name,"string",null),out,resolveRefs); }
    public synchronized void getValueForDensity(int id,int density,android.util.TypedValue out,boolean resolveRefs) {
        if (out == null) throw new NullPointerException("typed value"); requireOpen();
        if (density < 0 || density > 65535) throw new IllegalArgumentException("Resource density");
        resourceValue(id,density,resolveRefs,false,out);
    }
    private android.util.TypedValue scalar(int id,int type) {
        android.util.TypedValue value=new android.util.TypedValue(); getValue(id,value,true);
        if (value.type != type) throw new NotFoundException("Resource 0x"+Integer.toHexString(id)+" has incompatible type "+value.type);
        return value;
    }
    public synchronized float getDimension(int id) { return scalar(id,5).getDimension(metrics); }
    public synchronized int getDimensionPixelOffset(int id) { return android.util.TypedValue.complexToDimensionPixelOffset(scalar(id,5).data,metrics); }
    public synchronized int getDimensionPixelSize(int id) { return android.util.TypedValue.complexToDimensionPixelSize(scalar(id,5).data,metrics); }
    public float getFraction(int id,int base,int parentBase) { return scalar(id,6).getFraction(base,parentBase); }
    public int getInteger(int id) {
        android.util.TypedValue value=new android.util.TypedValue(); getValue(id,value,true);
        if (value.type < 16 || value.type > 31) throw new NotFoundException("Resource is not an integer"); return value.data;
    }
    public synchronized CharSequence[] getTextArray(int id) { requireOpen(); return nativeTextArray(handle,id); }
    public synchronized String[] getStringArray(int id) { requireOpen(); return nativeTextArray(handle,id); }
    public synchronized int[] getIntArray(int id) { requireOpen(); return nativeIntArray(handle,id); }
    public synchronized TypedArray obtainTypedArray(int id) {
        requireOpen();
        if (id == 0) throw new NotFoundException("Resource ID #0x0");
        Object[] bag = nativeTypedArray(handle, id);
        int[] fields = (int[]) bag[0];
        String[] strings = (String[]) bag[1];
        int count = strings.length;
        android.util.TypedValue[] values = new android.util.TypedValue[count];
        int[] entry = new int[6];
        for (int i = 0; i < count; i++) {
            System.arraycopy(fields, i * 6, entry, 0, 6);
            android.util.TypedValue value = new android.util.TypedValue();
            assignValue(value, entry, strings[i]);
            values[i] = value;
        }
        return new TypedArray(this, null, values, "<array>");
    }
    private static native String[] nativeTextArray(long handle,int id);
    private static native int[] nativeIntArray(long handle,int id);
    private static native Object[] nativeTypedArray(long handle,int id);
    public synchronized CharSequence getQuantityText(int id, int quantity) {
        requireOpen(); Object[] bag = nativePlural(handle, id);
        int[] keys = (int[]) bag[0]; String[] values = (String[]) bag[1];
        String selected = textFor(keys, values, quantityAttribute(shiraz.runtime.CardinalPlural.select(configuration.locale, quantity)));
        if (selected == null) selected = textFor(keys, values, 0x01000004);
        if (selected == null) throw new NotFoundException("Plural resource 0x" + Integer.toHexString(id) + " quantity=" + quantity);
        return selected;
    }
    public synchronized String getQuantityString(int id, int quantity) { return getQuantityText(id, quantity).toString(); }
    public synchronized String getQuantityString(int id, int quantity, Object... formatArgs) {
        return String.format(configuration.locale, getQuantityString(id, quantity), formatArgs);
    }
    private static String textFor(int[] keys, String[] values, int attribute) {
        for (int i = 0; i < keys.length; i++) if (keys[i] == attribute && values[i] != null) return values[i];
        return null;
    }
    private static int quantityAttribute(String category) {
        if ("zero".equals(category)) return 0x01000005;
        if ("one".equals(category)) return 0x01000006;
        if ("two".equals(category)) return 0x01000007;
        if ("few".equals(category)) return 0x01000008;
        if ("many".equals(category)) return 0x01000009;
        return 0x01000004;
    }
    private static native Object[] nativePlural(long handle, int id);
    public boolean getBoolean(int id) { return getInteger(id) != 0; }
    public float getFloat(int id) {
        android.util.TypedValue value=new android.util.TypedValue(); getValue(id,value,true);
        if (value.type == 4) return value.getFloat();
        if (value.type >= 16 && value.type <= 31) return value.data;
        throw new NotFoundException("Resource is not numeric");
    }
    synchronized byte[] shirazReadNonAsset(int cookie,String path) {
        requireOpen();return nativeResourceFile(handle,cookie,path.getBytes(StandardCharsets.UTF_8));
    }
    private byte[] fileBytes(android.util.TypedValue value) {
        requireOpen(); if (value.type != 3 || value.string == null) throw new NotFoundException("Resource is not a packaged file");
        return nativeResourceFile(handle,value.assetCookie,value.string.toString().getBytes(StandardCharsets.UTF_8));
    }
    public synchronized byte[] shirazReadResourceFile(int id,android.util.TypedValue out) {
        if (out == null) throw new NullPointerException("typed value"); android.util.TypedValue value=new android.util.TypedValue(); getValue(id,value,true);
        byte[] bytes=fileBytes(value); out.setTo(value); return bytes;
    }
    private final java.util.LinkedHashMap<Integer,shiraz.runtime.CompiledXmlParser> xmlCache=new java.util.LinkedHashMap<Integer,shiraz.runtime.CompiledXmlParser>(16,.75f,true);
    private int xmlBytes;
    private void clearXmlCache(){xmlCache.clear();xmlBytes=0;}
    public synchronized XmlResourceParser getXml(int id) {
        requireOpen();shiraz.runtime.CompiledXmlParser parsed=xmlCache.get(id);if(parsed!=null)return parsed.shirazFork();
        byte[] bytes=shirazReadResourceFile(id,new android.util.TypedValue());
        try{parsed=new shiraz.runtime.CompiledXmlParser(bytes,id);if(bytes.length<=65536){xmlCache.put(id,parsed);xmlBytes+=bytes.length;while(xmlCache.size()>32||xmlBytes>262144){Integer oldest=xmlCache.keySet().iterator().next();xmlBytes-=xmlCache.remove(oldest).shirazEncodedSize();}}return parsed.shirazFork();}
        catch(RuntimeException failure){throw new NotFoundException("Cannot parse XML resource #"+Integer.toHexString(id),failure);}
    }
    public android.graphics.Typeface getFont(int id) {
        android.util.TypedValue value=new android.util.TypedValue();getValue(id,value,true);
        if(value.type!=3||value.string==null)throw new NotFoundException("Resource is not a font");
        String path=value.string.toString();int weight=400,italic=0,ttc=0;
        if(path.endsWith(".xml")) {
            XmlResourceParser parser=getXml(id);int best=Integer.MAX_VALUE;String selected=null;int cookie=0;
            try {
                int event;
                while((event=parser.next())!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if(event!=org.xmlpull.v1.XmlPullParser.START_TAG)continue;
                    if("font-family".equals(parser.getName())) {
                        for(int i=0;i<parser.getAttributeCount();i++)if(parser.getAttributeName(i).startsWith("fontProvider"))throw new UnsupportedOperationException("Provider fonts are not supported");
                    }
                    if(!"font".equals(parser.getName()))continue;
                    int resource=0,w=400,it=0,index=0;
                    for(int i=0;i<parser.getAttributeCount();i++) {
                        String name=parser.getAttributeName(i);
                        if("font".equals(name))resource=parser.getAttributeResourceValue(i,0);
                        else if("fontWeight".equals(name))w=parser.getAttributeIntValue(i,400);
                        else if("fontStyle".equals(name))it=parser.getAttributeIntValue(i,0);
                        else if("ttcIndex".equals(name))index=parser.getAttributeIntValue(i,0);
                        else if("fontVariationSettings".equals(name))throw new UnsupportedOperationException("Variable resource fonts are not supported");
                    }
                    int score=Math.abs(w-400)*2+(it==0?0:1);
                    if(resource!=0&&score<best){android.util.TypedValue item=new android.util.TypedValue();getValue(resource,item,true);if(item.string==null)throw new NotFoundException("Invalid font entry");selected=item.string.toString();cookie=item.assetCookie;weight=w;italic=it;ttc=index;best=score;}
                }
            } catch(java.io.IOException|org.xmlpull.v1.XmlPullParserException error){throw new NotFoundException("Cannot read font family",error);} finally {parser.close();}
            if(selected==null)throw new NotFoundException("Empty font family");path=selected;value.assetCookie=cookie;
        }
        android.graphics.FontFamily family=new android.graphics.FontFamily();
        if(!family.addFontFromAssetManager(getAssets(),path,value.assetCookie,false,ttc,weight,italic,null)||!family.freeze())throw new NotFoundException("Cannot load font resource #"+Integer.toHexString(id));
        return android.graphics.Typeface.createFromFamiliesWithDefault(new android.graphics.FontFamily[]{family},weight,italic);
    }
    public XmlResourceParser getLayout(int id) { return getXml(id); }
    public XmlResourceParser getAnimation(int id) { return getXml(id); }
    public synchronized AssetFileDescriptor openRawResourceFd(int id) throws NotFoundException {
        requireOpen();android.util.TypedValue value=new android.util.TypedValue();getValue(id,value,true);
        if(value.type!=3||value.string==null)throw new NotFoundException("Resource is not a packaged file");
        long[] region=nativeResourceFd(handle,value.assetCookie,value.string.toString().getBytes(StandardCharsets.UTF_8));
        if(region==null)return null;
        int raw=(int)region[0];android.os.ParcelFileDescriptor descriptor=null;
        try{descriptor=android.os.ParcelFileDescriptor.adoptFd(raw);raw=-1;return new AssetFileDescriptor(descriptor,region[1],region[2]);}
        catch(Throwable failure){if(descriptor!=null)try{descriptor.close();}catch(java.io.IOException close){failure.addSuppressed(close);}throw failure;}
        finally{if(raw>=0)shiraz.runtime.NativeFd.closeRawQuietly(raw);}
    }
    private static native long[] nativeResourceFd(long handle,int cookie,byte[] path);
    public java.io.InputStream openRawResource(int id) { return openRawResource(id,new android.util.TypedValue()); }
    public java.io.InputStream openRawResource(int id,android.util.TypedValue out) { return new java.io.ByteArrayInputStream(shirazReadResourceFile(id,out)); }
    public android.graphics.drawable.Drawable getDrawable(int id) { return getDrawable(id,null); }
    private final java.util.ArrayList<Integer> drawableStack=new java.util.ArrayList<Integer>();
    private int drawableNodes; private long drawableBytes,drawablePixels;
    public synchronized void shirazClaimDrawableNode() { if(drawableStack.isEmpty()) throw new IllegalStateException("No drawable inflation"); if(++drawableNodes>1024) throw new IllegalArgumentException("Drawable inflation node budget"); }
    private byte[] drawableFile(android.util.TypedValue value) { byte[] bytes=fileBytes(value); drawableBytes+=bytes.length; if(drawableBytes>32L*1024*1024) throw new IllegalArgumentException("Drawable inflation byte budget"); return bytes; }
    public synchronized android.graphics.drawable.Drawable getDrawable(int id,Theme theme) {
        if(drawableStack.size()>=32 || drawableStack.contains(id)) throw new NotFoundException("Drawable reference cycle/depth");
        if(drawableStack.isEmpty()) {drawableNodes=0;drawableBytes=drawablePixels=0;}
        drawableStack.add(id);
        try { shirazClaimDrawableNode(); return loadDrawable(id,theme); } finally { drawableStack.remove(drawableStack.size()-1); }
    }
    private android.graphics.drawable.Drawable loadDrawable(int id,Theme theme) {
        if (theme != null && theme.getResources() != this) throw new UnsupportedOperationException("Cross-manager drawable themes are not implemented");
        android.util.TypedValue value=new android.util.TypedValue(); getValue(id,value,true); int flags=value.changingConfigurations;
        if (value.type == 2 && (theme == null || !theme.resolveAttribute(value.data,value,true))) throw new NotFoundException("Drawable requires a theme");
        flags |= value.changingConfigurations; android.graphics.drawable.Drawable drawable;
        if (value.type >= 28 && value.type <= 31) drawable=new android.graphics.drawable.ColorDrawable(value.data);
        else if (value.type == 3 && value.string != null) {
            String filename=value.string.toString();
            if (filename.endsWith(".xml")) drawable=shiraz.runtime.DrawableXml.inflate(this,theme,drawableFile(value));
            else {
                if (!filename.endsWith(".png")&&!filename.endsWith(".webp")&&!filename.endsWith(".jpg")&&!filename.endsWith(".jpeg")) throw new UnsupportedOperationException("Only PNG, JPEG, static WebP and supported XML file drawables are implemented");
                android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeResourceBytes(this,value,drawableFile(value),null);
                if (bitmap == null) throw new NotFoundException("Cannot decode image drawable");
                drawablePixels+=(long)bitmap.getWidth()*bitmap.getHeight(); if(drawablePixels>16L*1024*1024) {bitmap.recycle();throw new IllegalArgumentException("Drawable inflation pixel budget");}
                drawable=bitmap.getNinePatchChunk()==null?new android.graphics.drawable.BitmapDrawable(this,bitmap):new android.graphics.drawable.NinePatchDrawable(this,bitmap,bitmap.getNinePatchChunk(),null,filename);
            }
        } else throw new NotFoundException("Resource is not a drawable");
        drawable.setChangingConfigurations(drawable.getChangingConfigurations()|flags); return drawable;
    }
    public CharSequence getText(int id) { return getString(id); }
    public CharSequence getText(int id,CharSequence fallback) {
        if(id==0)return fallback;
        try{return getText(id);}catch(NotFoundException missing){return fallback;}
    }
    public synchronized String getString(int id) {
        requireOpen();
        return nativeString(handle, id);
    }
    public synchronized String getString(int id, Object... formatArgs) { requireOpen(); return String.format(configuration.locale, getString(id), formatArgs); }
    public synchronized Configuration getConfiguration() {
        requireOpen(); return new Configuration(configuration);
    }
    public synchronized android.util.DisplayMetrics getDisplayMetrics() {
        requireOpen(); android.util.DisplayMetrics copy = new android.util.DisplayMetrics(); copy.setTo(metrics); return copy;
    }
    public synchronized android.util.DisplayMetrics shirazBaseDisplayMetrics() {
        requireOpen(); android.util.DisplayMetrics copy = new android.util.DisplayMetrics(); copy.setTo(baseMetrics); return copy;
    }
    private static final class DisplayState {
        final Configuration configuration; final android.util.DisplayMetrics physical;
        DisplayState(Configuration c,android.util.DisplayMetrics p){configuration=c;physical=p;}
    }
    private synchronized DisplayState displayState(){
        requireOpen();Configuration c=new Configuration(configuration);
        android.util.DisplayMetrics p=new android.util.DisplayMetrics();p.setTo(baseMetrics);return new DisplayState(c,p);
    }
    /** Host-only resize; applications cannot resize the window through updateConfiguration. */
    public synchronized Configuration shirazPreviewInheritedConfiguration(Configuration rootConfiguration) {
        if(rootConfiguration==null)throw new NullPointerException("configuration");requireOpen();
        Configuration next=parent==null?new Configuration(rootConfiguration):parent.shirazPreviewInheritedConfiguration(rootConfiguration);
        if(parent!=null)next.updateFrom(override);return next;
    }
    public synchronized Configuration shirazPreviewDisplay(int newWidth,int newHeight) {return shirazPreviewDisplay(newWidth,newHeight,metrics.densityDpi);}
    public synchronized Configuration shirazPreviewDisplay(int newWidth,int newHeight,int density) {
        requireOpen();if(parent!=null)throw new IllegalStateException("Only the display owner can resize resources");
        if(newWidth<1||newHeight<1||newWidth>8192||newHeight>8192||(long)newWidth*newHeight>16*1024*1024)throw new IllegalArgumentException("Display geometry");

        Configuration next=new Configuration(configuration);
        if(density<1||density>1280)throw new IllegalArgumentException("Display density");
        next.densityDpi=density;
        int wd=Math.max(1,Math.round(newWidth*160f/density)),hd=Math.max(1,Math.round(newHeight*160f/density));
        if(wd>8192||hd>8192)throw new IllegalArgumentException("Display dp geometry");
        next.screenWidthDp=wd;next.screenHeightDp=hd;next.smallestScreenWidthDp=Math.min(wd,hd);
        next.orientation=newWidth>newHeight?Configuration.ORIENTATION_LANDSCAPE:Configuration.ORIENTATION_PORTRAIT;
        next.screenLayout=displayScreenLayout(next.screenLayout,wd,hd);
        return next;
    }
    private static int displayScreenLayout(int current,int wd,int hd) {
        int longer=Math.max(wd,hd),shorter=Math.min(wd,hd);
        int size=longer<470?1:longer>=960&&shorter>=720?4:longer>=640&&shorter>=480?3:2;
        int aspect=longer>=470&&longer*3/5>=shorter-1?32:16;
        return (current&~63)|size|aspect;
    }
    public synchronized void shirazResizeDisplay(int newWidth,int newHeight) {resizeDisplay(newWidth,newHeight,metrics.densityDpi,false);}
    public synchronized void shirazResizeDisplay(int newWidth,int newHeight,int density) {resizeDisplay(newWidth,newHeight,density,true);}
    private void resizeDisplay(int newWidth,int newHeight,int density,boolean hostDensity) {
        Configuration next=shirazPreviewDisplay(newWidth,newHeight,density);
        if(width==newWidth&&height==newHeight&&metrics.densityDpi==density&&(!hostDensity||baseMetrics.densityDpi==density))return;
        android.util.DisplayMetrics dm=new android.util.DisplayMetrics();dm.setTo(metrics);dm.widthPixels=newWidth;dm.heightPixels=newHeight;
        dm.densityDpi=density;dm.density=density/160f;dm.scaledDensity=dm.density*next.fontScale;dm.xdpi=dm.ydpi=density;
        configure(next,dm);
        width=newWidth;height=newHeight;configuration=next;metrics=dm;
        baseMetrics.widthPixels=newWidth;baseMetrics.heightPixels=newHeight;if(hostDensity)baseMetrics.setTo(dm);
    }
    private void requireOpen() {
        if(handle==0)throw new IllegalStateException("Resources are closed");
        if(parent!=null) {
            DisplayState state=parent.displayState();Configuration inherited=state.configuration;
            if(!inherited.equals(parentConfiguration)||width!=state.physical.widthPixels||height!=state.physical.heightPixels) {
                Configuration next=new Configuration(inherited);next.updateFrom(override);
                android.util.DisplayMetrics dm=new android.util.DisplayMetrics();dm.setTo(metrics);dm.widthPixels=state.physical.widthPixels;dm.heightPixels=state.physical.heightPixels;
                dm.densityDpi=next.densityDpi;dm.density=next.densityDpi/160f;dm.scaledDensity=dm.density*next.fontScale;dm.xdpi=dm.ydpi=next.densityDpi;
                configure(next,dm);width=dm.widthPixels;height=dm.heightPixels;configuration=next;metrics=dm;parentConfiguration=inherited;baseMetrics.setTo(state.physical);
            }
        }
    }
    public synchronized void updateConfiguration(Configuration changes, android.util.DisplayMetrics display) {
        requireOpen();
        Configuration next = new Configuration(configuration);
        if (changes != null) {
            Configuration c = new Configuration(changes);
            if (c.getLocales().size() > 1) throw new UnsupportedOperationException("Multiple resource fallback locales are not implemented");
            if (!Float.isFinite(c.fontScale) || c.fontScale < 0 || (c.screenLayout & ~255) != 0)
                throw new IllegalArgumentException("Invalid resource configuration override");
            next.updateFrom(c);
        }
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics(); dm.setTo(metrics);
        if (display != null) {
            dm.setTo(display);
            if (dm.widthPixels != width || dm.heightPixels != height)
                throw new UnsupportedOperationException("Resource updates cannot resize the host window");
            if (changes == null || changes.densityDpi == 0) next.densityDpi = dm.densityDpi;
        }
        if (!Float.isFinite(next.fontScale) || next.fontScale <= 0 || next.fontScale > 10 || next.densityDpi < 1 || next.densityDpi > 1280
                || next.screenWidthDp < 1 || next.screenWidthDp > 8192 || next.screenHeightDp < 1 || next.screenHeightDp > 8192
                || next.smallestScreenWidthDp < 1 || next.smallestScreenWidthDp > 8192 || next.orientation < 1 || next.orientation > 2
                || (next.screenLayout & ~255) != 0 || (next.uiMode & ~63) != 0)
            throw new IllegalArgumentException("Invalid resource configuration");
        dm.densityDpi = next.densityDpi; dm.density = next.densityDpi / 160f; dm.scaledDensity = dm.density * next.fontScale;
        dm.xdpi = dm.ydpi = next.densityDpi;
        configure(next, dm); configuration = next; metrics = dm;
        if(parent!=null) {
            if(changes!=null)override.updateFrom(changes);
            if(display!=null&&(changes==null||changes.densityDpi==0))override.densityDpi=display.densityDpi;
        }
    }
    private long themeGeneration;
    private void configure(Configuration c, android.util.DisplayMetrics dm) {
        nativeConfigure(handle, c.locale.toLanguageTag(), new int[]{dm.widthPixels, dm.heightPixels, dm.densityDpi, c.screenWidthDp,
            c.screenHeightDp, c.smallestScreenWidthDp, c.orientation, c.screenLayout, c.uiMode});
        themeGeneration++;valueCache.clear();styleCache.clear();clearXmlCache();
    }
    public synchronized Theme newTheme() { requireOpen(); return new Theme(); }
    public int getColor(int id) { return getColor(id, null); }
    public int getColor(int id, Theme theme) { return loadColors(id, theme, true).getDefaultColor(); }
    public ColorStateList getColorStateList(int id) { return getColorStateList(id, null); }
    public ColorStateList getColorStateList(int id, Theme theme) { return loadColors(id, theme, false); }
    private synchronized ColorStateList loadColors(int id, Theme theme, boolean integers) {
        requireOpen();
        if (theme != null && theme.getResources() != this) throw new UnsupportedOperationException("Cross-manager color themes are not implemented");
        int[] packed = nativeColorList(handle, id, theme == null ? null : theme.sequence(), integers);
        int count = packed[0], offset = 1; int[] colors = new int[count]; int[][] states = new int[count][];
        int[][] deferred = new int[count][]; boolean hasDeferred = false;
        for (int i = 0; i < count; ++i) {
            colors[i] = packed[offset++];
            int colorAttr = packed[offset++], alphaAttr = packed[offset++], base = packed[offset++], alpha = packed[offset++];
            if (colorAttr != 0 || alphaAttr != 0) { deferred[i] = new int[]{colorAttr, alphaAttr, base, alpha}; hasDeferred = true; }
            int size = packed[offset++]; states[i] = new int[size];
            System.arraycopy(packed, offset, states[i], 0, size); offset += size;
        }
        return new ColorStateList(states, colors, hasDeferred ? deferred : null);
    }
    public TypedArray obtainAttributes(android.util.AttributeSet set,int[] attrs){return styledAttributes(null,set,attrs,0,0);}
    private boolean styleValue(int style,int attr,android.util.TypedValue out) {
        long key=((long)style<<32)|(attr&0xffffffffL);android.util.TypedValue found=styleCache.get(key);
        if(found!=null){out.setTo(found);return true;}if(styleCache.containsKey(key))return false;
        int[] fields=new int[6];String[] text=new String[1];boolean present=nativeStyleValue(handle,style,attr,fields,text);
        if(present)assignValue(out,fields,text[0]);
        if(text[0]==null||text[0].length()<=1024){android.util.TypedValue copy=null;if(present){copy=new android.util.TypedValue();copy.setTo(out);}styleCache.put(key,copy);if(styleCache.size()>1024)styleCache.remove(styleCache.keySet().iterator().next());}return present;
    }
    private static native boolean nativeStyleValue(long handle,int style,int attr,int[] out,String[] text);
    private static final class StyleQuery {
        final Object source;final int position,defAttr,defRes;final int[] attrs;
        StyleQuery(shiraz.runtime.CompiledXmlParser parser,int[] attrs,int defAttr,int defRes){source=parser.shirazSourceKey();position=parser.shirazPosition();this.defAttr=defAttr;this.defRes=defRes;this.attrs=attrs.clone();}
        public int hashCode(){return System.identityHashCode(source)*31+position*17+defAttr+defRes+java.util.Arrays.hashCode(attrs);}
        public boolean equals(Object other){if(!(other instanceof StyleQuery))return false;StyleQuery q=(StyleQuery)other;return source==q.source&&position==q.position&&defAttr==q.defAttr&&defRes==q.defRes&&java.util.Arrays.equals(attrs,q.attrs);}
    }
    private TypedArray styledAttributes(Theme base,android.util.AttributeSet set,int[] attrs,int defAttr,int defRes) {
        if(attrs==null)throw new NullPointerException("attrs");
        if(attrs.length>4096)throw new IllegalArgumentException("Attribute query limit");
        if(set!=null&&!(set instanceof shiraz.runtime.CompiledXmlParser))throw new UnsupportedOperationException("Only compiled XML attributes are implemented");
        synchronized(this) {
            requireOpen();
            StyleQuery query=null;
            if(base!=null&&set instanceof shiraz.runtime.CompiledXmlParser&&attrs.length>=16&&attrs.length<=128){
                if(base.generation!=themeGeneration)base.invalidate();query=new StyleQuery((shiraz.runtime.CompiledXmlParser)set,attrs,defAttr,defRes);
                android.util.TypedValue[] cached=base.styled.get(query);if(cached!=null)return new TypedArray(this,base,cached,set.getPositionDescription());
            }
            android.util.TypedValue selected=new android.util.TypedValue();
            if(base!=null&&defAttr!=0&&base.resolveAttribute(defAttr,selected,false)&&selected.type==1)defRes=selected.data;
            int xmlStyle=0;
            shiraz.runtime.BinaryXml.Node node=set==null?null:((shiraz.runtime.CompiledXmlParser)set).shirazNode();
            if(set!=null&&(node==null||set.getAttributeCount()<0))throw new IllegalStateException("Attributes require an XML start tag");
            if(base!=null&&node!=null&&node.styleIndex>=0) {
                shiraz.runtime.BinaryXml.Value style=node.attributes.get(node.styleIndex).value;
                int id=style.data;
                if(style.type==2){if(!base.resolveAttribute(id,selected,false)||selected.type!=1)throw new NotFoundException("XML style attribute");id=selected.data;}
                else if(style.type!=1)throw new IllegalArgumentException("XML style type");
                if(id!=0)nativeValidateStyle(handle,id);xmlStyle=id;
            }
            android.util.TypedValue[] values=new android.util.TypedValue[attrs.length];
            for(int i=0;i<attrs.length;i++) {
                android.util.TypedValue value=new android.util.TypedValue();boolean found=false;
                if(node!=null)for(shiraz.runtime.BinaryXml.Attribute attribute:node.attributes)if(attribute.resourceId==attrs[i]&&attrs[i]!=0) {
                    value.type=attribute.value.type;value.data=attribute.value.data;value.string=attribute.value.text;value.assetCookie=-1;found=true;break;
                }
                if(!found&&xmlStyle!=0)found=styleValue(xmlStyle,attrs[i],value);
                if(!found&&defRes!=0)found=styleValue(defRes,attrs[i],value);
                if(!found&&base!=null)found=base.resolveAttribute(attrs[i],value,false);
                if(!found)continue;
                int flags=value.changingConfigurations;
                for(int depth=0;value.type==1||value.type==2;depth++) {
                    if(depth>=32)throw new NotFoundException("Attribute reference cycle/depth");
                    if(value.type==1){if(value.data==0){value.type=0;value.resourceId=0;break;}resourceValue(value.data,0,true,true,value);if(value.type==1)break;}
                    else {if(base==null)break;int id=value.data;if(!base.resolveAttribute(id,value,false))break;}
                    flags|=value.changingConfigurations;
                }
                value.changingConfigurations|=flags;values[i]=value;
            }
            if(query!=null){int chars=0;for(android.util.TypedValue value:values)if(value!=null&&value.string!=null)chars+=value.string.length();if(chars<=2048){if(base.styled.size()>=128)base.styled.clear();base.styled.put(query,values);}}
            return new TypedArray(this,base,values,set==null?"<theme>":set.getPositionDescription());
        }
    }
    public final class Theme {
        public android.graphics.drawable.Drawable getDrawable(int id) { return Resources.this.getDrawable(id,this); }
        private final java.util.ArrayList<Integer> styles = new java.util.ArrayList<Integer>();
        private int[] cachedSequence;
        private long generation=-1;
        private final java.util.HashMap<StyleQuery,android.util.TypedValue[]> styled=new java.util.HashMap<StyleQuery,android.util.TypedValue[]>();
        private final java.util.HashMap<Long,Resolved> resolved=new java.util.HashMap<Long,Resolved>();
        private final class Resolved {
            final int[] fields;final String text;
            Resolved(int[] fields,String text){this.fields=fields;this.text=text;}
        }
        private void invalidate(){cachedSequence=null;resolved.clear();styled.clear();generation=themeGeneration;}
        public TypedArray obtainStyledAttributes(int[] attrs){return styledAttributes(this,null,attrs,0,0);}
        public TypedArray obtainStyledAttributes(int style,int[] attrs){return styledAttributes(this,null,attrs,0,style);}
        public TypedArray obtainStyledAttributes(android.util.AttributeSet set,int[] attrs,int defStyleAttr,int defStyleRes){return styledAttributes(this,set,attrs,defStyleAttr,defStyleRes);}
        private Theme() { }
        private int[] sequence() {
            if(cachedSequence!=null)return cachedSequence;
            int[] sequence = new int[styles.size()];
            for (int i = 0; i < sequence.length; ++i) sequence[i] = styles.get(i);
            cachedSequence=sequence;return sequence;
        }
        public Resources getResources() { return Resources.this; }
        public void applyStyle(int id, boolean force) {
            synchronized (Resources.this) {
                requireOpen();
                if (id == 0) return;
                if (styles.size() >= 2048) throw new IllegalStateException("Too many theme styles");
                nativeValidateStyle(handle, id);
                styles.add(id); styles.add(force ? 1 : 0);
                invalidate();
            }
        }
        public void setTo(Theme other) {
            if (other == null) throw new NullPointerException("theme");
            if (!apkPath.equals(other.getResources().apkPath) || processSystem!=other.getResources().processSystem)
                throw new UnsupportedOperationException("Theme copying between different APKs is not implemented");
            int[] copied;
            synchronized(other.getResources()){other.getResources().requireOpen();copied=other.sequence();}
            synchronized(Resources.this){requireOpen();styles.clear();for(int value:copied)styles.add(value);invalidate();}
        }

        public boolean resolveAttribute(int id, android.util.TypedValue out, boolean resolveRefs) {
            if (out == null) throw new NullPointerException("typed value");
            synchronized (Resources.this) {
                requireOpen();if(generation!=themeGeneration)invalidate();
                long key=((long)id<<1)|(resolveRefs?1:0);
                Resolved found=resolved.get(key);
                if(found!=null){assignValue(out,found.fields,found.text);return true;}if(resolved.containsKey(key))return false;
                int[] sequence = sequence();
                int[] value = new int[6]; String[] text = new String[1];
                if (!nativeThemeResolve(handle, sequence, id, resolveRefs, value, text)){if(resolved.size()>=256)resolved.clear();resolved.put(key,null);return false;}
                if(resolved.size()>=256)resolved.clear();
                resolved.put(key,new Resolved(value,text[0]));
                assignValue(out,value,text[0]);
                return true;
            }
        }
        // Native style snapshots are invalidated whenever resource configuration changes.
        public void rebase() {
            synchronized (Resources.this) { requireOpen();invalidate(); for (int i = 0; i < styles.size(); i += 2) nativeValidateStyle(handle, styles.get(i)); }
        }
    }
    private synchronized String resourceNamePart(int id,int part){requireOpen();return nativeResourceName(handle,id,part);}
    public String getResourcePackageName(int id){return resourceNamePart(id,0);}
    public String getResourceTypeName(int id){return resourceNamePart(id,1);}
    public String getResourceEntryName(int id){return resourceNamePart(id,2);}
    public synchronized String getResourceName(int id){return getResourcePackageName(id)+":"+getResourceTypeName(id)+"/"+getResourceEntryName(id);}
    private static native String nativeResourceName(long handle,int id,int part);
    public synchronized int getIdentifier(String name, String defType, String defPackage) {
        if (name == null) throw new NullPointerException("Resource name");
        if (handle == 0) throw new IllegalStateException("Resources are closed");
        // Most callers pass symbolic names. Avoid allocating an exception and its
        // stack trace for each one, retaining parseInt semantics for numeric input.
        if (!name.isEmpty() && (name.charAt(0) == '+' || name.charAt(0) == '-'
                || Character.digit(name.charAt(0), 10) >= 0)) {
            try { return Integer.parseInt(name); } catch (NumberFormatException ignored) { }
        }
        return nativeIdentifier(handle, name.getBytes(StandardCharsets.UTF_8),
            (defType == null ? "" : defType).getBytes(StandardCharsets.UTF_8),
            (defPackage == null ? "" : defPackage).getBytes(StandardCharsets.UTF_8));
    }
    public static class NotFoundException extends RuntimeException {
        public NotFoundException() { }
        public NotFoundException(String message) { super(message); }
        public NotFoundException(String message, Exception cause) { super(message, cause); }
    }
    private static String coerce(int type, int data) { return shirazCoerce(type, data); }
    public static String shirazCoerce(int type, int data) {
        if (type == 1) return "@" + data;
        if (type == 2) return "?" + data;
        if (type == 4) return Float.toString(Float.intBitsToFloat(data));
        if (type == 5 || type == 6) {
            float value = android.util.TypedValue.complexToFloat(data);
            int unit = data & 15;
            String[] units = type == 5 ? new String[]{"px", "dip", "sp", "pt", "in", "mm"} : new String[]{"%", "%p"};
            if (unit >= units.length) throw new NotFoundException("Invalid complex resource unit");
            return Float.toString(type == 6 ? value * 100 : value) + units[unit];
        }
        if (type == 0x11) return "0x" + Integer.toHexString(data);
        if (type == 0x12) return data == 0 ? "false" : "true";
        if (type >= 0x1c && type <= 0x1f) return "#" + Integer.toHexString(data);
        if (type >= 0x10 && type <= 0x1f) return Integer.toString(data);
        throw new NotFoundException("Resource cannot be represented as text");
    }
    private static native int[] nativeSizeConfigurations(long handle);
    private static native long nativeOpenSystem(byte[] path,String locale,int width,int height);
    private static native long nativeOpen(byte[] path, byte[] framework, String locale, int width, int height);
    private static native void nativeClose(long handle);
    private static native byte[] nativeResourceFile(long handle,int cookie,byte[] path);
    private static native void nativeValue(long handle,int id,int density,boolean resolveRefs,boolean allowBag,int[] fields,String[] text);
    private static native String nativeString(long handle, int id);
    private static native int nativeIdentifier(long handle, byte[] name, byte[] type, byte[] packageName);
    private static native void nativeConfigure(long handle, String locale, int[] configuration);
    private static native void nativeValidateStyle(long handle, int id);
    private static native boolean nativeThemeResolve(long handle, int[] styles, int id, boolean resolveRefs, int[] out, String[] text);
    private static native int[] nativeColorList(long handle, int id, int[] styles, boolean integers);
    private static native long[] nativeAssetStream(long handle, byte[] path) throws java.io.IOException;
    private static native long[] nativeAssetFd(long handle, byte[] path) throws java.io.IOException;
}
