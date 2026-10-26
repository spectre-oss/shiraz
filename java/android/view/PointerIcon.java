// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.content.Context;
/** System pointer shapes presented by the SDL desktop backend. */
public final class PointerIcon {
 public static final int TYPE_NULL=0,TYPE_ARROW=1000,TYPE_CONTEXT_MENU=1001,TYPE_HAND=1002,TYPE_HELP=1003,TYPE_WAIT=1004,TYPE_CELL=1006,TYPE_CROSSHAIR=1007,TYPE_TEXT=1008,TYPE_VERTICAL_TEXT=1009,TYPE_ALIAS=1010,TYPE_COPY=1011,TYPE_NO_DROP=1012,TYPE_ALL_SCROLL=1013,TYPE_HORIZONTAL_DOUBLE_ARROW=1014,TYPE_VERTICAL_DOUBLE_ARROW=1015,TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW=1016,TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW=1017,TYPE_ZOOM_IN=1018,TYPE_ZOOM_OUT=1019,TYPE_GRAB=1020,TYPE_GRABBING=1021,TYPE_DEFAULT=TYPE_ARROW;
 private final int type;
 private PointerIcon(int type){this.type=type;}
 public static PointerIcon getSystemIcon(Context context,int type){if(context==null)throw new NullPointerException("context");return new PointerIcon(type==0||type>=1000&&type<=1021&&type!=1005?type:TYPE_ARROW);}
 public int getType(){return type;}
 @Override public boolean equals(Object other){return other instanceof PointerIcon&&((PointerIcon)other).type==type;}
 @Override public int hashCode(){return type;}
}
