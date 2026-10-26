// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
/** Bounded ARGB software bitmap sampling; transforms/GLES are separate backends. */
public final class NativeBitmap {
    private NativeBitmap() { }
    static { System.loadLibrary("shiraz-bitmap"); }
    public static void draw(int[] destination, int width, int height, int[] source, int sourceWidth, int sourceHeight,
        int left, int top, int right, int bottom, float destLeft, float destTop, float destRight, float destBottom, boolean filter, int alpha) {
        drawClipped(destination,width,height,source,sourceWidth,sourceHeight,left,top,right,bottom,destLeft,destTop,destRight,destBottom,filter,alpha,0,0,width,height);
    }
    public static void drawClipped(int[] destination, int width, int height, int[] source, int sourceWidth, int sourceHeight,
        int left, int top, int right, int bottom, float destLeft, float destTop, float destRight, float destBottom, boolean filter, int alpha,
        int clipLeft, int clipTop, int clipRight, int clipBottom) {
        drawStyled(destination,width,height,source,sourceWidth,sourceHeight,left,top,right,bottom,destLeft,destTop,destRight,destBottom,filter,alpha,clipLeft,clipTop,clipRight,clipBottom,0,-1);
    }
    public static void drawStyled(int[] destination,int width,int height,int[] source,int sourceWidth,int sourceHeight,
        int left,int top,int right,int bottom,float destLeft,float destTop,float destRight,float destBottom,boolean filter,int alpha,
        int clipLeft,int clipTop,int clipRight,int clipBottom,int tint,int mode) {
        drawEffects(destination,width,height,source,sourceWidth,sourceHeight,left,top,right,bottom,destLeft,destTop,destRight,destBottom,filter,alpha,clipLeft,clipTop,clipRight,clipBottom,tint,mode,null);
    }
    public static native void drawEffects(int[] destination,int width,int height,int[] source,int sourceWidth,int sourceHeight,
        int left,int top,int right,int bottom,float destLeft,float destTop,float destRight,float destBottom,boolean filter,int alpha,
        int clipLeft,int clipTop,int clipRight,int clipBottom,int tint,int mode,float[] matrix);
    public static native void fillRect(int[] pixels,int width,int height,double left,double top,double right,double bottom,int color,boolean antialias,int clipLeft,int clipTop,int clipRight,int clipBottom);
    public static native void applyClip(int[] destination,int width,int height,int[] rendered,int rw,int rh,int left,int top,byte[] mask,int mw,int mh,int ml,int mt);
    public static native int filterColorMatrix(int color,float[] matrix);
    public static native void shadow(int[] destination,int width,int height,int[] mask,int maskWidth,int maskHeight,int pad,int rx,int ry,int color,int mode,int left,int top,int right,int bottom);
    public static native void composite(int[] destination,int[] source,int[] coverage,int mode);
    public static native int filterColor(int source,int destination,int mode);
}
