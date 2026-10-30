// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.graphics.Insets;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;
/** Immutable cutout geometry. Host desktop surfaces supply no physical cutout. */
public final class DisplayCutout {
 private final Insets safe;
 private final Rect[] bounds;
 public DisplayCutout(Insets safeInsets,Rect left,Rect top,Rect right,Rect bottom){
  if(safeInsets==null)throw new NullPointerException("safeInsets");safe=safeInsets;
  bounds=new Rect[]{new Rect(left),new Rect(top),new Rect(right),new Rect(bottom)};
 }
 public DisplayCutout(Rect safeInsets,List<Rect> rectangles){
  safe=Insets.of(safeInsets);bounds=new Rect[]{new Rect(),new Rect(),new Rect(),new Rect()};
  if(safeInsets!=null&&rectangles!=null)for(Rect r:rectangles){
   int edge=r.left==0?0:r.top==0?1:safe.right>0?2:safe.bottom>0?3:-1;
   if(edge>=0)bounds[edge]=new Rect(r);
  }
 }
 public int getSafeInsetLeft(){return safe.left;}public int getSafeInsetTop(){return safe.top;}
 public int getSafeInsetRight(){return safe.right;}public int getSafeInsetBottom(){return safe.bottom;}
 public Rect getSafeInsets(){return safe.toRect();}
 public Rect getBoundingRectLeft(){return new Rect(bounds[0]);}public Rect getBoundingRectTop(){return new Rect(bounds[1]);}
 public Rect getBoundingRectRight(){return new Rect(bounds[2]);}public Rect getBoundingRectBottom(){return new Rect(bounds[3]);}
 public List<Rect> getBoundingRects(){ArrayList<Rect> result=new ArrayList<Rect>();for(Rect r:bounds)if(!r.isEmpty())result.add(new Rect(r));return result;}
 public boolean isEmpty(){return safe.equals(Insets.NONE);}
 public boolean isBoundsEmpty(){for(Rect r:bounds)if(!r.isEmpty())return false;return true;}
 DisplayCutout shirazInset(int l,int t,int r,int b){
  if(isBoundsEmpty()||(l|t|r|b)==0)return this;
  Rect[] moved=new Rect[4];for(int i=0;i<4;i++){moved[i]=new Rect(bounds[i]);if(!moved[i].isEmpty())moved[i].offset(-l,-t);}
  return new DisplayCutout(Insets.of(Math.max(0,safe.left-l),Math.max(0,safe.top-t),Math.max(0,safe.right-r),Math.max(0,safe.bottom-b)),moved[0],moved[1],moved[2],moved[3]);
 }
 @Override public boolean equals(Object o){if(!(o instanceof DisplayCutout))return false;DisplayCutout c=(DisplayCutout)o;return safe.equals(c.safe)&&java.util.Arrays.equals(bounds,c.bounds);}
 @Override public int hashCode(){return safe.hashCode()*31+java.util.Arrays.hashCode(bounds);}
}
