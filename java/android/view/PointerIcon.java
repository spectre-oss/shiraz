// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.content.Context;
/** System and bitmap pointer shapes presented by the SDL desktop backend. */
public final class PointerIcon {
 public static final int TYPE_NULL=0,TYPE_ARROW=1000,TYPE_CONTEXT_MENU=1001,TYPE_HAND=1002,TYPE_HELP=1003,TYPE_WAIT=1004,TYPE_CELL=1006,TYPE_CROSSHAIR=1007,TYPE_TEXT=1008,TYPE_VERTICAL_TEXT=1009,TYPE_ALIAS=1010,TYPE_COPY=1011,TYPE_NO_DROP=1012,TYPE_ALL_SCROLL=1013,TYPE_HORIZONTAL_DOUBLE_ARROW=1014,TYPE_VERTICAL_DOUBLE_ARROW=1015,TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW=1016,TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW=1017,TYPE_ZOOM_IN=1018,TYPE_ZOOM_OUT=1019,TYPE_GRAB=1020,TYPE_GRABBING=1021,TYPE_DEFAULT=TYPE_ARROW;
 public static final int TYPE_CUSTOM=-1;
 private final int type;
 private final android.graphics.Bitmap bitmap;
 private final float hotSpotX,hotSpotY;
 private PointerIcon(int type){this(type,null,0,0);}
 private PointerIcon(int type,android.graphics.Bitmap bitmap,float x,float y){this.type=type;this.bitmap=bitmap;hotSpotX=x;hotSpotY=y;}
 public static PointerIcon create(android.graphics.Bitmap bitmap,float hotSpotX,float hotSpotY){
  if(bitmap==null)throw new IllegalArgumentException("Pointer bitmap is null");
  if(bitmap.isRecycled()||!Float.isFinite(hotSpotX)||!Float.isFinite(hotSpotY)||hotSpotX<0||hotSpotY<0||hotSpotX>=bitmap.getWidth()||hotSpotY>=bitmap.getHeight())throw new IllegalArgumentException("Pointer hotspot outside live bitmap");
  return new PointerIcon(TYPE_CUSTOM,bitmap,hotSpotX,hotSpotY);
 }
 public android.graphics.Bitmap shirazBitmap(){return bitmap;}
 public float shirazHotSpotX(){return hotSpotX;}
 public float shirazHotSpotY(){return hotSpotY;}
 public static PointerIcon getSystemIcon(Context context,int type){if(context==null)throw new NullPointerException("context");return new PointerIcon(type==0||type>=1000&&type<=1021&&type!=1005?type:TYPE_ARROW);}
 public int getType(){return type;}
 @Override public boolean equals(Object other){return other instanceof PointerIcon&&((PointerIcon)other).type==type&&((PointerIcon)other).bitmap==bitmap&&((PointerIcon)other).hotSpotX==hotSpotX&&((PointerIcon)other).hotSpotY==hotSpotY;}
 @Override public int hashCode(){return 31*(31*(31*type+System.identityHashCode(bitmap))+Float.floatToIntBits(hotSpotX==0?0:hotSpotX))+Float.floatToIntBits(hotSpotY==0?0:hotSpotY);}
}
