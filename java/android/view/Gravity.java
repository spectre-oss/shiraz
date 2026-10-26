// SPDX-License-Identifier: GPL-3.0-only
package android.view;
public class Gravity {
    public static final int NO_GRAVITY=0, TOP=48, BOTTOM=80, LEFT=3, RIGHT=5,
        CENTER_VERTICAL=16, CENTER_HORIZONTAL=1, CENTER=17, FILL_VERTICAL=112,
        FILL_HORIZONTAL=7, FILL=119, CLIP_VERTICAL=128, CLIP_HORIZONTAL=8,
        HORIZONTAL_GRAVITY_MASK=7, VERTICAL_GRAVITY_MASK=112,
        RELATIVE_LAYOUT_DIRECTION=0x00800000, START=0x00800003, END=0x00800005,
        RELATIVE_HORIZONTAL_GRAVITY_MASK=0x00800007;
    public static int getAbsoluteGravity(int gravity,int direction) {
        if ((gravity & RELATIVE_LAYOUT_DIRECTION) != 0) {
            int horizontal=gravity & HORIZONTAL_GRAVITY_MASK;
            if (horizontal == LEFT) gravity=(gravity & ~HORIZONTAL_GRAVITY_MASK) | (direction == View.LAYOUT_DIRECTION_RTL ? RIGHT : LEFT);
            else if (horizontal == RIGHT) gravity=(gravity & ~HORIZONTAL_GRAVITY_MASK) | (direction == View.LAYOUT_DIRECTION_RTL ? LEFT : RIGHT);
            gravity &= ~RELATIVE_LAYOUT_DIRECTION;
        }
        return gravity;
    }
    public static void apply(int gravity,int width,int height,android.graphics.Rect container,android.graphics.Rect out) { apply(gravity,width,height,container,out,0); }
    public static void apply(int gravity,int width,int height,android.graphics.Rect container,android.graphics.Rect out,int direction) {
        int value=getAbsoluteGravity(gravity,direction);
        if ((value & ~(HORIZONTAL_GRAVITY_MASK|VERTICAL_GRAVITY_MASK|CLIP_HORIZONTAL|CLIP_VERTICAL)) != 0) throw new UnsupportedOperationException("Gravity flags");
        int left,right,top,bottom;
        switch (value&HORIZONTAL_GRAVITY_MASK) {
            case FILL_HORIZONTAL:left=container.left;right=container.right;break;
            case RIGHT:right=container.right;left=right-width;break;
            case LEFT:left=container.left;right=left+width;break;
            default:left=container.left+(container.width()-width)/2;right=left+width;
        }
        switch (value&VERTICAL_GRAVITY_MASK) {
            case FILL_VERTICAL:top=container.top;bottom=container.bottom;break;
            case BOTTOM:bottom=container.bottom;top=bottom-height;break;
            case TOP:top=container.top;bottom=top+height;break;
            default:top=container.top+(container.height()-height)/2;bottom=top+height;
        }
        if ((value&CLIP_HORIZONTAL)!=0) {left=Math.max(left,container.left);right=Math.min(right,container.right);}
        if ((value&CLIP_VERTICAL)!=0) {top=Math.max(top,container.top);bottom=Math.min(bottom,container.bottom);}
        out.set(left,top,right,bottom);
    }
}
