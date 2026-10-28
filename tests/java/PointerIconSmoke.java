// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.Bitmap;
import android.view.PointerIcon;
public final class PointerIconSmoke {
 public static void main(String[] args){
  Bitmap bitmap=Bitmap.createBitmap(new int[]{0xffff0000,0xff00ff00,0xff0000ff,0},2,2,Bitmap.Config.ARGB_8888);
  PointerIcon icon=PointerIcon.create(bitmap,1.5f,0);
  if(icon.getType()!=PointerIcon.TYPE_CUSTOM||icon.shirazBitmap()!=bitmap||icon.shirazHotSpotX()!=1.5f||icon.shirazHotSpotY()!=0)throw new AssertionError("custom data");
  PointerIcon same=PointerIcon.create(bitmap,1.5f,-0f);
  if(!icon.equals(same)||icon.hashCode()!=same.hashCode()||icon.equals(PointerIcon.create(bitmap,0,0)))throw new AssertionError("custom equality");
  Bitmap other=Bitmap.createBitmap(new int[]{0xffff0000,0xff00ff00,0xff0000ff,0},2,2,Bitmap.Config.ARGB_8888);
  if(icon.equals(PointerIcon.create(other,1.5f,0)))throw new AssertionError("different bitmap identity");
  for(float bad:new float[]{-1,2,Float.NaN,Float.POSITIVE_INFINITY}){
   try{PointerIcon.create(bitmap,bad,0);throw new AssertionError("bad x");}catch(IllegalArgumentException expected){}
   try{PointerIcon.create(bitmap,0,bad);throw new AssertionError("bad y");}catch(IllegalArgumentException expected){}
  }
  try{PointerIcon.create(null,0,0);throw new AssertionError("null bitmap");}catch(IllegalArgumentException expected){}
  bitmap.recycle();
  try{PointerIcon.create(bitmap,0,0);throw new AssertionError("recycled bitmap");}catch(IllegalArgumentException expected){}
  other.recycle();
  System.out.println("POINTER_ICON_BITMAP_OK");
 }
}
