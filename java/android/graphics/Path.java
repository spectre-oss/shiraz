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
        snapshot=null;
    }
    private void append(int command){reserve(1);data[size++]=command;}
    private void append(int command,float a,float b){valid(a);valid(b);reserve(3);data[size++]=command;data[size++]=a;data[size++]=b;}
    private void append(int command,float a,float b,float c,float d,float e,float f){
        valid(a);valid(b);valid(c);valid(d);valid(e);valid(f);reserve(7);
        data[size++]=command;data[size++]=a;data[size++]=b;data[size++]=c;data[size++]=d;data[size++]=e;data[size++]=f;
    }
    private void ensureStart() { if (size == 0) moveTo(0,0); }
    float[] commands() { if (snapshot == null) snapshot=Arrays.copyOf(data,size); return snapshot; }
    /** Snapshot for host property animation: distance, x, y triples. */
    public double[] shirazAnimationPoints() { return shirazAnimationPoints(1); }
    public double[] shirazAnimationPoints(final double maxSegment) {
        if(!shirazIsFinite())throw new IllegalArgumentException("Nonfinite animation path");
        if(!Double.isFinite(maxSegment)||maxSegment<=0)throw new IllegalArgumentException("Sampling precision");
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
        Sampler sampler=new Sampler();double sx=0,sy=0;float[] source=commands();
        for(int i=0;i<source.length;){int op=(int)source[i++];
            if(op==1){sx=source[i++];sy=source[i++];sampler.add(sx,sy,true);}
            else if(op==2){sampler.add(source[i++],source[i++],false);}
            else if(op==3){sampler.cubic(sampler.px,sampler.py,source[i],source[i+1],source[i+2],source[i+3],source[i+4],source[i+5],0);i+=6;}
            else sampler.add(sx,sy,false);
        }
        if(points.isEmpty())throw new IllegalArgumentException("Empty animation path");
        double[] result=new double[points.size()];for(int i=0;i<result.length;i++)result[i]=points.get(i);return result;
    }
    public void set(Path other) { if (other == this) return; data=other.commands().clone(); size=data.length; x=other.x; y=other.y; startX=other.startX; startY=other.startY; fill=other.fill; snapshot=null; }
    public void reset() { size=0; x=y=startX=startY=0; snapshot=null; }
    public void rewind() { FillType old=fill; reset(); fill=old; }
    public int shirazCommandCount() { return size; }
    public boolean isEmpty() { return size == 0; }
    public void setFillType(FillType value) { if (value == null) throw new NullPointerException("fill type"); fill=value; }
    public FillType getFillType() { return fill; }
    public boolean isInverseFillType() { return fill.ordinal() >= 2; }
    public void toggleInverseFillType() { fill=FillType.values()[fill.ordinal()^2]; }
    public void moveTo(float x,float y) { append(1,x,y); this.x=startX=x; this.y=startY=y; }
    public void rMoveTo(float dx,float dy) { moveTo(x+dx,y+dy); }
    public void lineTo(float x,float y) { valid(x); valid(y); ensureStart(); append(2,x,y); this.x=x; this.y=y; }
    public void rLineTo(float dx,float dy) { lineTo(x+dx,y+dy); }
    public void cubicTo(float x1,float y1,float x2,float y2,float x3,float y3) { valid(x1);valid(y1);valid(x2);valid(y2);valid(x3);valid(y3); ensureStart(); append(3,x1,y1,x2,y2,x3,y3); x=x3; y=y3; }
    public void rCubicTo(float dx1,float dy1,float dx2,float dy2,float dx3,float dy3) { cubicTo(x+dx1,y+dy1,x+dx2,y+dy2,x+dx3,y+dy3); }
    public void quadTo(float x1,float y1,float x2,float y2) { valid(x1);valid(y1);valid(x2);valid(y2); cubicTo(x+(x1-x)*2/3,y+(y1-y)*2/3,x2+(x1-x2)*2/3,y2+(y1-y2)*2/3,x2,y2); }
    public void rQuadTo(float dx1,float dy1,float dx2,float dy2) { quadTo(x+dx1,y+dy1,x+dx2,y+dy2); }
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
    public void addPath(Path other) {
        if(other==null)throw new NullPointerException("path"); float[] values=other.commands(); if(values.length==0)return;
        if(size+values.length>65536)throw new IllegalStateException("Path exceeds 65536 command values");
        float nextX=other.x,nextY=other.y,nextStartX=other.startX,nextStartY=other.startY;
        if(size+values.length>data.length)data=Arrays.copyOf(data,size+values.length);
        System.arraycopy(values,0,data,size,values.length);size+=values.length;snapshot=null;
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
        if(destination==null)destination=this;
        destination.data=copy;destination.size=copy.length;destination.snapshot=null;destination.fill=fill;
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
        destination.data=copy; destination.size=copy.length; destination.snapshot=null;
        destination.x=nextX; destination.y=nextY; destination.startX=nextStartX; destination.startY=nextStartY; destination.fill=nextFill;
    }
}
