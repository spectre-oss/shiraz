// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

/** CPU rendering with affine paths/shapes and antialiased path clips; bitmap/text transforms remain axis-aligned. */
public class Canvas {
    private Bitmap bitmap;
    private long layerPixels;
    private static final class State {
        float sx = 1, sy = 1, kx, ky, tx, ty; int left, top, right, bottom;
        Region clip;ClipCoverage coverage;Bitmap layerParent;int layerAlpha;Paint layerPaint;State layerBounds;float[] roundClip;
        State() { }
        State(State other) { sx=other.sx; sy=other.sy; kx=other.kx; ky=other.ky; tx=other.tx; ty=other.ty; left=other.left; top=other.top; right=other.right; bottom=other.bottom; clip=other.clip; coverage=other.coverage; }
    }
    private State state = new State();
    private static final int MAX_SAVE_DEPTH=256;
    private final java.util.ArrayList<State> saves = new java.util.ArrayList<State>();
    public int getSaveCount() { return saves.size() + 1; }
    public int getMaximumBitmapWidth() { return 8192; }
    public int getMaximumBitmapHeight() { return 8192; }
    public int save() {
        if (saves.size() >= MAX_SAVE_DEPTH) throw new IllegalStateException("Canvas save depth exceeds "+MAX_SAVE_DEPTH);
        int count = getSaveCount(); saves.add(new State(state)); return count;
    }
    public void restore() {
        if (saves.isEmpty()) throw new IllegalStateException("Canvas restore underflow");
        State restored=saves.remove(saves.size()-1);
        if(restored.layerParent==null){state=restored;return;}
        Bitmap layer=bitmap;bitmap=restored.layerParent;restored.layerParent=null;int alpha=restored.layerAlpha;
        state=new State(restored.layerBounds);state.sx=state.sy=1;state.kx=state.ky=state.tx=state.ty=0;
        try{if(restored.roundClip!=null)maskRoundClip(layer,restored.roundClip);Paint paint=restored.layerPaint;if(paint==null){paint=new Paint();paint.setAlpha(alpha);}drawBitmap(layer,null,new Rect(0,0,layer.width,layer.height),paint);}
        finally{layerPixels-=(long)layer.width*layer.height;layer.recycle();state=restored;}
    }
    public void restoreToCount(int count) {
        if (count < 1 || count > getSaveCount()) throw new IllegalArgumentException("Canvas save count");
        while (getSaveCount() > count) restore();
    }
    public int saveLayer(RectF bounds,Paint paint){
        Paint composite=new Paint();
        if(paint!=null){composite.setAlpha(paint.getAlpha());composite.setColorFilter(paint.getColorFilter());composite.setXfermode(paint.getXfermode());}
        int count=saveLayerAlpha(bounds,255);saves.get(saves.size()-1).layerPaint=composite;return count;
    }
    public int saveLayer(float l,float t,float r,float b,Paint paint){return saveLayer(new RectF(l,t,r,b),paint);}
    public int saveLayer(RectF bounds,Paint paint,int flags){if(flags!=31)throw new UnsupportedOperationException("Only ALL_SAVE_FLAG layers are implemented");return saveLayer(bounds,paint);}
    public int saveLayer(float l,float t,float r,float b,Paint paint,int flags){return saveLayer(new RectF(l,t,r,b),paint,flags);}
    public int saveLayerAlpha(RectF bounds,int alpha){
        if(alpha<0||alpha>255)throw new IllegalArgumentException("Layer alpha");Bitmap parent=target();long pixels=(long)parent.width*parent.height;
        if(layerPixels+pixels>16L*1024*1024)throw new IllegalArgumentException("Canvas alpha layers exceed 16M pixels");
        int count=save();try{if(bounds!=null)clipRect(bounds);Bitmap layer=Bitmap.createBitmap(parent.width,parent.height,Bitmap.Config.ARGB_8888);layer.setDensity(parent.getDensity());State saved=saves.get(saves.size()-1);saved.layerParent=parent;saved.layerAlpha=alpha;saved.layerBounds=new State(state);bitmap=layer;state.clip=null;state.coverage=null;layerPixels+=pixels;return count;}catch(RuntimeException|Error failure){restoreToCount(count);throw failure;}
    }
    public int saveLayerAlpha(float l,float t,float r,float b,int alpha){return saveLayerAlpha(new RectF(l,t,r,b),alpha);}
    public int saveLayerAlpha(RectF bounds,int alpha,int flags){if(flags!=31)throw new UnsupportedOperationException("Only ALL_SAVE_FLAG alpha layers are implemented");return saveLayerAlpha(bounds,alpha);}
    public int saveLayerAlpha(float l,float t,float r,float b,int alpha,int flags){return saveLayerAlpha(new RectF(l,t,r,b),alpha,flags);}
    /** Scoped outline mask, applied once to the complete view subtree on restore. */
    public int shirazSaveOutlineClip(Outline outline){
        if(outline.shirazPath()!=null){int saved=save();clipPath(outline.shirazPath());return saved;}
        Rect rect=new Rect();if(!outline.getRect(rect))throw new IllegalArgumentException("Outline cannot clip");
        float radius=Math.min(outline.getRadius(),Math.min(((long)rect.right-rect.left)/2f,((long)rect.bottom-rect.top)/2f));
        float[] mask={x(rect.left),y(rect.top),x(rect.right),y(rect.bottom),finite(radius*state.sx),finite(radius*state.sy)};
        if(radius==0){int saved=save();clipRect(rect);return saved;}
        int saved=saveLayerAlpha(new RectF(rect),255);saves.get(saves.size()-1).roundClip=mask;return saved;
    }
    private static void maskRoundClip(Bitmap layer,float[] m){
        int l=edge(m[0],layer.width),t=edge(m[1],layer.height),r=edge(m[2],layer.width),b=edge(m[3],layer.height);
        for(int y=0;y<layer.height;y++)for(int x=0;x<layer.width;x++){
            int index=y*layer.width+x,color=layer.pixels[index];if((color>>>24)==0)continue;
            if(x<l||x>=r||y<t||y>=b){layer.pixels[index]=0;continue;}
            int coverage=0;
            for(int sy=0;sy<4;sy++)for(int sx=0;sx<4;sx++){
                double px=x+(sx+.5)/4,py=y+(sy+.5)/4;
                if(px<m[0]||px>=m[2]||py<m[1]||py>=m[3])continue;
                double cx=Math.max(m[0]+m[4],Math.min(m[2]-m[4],px)),cy=Math.max(m[1]+m[5],Math.min(m[3]-m[5],py));
                double dx=m[4]==0?0:(px-cx)/m[4],dy=m[5]==0?0:(py-cy)/m[5];
                if(dx*dx+dy*dy<=1)coverage++;
            }
            layer.pixels[index]=(color&0xffffff)|((((color>>>24)*coverage+8)/16)<<24);
        }
    }
    /** Approximate CPU ambient/spot shadows. No RenderThread light-model parity is claimed. */
    public void shirazDrawOutlineShadow(final Outline outline,final float z,final float opacity){
        if(clipped(new ClipDraw(){public void draw(){shirazDrawOutlineShadow(outline,z,opacity);}}))return;
        if(!Float.isFinite(z)||z<0||z>8192||!Float.isFinite(opacity)||opacity<0||opacity>1)throw new IllegalArgumentException("Shadow geometry");
        if(z==0||opacity==0||outline.isEmpty()||outline.getAlpha()==0)return;
        shadowPass(outline,z*.35f+.5f,0,.16f*opacity*outline.getAlpha());
        shadowPass(outline,z*.5f+.5f,z*.5f,.24f*opacity*outline.getAlpha());
    }
    private void shadowPass(Outline outline,float sigma,float offset,float opacity){
        Bitmap target=target();Rect rect=outline.shirazBounds();double spread=3.0*sigma;
        int l=Math.max(state.left,edge(x((float)(rect.left-spread)),target.width)),r=Math.min(state.right,edge(x((float)(rect.right+spread)),target.width));
        int t=Math.max(state.top,edge(y((float)(rect.top+offset-spread)),target.height)),b=Math.min(state.bottom,edge(y((float)(rect.bottom+offset+spread)),target.height));
        double halfW=((long)rect.right-rect.left)/2.0,halfH=((long)rect.bottom-rect.top)/2.0,cx=rect.left+halfW,cy=rect.top+halfH;
        double radius=Math.min(Math.max(0,outline.getRadius()),Math.min(halfW,halfH));
        boolean oval=outline.shirazIsOval();
        for(int py=t;py<b;py++){
          double dy=Math.abs((py+.5-state.ty)/state.sy-offset-cy),qy=dy-(halfH-radius);
          for(int px=l;px<r;px++){
            double dx=Math.abs((px+.5-state.tx)/state.sx-cx),distance;
            if(oval)distance=(Math.sqrt(dx*dx/(halfW*halfW)+dy*dy/(halfH*halfH))-1)*Math.min(halfW,halfH);
            else{
                double qx=dx-(halfW-radius);
                // Away from a rounded corner one hypot operand is zero.
                // Its exact result is the other operand; avoid the library call.
                double outside=qx<=0?Math.max(0,qy):qy<=0?qx:Math.hypot(qx,qy);
                distance=outside+Math.min(Math.max(qx,qy),0)-radius;
            }
            // Smooth coverage across the edge, with a Gaussian falloff outside it.
            double coverage=distance>=0?.5*Math.exp(-distance*distance/(2*sigma*sigma)):1-.5*Math.exp(-distance*distance/(2*sigma*sigma));
            int alpha=(int)Math.round(255*opacity*coverage);if(alpha==0)continue;
            int index=py*target.width+px;target.pixels[index]=over(alpha<<24,target.pixels[index]);
          }
        }
    }
    private static float finite(float value) { if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite canvas coordinate"); return value; }
    private boolean affine() { return state.kx!=0 || state.ky!=0 || state.sx<=0 || state.sy<=0; }
    private void axisAligned() { if(affine())throw new UnsupportedOperationException("This Canvas operation requires a positive axis-aligned transform"); }
    private float x(float value) { axisAligned(); return finite(finite(value) * state.sx + state.tx); }
    private float y(float value) { axisAligned(); return finite(finite(value) * state.sy + state.ty); }
    private void concat(float a,float b,float c,float d,float tx,float ty) {
        float na=finite(state.sx*a+state.kx*b),nb=finite(state.ky*a+state.sy*b);
        float nc=finite(state.sx*c+state.kx*d),nd=finite(state.ky*c+state.sy*d);
        float nx=finite(state.sx*tx+state.kx*ty+state.tx),ny=finite(state.ky*tx+state.sy*ty+state.ty);
        if((double)na*nd-(double)nb*nc==0)throw new IllegalArgumentException("Canvas transform underflow");
        state.sx=na;state.ky=nb;state.kx=nc;state.sy=nd;state.tx=nx;state.ty=ny;
    }
    public void concat(Matrix matrix) {
        if(matrix==null)return;
        float[] v=new float[9];matrix.getValues(v);for(float f:v)finite(f);
        if(v[6]!=0||v[7]!=0||v[8]!=1)throw new UnsupportedOperationException("Perspective canvas transforms are not implemented");
        concat(v[0],v[3],v[1],v[4],v[2],v[5]);
    }
    public void getMatrix(Matrix ctm) {
        if(ctm==null)throw new NullPointerException("ctm");
        ctm.setValues(new float[]{state.sx,state.kx,state.tx,state.ky,state.sy,state.ty,0,0,1});
    }
    public Matrix getMatrix() { Matrix result=new Matrix();getMatrix(result);return result; }
    public void setMatrix(Matrix matrix) {
        float[] v=new float[9];
        if(matrix==null)new Matrix().getValues(v);else matrix.getValues(v);
        for(float f:v)finite(f);
        if(v[6]!=0||v[7]!=0||v[8]!=1)throw new UnsupportedOperationException("Perspective canvas transforms are not implemented");
        if((double)v[0]*v[4]-(double)v[1]*v[3]==0)throw new IllegalArgumentException("Canvas transform underflow");
        state.sx=v[0];state.kx=v[1];state.tx=v[2];state.ky=v[3];state.sy=v[4];state.ty=v[5];
    }
    protected final void shirazMatrixValues(float[] out){out[0]=state.sx;out[1]=state.kx;out[2]=state.tx;out[3]=state.ky;out[4]=state.sy;out[5]=state.ty;out[6]=out[7]=0;out[8]=1;}
    public Matrix getTotalMatrix() { return getMatrix(); }
    public void translate(float dx,float dy) { concat(1,0,0,1,finite(dx),finite(dy)); }
    public void scale(float sx,float sy) { scale(sx,sy,0,0); }
    public void scale(float sx,float sy,float px,float py) {
        finite(sx);finite(sy);finite(px);finite(py);
        if(sx<=0 || sy<=0)throw new UnsupportedOperationException("Canvas scale must be positive");
        concat(sx,0,0,sy,finite(px*(1-sx)),finite(py*(1-sy)));
    }
    public void rotate(float degrees) { rotate(degrees,0,0); }
    public void rotate(float degrees,float px,float py) {
        finite(degrees);finite(px);finite(py);
        double radians=Math.toRadians(degrees%360);float c=(float)Math.cos(radians),s=(float)Math.sin(radians);
        if(degrees%90==0){c=Math.round(c);s=Math.round(s);}
        concat(c,s,-s,c,finite(px*(1-c)+py*s),finite(py*(1-c)-px*s));
    }
    private static int edge(float coordinate, int limit) { return (int)Math.max(0,Math.min(limit,Math.ceil((double)coordinate-.5))); }
    public Rect getClipBounds(){Rect out=new Rect();getClipBounds(out);return out;}
    public boolean getClipBounds(Rect out){
        if(out==null)throw new NullPointerException("bounds");
        if(state.left>=state.right||state.top>=state.bottom){out.setEmpty();return false;}
        double determinant=(double)state.sx*state.sy-(double)state.kx*state.ky;
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(int i=0;i<4;i++){
            double dx=(i%2==0?state.left:state.right)-(double)state.tx,dy=(i<2?state.top:state.bottom)-(double)state.ty;
            double px=(state.sy*dx-state.kx*dy)/determinant,py=(state.sx*dy-state.ky*dx)/determinant;
            minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);
        }
        out.set((int)Math.floor(minX),(int)Math.floor(minY),(int)Math.ceil(maxX),(int)Math.ceil(maxY));return true;
    }
    public boolean clipRect(float left, float top, float right, float bottom) {
        if(affine())return clipRect(left,top,right,bottom,Region.Op.INTERSECT);
        float l=x(left), t=y(top), r=x(right), b=y(bottom);
        state.left=Math.max(state.left,edge(l,bitmapWidth())); state.top=Math.max(state.top,edge(t,bitmapHeight()));
        state.right=Math.max(state.left,Math.min(state.right,edge(r,bitmapWidth())));
        state.bottom=Math.max(state.top,Math.min(state.bottom,edge(b,bitmapHeight())));
        if(state.clip!=null){
            Region clipped=new Region(state.clip);clipped.op(new Rect(state.left,state.top,state.right,state.bottom),Region.Op.INTERSECT);
            Rect bounds=clipped.getBounds();state.clip=clipped;state.left=bounds.left;state.top=bounds.top;state.right=bounds.right;state.bottom=bounds.bottom;
        }
        if(state.coverage!=null)updateCoverageBounds();
        return state.left < state.right && state.top < state.bottom;
    }
    public boolean clipRect(int l,int t,int r,int b) { return clipRect((float)l,t,r,b); }
    public boolean clipRect(Rect value) { return clipRect(value.left,value.top,value.right,value.bottom); }
    public boolean clipRect(RectF value) { return clipRect(value.left,value.top,value.right,value.bottom); }
    public boolean clipRect(float left,float top,float right,float bottom,Region.Op op) {
        if(op==null)throw new NullPointerException("op");
        if(op==Region.Op.INTERSECT&&!affine())return clipRect(left,top,right,bottom);
        finite(left);finite(top);finite(right);finite(bottom);
        if(affine()){
            Path path=new Path();if(left<right&&top<bottom)path.addRect(left,top,right,bottom,Path.Direction.CW);
            return clipPath(path,op);
        }
        Region shape=new Region(edge(x(left),bitmapWidth()),edge(y(top),bitmapHeight()),edge(x(right),bitmapWidth()),edge(y(bottom),bitmapHeight()));
        return clipRegion(shape,op);
    }
    public boolean clipRect(Rect rect,Region.Op op){return clipRect(rect.left,rect.top,rect.right,rect.bottom,op);}
    public boolean clipRect(RectF rect,Region.Op op){return clipRect(rect.left,rect.top,rect.right,rect.bottom,op);}
    public boolean clipOutRect(float left,float top,float right,float bottom){return clipRect(left,top,right,bottom,Region.Op.DIFFERENCE);}
    public boolean clipOutRect(int left,int top,int right,int bottom){return clipOutRect((float)left,top,right,bottom);}
    public boolean clipOutRect(Rect rect){return clipOutRect(rect.left,rect.top,rect.right,rect.bottom);}
    public boolean clipOutRect(RectF rect){return clipOutRect(rect.left,rect.top,rect.right,rect.bottom);}
    /** Clip using an extra local transform without changing the drawing transform. */
    public boolean shirazClipPath(Path path,float[] extra){
        if(extra==null||extra.length!=6)throw new IllegalArgumentException("Clip matrix length");for(float value:extra)finite(value);
        float sx=state.sx,ky=state.ky,kx=state.kx,sy=state.sy,tx=state.tx,ty=state.ty;
        try{concat(extra[0],extra[1],extra[2],extra[3],extra[4],extra[5]);return clipPath(path);}
        finally{state.sx=sx;state.ky=ky;state.kx=kx;state.sy=sy;state.tx=tx;state.ty=ty;}
    }
    public boolean clipPath(Path path) { return clipPath(path,Region.Op.INTERSECT); }
    public boolean clipPath(Path path,Region.Op op) {
        if(path==null || op==null)throw new NullPointerException("path/op");
        ClipCoverage shape=ClipCoverage.path(path,new float[]{state.sx,state.ky,state.kx,state.sy,state.tx,state.ty},pathClipBounds(path).getBounds());
        return clipCoverage(shape,op);
    }
    private void updateCoverageBounds(){Rect b=state.coverage.nonzeroBounds(new Rect(state.left,state.top,state.right,state.bottom));state.left=b.left;state.top=b.top;state.right=b.right;state.bottom=b.bottom;}
    private boolean clipCoverage(ClipCoverage shape,Region.Op op){
        state.coverage=ClipCoverage.combine(state.coverage,new Rect(state.left,state.top,state.right,state.bottom),state.clip,shape,op);state.clip=null;
        state.left=state.coverage.left;state.top=state.coverage.top;state.right=state.left+state.coverage.width;state.bottom=state.top+state.coverage.height;updateCoverageBounds();return state.left<state.right&&state.top<state.bottom;
    }
    private boolean clipRegion(Region shape,Region.Op op){
        if(state.coverage!=null)return clipCoverage(ClipCoverage.region(shape),op);
        Region current=state.clip==null?new Region(state.left,state.top,state.right,state.bottom):new Region(state.clip);
        current.op(new Rect(state.left,state.top,state.right,state.bottom),Region.Op.INTERSECT);
        current.op(shape,op);Rect bounds=current.getBounds();
        state.clip=current;state.left=bounds.left;state.top=bounds.top;state.right=bounds.right;state.bottom=bounds.bottom;
        return !current.isEmpty();
    }
    private Region pathClipBounds(Path path) {
        if(path.isInverseFillType())return new Region(0,0,bitmapWidth(),bitmapHeight());
        // Bezier curves remain inside their control hull; transform that hull before rasterization.
        float left=Float.POSITIVE_INFINITY,top=left,right=Float.NEGATIVE_INFINITY,bottom=right;
        float[] commands=path.commands();
        for(int at=0;at<commands.length;){
            int code=(int)commands[at++],count=code==3?6:code==4?0:2;
            for(int end=at+count;at<end;){
                float px=commands[at++],py=commands[at++];
                float x=finite(state.sx*px+state.kx*py+state.tx),y=finite(state.ky*px+state.sy*py+state.ty);
                left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
            }
        }
        return new Region((int)Math.max(0,Math.min(bitmapWidth(),Math.floor(left))),(int)Math.max(0,Math.min(bitmapHeight(),Math.floor(top))),(int)Math.max(0,Math.min(bitmapWidth(),Math.ceil(right))),(int)Math.max(0,Math.min(bitmapHeight(),Math.ceil(bottom))));
    }
    private interface ClipDraw { void draw(); }
    private boolean clippedShadow(ClipDraw draw) {
        if(state.clip==null)return false;
        java.util.ArrayList<Rect> rectangles=state.clip.snapshot();
        int w=state.right-state.left,h=state.bottom-state.top;
        // Curved regions contain many spans. Rasterize the shadow once, keeping
        // destination pixels for transfer modes, then publish only covered spans.
        // Keep scratch allocation bounded; large/simple clips use the span path.
        if(rectangles.size()<4||w<=0||h<=0||(long)w*h>1024*1024)return false;
        State original=state;Bitmap destination=target();
        Bitmap scratch=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        try {
            for(int y=0;y<h;y++)System.arraycopy(destination.pixels,(y+original.top)*destination.width+original.left,scratch.pixels,y*w,w);
            State local=new State(original);local.clip=null;local.left=local.top=0;local.right=w;local.bottom=h;
            local.tx-=original.left;local.ty-=original.top;state=local;bitmap=scratch;
            draw.draw();
            for(Rect rect:rectangles){
                int l=Math.max(original.left,rect.left),r=Math.min(original.right,rect.right);
                if(l>=r)continue;
                for(int y=Math.max(original.top,rect.top);y<Math.min(original.bottom,rect.bottom);y++)
                    System.arraycopy(scratch.pixels,(y-original.top)*w+l-original.left,destination.pixels,y*destination.width+l,r-l);
            }
        } finally {state=original;bitmap=destination;scratch.recycle();}
        return true;
    }
    private boolean clipped(ClipDraw draw) {
        if(state.coverage!=null){
            State original=state;Bitmap destination=target();int w=state.right-state.left,h=state.bottom-state.top;
            if(w<=0||h<=0)return true;
            Bitmap scratch=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            try{
                for(int y=0;y<h;y++)System.arraycopy(destination.pixels,(y+original.top)*destination.width+original.left,scratch.pixels,y*w,w);
                State local=new State(original);local.coverage=null;local.clip=null;local.left=local.top=0;local.right=w;local.bottom=h;local.tx-=original.left;local.ty-=original.top;
                state=local;bitmap=scratch;draw.draw();
                ClipCoverage clip=original.coverage;
                shiraz.runtime.NativeBitmap.applyClip(destination.pixels,destination.width,destination.height,scratch.pixels,w,h,original.left,original.top,clip.pixels,clip.width,clip.height,clip.left,clip.top);
            }finally{state=original;bitmap=destination;scratch.recycle();}
            return true;
        }
        if(state.clip==null)return false;
        State original=state;
        try {
            for(Rect rect:original.clip.snapshot()) {
                State part=new State(original);part.clip=null;
                part.left=Math.max(original.left,rect.left);part.top=Math.max(original.top,rect.top);
                part.right=Math.min(original.right,rect.right);part.bottom=Math.min(original.bottom,rect.bottom);
                if(part.left>=part.right || part.top>=part.bottom)continue;
                state=part;draw.draw();
            }
        } finally {state=original;}
        return true;
    }
    private int viewWidth, viewHeight;
    public int shirazPushViewSize(int w,int h){int prev=(viewWidth<<16)|(viewHeight&0xffff);viewWidth=w;viewHeight=h;return prev;}
    public void shirazPopViewSize(int prev){viewWidth=prev>>>16;viewHeight=prev&0xffff;}
    private int bitmapWidth(){return bitmap == null ? 0 : bitmap.width;}
    private int bitmapHeight(){return bitmap == null ? 0 : bitmap.height;}
    public boolean isHardwareAccelerated(){return false;}
    public final boolean shirazIsGpuCanvas(){return this instanceof GpuCanvas;}
    /** Hardware screen surfaces are opt-in; app-created bitmap Canvases keep CPU semantics. */
    public static Canvas shirazScreen(Bitmap bitmap){return shiraz.runtime.NativeGpu.available()?new GpuCanvas(bitmap):new Canvas(bitmap);}
    protected final void shirazHardwareBitmap(Bitmap bitmap){bitmap.requireLive();if(!bitmap.isMutable())throw new IllegalStateException("Bitmap is immutable");this.bitmap=bitmap;state.right=bitmap.width;state.bottom=bitmap.height;}
    public boolean shirazPatternRipple(float[] geometry,int color,int sparkle,Paint paint){return false;}
    public Canvas() { }
    public Canvas(Bitmap bitmap) { setBitmap(bitmap); }
    public void setBitmap(Bitmap bitmap) {
        if(layerPixels!=0)throw new IllegalStateException("Cannot replace bitmap inside an alpha layer");
        if (bitmap != null) bitmap.requireMutable();
        this.bitmap = bitmap; saves.clear(); state = new State(); state.right=bitmapWidth(); state.bottom=bitmapHeight();
    }
    private Bitmap target() {
        if (bitmap == null) throw new IllegalStateException("Canvas has no bitmap");
        bitmap.requireMutable(); return bitmap;
    }
    public float shirazScaleX() { return (float)Math.hypot(state.sx,state.ky); }
    public float shirazScaleY() { return (float)Math.hypot(state.kx,state.sy); }
    public int getDensity() { return bitmap == null ? 0 : bitmap.getDensity(); }
    public int getWidth() { return viewWidth > 0 ? viewWidth : bitmapWidth(); }
    public int getHeight() { return viewHeight > 0 ? viewHeight : bitmapHeight(); }
    private static int over(int src, int dst) {
        int sa = src >>> 24, da = dst >>> 24;
        if (sa == 255) return src;
        if (sa == 0) return da == 0 ? 0 : dst;
        if (da == 0) return src;
        // Most window pixels are opaque. Constant-denominator blending avoids
        // three variable integer divisions without changing straight-alpha rounding.
        if (da == 255) {
            int inverse = 255 - sa;
            int red = (((src >>> 16) & 255) * sa + ((dst >>> 16) & 255) * inverse + 127) / 255;
            int green = (((src >>> 8) & 255) * sa + ((dst >>> 8) & 255) * inverse + 127) / 255;
            int blue = ((src & 255) * sa + (dst & 255) * inverse + 127) / 255;
            return 0xff000000 | (red << 16) | (green << 8) | blue;
        }
        int remainder = (da * (255 - sa) + 127) / 255, alpha = sa + remainder;
        if (alpha == 0) return 0;
        int out = alpha << 24;
        for (int shift = 0; shift <= 16; shift += 8) {
            int s = (src >>> shift) & 255, d = (dst >>> shift) & 255;
            out |= ((s * sa + d * remainder + alpha / 2) / alpha) << shift;
        }
        return out;
    }
    private interface TransferDraw {void draw(Canvas canvas,Paint paint,boolean mask);}
    private boolean transfer(Paint paint,TransferDraw draw){
        if(paint==null)return false;
        if(paint.hasShadowLayer()){
            shadow(paint,draw);Paint clean=new Paint(paint);clean.clearShadowLayer();draw.draw(this,clean,false);return true;
        }
        if(paint.getXfermode()==null)return false;
        PorterDuff.Mode mode=((PorterDuffXfermode)paint.getXfermode()).mode;
        if(mode==PorterDuff.Mode.SRC_OVER)return false;if(mode==PorterDuff.Mode.DST)return true;
        Bitmap dst=target();if((long)dst.width*dst.height>4L*1024*1024)throw new IllegalArgumentException("Transfer surface exceeds 4M pixels");
        Bitmap src=Bitmap.createBitmap(dst.width,dst.height,Bitmap.Config.ARGB_8888),mask=null;
        try{mask=Bitmap.createBitmap(dst.width,dst.height,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(src);c.state=new State(state);Paint p=new Paint(paint);p.setXfermode(null);draw.draw(c,p,false);
            c=new Canvas(mask);c.state=new State(state);p.setColor(Color.WHITE);p.setColorFilter(null);draw.draw(c,p,true);
            shiraz.runtime.NativeBitmap.composite(dst.pixels,src.pixels,mask.pixels,mode.ordinal());return true;
        }finally{src.recycle();if(mask!=null)mask.recycle();}
    }
    private void shadow(Paint paint,TransferDraw draw){
        axisAligned();
        int color=paint.getShadowLayerColor();int alpha=color>>>24;if(alpha==255)alpha=paint.getAlpha();int mode=paint.getXfermode()==null?PorterDuff.Mode.SRC_OVER.ordinal():((PorterDuffXfermode)paint.getXfermode()).mode.ordinal();if(state.left==state.right||state.top==state.bottom||mode==PorterDuff.Mode.DST.ordinal()||alpha==0&&mode==PorterDuff.Mode.SRC_OVER.ordinal())return;color=(color&0xffffff)|(alpha<<24);
        float sigma=paint.getShadowLayerRadius()*.57735f+.5f;float sx=sigma*state.sx,sy=sigma*state.sy;
        if(!Float.isFinite(sx)||!Float.isFinite(sy)||sx>512||sy>512)throw new IllegalArgumentException("Scaled shadow radius bound");
        int rx=(int)Math.round((Math.sqrt(1+4*sx*sx)-1)/2),ry=(int)Math.round((Math.sqrt(1+4*sy*sy)-1)/2);int pad=3*Math.max(rx,ry)+2;Bitmap dst=target();long w=(long)state.right-state.left+2*pad,h=(long)state.bottom-state.top+2*pad;
        if(w>8192||h>8192||w*h>4L*1024*1024)throw new IllegalArgumentException("Shadow mask exceeds 4M pixels");
        // Three box-blur passes have finite support: retain a halo around only the output clip.
        Bitmap mask=Bitmap.createBitmap((int)w,(int)h,Bitmap.Config.ARGB_8888);try{Canvas c=new Canvas(mask);c.state=new State(state);c.state.left=c.state.top=0;c.state.right=(int)w;c.state.bottom=(int)h;c.state.tx=finite(state.tx+pad-state.left+paint.getShadowLayerDx()*state.sx);c.state.ty=finite(state.ty+pad-state.top+paint.getShadowLayerDy()*state.sy);Paint p=new Paint(paint);p.clearShadowLayer();p.setXfermode(null);p.setColor(Color.WHITE);p.setColorFilter(null);draw.draw(c,p,false);
            shiraz.runtime.NativeBitmap.shadow(dst.pixels,dst.width,dst.height,mask.pixels,(int)w,(int)h,pad,rx,ry,color,mode,state.left,state.top,state.right,state.bottom);
        }finally{mask.recycle();}
    }
    /** Matrix bitmap draws use source pixels, independent of bitmap/display density. */
    public void drawBitmap(Bitmap source, Matrix matrix, Paint paint) {
        if(source==null||matrix==null)throw new NullPointerException("source/matrix");
        source.requireLive();int count=save();
        try {concat(matrix);drawBitmap(source,null,new RectF(0,0,source.width,source.height),paint);}
        finally {restoreToCount(count);}
    }
    public void drawBitmap(Bitmap source, float left, float top, Paint paint) {
        if (source == null) throw new NullPointerException("source");
        if (viewWidth > 0 && left == 0f && top == 0f && paint == null && source.width > 0 && source.height > 0) {
            int saved = save();
            try {
                state.sx = 1f; state.sy = 1f;
                int ox = (viewWidth - source.width) / 2, oy = (viewHeight - source.height) / 2;
                drawBitmap(source, null, new RectF(ox, oy, ox + source.width, oy + source.height), null);
            } finally {
                restoreToCount(saved);
            }
            return;
        }
        drawBitmap(source, null, new RectF(left, top, left + source.getScaledWidth(getDensity()), top + source.getScaledHeight(getDensity())), paint);
    }
    public void drawBitmap(Bitmap source, Rect from, Rect to, Paint paint) { if (to == null) throw new NullPointerException("destination"); drawBitmap(source, from, new RectF(to), paint); }
    public void drawBitmap(final Bitmap source, final Rect from, final RectF to, final Paint paint) {
        if(clipped(new ClipDraw(){public void draw(){drawBitmap(source,from,to,paint);}}))return;
        if(paint!=null&&(paint.getXfermode()!=null||paint.hasShadowLayer())&&transfer(paint,new TransferDraw(){public void draw(Canvas c,Paint p,boolean mask){if(mask){p.setStyle(Paint.Style.FILL);p.setAntiAlias(false);p.setPathEffect(null);c.drawRect(to.left,to.top,to.right,to.bottom,p);}else c.drawBitmap(source,from,to,p);}}))return;
        Bitmap destination = target(); if (source == null || to == null) throw new NullPointerException("source/destination"); source.requireLive();
        Rect region = from == null ? new Rect(0, 0, source.width, source.height) : from;
        if(affine()) {
            if(region.left<0||region.top<0||region.right>source.width||region.bottom>source.height||region.width()<=0||region.height()<=0)throw new IllegalArgumentException("Bitmap source bounds");
            if(to.width()<=0||to.height()<=0)return;
            Bitmap sampled=source;boolean cropped=region.left!=0||region.top!=0||region.right!=source.width||region.bottom!=source.height;
            if(cropped){sampled=Bitmap.createBitmap(region.width(),region.height(),source.getConfig());int[] pixels=new int[region.width()*region.height()];source.getPixels(pixels,0,region.width(),region.left,region.top,region.width(),region.height());sampled.setPixels(pixels,0,region.width(),0,0,region.width(),region.height());}
            try {
                Paint transformed=paint==null?new Paint(Paint.FILTER_BITMAP_FLAG|Paint.ANTI_ALIAS_FLAG):new Paint(paint);transformed.setStyle(Paint.Style.FILL);
                BitmapShader shader=new BitmapShader(sampled,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);Matrix mapping=new Matrix();mapping.setScale(to.width()/sampled.width,to.height()/sampled.height);mapping.postTranslate(to.left,to.top);shader.setLocalMatrix(mapping);transformed.setShader(shader);drawRect(to.left,to.top,to.right,to.bottom,transformed);
            }finally{if(cropped)sampled.recycle();}
            return;
        }
        ColorFilter filter=paint == null ? null : paint.getColorFilter();
        shiraz.runtime.NativeBitmap.drawEffects(destination.pixels, destination.width, destination.height, source.colorPixels(paint==null?Color.BLACK:paint.getColor()), source.width, source.height,
            region.left, region.top, region.right, region.bottom, x(to.left), y(to.top), x(to.right), y(to.bottom), paint != null && paint.isFilterBitmap(), paint == null ? 255 : paint.getAlpha(), state.left,state.top,state.right,state.bottom,ColorFilter.shirazColor(filter),ColorFilter.shirazMode(filter),ColorFilter.shirazMatrix(filter));
    }
    public void drawText(String text, float x, float y, Paint paint) {
        if(text==null||paint==null)throw new NullPointerException("text/paint");
        // Android's raster glyph placement discards NaN/infinite positions.
        if(!Float.isFinite(x)||!Float.isFinite(y))return;

        if(paint!=null&&paint.getShader()!=null)throw new UnsupportedOperationException("Text shader rendering is not implemented");
        final float clipX=x,clipY=y;
        if(clipped(new ClipDraw(){public void draw(){drawText(text,clipX,clipY,paint);}}))return;
        final float textX=x,textY=y;if(paint!=null&&(paint.getXfermode()!=null||paint.hasShadowLayer())&&transfer(paint,new TransferDraw(){public void draw(Canvas c,Paint p,boolean mask){c.drawText(text,textX,textY,p);}}))return;
        Bitmap b = target();
        if (text == null || paint == null) throw new NullPointerException("text/paint");
        if(paint.getStyle()!=Paint.Style.FILL && paint.getPathEffect()!=null)throw new UnsupportedOperationException("Text stroke path effects");
        if (paint.getTextAlign() != Paint.Align.LEFT) {
            float advance = paint.measureText(text);
            x -= paint.getTextAlign() == Paint.Align.CENTER ? advance / 2 : advance;
        }
        if(state.sx!=1||state.sy!=1||state.kx!=0||state.ky!=0){
        shiraz.runtime.NativeText.drawAffineSubpixelResolved(b.pixels, b.width, b.height, text, x, y, paint.getTextSize(), paint.shirazFilteredColor(), paint.isAntiAlias(), paint.shirazTypeface().shirazFamily(), paint.shirazTypeface().getStyle(),state.left,state.top,state.right,state.bottom,paint.isFakeBoldText(),paint.getTextSkewX(),paint.shirazVariationTags(),paint.shirazVariationValues(),paint.getLetterSpacing(),paint.getStyle().ordinal(),paint.getStrokeWidth(),paint.getStrokeCap().ordinal(),paint.getStrokeJoin().ordinal(),paint.getStrokeMiter(),paint.shirazTextHinting(),paint.getTextScaleX(),paint.shirazBidiLevels(),new float[]{state.sx,state.ky,state.kx,state.sy,state.tx,state.ty},paint.isSubpixelText());
        }else{
        shiraz.runtime.NativeText.drawSubpixelResolved(b.pixels, b.width, b.height, text, x(x), y(y), paint.getTextSize(), paint.shirazFilteredColor(), paint.isAntiAlias(), paint.shirazTypeface().shirazFamily(), paint.shirazTypeface().getStyle(),state.left,state.top,state.right,state.bottom,paint.isFakeBoldText(),paint.getTextSkewX(),paint.shirazVariationTags(),paint.shirazVariationValues(),paint.getLetterSpacing(),paint.getStyle().ordinal(),paint.getStrokeWidth(),paint.getStrokeCap().ordinal(),paint.getStrokeJoin().ordinal(),paint.getStrokeMiter(),paint.shirazTextHinting(),paint.getTextScaleX(),paint.shirazBidiLevels(),paint.isSubpixelText());
        }
        if(paint.isUnderlineText()&&!text.isEmpty()){
            float advance=paint.measureText(text),left=textX;if(paint.getTextAlign()==Paint.Align.CENTER)left-=advance/2;else if(paint.getTextAlign()==Paint.Align.RIGHT)left-=advance;
            float top=textY+paint.getTextSize()/9f;drawRect(left,top,left+advance,top+Math.max(1,paint.getTextSize()/18f),paint);
        }
    }
    public void drawTextOnPath(final String text,final Path path,final float hOffset,final float vOffset,final Paint paint){
        if(text==null||path==null||paint==null)throw new NullPointerException("text/path/paint");finite(hOffset);finite(vOffset);
        if(paint.getShader()!=null)throw new UnsupportedOperationException("Text path shader rendering");
        if(paint.isUnderlineText())throw new UnsupportedOperationException("Text path underline decoration");
        if(paint.getStyle()!=Paint.Style.FILL&&paint.getPathEffect()!=null)throw new UnsupportedOperationException("Text path stroke effects");
        if(clipped(new ClipDraw(){public void draw(){drawTextOnPath(text,path,hOffset,vOffset,paint);}}))return;
        if((paint.getXfermode()!=null||paint.hasShadowLayer())&&transfer(paint,new TransferDraw(){public void draw(Canvas canvas,Paint value,boolean mask){canvas.drawTextOnPath(text,path,hOffset,vOffset,value);}}))return;
        if(text.isEmpty())return;Bitmap bitmap=target();PathMeasure measure=new PathMeasure(path,false);float offset=hOffset;
        if(paint.getTextAlign()!=Paint.Align.LEFT){float remaining=measure.getLength()-paint.measureText(text);offset+=paint.getTextAlign()==Paint.Align.CENTER?remaining/2:remaining;}
        shiraz.runtime.NativeText.drawPathSubpixelResolved(bitmap.pixels,bitmap.width,bitmap.height,text,offset,vOffset,paint.getTextSize(),paint.shirazFilteredColor(),paint.isAntiAlias(),paint.shirazTypeface().shirazFamily(),paint.shirazTypeface().getStyle(),state.left,state.top,state.right,state.bottom,paint.isFakeBoldText(),paint.getTextSkewX(),paint.shirazVariationTags(),paint.shirazVariationValues(),paint.getLetterSpacing(),paint.getStyle().ordinal(),paint.getStrokeWidth(),paint.getStrokeCap().ordinal(),paint.getStrokeJoin().ordinal(),paint.getStrokeMiter(),paint.shirazTextHinting(),paint.getTextScaleX(),paint.shirazBidiLevels(),measure,new float[]{state.sx,state.ky,state.kx,state.sy,state.tx,state.ty},paint.isSubpixelText());
    }
    public void drawTextOnPath(char[] text,int index,int count,Path path,float hOffset,float vOffset,Paint paint){
        if(text==null)throw new NullPointerException("text");if(index<0||count<0||index>text.length-count)throw new ArrayIndexOutOfBoundsException();drawTextOnPath(new String(text,index,count),path,hOffset,vOffset,paint);
    }
    public void drawText(String text, int start, int end, float x, float y, Paint paint) { drawText(text.substring(start, end), x, y, paint); }
    public void drawText(CharSequence text, int start, int end, float x, float y, Paint paint) { drawText(text.subSequence(start, end).toString(), x, y, paint); }
    public void drawText(char[] text, int index, int count, float x, float y, Paint paint) {
        if (text == null) throw new NullPointerException("text");
        if (index < 0 || count < 0 || index > text.length - count) throw new IndexOutOfBoundsException();
        drawText(new String(text, index, count), x, y, paint);
    }
    public void drawLines(float[] points,Paint paint){if(points==null)throw new NullPointerException("points");drawLines(points,0,points.length,paint);}
    public void drawLines(float[] points,int offset,int count,Paint paint){
        if(points==null||paint==null)throw new NullPointerException("points/paint");
        if(offset<0||count<0||offset>points.length-count)throw new ArrayIndexOutOfBoundsException();
        if(count>65536)throw new IllegalArgumentException("Line batch exceeds 65536 coordinates");
        int end=offset+(count/4)*4;for(int i=offset;i<end;i++)finite(points[i]);
        for(int i=offset;i<end;i+=4)drawLine(points[i],points[i+1],points[i+2],points[i+3],paint);
    }
    public void drawLine(float startX,float startY,float stopX,float stopY,Paint paint){
        if(paint==null)throw new NullPointerException("paint");finite(startX);finite(startY);finite(stopX);finite(stopY);
        Paint stroke=new Paint(paint);stroke.setStyle(Paint.Style.STROKE);Path path=new Path();
        if(startX==stopX&&startY==stopY&&paint.getStrokeCap()==Paint.Cap.SQUARE){
            if(paint.getPathEffect()!=null)throw new UnsupportedOperationException("Zero-length square line path effects are not implemented");
            float width=paint.getStrokeWidth();if(width==0){double sx=Math.hypot(state.sx,state.ky),sy=Math.hypot(state.kx,state.sy);if(Math.abs(sx-sy)>1e-6*Math.max(sx,sy)||Math.abs((double)state.sx*state.kx+(double)state.ky*state.sy)>1e-6*sx*sy)throw new UnsupportedOperationException("Anisotropic hairline path is not implemented");width=sx==0?1:(float)(1/sx);}
            float half=width/2;path.addRect(startX-half,startY-half,startX+half,startY+half,Path.Direction.CW);stroke.setStyle(Paint.Style.FILL);
        }else{path.moveTo(startX,startY);path.lineTo(stopX,stopY);}
        drawPath(path,stroke);
    }
    public void drawPath(Path path,Paint paint) { shirazDrawPath(path,paint,null); }
    public void shirazDrawPath(Path path,final Paint paint,final float[] extra) {
        if(path==null||paint==null)throw new NullPointerException("path/paint");
        // AOSP SkCanvas::onDrawPath rejects the whole non-finite path, including
        // inverse fills, before effects or native rasterization.
        if(!path.shirazIsFinite())return;
        if(paint!=null&&paint.getShader()!=null&&(paint.getColorFilter()!=null&&!(paint.getShader() instanceof BitmapShader)||paint.hasShadowLayer()||paint.getXfermode()!=null))return;
        final Path clippedPath=path;
        if(paint.hasShadowLayer()&&clippedShadow(new ClipDraw(){public void draw(){shirazDrawPath(clippedPath,paint,extra);}}))return;
        if(clipped(new ClipDraw(){public void draw(){shirazDrawPath(clippedPath,paint,extra);}}))return;
        final Path transferPath=path;if(paint!=null&&(paint.getXfermode()!=null||paint.hasShadowLayer())&&transfer(paint,new TransferDraw(){public void draw(Canvas c,Paint p,boolean mask){c.shirazDrawPath(transferPath,p,extra);}}))return;
        Bitmap b=target(); if (path == null || paint == null) throw new NullPointerException("path/paint");
        if (extra != null && extra.length != 6) throw new IllegalArgumentException("Path matrix length");
        float[] m=extra == null ? new float[]{state.sx,state.ky,state.kx,state.sy,state.tx,state.ty} : new float[]{
            finite(state.sx*extra[0]+state.kx*extra[1]),finite(state.ky*extra[0]+state.sy*extra[1]),
            finite(state.sx*extra[2]+state.kx*extra[3]),finite(state.ky*extra[2]+state.sy*extra[3]),
            finite(state.sx*extra[4]+state.kx*extra[5]+state.tx),finite(state.ky*extra[4]+state.sy*extra[5]+state.ty)};
        PathEffect effect=paint.getPathEffect(); PathEffect.shirazValidate(effect);
        DashPathEffect dash=effect instanceof DashPathEffect ? (DashPathEffect)effect : null;
        if(effect instanceof CornerPathEffect)path=((CornerPathEffect)effect).apply(path);
        Shader source=paint.getShader();if(source!=null&&!(source instanceof BitmapShader)&&!(source instanceof LinearGradient)&&!(source instanceof RadialGradient))throw new UnsupportedOperationException("Shader type is not implemented");
        if(source instanceof LinearGradient||source instanceof RadialGradient){
            float[] points,positions;int[] colors;Shader.TileMode tile;
            if(source instanceof LinearGradient){LinearGradient g=(LinearGradient)source;points=g.points;positions=g.positions;colors=g.colors;tile=g.tile;}
            else{RadialGradient g=(RadialGradient)source;points=g.points;positions=g.positions;colors=g.colors;tile=g.tile;}
            shiraz.runtime.NativePath.drawGradient(b.pixels,b.width,b.height,path.commands(),path.getFillType().ordinal(),paint.getAlpha()<<24,paint.getStyle().ordinal(),paint.getStrokeWidth(),paint.getStrokeCap().ordinal(),paint.getStrokeJoin().ordinal(),paint.getStrokeMiter(),paint.isAntiAlias(),m,state.left,state.top,state.right,state.bottom,dash==null?null:dash.shirazIntervals(),dash==null?0:dash.shirazPhase(),points,colors,positions,tile.ordinal(),source.inverse(m));return;
        }
        BitmapShader shader=(BitmapShader)source;if(shader!=null)shader.bitmap.requireLive();
        shiraz.runtime.NativePath.drawShader(b.pixels,b.width,b.height,path.commands(),path.getFillType().ordinal(),shader==null?paint.shirazFilteredColor():paint.getAlpha()<<24,paint.getStyle().ordinal(),paint.getStrokeWidth(),paint.getStrokeCap().ordinal(),paint.getStrokeJoin().ordinal(),paint.getStrokeMiter(),paint.isAntiAlias(),m,state.left,state.top,state.right,state.bottom,dash==null?null:dash.shirazIntervals(),dash==null?0:dash.shirazPhase(),shader==null?null:shader.bitmap.colorPixels(paint.getColor()),shader==null?0:shader.bitmap.width,shader==null?0:shader.bitmap.height,shader==null?0:shader.tileX.ordinal(),shader==null?0:shader.tileY.ordinal(),shader==null?null:shader.inverse(m),paint.isFilterBitmap(),ColorFilter.shirazColor(shader==null?null:paint.getColorFilter()),ColorFilter.shirazMode(shader==null?null:paint.getColorFilter()),ColorFilter.shirazMatrix(shader==null?null:paint.getColorFilter()));
    }
    public void drawRoundRect(RectF rect,float rx,float ry,Paint paint){if(rect==null)throw new NullPointerException("rect");drawRoundRect(rect.left,rect.top,rect.right,rect.bottom,rx,ry,paint);}
    public void drawRoundRect(float left,float top,float right,float bottom,float rx,float ry,Paint paint){if(paint==null)throw new NullPointerException("paint");Path p=new Path();p.addRoundRect(left,top,right,bottom,rx,ry,Path.Direction.CW);drawPath(p,paint);}
    public void drawOval(RectF rect,Paint paint){if(rect==null)throw new NullPointerException("rect");drawOval(rect.left,rect.top,rect.right,rect.bottom,paint);}
    public void drawOval(float left,float top,float right,float bottom,Paint paint){if(paint==null)throw new NullPointerException("paint");Path p=new Path();p.addOval(left,top,right,bottom,Path.Direction.CW);drawPath(p,paint);}
    public void drawArc(RectF oval,float start,float sweep,boolean useCenter,Paint paint){if(oval==null)throw new NullPointerException("oval");drawArc(oval.left,oval.top,oval.right,oval.bottom,start,sweep,useCenter,paint);}
    public void drawArc(float left,float top,float right,float bottom,float start,float sweep,boolean useCenter,Paint paint){
        if(paint==null)throw new NullPointerException("paint");
        if(!Float.isFinite(start)||!Float.isFinite(sweep))throw new IllegalArgumentException("Nonfinite arc angle");
        if(left>=right||top>=bottom||sweep==0)return;
        if(Math.abs(sweep)>=360){drawOval(left,top,right,bottom,paint);return;}
        Path path=new Path();if(useCenter)path.moveTo(left+(right-left)/2,top+(bottom-top)/2);
        path.arcTo(left,top,right,bottom,start,sweep,!useCenter);if(useCenter)path.close();drawPath(path,paint);
    }
    public void drawCircle(float cx,float cy,float radius,Paint paint){if(paint==null)throw new NullPointerException("paint");Path p=new Path();p.addCircle(cx,cy,radius,Path.Direction.CW);drawPath(p,paint);}
    public void drawColor(final int color) {
        if(clipped(new ClipDraw(){public void draw(){drawColor(color);}}))return;
        Bitmap b = target();
        shiraz.runtime.NativeBitmap.fillRect(b.pixels,b.width,b.height,state.left,state.top,state.right,state.bottom,color,false,state.left,state.top,state.right,state.bottom);
    }
    public void drawRect(RectF rect,Paint paint){if(rect==null)throw new NullPointerException("rect");drawRect(rect.left,rect.top,rect.right,rect.bottom,paint);}
    public void drawRect(Rect rect,Paint paint){if(rect==null)throw new NullPointerException("rect");drawRect(rect.left,rect.top,rect.right,rect.bottom,paint);}
    public void drawRect(final float left, final float top, final float right, final float bottom, final Paint paint) {
        if(paint!=null&&paint.getShader()!=null&&(paint.getColorFilter()!=null&&!(paint.getShader() instanceof BitmapShader)||paint.hasShadowLayer()||paint.getXfermode()!=null))return;
        if(clipped(new ClipDraw(){public void draw(){drawRect(left,top,right,bottom,paint);}}))return;
        if(paint!=null&&(paint.getXfermode()!=null||paint.hasShadowLayer())&&transfer(paint,new TransferDraw(){public void draw(Canvas c,Paint p,boolean mask){c.drawRect(left,top,right,bottom,p);}}))return;
        Bitmap b = target();
        if (paint == null) throw new NullPointerException("paint");
        for (float value : new float[]{left, top, right, bottom})
            if (Float.isNaN(value) || Float.isInfinite(value)) throw new IllegalArgumentException("Non-finite rectangle");
        if (left >= right || top >= bottom) return;
        if(paint.getShader()!=null || affine() || paint.getPathEffect()!=null && (paint.getStyle()!=Paint.Style.FILL||paint.getPathEffect() instanceof CornerPathEffect)) {Path path=new Path();path.addRect(left,top,right,bottom,Path.Direction.CW);drawPath(path,paint);return;}
        int filteredColor=paint.shirazFilteredColor();
        if(paint.getStyle()==Paint.Style.FILL){shiraz.runtime.NativeBitmap.fillRect(b.pixels,b.width,b.height,x(left),y(top),x(right),y(bottom),filteredColor,paint.isAntiAlias(),state.left,state.top,state.right,state.bottom);return;}
        boolean stroke = paint.getStyle() != Paint.Style.FILL;
        double half = stroke ? (paint.getStrokeWidth() == 0 ? .5 : paint.getStrokeWidth() / 2.0) : 0;
        boolean aa = paint.isAntiAlias();
        if (stroke && paint.getStrokeWidth() == 0) {
            if (state.sx != state.sy) throw new UnsupportedOperationException("Anisotropic hairline stroke is not implemented");
            half=.5/state.sx;
        }
        double outerLeft = x((float)(left-half)), outerTop = y((float)(top-half)), outerRight = x((float)(right+half)), outerBottom = y((float)(bottom+half));
        int x0 = Math.max(state.left, (int)(aa ? Math.floor(outerLeft) : Math.ceil(outerLeft - .5)));
        int y0 = Math.max(state.top, (int)(aa ? Math.floor(outerTop) : Math.ceil(outerTop - .5)));
        int x1 = Math.min(state.right, (int)(aa ? Math.ceil(outerRight) : Math.ceil(outerRight - .5)));
        int y1 = Math.min(state.bottom, (int)(aa ? Math.ceil(outerBottom) : Math.ceil(outerBottom - .5)));
        for (int y = y0; y < y1; ++y) for (int x = x0; x < x1; ++x) {
            int coverage = 0, grid = aa ? 4 : 1;
            for (int sy = 0; sy < grid; ++sy) for (int sx = 0; sx < grid; ++sx)
                if (rectangleContains((x + (sx + .5) / grid - state.tx)/state.sx, (y + (sy + .5) / grid - state.ty)/state.sy, left, top, right, bottom, half, paint)) ++coverage;
            if (coverage != 0) {
                int color = filteredColor;
                color = (color & 0xffffff) | (((color >>> 24) * coverage + grid * grid / 2) / (grid * grid) << 24);
                int i = y * b.width + x; b.pixels[i] = over(color, b.pixels[i]);
            }
        }
    }
    private static boolean rectangleContains(double x, double y, double left, double top, double right, double bottom, double half, Paint paint) {
        if (x < left - half || x >= right + half || y < top - half || y >= bottom + half) return false;
        if (paint.getStyle() == Paint.Style.FILL) return true;
        double dx = Math.max(Math.max(left - x, x - right), 0), dy = Math.max(Math.max(top - y, y - bottom), 0);
        if (dx > 0 && dy > 0) {
            if (paint.getStrokeJoin() == Paint.Join.ROUND && dx * dx + dy * dy > half * half) return false;
            if ((paint.getStrokeJoin() == Paint.Join.BEVEL || (paint.getStrokeJoin() == Paint.Join.MITER && paint.getStrokeMiter() < Math.sqrt(2)))
                    && dx + dy > half) return false;
        }
        return paint.getStyle() == Paint.Style.FILL_AND_STROKE || !(x >= left + half && x < right - half && y >= top + half && y < bottom - half);
    }
}
