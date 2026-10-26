// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

/** Outline geometry for software view clipping. Paths retain their geometry for clipping. */
public final class Outline {
    private final Rect bounds=new Rect();
    private float radius=Float.NEGATIVE_INFINITY,alpha;
    private boolean oval;
    private Path path;
    public Outline() { }
    public Outline(Outline source){set(source);}
    public void set(Outline source){bounds.set(source.bounds);radius=source.radius;alpha=source.alpha;oval=source.oval;path=source.path==null?null:new Path(source.path);}
    public void setEmpty(){path=null;bounds.setEmpty();radius=Float.NEGATIVE_INFINITY;oval=false;}
    public boolean isEmpty(){return bounds.isEmpty();}
    public boolean canClip(){return !oval;}
    public void setAlpha(float value){if(!Float.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("Outline alpha");alpha=value;}
    public float getAlpha(){return alpha;}
    public void setRect(int l,int t,int r,int b){setRoundRect(l,t,r,b,0);}
    public void setRect(Rect rect){setRect(rect.left,rect.top,rect.right,rect.bottom);}
    public void setRoundRect(int l,int t,int r,int b,float value){if(!Float.isFinite(value)||value<0)throw new IllegalArgumentException("Outline radius");if(l>=r||t>=b){setEmpty();return;}path=null;bounds.set(l,t,r,b);radius=value;oval=false;}
    public void setRoundRect(Rect rect,float value){setRoundRect(rect.left,rect.top,rect.right,rect.bottom,value);}
    public boolean getRect(Rect out){if(isEmpty()||oval||path!=null)return false;out.set(bounds);return true;}
    public float getRadius(){return radius;}
    public void setOval(int l,int t,int r,int b){if(l>=r||t>=b){setEmpty();return;}if((long)r-l==(long)b-t){setRoundRect(l,t,r,b,((long)b-t)/2f);return;}path=null;bounds.set(l,t,r,b);radius=Float.NEGATIVE_INFINITY;oval=true;}
    public void setOval(Rect rect){setOval(rect.left,rect.top,rect.right,rect.bottom);}
    public void setConvexPath(Path value){setPath(value);}
    public void setPath(Path value){if(value==null)throw new NullPointerException();if(value.isEmpty()){setEmpty();return;}path=new Path(value);RectF box=new RectF();path.computeBounds(box,true);bounds.set((int)Math.floor(box.left),(int)Math.floor(box.top),(int)Math.ceil(box.right),(int)Math.ceil(box.bottom));radius=Float.NEGATIVE_INFINITY;oval=false;}
    Path shirazPath(){return path;}
    boolean shirazIsOval(){return oval;}
    Rect shirazBounds(){return bounds;}
    public void offset(int dx,int dy){if(!isEmpty()){bounds.offset(dx,dy);if(path!=null)path.offset(dx,dy);}}
}
