// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
import java.util.Arrays;

/** Bounded software path geometry; boolean operations and path effects are separate. */
public class Path {
    public enum Direction { CW, CCW }
    public enum Op { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }
    public enum FillType { WINDING, EVEN_ODD, INVERSE_WINDING, INVERSE_EVEN_ODD }
    private float[] data=new float[32],snapshot;
    private int size;
    // Software rendering keeps its existing cubic encoding. Native iterators
    // need the original verbs, including quadratics before cubic conversion.
    private float[] semanticData=new float[32];
    private int semanticSize;
    private java.util.HashMap<Integer,Float> conicWeights;
    private long shirazNativePath;
    private boolean nativePathDirty=true;
    private int nativePathGeneration=1,nativePathPublishedGeneration;
    private float x,y,startX,startY;
    private FillType fill=FillType.WINDING;
    /** Vector boolean operations preserve fractional geometry without allocating raster masks. */
    public boolean op(Path path,Op op) { return op(this,path,op); }
    public boolean op(Path path1,Path path2,Op op) {
        if(path1==null||path2==null||op==null)throw new NullPointerException();
        if(!path1.shirazIsFinite()||!path2.shirazIsFinite())return false;
        float[] result=shiraz.runtime.NativeGpu.pathOp(path1.commands(),path1.fill.ordinal(),path2.commands(),path2.fill.ordinal(),op.ordinal());
        if(result==null)return false;
        Path out=new Path();out.setFillType(FillType.values()[(int)result[0]]);
        for(int i=1;i<result.length;){int verb=(int)result[i++];
            if(verb==1){out.moveTo(result[i],result[i+1]);i+=2;}
            else if(verb==2){out.lineTo(result[i],result[i+1]);i+=2;}
            else if(verb==3){out.cubicTo(result[i],result[i+1],result[i+2],result[i+3],result[i+4],result[i+5]);i+=6;}
            else if(verb==4)out.close();
            else throw new IllegalStateException("Native path operation encoding");
        }
        set(out);return true;
    }
    /** Conservative convexity: unsupported curves and compound contours use the general clip path. */
    public boolean isConvex() {
        if(size==0)return true;if(!shirazIsFinite())return false;
        float[] points=new float[512];int n=0,moves=0;
        for(int i=0;i<size;){int command=(int)data[i++];if(command==4)continue;if(command==3)return false;
            if(command==1&&++moves>1)return false;if(n==256)return false;
            points[n*2]=data[i++];points[n*2+1]=data[i++];n++;
        }
        int sign=0;for(int i=0;i<n;i++){int j=(i+1)%n;double dx=points[j*2]-points[i*2],dy=points[j*2+1]-points[i*2+1];
            for(int k=0;k<n;k++){double cross=dx*(points[k*2+1]-points[i*2+1])-dy*(points[k*2]-points[i*2]);if(cross==0)continue;int side=cross>0?1:-1;if(sign!=0&&side!=sign)return false;sign=side;}
        }return true;
    }
    public Path() { }
    public Path(Path other) { set(other); }
    private static void valid(float value) { if (Float.isFinite(value) && Math.abs(value) > 10000000) throw new IllegalArgumentException("Path coordinate range"); }
    /** Android stores non-finite geometry, but does not rasterize it. */
    boolean shirazIsFinite() {
        for(int i=0;i<size;i++)if(!Float.isFinite(data[i]))return false;
        return true;
    }
    private void reserve(int count) {
        if(size+count>65536)throw new IllegalStateException("Path exceeds 65536 command values");
        if(size+count>data.length)data=Arrays.copyOf(data,Math.min(65536,Math.max(size+count,data.length*2)));
        if(semanticSize+count>65536)throw new IllegalStateException("Path exceeds 65536 semantic values");
        if(semanticSize+count>semanticData.length)semanticData=Arrays.copyOf(semanticData,Math.min(65536,Math.max(semanticSize+count,semanticData.length*2)));
        snapshot=null;
    }
    private void changed() {
        nativePathDirty=true;
        nativePathGeneration=nativePathGeneration==Integer.MAX_VALUE?1:nativePathGeneration+1;
        if(nativePathGeneration==nativePathPublishedGeneration)nativePathGeneration=nativePathGeneration==Integer.MAX_VALUE?1:nativePathGeneration+1;
    }
    private void append(int command){reserve(1);data[size++]=command;semanticData[semanticSize++]=command;changed();}
    private void append(int command,float a,float b){valid(a);valid(b);reserve(3);data[size++]=command;data[size++]=a;data[size++]=b;semanticData[semanticSize++]=command;semanticData[semanticSize++]=a;semanticData[semanticSize++]=b;changed();}
    private void append(int command,float a,float b,float c,float d,float e,float f){
        valid(a);valid(b);valid(c);valid(d);valid(e);valid(f);reserve(7);
        data[size++]=command;data[size++]=a;data[size++]=b;data[size++]=c;data[size++]=d;data[size++]=e;data[size++]=f;
        semanticData[semanticSize++]=command;semanticData[semanticSize++]=a;semanticData[semanticSize++]=b;semanticData[semanticSize++]=c;semanticData[semanticSize++]=d;semanticData[semanticSize++]=e;semanticData[semanticSize++]=f;changed();
    }
    /**
     * Called by the audited Darwin shadow JNI field alias before reading its backing field. Native
     * iterator owners must retain this Path until their iterator is destroyed.
     */
    public final synchronized void shirazPrepareNativePath() {
        if(shirazNativePath==0) {
            long pointer=shiraz.runtime.NativeGuestPath.createPath();
            if(pointer==0)throw new IllegalStateException("Native guest path allocation returned zero");
            shirazNativePath=pointer;nativePathDirty=true;
        }
        if(nativePathDirty) {
            shiraz.runtime.NativeGuestPath.updatePath(shirazNativePath,Arrays.copyOf(semanticData,semanticSize),nativePathGeneration);
            nativePathPublishedGeneration=nativePathGeneration;
            nativePathDirty=false;
        }
    }
    @Override protected void finalize() throws Throwable {
        try {
            long pointer;
            synchronized(this) { pointer=shirazNativePath;shirazNativePath=0; }
            if(pointer!=0)shiraz.runtime.NativeGuestPath.destroyPath(pointer);
        } finally { super.finalize(); }
    }
    private void ensureStart() { if (size == 0) moveTo(0,0); }
    float[] commands() { if (snapshot == null) snapshot=Arrays.copyOf(data,size); return snapshot; }
    /** Snapshot for host property animation: distance, x, y triples. */
    // Geometry only: no resources, theme, configuration or mutable Path identity.
    private static final class SampleKey {
        final float[] commands;final long precision;final int hash;
        SampleKey(float[] source,double segment){commands=source.clone();precision=Double.doubleToLongBits(segment);hash=31*Arrays.hashCode(commands)+(int)(precision^(precision>>>32));}
        public int hashCode(){return hash;}
        public boolean equals(Object other){if(!(other instanceof SampleKey))return false;SampleKey key=(SampleKey)other;return precision==key.precision&&Arrays.equals(commands,key.commands);}
    }
    private static final java.util.LinkedHashMap<SampleKey,double[]> SAMPLE_CACHE=new java.util.LinkedHashMap<SampleKey,double[]>(16,.75f,true);
    private static int sampleCacheBytes;
    private static double[] cachedSamples(SampleKey key){synchronized(SAMPLE_CACHE){double[] value=SAMPLE_CACHE.get(key);return value==null?null:value.clone();}}
    private static void cacheSamples(SampleKey key,double[] points){
        if(key==null||points.length>49152)return;
        synchronized(SAMPLE_CACHE){
            if(SAMPLE_CACHE.containsKey(key))return;
            int bytes=key.commands.length*4+points.length*8;
            while(!SAMPLE_CACHE.isEmpty()&&(SAMPLE_CACHE.size()>=64||sampleCacheBytes+bytes>2*1024*1024)){
                java.util.Map.Entry<SampleKey,double[]> oldest=SAMPLE_CACHE.entrySet().iterator().next();
                sampleCacheBytes-=oldest.getKey().commands.length*4+oldest.getValue().length*8;SAMPLE_CACHE.remove(oldest.getKey());
            }
            SAMPLE_CACHE.put(key,points.clone());sampleCacheBytes+=bytes;
        }
    }
    public double[] shirazAnimationPoints() { return shirazAnimationPoints(1); }
    public double[] shirazAnimationPoints(final double maxSegment) {
        if(!shirazIsFinite())throw new IllegalArgumentException("Nonfinite animation path");
        if(!Double.isFinite(maxSegment)||maxSegment<=0)throw new IllegalArgumentException("Sampling precision");
        float[] source=commands();SampleKey key=source.length<=256?new SampleKey(source,maxSegment):null;
        double[] cached=key==null?null:cachedSamples(key);if(cached!=null)return cached;
        final java.util.ArrayList<Double> points=new java.util.ArrayList<Double>();
        class Sampler {
            double length,px,py;
            void add(double ax,double ay,boolean move) {
                if(points.size()>=196608)throw new IllegalArgumentException("Animation path sampling limit");
                if(!move)length+=Math.hypot(ax-px,ay-py);
                points.add(length);points.add(ax);points.add(ay);px=ax;py=ay;
            }
            void cubic(double ax,double ay,double bx,double by,double cx,double cy,double dx,double dy,int depth) {
                double polygon=Math.hypot(bx-ax,by-ay)+Math.hypot(cx-bx,cy-by)+Math.hypot(dx-cx,dy-cy);
                double chord=Math.hypot(dx-ax,dy-ay);
                // Control polygon excess bounds subdivision even for loops and cusps.
                if(polygon-chord<=0.01 && polygon<=maxSegment){add(dx,dy,false);return;}
                if(depth>=24)throw new IllegalArgumentException("Animation path subdivision limit");
                double abx=(ax+bx)/2,aby=(ay+by)/2,bcx=(bx+cx)/2,bcy=(by+cy)/2,cdx=(cx+dx)/2,cdy=(cy+dy)/2;
                double ex=(abx+bcx)/2,ey=(aby+bcy)/2,fx=(bcx+cdx)/2,fy=(bcy+cdy)/2,mx=(ex+fx)/2,my=(ey+fy)/2;
                cubic(ax,ay,abx,aby,ex,ey,mx,my,depth+1);cubic(mx,my,fx,fy,cdx,cdy,dx,dy,depth+1);
            }
        }
        Sampler sampler=new Sampler();double sx=0,sy=0;
        for(int i=0;i<source.length;){int op=(int)source[i++];
            if(op==1){sx=source[i++];sy=source[i++];sampler.add(sx,sy,true);}
            else if(op==2){sampler.add(source[i++],source[i++],false);}
            else if(op==3){sampler.cubic(sampler.px,sampler.py,source[i],source[i+1],source[i+2],source[i+3],source[i+4],source[i+5],0);i+=6;}
            else sampler.add(sx,sy,false);
        }
        if(points.isEmpty())throw new IllegalArgumentException("Empty animation path");
        double[] result=new double[points.size()];for(int i=0;i<result.length;i++)result[i]=points.get(i);cacheSamples(key,result);return result;
    }
    public void set(Path other) { if (other == null) { reset(); return; } if (other == this) return; float[] copy=other.commands().clone(),semantic=Arrays.copyOf(other.semanticData,other.semanticSize);data=copy;size=data.length;semanticData=semantic;semanticSize=semantic.length;conicWeights=other.conicWeights==null?null:new java.util.HashMap<Integer,Float>(other.conicWeights);x=other.x;y=other.y;startX=other.startX;startY=other.startY;fill=other.fill;snapshot=null;changed(); }
    public void reset() { size=semanticSize=0; conicWeights=null; x=y=startX=startY=0; snapshot=null; changed(); }
    public void rewind() { reset(); }
    public int shirazCommandCount() { return size; }
    public boolean isEmpty() { return size == 0; }
    public void setFillType(FillType value) { if (value == null) throw new NullPointerException("fill type"); if(fill!=value){fill=value;changed();} }
    public FillType getFillType() { return fill; }
    public boolean isInverseFillType() { return fill.ordinal() >= 2; }
    public void toggleInverseFillType() { fill=FillType.values()[fill.ordinal()^2];changed(); }
    public void moveTo(float x,float y) { append(1,x,y); this.x=startX=x; this.y=startY=y; }
    public void rMoveTo(float dx,float dy) { moveTo(x+dx,y+dy); }
    public void setLastPoint(float px,float py) {
        valid(px);valid(py);if(semanticSize==0){moveTo(px,py);return;}
        float[] commands=Arrays.copyOf(semanticData,semanticSize);int end=-1;
        for(int i=0;i<commands.length;){int verb=(int)commands[i++],count=verb==3?6:verb==5?4:verb==4?0:2;if(count!=0)end=i+count-2;i+=count;}
        commands[end]=px;commands[end+1]=py;
        java.util.HashMap<Integer,Float> savedConics=conicWeights==null?null:new java.util.HashMap<Integer,Float>(conicWeights);
        rewind();for(int i=0;i<commands.length;){int pos=i;int verb=(int)commands[i++];if(verb==1){moveTo(commands[i],commands[i+1]);i+=2;}else if(verb==2){lineTo(commands[i],commands[i+1]);i+=2;}else if(verb==3){cubicTo(commands[i],commands[i+1],commands[i+2],commands[i+3],commands[i+4],commands[i+5]);i+=6;}else if(verb==5){Float cw=savedConics==null?null:savedConics.get(Integer.valueOf(pos));if(cw!=null)conicTo(commands[i],commands[i+1],commands[i+2],commands[i+3],cw.floatValue());else quadTo(commands[i],commands[i+1],commands[i+2],commands[i+3]);i+=4;}else close();}
    }
    public void lineTo(float x,float y) { valid(x); valid(y); ensureStart(); append(2,x,y); this.x=x; this.y=y; }
    public void rLineTo(float dx,float dy) { lineTo(x+dx,y+dy); }
    public void cubicTo(float x1,float y1,float x2,float y2,float x3,float y3) { valid(x1);valid(y1);valid(x2);valid(y2);valid(x3);valid(y3); ensureStart(); append(3,x1,y1,x2,y2,x3,y3); x=x3; y=y3; }
    public void rCubicTo(float dx1,float dy1,float dx2,float dy2,float dx3,float dy3) { cubicTo(x+dx1,y+dy1,x+dx2,y+dy2,x+dx3,y+dy3); }
    public void quadTo(float x1,float y1,float x2,float y2) {
        valid(x1);valid(y1);valid(x2);valid(y2);
        float sx=size==0?0:x,sy=size==0?0:y;
        float a=sx+(x1-sx)*2/3,b=sy+(y1-sy)*2/3,c=x2+(x1-x2)*2/3,d=y2+(y1-y2)*2/3;
        valid(a);valid(b);valid(c);valid(d);ensureStart();reserve(7);
        data[size++]=3;data[size++]=a;data[size++]=b;data[size++]=c;data[size++]=d;data[size++]=x2;data[size++]=y2;
        semanticData[semanticSize++]=5;semanticData[semanticSize++]=x1;semanticData[semanticSize++]=y1;semanticData[semanticSize++]=x2;semanticData[semanticSize++]=y2;
        x=x2;y=y2;changed();
    }
    public void rQuadTo(float dx1,float dy1,float dx2,float dy2) { quadTo(x+dx1,y+dy1,x+dx2,y+dy2); }
    public void conicTo(float x1,float y1,float x2,float y2,float weight) {
        valid(x1);valid(y1);valid(x2);valid(y2);
        if(!(weight>0f)){lineTo(x2,y2);return;}
        if(!Float.isFinite(weight)){lineTo(x1,y1);lineTo(x2,y2);return;}
        if(weight==1f){quadTo(x1,y1,x2,y2);return;}
        float sx=size==0?0:x,sy=size==0?0:y;
        float k=(float)((4.0*weight)/(3.0*(1.0+weight)));
        float a=sx+(x1-sx)*k,b=sy+(y1-sy)*k,c=x2+(x1-x2)*k,d=y2+(y1-y2)*k;
        valid(a);valid(b);valid(c);valid(d);ensureStart();reserve(7);
        data[size++]=3;data[size++]=a;data[size++]=b;data[size++]=c;data[size++]=d;data[size++]=x2;data[size++]=y2;
        int semPos=semanticSize;
        semanticData[semanticSize++]=5;semanticData[semanticSize++]=x1;semanticData[semanticSize++]=y1;semanticData[semanticSize++]=x2;semanticData[semanticSize++]=y2;
        if(conicWeights==null)conicWeights=new java.util.HashMap<Integer,Float>();
        conicWeights.put(Integer.valueOf(semPos),Float.valueOf(weight));
        x=x2;y=y2;changed();
    }
    public void rConicTo(float dx1,float dy1,float dx2,float dy2,float weight) { conicTo(x+dx1,y+dy1,x+dx2,y+dy2,weight); }
    public void close() { if (size != 0) { append(4); x=startX; y=startY; } }
    public void addRect(float left,float top,float right,float bottom,Direction direction) {
        if (direction == null) throw new NullPointerException("direction");
        for (float v:new float[]{left,top,right,bottom}) valid(v);
        moveTo(left,top);
        if (direction == Direction.CW) { lineTo(right,top); lineTo(right,bottom); lineTo(left,bottom); }
        else { lineTo(left,bottom); lineTo(right,bottom); lineTo(right,top); }
        close();
    }
    public void addRect(RectF rect,Direction direction) { addRect(rect.left,rect.top,rect.right,rect.bottom,direction); }
    public void addPath(Path other,float dx,float dy) {
        if(other==null)throw new NullPointerException("path");
        Path translated=new Path();other.offset(dx,dy,translated);addPath(translated);
    }
    public void addPath(Path other,Matrix matrix) {
        if(other==null)throw new NullPointerException("path");
        Path transformed=new Path();other.transform(matrix,transformed);addPath(transformed);
    }
    public void addPath(Path other) {
        if(other==null)throw new NullPointerException("path"); float[] values=other.commands(); if(values.length==0)return;
        float[] semantic=Arrays.copyOf(other.semanticData,other.semanticSize);
        java.util.HashMap<Integer,Float> otherConics=other.conicWeights==null?null:new java.util.HashMap<Integer,Float>(other.conicWeights);
        if(size+values.length>65536)throw new IllegalStateException("Path exceeds 65536 command values");
        float nextX=other.x,nextY=other.y,nextStartX=other.startX,nextStartY=other.startY;
        int baseSemantic=semanticSize;
        if(size+values.length>data.length)data=Arrays.copyOf(data,size+values.length);
        if(semanticSize+semantic.length>semanticData.length)semanticData=Arrays.copyOf(semanticData,semanticSize+semantic.length);
        System.arraycopy(values,0,data,size,values.length);size+=values.length;snapshot=null;
        System.arraycopy(semantic,0,semanticData,semanticSize,semantic.length);semanticSize+=semantic.length;
        if(otherConics!=null){
            if(conicWeights==null)conicWeights=new java.util.HashMap<Integer,Float>();
            for(java.util.Map.Entry<Integer,Float> e:otherConics.entrySet())conicWeights.put(Integer.valueOf(baseSemantic+e.getKey().intValue()),e.getValue());
        }
        changed();
        x=nextX;y=nextY;startX=nextStartX;startY=nextStartY;
    }
    private void ellipseArc(float cx,float cy,float rx,float ry,double start,double sweep) {
        // Subdivide quarter turns to limit cubic approximation error.
        int count=(int)Math.ceil(Math.abs(sweep)/(Math.PI/8));double step=sweep/count;
        for(int i=0;i<count;i++) {
            double a=start+i*step,b=a+step,k=4.0/3*Math.tan(step/4);
            cubicTo((float)(cx+rx*(Math.cos(a)-k*Math.sin(a))),(float)(cy+ry*(Math.sin(a)+k*Math.cos(a))),
                (float)(cx+rx*(Math.cos(b)+k*Math.sin(b))),(float)(cy+ry*(Math.sin(b)-k*Math.cos(b))),
                (float)(cx+rx*Math.cos(b)),(float)(cy+ry*Math.sin(b)));
        }
    }
    public void arcTo(RectF oval,float startAngle,float sweepAngle){arcTo(oval,startAngle,sweepAngle,false);}
    public void arcTo(RectF oval,float startAngle,float sweepAngle,boolean forceMoveTo){
        if(oval==null)throw new NullPointerException("oval");arcTo(oval.left,oval.top,oval.right,oval.bottom,startAngle,sweepAngle,forceMoveTo);
    }
    public void addArc(RectF oval,float startAngle,float sweepAngle){
        if(oval==null)throw new NullPointerException("oval");addArc(oval.left,oval.top,oval.right,oval.bottom,startAngle,sweepAngle);
    }
    public void addArc(float left,float top,float right,float bottom,float startAngle,float sweepAngle){
        arcTo(left,top,right,bottom,startAngle,sweepAngle,true);
    }
    public void arcTo(float left,float top,float right,float bottom,float startAngle,float sweepAngle,boolean forceMoveTo){
        for(float value:new float[]{left,top,right,bottom})valid(value);
        if(!Float.isFinite(startAngle)||!Float.isFinite(sweepAngle))throw new IllegalArgumentException("Nonfinite arc angle");
        if(left>right||top>bottom)return;
        double start=Math.toRadians(startAngle%360),sweep=Math.toRadians(sweepAngle%360);
        float rx=(right-left)/2,ry=(bottom-top)/2,cx=left+rx,cy=top+ry;
        float firstX=(float)(cx+rx*Math.cos(start)),firstY=(float)(cy+ry*Math.sin(start));
        Path result=new Path(this);
        if(forceMoveTo||isEmpty())result.moveTo(firstX,firstY);else result.lineTo(firstX,firstY);
        if(sweep!=0)result.ellipseArc(cx,cy,rx,ry,start,sweep);
        set(result);
    }
    public void addOval(RectF rect,Direction direction){addOval(rect.left,rect.top,rect.right,rect.bottom,direction);}
    public void addOval(float left,float top,float right,float bottom,Direction direction) {
        if(direction==null)throw new NullPointerException("direction");for(float v:new float[]{left,top,right,bottom})valid(v);
        if(left>=right||top>=bottom)return;
        float rx=(right-left)/2,ry=(bottom-top)/2,cx=left+rx,cy=top+ry;Path shape=new Path();shape.moveTo(right,cy);
        shape.ellipseArc(cx,cy,rx,ry,0,direction==Direction.CW?Math.PI*2:-Math.PI*2);shape.close();addPath(shape);
    }
    public void addCircle(float cx,float cy,float radius,Direction direction) {
        valid(cx);valid(cy);valid(radius);if(direction==null)throw new NullPointerException("direction");if(radius<=0)return;
        addOval(cx-radius,cy-radius,cx+radius,cy+radius,direction);
    }
    public void addRoundRect(RectF rect,float rx,float ry,Direction direction){addRoundRect(rect.left,rect.top,rect.right,rect.bottom,rx,ry,direction);}
    public void addRoundRect(float left,float top,float right,float bottom,float rx,float ry,Direction direction) {
        if(direction==null)throw new NullPointerException("direction");
        valid(rx);valid(ry);
        // Android 8.1's scalar Path overload ignores negative radii.
        if(rx<0||ry<0)return;
        addRoundRect(left,top,right,bottom,new float[]{rx,ry,rx,ry,rx,ry,rx,ry},direction);
    }
    public void addRoundRect(RectF rect,float[] radii,Direction direction){addRoundRect(rect.left,rect.top,rect.right,rect.bottom,radii,direction);}
    public void addRoundRect(float left,float top,float right,float bottom,float[] radii,Direction direction){
        if(radii==null||direction==null)throw new NullPointerException("radii/direction");
        if(radii.length<8)throw new ArrayIndexOutOfBoundsException("Eight corner radii required");
        for(float v:new float[]{left,top,right,bottom}){valid(v);if(!Float.isFinite(v))return;}
        if(left>right){float swap=left;left=right;right=swap;}if(top>bottom){float swap=top;top=bottom;bottom=swap;}
        float[] r=Arrays.copyOf(radii,8);boolean finite=true;for(float v:r){valid(v);finite&=Float.isFinite(v);}
        if(left>=right||top>=bottom)return;
        // A nonfinite corner radius makes the whole rounded rectangle square.
        if(!finite){addRect(left,top,right,bottom,direction);return;}
        for(int i=0;i<8;i+=2)if(r[i]<=0||r[i+1]<=0){r[i]=0;r[i+1]=0;}
        // One common scale preserves corner proportions while adjacent radii fit every side.
        double w=(double)right-left,h=(double)bottom-top,scale=1;
        double[] sums={(double)r[0]+r[2],(double)r[3]+r[5],(double)r[4]+r[6],(double)r[7]+r[1]};
        for(int i=0;i<4;i++)if(sums[i]>0)scale=Math.min(scale,(i%2==0?w:h)/sums[i]);
        for(int i=0;i<8;i++)r[i]*=scale;
        Path shape=new Path();shape.moveTo(left+r[0],top);
        if(direction==Direction.CW){
            shape.lineTo(right-r[2],top);shape.ellipseArc(right-r[2],top+r[3],r[2],r[3],-Math.PI/2,Math.PI/2);
            shape.lineTo(right,bottom-r[5]);shape.ellipseArc(right-r[4],bottom-r[5],r[4],r[5],0,Math.PI/2);
            shape.lineTo(left+r[6],bottom);shape.ellipseArc(left+r[6],bottom-r[7],r[6],r[7],Math.PI/2,Math.PI/2);
            shape.lineTo(left,top+r[1]);shape.ellipseArc(left+r[0],top+r[1],r[0],r[1],Math.PI,Math.PI/2);
        }else{
            shape.ellipseArc(left+r[0],top+r[1],r[0],r[1],-Math.PI/2,-Math.PI/2);shape.lineTo(left,bottom-r[7]);
            shape.ellipseArc(left+r[6],bottom-r[7],r[6],r[7],Math.PI,-Math.PI/2);shape.lineTo(right-r[4],bottom);
            shape.ellipseArc(right-r[4],bottom-r[5],r[4],r[5],Math.PI/2,-Math.PI/2);shape.lineTo(right,top+r[3]);
            shape.ellipseArc(right-r[2],top+r[3],r[2],r[3],0,-Math.PI/2);shape.lineTo(left+r[0],top);
        }
        shape.close();addPath(shape);
    }
    /** Bounds of stored control points; the legacy exact flag has no effect. */
    public void computeBounds(RectF bounds,boolean exact){
        if(bounds==null)throw new NullPointerException("bounds");
        if(!shirazIsFinite()){bounds.set(0,0,0,0);return;}
        float left=Float.POSITIVE_INFINITY,top=left,right=Float.NEGATIVE_INFINITY,bottom=right;int points=0;
        for(int i=0;i<size;){int command=(int)data[i++],count=command==3?6:command==4?0:2;
            for(int j=0;j<count;j+=2){float px=data[i+j],py=data[i+j+1];left=Math.min(left,px);right=Math.max(right,px);top=Math.min(top,py);bottom=Math.max(bottom,py);points++;}i+=count;
        }
        if(points<2)bounds.set(0,0,0,0);else bounds.set(left,top,right,bottom);
    }
    public void transform(Matrix matrix){transform(matrix,this);}
    public void transform(Matrix matrix,Path destination){
        if(matrix==null)throw new NullPointerException("matrix");
        if(!matrix.isAffine())throw new UnsupportedOperationException("Perspective path transforms are not implemented");
        float[] coefficients=new float[9];matrix.getValues(coefficients);for(float value:coefficients)if(!Float.isFinite(value))throw new IllegalArgumentException("Nonfinite path transform");
        float[] copy=commands().clone();
        for(int i=0;i<copy.length;){int command=(int)copy[i++],count=command==3?6:command==4?0:2;matrix.mapPoints(copy,i,copy,i,count/2);for(int j=0;j<count;j++)valid(copy[i+j]);i+=count;}
        float[] cursor={x,y,startX,startY};if(size!=0){matrix.mapPoints(cursor);for(float value:cursor)valid(value);}
        float[] semantic=Arrays.copyOf(semanticData,semanticSize);
        for(int i=0;i<semantic.length;){int command=(int)semantic[i++],count=command==3?6:command==5?4:command==4?0:2;matrix.mapPoints(semantic,i,semantic,i,count/2);i+=count;}
        java.util.HashMap<Integer,Float> nextConics=conicWeights==null?null:new java.util.HashMap<Integer,Float>(conicWeights);
        if(destination==null)destination=this;
        destination.data=copy;destination.size=copy.length;destination.snapshot=null;destination.fill=fill;
        destination.semanticData=semantic;destination.semanticSize=semantic.length;destination.conicWeights=nextConics;destination.changed();
        destination.x=cursor[0];destination.y=cursor[1];destination.startX=cursor[2];destination.startY=cursor[3];
    }
    public void offset(float dx,float dy) { offset(dx,dy,this); }
    public void offset(float dx,float dy,Path destination) {
        valid(dx); valid(dy); if (destination == null) destination=this;
        float[] copy=commands().clone();
        for (int i=0;i<copy.length;) {
            int command=(int)copy[i++],count=command == 3 ? 6 : command == 4 ? 0 : 2;
            for (int j=0;j<count;j++) { copy[i+j]+=j%2 == 0 ? dx : dy; valid(copy[i+j]); } i+=count;
        }
        float nextX=size == 0 ? x : x+dx,nextY=size == 0 ? y : y+dy;
        float nextStartX=size == 0 ? startX : startX+dx,nextStartY=size == 0 ? startY : startY+dy;
        FillType nextFill=fill;
        float[] semantic=Arrays.copyOf(semanticData,semanticSize);
        for(int i=0;i<semantic.length;){int command=(int)semantic[i++],count=command==3?6:command==5?4:command==4?0:2;for(int j=0;j<count;j++)semantic[i+j]+=j%2==0?dx:dy;i+=count;}
        java.util.HashMap<Integer,Float> nextConics=conicWeights==null?null:new java.util.HashMap<Integer,Float>(conicWeights);
        destination.data=copy; destination.size=copy.length; destination.snapshot=null;
        destination.semanticData=semantic;destination.semanticSize=semantic.length;destination.conicWeights=nextConics;destination.changed();
        destination.x=nextX; destination.y=nextY; destination.startX=nextStartX; destination.startY=nextStartY; destination.fill=nextFill;
    }
    public void incReserve(int extraPtCount) {
        if (extraPtCount > 0) reserve(Math.min(1024, extraPtCount * 3));
    }
    public int getGenerationId() { return nativePathGeneration; }
    public PathIterator getPathIterator() {
        java.util.ArrayList<Integer> vList = new java.util.ArrayList<Integer>();
        java.util.ArrayList<float[]> pList = new java.util.ArrayList<float[]>();
        java.util.ArrayList<Float> wList = new java.util.ArrayList<Float>();
        float cx = 0f, cy = 0f, sx = 0f, sy = 0f;
        for (int i = 0; i < semanticSize; ) {
            int semPos = i;
            int cmd = (int) semanticData[i++];
            if (cmd == 1) {
                float nx = semanticData[i++], ny = semanticData[i++];
                vList.add(Integer.valueOf(PathIterator.VERB_MOVE));
                pList.add(new float[]{nx, ny});
                wList.add(Float.valueOf(0f));
                cx = sx = nx; cy = sy = ny;
            } else if (cmd == 2) {
                float nx = semanticData[i++], ny = semanticData[i++];
                vList.add(Integer.valueOf(PathIterator.VERB_LINE));
                pList.add(new float[]{cx, cy, nx, ny});
                wList.add(Float.valueOf(0f));
                cx = nx; cy = ny;
            } else if (cmd == 3) {
                float x1 = semanticData[i++], y1 = semanticData[i++], x2 = semanticData[i++], y2 = semanticData[i++], x3 = semanticData[i++], y3 = semanticData[i++];
                vList.add(Integer.valueOf(PathIterator.VERB_CUBIC));
                pList.add(new float[]{cx, cy, x1, y1, x2, y2, x3, y3});
                wList.add(Float.valueOf(0f));
                cx = x3; cy = y3;
            } else if (cmd == 5) {
                float x1 = semanticData[i++], y1 = semanticData[i++], x2 = semanticData[i++], y2 = semanticData[i++];
                Float cw = conicWeights == null ? null : conicWeights.get(Integer.valueOf(semPos));
                if (cw != null) {
                    vList.add(Integer.valueOf(PathIterator.VERB_CONIC));
                    pList.add(new float[]{cx, cy, x1, y1, x2, y2});
                    wList.add(cw);
                } else {
                    vList.add(Integer.valueOf(PathIterator.VERB_QUAD));
                    pList.add(new float[]{cx, cy, x1, y1, x2, y2});
                    wList.add(Float.valueOf(0f));
                }
                cx = x2; cy = y2;
            } else if (cmd == 4) {
                vList.add(Integer.valueOf(PathIterator.VERB_CLOSE));
                pList.add(new float[0]);
                wList.add(Float.valueOf(0f));
                cx = sx; cy = sy;
            }
        }
        int n = vList.size();
        int[] verbs = new int[n];
        float[][] pts = new float[n][];
        float[] weights = new float[n];
        for (int i = 0; i < n; i++) {
            verbs[i] = vList.get(i).intValue();
            pts[i] = pList.get(i);
            weights[i] = wList.get(i).floatValue();
        }
        return new PathIterator(this, verbs, pts, weights, getGenerationId());
    }
    public boolean isRect(RectF rect) {
        if (semanticSize == 0 || !shirazIsFinite()) return false;
        float[] px = new float[5], py = new float[5];
        int count = 0;
        boolean sawClose = false;
        for (int i = 0; i < semanticSize; ) {
            int cmd = (int) semanticData[i++];
            if (cmd == 1) {
                if (count != 0) return false;
                px[count] = semanticData[i++]; py[count++] = semanticData[i++];
            } else if (cmd == 2) {
                if (count == 0 || count >= 5 || sawClose) return false;
                px[count] = semanticData[i++]; py[count++] = semanticData[i++];
            } else if (cmd == 4) {
                sawClose = true;
            } else {
                return false;
            }
        }
        if (count == 5) {
            if (px[4] != px[0] || py[4] != py[0]) return false;
            count = 4;
        } else if (count != 4 || !sawClose) {
            return false;
        }
        float minX = Math.min(Math.min(px[0], px[1]), Math.min(px[2], px[3]));
        float maxX = Math.max(Math.max(px[0], px[1]), Math.max(px[2], px[3]));
        float minY = Math.min(Math.min(py[0], py[1]), Math.min(py[2], py[3]));
        float maxY = Math.max(Math.max(py[0], py[1]), Math.max(py[2], py[3]));
        if (minX >= maxX || minY >= maxY) return false;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            boolean horiz = py[i] == py[j] && px[i] != px[j];
            boolean vert = px[i] == px[j] && py[i] != py[j];
            if (!horiz && !vert) return false;
            if ((px[i] != minX && px[i] != maxX) || (py[i] != minY && py[i] != maxY)) return false;
        }
        if (rect != null) rect.set(minX, minY, maxX, maxY);
        return true;
    }
    public float[] approximate(float acceptableError) {
        if (acceptableError < 0f || Float.isNaN(acceptableError)) {
            throw new IllegalArgumentException("acceptableError must be >= 0");
        }
        if (isEmpty() || !shirazIsFinite()) {
            return new float[]{0f, 0f, 0f, 1f, 0f, 0f};
        }
        double step = Math.max(0.25, Math.min(64.0, acceptableError == 0f ? 0.25 : acceptableError * 4.0));
        double[] samples = shirazAnimationPoints(step);
        int count = samples.length / 3;
        if (count < 2) {
            float sx = (float) samples[1], sy = (float) samples[2];
            return new float[]{0f, sx, sy, 1f, sx, sy};
        }
        double total = samples[(count - 1) * 3];
        float[] out = new float[count * 3];
        for (int i = 0; i < count; i++) {
            out[i * 3] = total <= 0.0 ? (i / (float) (count - 1)) : (float) (samples[i * 3] / total);
            out[i * 3 + 1] = (float) samples[i * 3 + 1];
            out[i * 3 + 2] = (float) samples[i * 3 + 2];
        }
        out[0] = 0f;
        out[(count - 1) * 3] = 1f;
        return out;
    }
    public boolean isInterpolatable(Path otherPath) {
        if (otherPath == null) throw new NullPointerException("otherPath");
        if (semanticSize != otherPath.semanticSize) return false;
        for (int i = 0; i < semanticSize; ) {
            int v1 = (int) semanticData[i], v2 = (int) otherPath.semanticData[i];
            if (v1 != v2) return false;
            boolean c1 = conicWeights != null && conicWeights.containsKey(Integer.valueOf(i));
            boolean c2 = otherPath.conicWeights != null && otherPath.conicWeights.containsKey(Integer.valueOf(i));
            if (c1 != c2) return false;
            int step = v1 == 3 ? 7 : v1 == 5 ? 5 : v1 == 4 ? 1 : 3;
            i += step;
        }
        return true;
    }
    public boolean interpolate(Path otherPath, float t, Path interpolatedPath) {
        if (otherPath == null || interpolatedPath == null) throw new NullPointerException();
        if (!isInterpolatable(otherPath)) return false;
        Path out = new Path();
        out.setFillType(fill);
        for (int i = 0; i < semanticSize; ) {
            int pos = i;
            int verb = (int) semanticData[i++];
            if (verb == 1) {
                float ix = lerp(semanticData[i], otherPath.semanticData[i], t); i++;
                float iy = lerp(semanticData[i], otherPath.semanticData[i], t); i++;
                out.moveTo(ix, iy);
            } else if (verb == 2) {
                float ix = lerp(semanticData[i], otherPath.semanticData[i], t); i++;
                float iy = lerp(semanticData[i], otherPath.semanticData[i], t); i++;
                out.lineTo(ix, iy);
            } else if (verb == 3) {
                float x1 = lerp(semanticData[i], otherPath.semanticData[i], t); i++;
                float y1 = lerp(semanticData[i], otherPath.semanticData[i], t); i++;
                float x2 = lerp(semanticData[i], otherPath.semanticData[i], t); i++;
                float y2 = lerp(semanticData[i], otherPath.semanticData[i], t); i++;
                float x3 = lerp(semanticData[i], otherPath.semanticData[i], t); i++;
                float y3 = lerp(semanticData[i], otherPath.semanticData[i], t); i++;
                out.cubicTo(x1, y1, x2, y2, x3, y3);
            } else if (verb == 5) {
                float x1 = lerp(semanticData[i], otherPath.semanticData[i], t); i++;
                float y1 = lerp(semanticData[i], otherPath.semanticData[i], t); i++;
                float x2 = lerp(semanticData[i], otherPath.semanticData[i], t); i++;
                float y2 = lerp(semanticData[i], otherPath.semanticData[i], t); i++;
                Float w1 = conicWeights == null ? null : conicWeights.get(Integer.valueOf(pos));
                if (w1 != null) {
                    float w2 = otherPath.conicWeights.get(Integer.valueOf(pos)).floatValue();
                    out.conicTo(x1, y1, x2, y2, lerp(w1.floatValue(), w2, t));
                } else {
                    out.quadTo(x1, y1, x2, y2);
                }
            } else if (verb == 4) {
                out.close();
            }
        }
        interpolatedPath.set(out);
        return true;
    }
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
