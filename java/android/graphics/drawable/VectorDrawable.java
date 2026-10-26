// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.graphics.*;
import android.content.res.*;
import android.util.*;
import shiraz.runtime.BinaryXml;
import shiraz.runtime.PathData;
import java.util.*;

/** Named path/group vectors with device-resolution raster caching. Unsupported vector features reject. */
public class VectorDrawable extends Drawable {
    private static final class Group {String name;Group parent;float px,py,sx=1,sy=1,tx,ty,rotation;}
    private static final class Entry {boolean clip;ArrayList<Entry> clips=new ArrayList<Entry>();String name;Group parent;float trimStart,trimEnd=1,trimOffset;PathMeasure measure;
        Path path; VectorGradient fillGradient,strokeGradient; ColorStateList fill,stroke; float fillAlpha,strokeAlpha,width,miter; int cap,join;
    }
    private static final class Model {
        String name;ArrayList<Group> groups=new ArrayList<Group>();byte[] xml; ArrayList<Entry> entries=new ArrayList<Entry>(); int width,height,flags; float viewportW,viewportH,alpha=1;
        boolean mirrored,themed; ColorStateList tint; PorterDuff.Mode tintMode=PorterDuff.Mode.SRC_IN;
        DisplayMetrics metrics;
    }
    private Model model;
    private final HashMap<String,Target> targets=new HashMap<String,Target>();
    private static Model copy(Model source){
        Model out=new Model();out.name=source.name;out.xml=source.xml;out.width=source.width;out.height=source.height;out.flags=source.flags;out.viewportW=source.viewportW;out.viewportH=source.viewportH;out.alpha=source.alpha;out.mirrored=source.mirrored;out.themed=source.themed;out.tint=source.tint;out.tintMode=source.tintMode;out.metrics=source.metrics;
        IdentityHashMap<Group,Group> groups=new IdentityHashMap<Group,Group>();
        for(Group old:source.groups){Group g=new Group();g.name=old.name;g.parent=groups.get(old.parent);g.px=old.px;g.py=old.py;g.sx=old.sx;g.sy=old.sy;g.tx=old.tx;g.ty=old.ty;g.rotation=old.rotation;groups.put(old,g);out.groups.add(g);}
        IdentityHashMap<Entry,Entry> entries=new IdentityHashMap<Entry,Entry>();
        for(Entry old:source.entries){Entry e=new Entry();e.clip=old.clip;for(Entry clip:old.clips)e.clips.add(entries.get(clip));entries.put(old,e);e.name=old.name;e.parent=groups.get(old.parent);e.path=new Path(old.path);e.fill=old.fill;e.stroke=old.stroke;e.fillGradient=old.fillGradient;e.strokeGradient=old.strokeGradient;e.fillAlpha=old.fillAlpha;e.strokeAlpha=old.strokeAlpha;e.width=old.width;e.miter=old.miter;e.cap=old.cap;e.join=old.join;e.trimStart=old.trimStart;e.trimEnd=old.trimEnd;e.trimOffset=old.trimOffset;out.entries.add(e);}return out;
    }
    private void named(String name,Target target){if(name==null)return;if(name.isEmpty()||name.length()>512||targets.put(name,target)!=null)throw new IllegalArgumentException("Duplicate or invalid vector target name");}
    public Object shirazTarget(String name){Target target=targets.get(name);if(target==null)throw new IllegalArgumentException("Unknown vector target "+name);return target;}
    public final class Target {
        private final Group group;private final Entry entry;Target(Group g,Entry e){group=g;entry=e;}
        private void change(float v){if(!Float.isFinite(v)||Math.abs(v)>10000000)throw new IllegalArgumentException("Vector target number");}
        private void changed(){revision++;invalidateSelf();}
        private Group group(){if(group==null)throw new UnsupportedOperationException("Target is not a vector group");return group;}
        private Entry path(){if(entry==null||entry.clip)throw new UnsupportedOperationException("Target is not a painted vector path");return entry;}
        public void shirazMorphPath(shiraz.runtime.PathData.Morph.Evaluator evaluator,float fraction){if(entry==null)throw new IllegalArgumentException("Path target required");evaluator.apply(entry.path,fraction);entry.measure=null;changed();}
        public float getRotation(){return group().rotation;}public void setRotation(float v){Group g=group();change(v);g.rotation=v;changed();}
        public float getPivotX(){return group().px;}public void setPivotX(float v){Group g=group();change(v);g.px=v;changed();}
        public float getPivotY(){return group().py;}public void setPivotY(float v){Group g=group();change(v);g.py=v;changed();}
        public float getScaleX(){return group().sx;}public void setScaleX(float v){Group g=group();change(v);g.sx=v;changed();}
        public float getScaleY(){return group().sy;}public void setScaleY(float v){Group g=group();change(v);g.sy=v;changed();}
        public float getTranslateX(){return group().tx;}public void setTranslateX(float v){Group g=group();change(v);g.tx=v;changed();}
        public float getTranslateY(){return group().ty;}public void setTranslateY(float v){Group g=group();change(v);g.ty=v;changed();}
        public float getTrimPathStart(){return path().trimStart;}public void setTrimPathStart(float v){Entry e=path();change(v);e.trimStart=v;changed();}
        public float getTrimPathEnd(){return path().trimEnd;}public void setTrimPathEnd(float v){Entry e=path();change(v);e.trimEnd=v;changed();}
        public float getTrimPathOffset(){return path().trimOffset;}public void setTrimPathOffset(float v){Entry e=path();change(v);e.trimOffset=v;changed();}
        public float getFillAlpha(){return path().fillAlpha;}public void setFillAlpha(float v){if(v<0||v>1)throw new IllegalArgumentException("Alpha");Entry e=path();change(v);e.fillAlpha=v;changed();}
        public float getStrokeAlpha(){return path().strokeAlpha;}public void setStrokeAlpha(float v){if(v<0||v>1)throw new IllegalArgumentException("Alpha");Entry e=path();change(v);e.strokeAlpha=v;changed();}
        public float getStrokeWidth(){return path().width;}public void setStrokeWidth(float v){if(v<0)throw new IllegalArgumentException("Stroke width");Entry e=path();change(v);e.width=v;changed();}
        public int getFillColor(){return path().fill.getDefaultColor();}public void setFillColor(int v){path().fill=ColorStateList.valueOf(v);path().fillGradient=null;revision++;invalidateSelf();}
        public int getStrokeColor(){return path().stroke.getDefaultColor();}public void setStrokeColor(int v){path().stroke=ColorStateList.valueOf(v);path().strokeGradient=null;revision++;invalidateSelf();}
        public float getAlpha(){if(group!=null||entry!=null)throw new UnsupportedOperationException("Root alpha target required");return model.alpha;}public void setAlpha(float v){getAlpha();if(v<0||v>1)throw new IllegalArgumentException("Alpha");change(v);model.alpha=v;changed();}
    }
    private static float[] matrix(Group group){if(group==null)return new float[]{1,0,0,1,0,0};double angle=Math.toRadians(group.rotation);float a=(float)Math.cos(angle)*group.sx,b=(float)Math.sin(angle)*group.sx,c=-(float)Math.sin(angle)*group.sy,d=(float)Math.cos(angle)*group.sy;return multiply(matrix(group.parent),new float[]{a,b,c,d,group.tx+group.px-a*group.px-c*group.py,group.ty+group.py-b*group.px-d*group.py});}
    private static Path trimmed(Entry entry){if(entry.trimStart==0&&entry.trimEnd==1)return entry.path;if(entry.measure==null)entry.measure=new PathMeasure(entry.path,false);float length=entry.measure.getLength(),start=((entry.trimStart+entry.trimOffset)%1+1)%1*length,end=((entry.trimEnd+entry.trimOffset)%1+1)%1*length;Path path=new Path();path.setFillType(entry.path.getFillType());if(start>end){entry.measure.getSegment(start,length,path,true);entry.measure.getSegment(0,end,path,true);}else entry.measure.getSegment(start,end,path,true);return path;}

    private int width,height,alpha=255,revision;
    private boolean mirrored;
    private ColorStateList tint;
    private PorterDuff.Mode tintMode=PorterDuff.Mode.SRC_IN;
    private ColorFilter filter;
    private Bitmap cache;
    private int cacheRevision=-1;
    private final Paint presentation=new Paint(Paint.FILTER_BITMAP_FLAG);
    public VectorDrawable() { }
    private VectorDrawable(Model model,Resources resources) { install(model,resources); }
    private void install(Model value,Resources resources) {
        model=copy(value);targets.clear();named(model.name,new Target(null,null));for(Group g:model.groups)named(g.name,new Target(g,null));for(Entry e:model.entries)named(e.name,new Target(null,e)); DisplayMetrics dm=resources == null ? model.metrics : resources.getDisplayMetrics();
        width=Math.max(1,Math.round(TypedValue.complexToDimension(model.width,dm))); height=Math.max(1,Math.round(TypedValue.complexToDimension(model.height,dm)));
        if (width > 8192 || height > 8192) throw new IllegalArgumentException("Vector intrinsic dimensions");
        mirrored=model.mirrored; tint=model.tint; tintMode=model.tintMode; revision++; invalidateSelf();
    }
    public static VectorDrawable shirazInflate(Resources resources,Resources.Theme theme,byte[] xml) { return new VectorDrawable(new Loader(resources,theme,xml).load(),resources); }
    public int getChangingConfigurations() { return super.getChangingConfigurations() | (model == null ? 0 : model.flags); }
    public int getIntrinsicWidth() { return width; }
    public int getIntrinsicHeight() { return height; }
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    public int getAlpha() { return alpha; }
    public void setAlpha(int value) { if (value < 0 || value > 255) throw new IllegalArgumentException("alpha"); if (value != alpha) { alpha=value; invalidateSelf(); } }
    public ColorFilter getColorFilter() { return filter; }
    public void setColorFilter(ColorFilter value) { ColorFilter.shirazValidate(value); if (filter != value) { filter=value; invalidateSelf(); } }
    public void setTintList(ColorStateList value) { tint=value; refreshTintOwner(); }
    public void setTintMode(PorterDuff.Mode value) { tintMode=value; invalidateSelf(); }
    public void setAutoMirrored(boolean value) { if (mirrored != value) { mirrored=value; revision++; invalidateSelf(); } }
    public boolean isAutoMirrored() { return mirrored; }
    public boolean onLayoutDirectionChanged(int value) { if (mirrored) { revision++; invalidateSelf(); return true; } return false; }
    public boolean isStateful() {
        if (tint != null && tint.isStateful()) return true;
        if (model != null) for (Entry entry:model.entries) if (!entry.clip&&(entry.fill.isStateful() || entry.stroke.isStateful())) return true;
        return false;
    }
    protected boolean onStateChange(int[] state) { if (!isStateful()) return false; revision++; invalidateSelf(); return true; }
    public boolean canApplyTheme() { return model != null && model.themed; }
    public void applyTheme(Resources.Theme theme) { if (theme == null) throw new NullPointerException("theme"); if (model != null) install(new Loader(theme.getResources(),theme,model.xml).load(),theme.getResources()); }
    public ConstantState getConstantState() {
        if (model == null) return null;
        final Model value=copy(model); final int flags=getChangingConfigurations();
        final int savedAlpha=alpha; final boolean savedMirrored=mirrored; final ColorStateList savedTint=tint; final PorterDuff.Mode savedMode=tintMode;
        return new ConstantState() {
            public Drawable newDrawable() { return newDrawable(null); }
            public Drawable newDrawable(Resources resources) { VectorDrawable result=new VectorDrawable(value,resources); result.setChangingConfigurations(flags); result.alpha=savedAlpha; result.mirrored=savedMirrored; result.tint=savedTint; result.tintMode=savedMode; return result; }
            public Drawable newDrawable(Resources resources,Resources.Theme theme) { return theme != null && value.themed ? shirazInflate(theme.getResources(),theme,value.xml) : newDrawable(resources); }
            public int getChangingConfigurations() { return flags; }
            public boolean canApplyTheme() { return value.themed; }
        };
    }
    private static float[] multiply(float[] l,float[] r) { return new float[]{l[0]*r[0]+l[2]*r[1],l[1]*r[0]+l[3]*r[1],l[0]*r[2]+l[2]*r[3],l[1]*r[2]+l[3]*r[3],l[0]*r[4]+l[2]*r[5]+l[4],l[1]*r[4]+l[3]*r[5]+l[5]}; }
    private void raster(Bitmap bitmap) {
        bitmap.eraseColor(0); Canvas canvas=new Canvas(bitmap); float sx=(float)bitmap.getWidth()/model.viewportW,sy=(float)bitmap.getHeight()/model.viewportH;
        float[] root=mirrored && getLayoutDirection() == 1 ? new float[]{-sx,0,0,sy,bitmap.getWidth(),0} : new float[]{sx,0,0,sy,0,0};
        int[] state=getState(); Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        for (Entry entry:model.entries) {
            if(entry.clip)continue;int saved=canvas.save();try{
            for(Entry clip:entry.clips)canvas.shirazClipPath(clip.path,multiply(root,matrix(clip.parent)));
            float[] matrix=multiply(root,matrix(entry.parent));Path path=trimmed(entry); paint.setStyle(Paint.Style.FILL); paint.setColor(entry.fill.getColorForState(state,entry.fill.getDefaultColor())); paint.setAlpha(Math.round(paint.getAlpha()*entry.fillAlpha));
            if(entry.fillGradient!=null)entry.fillGradient.draw(canvas,path,paint,matrix,bitmap.getWidth(),bitmap.getHeight(),entry.fillAlpha);else if (paint.getAlpha() != 0) canvas.shirazDrawPath(path,paint,matrix);
            if (entry.width > 0) {
                paint.setStyle(Paint.Style.STROKE); paint.setColor(entry.stroke.getColorForState(state,entry.stroke.getDefaultColor())); paint.setAlpha(Math.round(paint.getAlpha()*entry.strokeAlpha)); paint.setStrokeWidth(entry.width); paint.setStrokeMiter(entry.miter); paint.setStrokeCap(Paint.Cap.values()[entry.cap]); paint.setStrokeJoin(Paint.Join.values()[entry.join]);
                if(entry.strokeGradient!=null)entry.strokeGradient.draw(canvas,path,paint,matrix,bitmap.getWidth(),bitmap.getHeight(),entry.strokeAlpha);else if (paint.getAlpha() != 0) canvas.shirazDrawPath(path,paint,matrix);
            }
            }finally{canvas.restoreToCount(saved);}
        }
    }
    public void draw(Canvas canvas) {
        Rect bounds=getBounds(); if (model == null || bounds.isEmpty()) return;
        int w=(int)Math.ceil(bounds.width()*canvas.shirazScaleX()),h=(int)Math.ceil(bounds.height()*canvas.shirazScaleY());
        if (w <= 0 || h <= 0 || w > 8192 || h > 8192 || (long)w*h > 16*1024*1024) throw new IllegalArgumentException("Vector raster dimensions");
        if (cache == null || cache.getWidth() != w || cache.getHeight() != h) { if (cache != null) cache.recycle(); cache=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); cacheRevision=-1; }
        if (cacheRevision != revision) { raster(cache); cacheRevision=revision; }
        ColorFilter active=filter;
        if (active == null && tint != null && tintMode != null) active=new PorterDuffColorFilter(tint.getColorForState(getState(),tint.getDefaultColor()),tintMode);
        presentation.setColorFilter(active); presentation.setAlpha(Math.round(alpha*model.alpha)); canvas.drawBitmap(cache,null,bounds,presentation);
    }
    private static final class Loader {
        final Resources resources; final Resources.Theme theme; final Model model=new Model(); int commands;
        Loader(Resources resources,Resources.Theme theme,byte[] xml) {
            if (resources == null || xml == null) throw new NullPointerException("vector source");
            if (xml.length > 4*1024*1024) throw new IllegalArgumentException("Vector XML length");
            this.resources=resources; this.theme=theme; model.xml=xml.clone(); model.metrics=resources.getDisplayMetrics();
        }
        BinaryXml.Value resolved(BinaryXml.Value value) {
            for (int i=0;i<32;i++) {
                if (value.type != 1 && value.type != 2) return value;
                if (value.type == 1 && value.data == 0) return new BinaryXml.Value(0,0,null);
                TypedValue out=new TypedValue();
                if (value.type == 2) { model.themed=true; if (theme == null || !theme.resolveAttribute(value.data,out,true)) throw new Resources.NotFoundException("Vector requires theme attribute"); }
                else resources.getValue(value.data,out,true);
                model.flags |= out.changingConfigurations; value=new BinaryXml.Value(out.type,out.data,out.string == null ? null : out.string.toString());
            }
            throw new IllegalArgumentException("Vector reference depth");
        }
        float number(BinaryXml.Node node,String key,float fallback) {
            BinaryXml.Value value=node.android(key); if (value == null) return fallback; value=resolved(value);
            float result=value.type == 4 ? Float.intBitsToFloat(value.data) : value.type >= 16 && value.type <= 31 ? value.data : Float.NaN;
            if (!Float.isFinite(result) || Math.abs(result) > 10000000) throw new IllegalArgumentException("Vector number "+key); return result;
        }
        float alpha(BinaryXml.Node node,String key) { float value=number(node,key,1); if (value < 0 || value > 1) throw new IllegalArgumentException("Vector alpha"); return value; }
        int enumeration(BinaryXml.Node node,String key,int fallback) { float value=number(node,key,fallback); if (value != (int)value) throw new IllegalArgumentException("Vector enum "+key); return (int)value; }
        int dimension(BinaryXml.Node node,String key) {
            BinaryXml.Value value=node.android(key); if (value == null || (value=resolved(value)).type != 5 || TypedValue.complexToDimension(value.data,model.metrics) <= 0) throw new IllegalArgumentException("Vector dimension "+key); return value.data;
        }
        VectorGradient gradient(BinaryXml.Value value){
            if(value==null)return null;int id=0;TypedValue meta=new TypedValue();
            if(value.type==1)id=value.data;else if(value.type==2){model.themed=true;if(theme==null||!theme.resolveAttribute(value.data,meta,true))throw new Resources.NotFoundException("Gradient theme attribute");id=meta.resourceId;}
            if(id==0)return null;resources.getValue(id,meta,true);model.flags|=meta.changingConfigurations;if(meta.type!=TypedValue.TYPE_STRING)return null;
            BinaryXml.Node node;try(XmlResourceParser parser=resources.getXml(id)){while(parser.next()!=org.xmlpull.v1.XmlPullParser.START_TAG){if(parser.getEventType()==org.xmlpull.v1.XmlPullParser.END_DOCUMENT)throw new IllegalArgumentException("Missing gradient root");}node=((shiraz.runtime.CompiledXmlParser)parser).shirazNode();}catch(Exception failure){throw new Resources.NotFoundException("Cannot load vector color XML",failure);}
            if(!node.name.equals("gradient"))return null;
            attributes(node,"startX startY endX endY centerX centerY type tileMode gradientRadius startColor centerColor endColor angle");
            ArrayList<Integer> colors=new ArrayList<Integer>();ArrayList<Float> stops=new ArrayList<Float>();
            for(BinaryXml.Node item:node.children){if(!item.name.equals("item")||!item.children.isEmpty()||item.android("color")==null||item.android("offset")==null)throw new IllegalArgumentException("Gradient item requires color and offset");attributes(item,"color offset");if(colors.size()>=256)throw new IllegalArgumentException("Gradient stop bound");colors.add(color(item.android("color")).getDefaultColor());stops.add(number(item,"offset",0));}
            if(colors.isEmpty()){colors.add(color(node.android("startColor")).getDefaultColor());stops.add(0f);if(node.android("centerColor")!=null){colors.add(color(node.android("centerColor")).getDefaultColor());stops.add(.5f);}colors.add(color(node.android("endColor")).getDefaultColor());stops.add(1f);}
            int[] values=new int[colors.size()];float[] positions=new float[values.length];for(int i=0;i<values.length;i++){values[i]=colors.get(i);positions[i]=stops.get(i);}
            return new VectorGradient(enumeration(node,"type",0),enumeration(node,"tileMode",0),number(node,"startX",0),number(node,"startY",0),number(node,"endX",0),number(node,"endY",0),number(node,"centerX",0),number(node,"centerY",0),number(node,"gradientRadius",0),values,positions);
        }
        ColorStateList color(BinaryXml.Value value) {
            if (value == null || value.type == 0 || value.type == 1 && value.data == 0) return ColorStateList.valueOf(0);
            if (value.type == 1) {
                TypedValue metadata=new TypedValue(); resources.getValue(value.data,metadata,true); model.flags |= metadata.changingConfigurations;
                return resources.getColorStateList(value.data,theme);
            }
            if (value.type == 2) {
                model.themed=true; TypedValue out=new TypedValue();
                if (theme == null || !theme.resolveAttribute(value.data,out,true)) throw new Resources.NotFoundException("Vector color requires a theme");
                model.flags |= out.changingConfigurations;
                if (out.type == 3 && out.resourceId != 0) return resources.getColorStateList(out.resourceId,theme);
                value=new BinaryXml.Value(out.type,out.data,null);
            }
            if (value.type < 28 || value.type > 31) throw new UnsupportedOperationException("Vector gradients/complex colors are not implemented");
            return ColorStateList.valueOf(value.data);
        }
        void attributes(BinaryXml.Node node,String allowed) {
            for (BinaryXml.Attribute attribute:node.attributes) if (!BinaryXml.ANDROID.equals(attribute.namespace) || !Arrays.asList(allowed.split(" ")).contains(attribute.name)) throw new UnsupportedOperationException("Vector attribute "+attribute.name);
        }
        String name(BinaryXml.Node node){BinaryXml.Value value=node.android("name");if(value==null)return null;value=resolved(value);if(value.type!=3||value.text==null)throw new IllegalArgumentException("Vector target name");return value.text;}
        Model load() {
            BinaryXml.Node root=BinaryXml.parse(model.xml); if (!root.name.equals("vector") || !root.namespace.isEmpty()) throw new UnsupportedOperationException("Only vector XML drawables are implemented");
            attributes(root,"name width height viewportWidth viewportHeight alpha tint tintMode autoMirrored opticalInsetLeft opticalInsetTop opticalInsetRight opticalInsetBottom");
            model.name=name(root);model.width=dimension(root,"width"); model.height=dimension(root,"height"); model.viewportW=number(root,"viewportWidth",0); model.viewportH=number(root,"viewportHeight",0);
            if (model.viewportW <= 0 || model.viewportH <= 0) throw new IllegalArgumentException("Vector viewport"); model.alpha=alpha(root,"alpha"); model.mirrored=number(root,"autoMirrored",0) != 0;
            if (root.android("tint") != null && !(root.android("tint").type == 1 && root.android("tint").data == 0)) model.tint=color(root.android("tint"));
            int mode=enumeration(root,"tintMode",5); switch (mode) { case 3:model.tintMode=PorterDuff.Mode.SRC_OVER;break; case 5:break; case 9:model.tintMode=PorterDuff.Mode.SRC_ATOP;break; case 14:model.tintMode=PorterDuff.Mode.MULTIPLY;break; case 15:model.tintMode=PorterDuff.Mode.SCREEN;break; case 16:model.tintMode=PorterDuff.Mode.ADD;break; default:throw new UnsupportedOperationException("Vector XML tint mode"); }
            children(root,null,0,new ArrayList<Entry>()); boolean painted=false;for(Entry entry:model.entries)if(!entry.clip)painted=true;if (!painted) throw new IllegalArgumentException("Vector requires a path"); return model;
        }
        void children(BinaryXml.Node parent,Group parentGroup,int depth,ArrayList<Entry> inherited) {ArrayList<Entry> active=new ArrayList<Entry>(inherited);if(depth>64)throw new IllegalArgumentException("Vector group depth");
            for (BinaryXml.Node node:parent.children) {
                if (!node.namespace.isEmpty()) throw new UnsupportedOperationException("Vector namespaced element");
                if (node.name.equals("group")) {
                    attributes(node,"name rotation pivotX pivotY scaleX scaleY translateX translateY");
                    if(model.groups.size()>=1024)throw new IllegalArgumentException("Vector group limit");Group group=new Group();group.name=name(node);group.parent=parentGroup;group.px=number(node,"pivotX",0);group.py=number(node,"pivotY",0);group.sx=number(node,"scaleX",1);group.sy=number(node,"scaleY",1);group.tx=number(node,"translateX",0);group.ty=number(node,"translateY",0);group.rotation=number(node,"rotation",0);model.groups.add(group);children(node,group,depth+1,active);
                } else if (node.name.equals("path")||node.name.equals("clip-path")) {
                    boolean clip=node.name.equals("clip-path");attributes(node,clip?"name pathData fillType":"name pathData fillColor fillAlpha fillType strokeColor strokeAlpha strokeWidth strokeLineCap strokeLineJoin strokeMiterLimit trimPathStart trimPathEnd trimPathOffset");
                    if (!node.children.isEmpty()) throw new UnsupportedOperationException("Vector child shaders are not implemented");
                    BinaryXml.Value data=node.android("pathData"); if (data == null || (data=resolved(data)).type != 3 || data.text == null) throw new IllegalArgumentException("Vector pathData");
                    Entry entry=new Entry();entry.clip=clip;entry.clips.addAll(active);entry.name=name(node);entry.parent=parentGroup;entry.trimStart=number(node,"trimPathStart",0);entry.trimEnd=number(node,"trimPathEnd",1);entry.trimOffset=number(node,"trimPathOffset",0); entry.path=PathData.parse(data.text); commands+=entry.path.shirazCommandCount(); if (commands > 65536 || model.entries.size() >= 1024) throw new IllegalArgumentException("Vector geometry budget");
                    int fill=enumeration(node,"fillType",0); if (fill != 0 && fill != 1) throw new IllegalArgumentException("Vector fill type"); entry.path.setFillType(fill == 0 ? Path.FillType.WINDING : Path.FillType.EVEN_ODD);
                    if(clip){model.entries.add(entry);active.add(entry);continue;}
                    entry.fillGradient=gradient(node.android("fillColor"));entry.strokeGradient=gradient(node.android("strokeColor"));entry.fill=entry.fillGradient==null?color(node.android("fillColor")):ColorStateList.valueOf(0); entry.stroke=entry.strokeGradient==null?color(node.android("strokeColor")):ColorStateList.valueOf(0); entry.fillAlpha=alpha(node,"fillAlpha"); entry.strokeAlpha=alpha(node,"strokeAlpha"); entry.width=number(node,"strokeWidth",0); entry.miter=number(node,"strokeMiterLimit",4); entry.cap=enumeration(node,"strokeLineCap",0); entry.join=enumeration(node,"strokeLineJoin",0);
                    if (entry.width < 0 || entry.miter < 0 || entry.cap < 0 || entry.cap > 2 || entry.join < 0 || entry.join > 2) throw new IllegalArgumentException("Vector stroke"); model.entries.add(entry);
                } else throw new UnsupportedOperationException("Vector element "+node.name);
            }
        }
    }
}
