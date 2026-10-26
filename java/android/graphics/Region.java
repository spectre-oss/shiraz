// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
import java.util.*;
import android.os.Parcel;
import android.os.Parcelable;

/** Bounded integer regions represented by disjoint, vertically merged spans. */
public class Region implements Parcelable {
 public enum Op {DIFFERENCE,INTERSECT,UNION,XOR,REVERSE_DIFFERENCE,REPLACE}
 private ArrayList<Rect> rects=new ArrayList<Rect>();
 private static final int LIMIT=4096;
 public Region(){}public Region(Region other){set(other);}public Region(Rect r){set(r);}public Region(int l,int t,int r,int b){set(l,t,r,b);}
 public void setEmpty(){rects.clear();}public boolean set(Region other){rects=other.snapshot();return !isEmpty();}
 ArrayList<Rect> snapshot(){ArrayList<Rect> copy=new ArrayList<Rect>();for(Rect r:rects)copy.add(new Rect(r));return copy;}
 public boolean set(Rect r){return set(r.left,r.top,r.right,r.bottom);}public boolean set(int l,int t,int r,int b){ArrayList<Rect> next=new ArrayList<Rect>();if(l<r&&t<b)next.add(new Rect(l,t,r,b));rects=next;return !isEmpty();}
 public boolean isEmpty(){return rects.isEmpty();}public boolean isRect(){return rects.size()==1;}public boolean isComplex(){return rects.size()>1;}
 public Rect getBounds(){Rect out=new Rect();getBounds(out);return out;}public boolean getBounds(Rect out){if(isEmpty()){out.set(0,0,0,0);return false;}out.set(rects.get(0));for(Rect r:rects){out.left=Math.min(out.left,r.left);out.top=Math.min(out.top,r.top);out.right=Math.max(out.right,r.right);out.bottom=Math.max(out.bottom,r.bottom);}return true;}
 public boolean contains(int x,int y){for(int i=0,n=rects.size();i<n;i++){Rect r=rects.get(i);if(x>=r.left&&x<r.right&&y>=r.top&&y<r.bottom)return true;}return false;}
 public boolean quickContains(Rect r){return quickContains(r.left,r.top,r.right,r.bottom);}public boolean quickContains(int l,int t,int r,int b){if(!isRect()||l>=r||t>=b)return false;Rect a=rects.get(0);return l>=a.left&&t>=a.top&&r<=a.right&&b<=a.bottom;}
 public boolean quickReject(Rect r){return quickReject(r.left,r.top,r.right,r.bottom);}public boolean quickReject(int l,int t,int r,int b){if(l>=r||t>=b)return true;for(Rect a:rects)if(a.left<r&&l<a.right&&a.top<b&&t<a.bottom)return false;return true;}
 public boolean quickReject(Region other){return isEmpty()||other.isEmpty()||quickReject(other.getBounds());}
 public void translate(int dx,int dy){translate(dx,dy,this);}public void translate(int dx,int dy,Region dst){ArrayList<Rect> next=new ArrayList<Rect>();for(Rect r:rects)next.add(new Rect(Math.addExact(r.left,dx),Math.addExact(r.top,dy),Math.addExact(r.right,dx),Math.addExact(r.bottom,dy)));(dst==null?this:dst).rects=next;}
 public final boolean union(Rect r){return op(r,Op.UNION);}public boolean op(Rect r,Op op){return op(this,new Region(r),op);}public boolean op(int l,int t,int r,int b,Op op){return op(new Rect(l,t,r,b),op);}public boolean op(Region r,Op op){return op(this,r,op);}public boolean op(Rect r,Region other,Op op){return op(new Region(r),other,op);}
 private static boolean selected(boolean a,boolean b,Op op){switch(op){case DIFFERENCE:return a&&!b;case INTERSECT:return a&&b;case UNION:return a||b;case XOR:return a!=b;case REVERSE_DIFFERENCE:return b&&!a;default:return b;}}
 private static void event(TreeMap<Integer,int[]> events,int x,int side,int delta){int[] e=events.get(x);if(e==null){e=new int[2];events.put(x,e);}e[side]+=delta;}
 private static void span(ArrayList<Rect> out,Map<Long,Rect> previous,Map<Long,Rect> current,int l,int r,int t,int b){long key=((long)l<<32)^(r&0xffffffffL);Rect old=previous.get(key);if(old!=null&&old.bottom==t){old.bottom=b;current.put(key,old);}else{if(out.size()>=LIMIT)throw new IllegalArgumentException("Region exceeds 4096 spans");Rect rect=new Rect(l,t,r,b);out.add(rect);current.put(key,rect);}}
 public boolean op(Region a,Region b,Op op){if(op==null)throw new NullPointerException("op");if(op==Op.REPLACE)return set(b);TreeSet<Integer> ys=new TreeSet<Integer>();for(Rect r:a.rects){ys.add(r.top);ys.add(r.bottom);}for(Rect r:b.rects){ys.add(r.top);ys.add(r.bottom);}ArrayList<Rect> out=new ArrayList<Rect>();Map<Long,Rect> previous=new HashMap<Long,Rect>();Integer top=null;long work=0;
  for(int bottom:ys){if(top==null){top=bottom;continue;}TreeMap<Integer,int[]> events=new TreeMap<Integer,int[]>();int side=0;for(Region region:new Region[]{a,b}){for(Rect r:region.rects){if(++work>4000000)throw new IllegalArgumentException("Region operation work limit");if(r.top<=top&&r.bottom>=bottom){event(events,r.left,side,1);event(events,r.right,side,-1);}}side++;}
   int ca=0,cb=0,start=0;boolean active=false;Map<Long,Rect> current=new HashMap<Long,Rect>();for(Map.Entry<Integer,int[]> e:events.entrySet()){ca+=e.getValue()[0];cb+=e.getValue()[1];boolean next=selected(ca>0,cb>0,op);if(!active&&next)start=e.getKey();else if(active&&!next)span(out,previous,current,start,e.getKey(),top,bottom);active=next;}previous=current;top=bottom;
  }rects=out;return !isEmpty();
 }
 public boolean setPath(Path path,Region clip){return shirazSetPath(path,clip,null);}
 boolean shirazSetPath(Path path,Region clip,float[] matrix){if(path==null||clip==null)throw new NullPointerException("path/clip");Rect bounds=clip.getBounds();boolean rectangularClip=clip.isRect();long w=(long)bounds.right-bounds.left,h=(long)bounds.bottom-bounds.top;if(w==0||h==0){setEmpty();return false;}if(w>8192||h>8192||w*h>4L*1024*1024||w*h*Math.max(1,clip.rects.size())>16000000)throw new IllegalArgumentException("Region path raster bounds");Bitmap mask=Bitmap.createBitmap((int)w,(int)h,Bitmap.Config.ARGB_8888);ArrayList<Rect> out=new ArrayList<Rect>();Map<Long,Rect> previous=new HashMap<Long,Rect>();try{Canvas canvas=new Canvas(mask);canvas.translate(-(float)bounds.left,-(float)bounds.top);Paint paint=new Paint();paint.setColor(Color.WHITE);canvas.shirazDrawPath(path,paint,matrix);for(int y=0;y<(int)h;y++){Map<Long,Rect> current=new HashMap<Long,Rect>();int start=-1;for(int x=0;x<=(int)w;x++){boolean inside=x<w&&(mask.pixels[y*(int)w+x]>>>24)!=0&&(rectangularClip||clip.contains(bounds.left+x,bounds.top+y));if(inside&&start<0)start=x;else if(!inside&&start>=0){span(out,previous,current,bounds.left+start,bounds.left+x,bounds.top+y,bounds.top+y+1);start=-1;}}previous=current;}rects=out;return !isEmpty();}finally{mask.recycle();}}
 public Path getBoundaryPath(){Path out=new Path();getBoundaryPath(out);return out;}public boolean getBoundaryPath(Path out){if(isComplex())throw new UnsupportedOperationException("Complex Region boundary contours");out.reset();out.setFillType(Path.FillType.WINDING);if(isEmpty())return false;Rect r=rects.get(0);out.addRect(r.left,r.top,r.right,r.bottom,Path.Direction.CW);return true;}
 public static Region obtain(){return new Region();}public static Region obtain(Region other){return new Region(other);}public void recycle(){setEmpty();}
 public int describeContents(){return 0;}public void writeToParcel(Parcel out,int flags){throw new UnsupportedOperationException("Region parcel transport");}
 public static final Parcelable.Creator<Region> CREATOR=new Parcelable.Creator<Region>(){public Region createFromParcel(Parcel p){throw new UnsupportedOperationException("Region parcel transport");}public Region[] newArray(int n){return new Region[n];}};
 @Override public boolean equals(Object value){return value instanceof Region&&rects.equals(((Region)value).rects);}@Override public int hashCode(){return rects.hashCode();}@Override public String toString(){return "Region"+rects;}
}
