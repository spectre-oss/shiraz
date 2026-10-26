// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
public final class NativePath {
    private NativePath() { }
    public static native void ripple(int[] output,int width,int height,float left,float top,float cx,float cy,float radius,float progress,float phase,float resolutionWidth,float resolutionHeight,int color,int sparkle,float boundX,float boundY,float boundRadius);
    static { System.loadLibrary("shiraz-path"); }
    public static void draw(int[] pixels,int width,int height,float[] commands,int fillType,int color,
        int style,float strokeWidth,int cap,int join,float miter,boolean antialias,float[] matrix,
        int left,int top,int right,int bottom) {
        drawDashed(pixels,width,height,commands,fillType,color,style,strokeWidth,cap,join,miter,antialias,matrix,left,top,right,bottom,null,0);
    }
    public static void drawDashed(int[] pixels,int width,int height,float[] commands,int fillType,int color,
        int style,float strokeWidth,int cap,int join,float miter,boolean antialias,float[] matrix,
        int left,int top,int right,int bottom,float[] intervals,float phase) {
        drawShader(pixels,width,height,commands,fillType,color,style,strokeWidth,cap,join,miter,antialias,matrix,left,top,right,bottom,intervals,phase,null,0,0,0,0,null,false,0,-1,null);
    }
    public static void drawShader(int[] pixels,int width,int height,float[] commands,int fillType,int color,
        int style,float strokeWidth,int cap,int join,float miter,boolean antialias,float[] matrix,
        int left,int top,int right,int bottom,float[] intervals,float phase,int[] texture,int textureWidth,int textureHeight,int tileX,int tileY,float[] inverse,boolean filter,int filterColor,int filterMode,float[] filterMatrix){
        drawNative(pixels,width,height,commands,fillType,color,style,strokeWidth,cap,join,miter,antialias,matrix,left,top,right,bottom,intervals,phase,texture,textureWidth,textureHeight,tileX,tileY,inverse,filter,null,null,null,filterColor,filterMode,filterMatrix);
    }
    public static void drawGradient(int[] pixels,int width,int height,float[] commands,int fillType,int color,
        int style,float strokeWidth,int cap,int join,float miter,boolean antialias,float[] matrix,
        int left,int top,int right,int bottom,float[] intervals,float phase,float[] points,int[] colors,float[] positions,int tile,float[] inverse){
        drawNative(pixels,width,height,commands,fillType,color,style,strokeWidth,cap,join,miter,antialias,matrix,left,top,right,bottom,intervals,phase,null,0,0,tile,0,inverse,false,points,colors,positions,0,-1,null);
    }
    private static native void drawNative(int[] pixels,int width,int height,float[] commands,int fillType,int color,
        int style,float strokeWidth,int cap,int join,float miter,boolean antialias,float[] matrix,
        int left,int top,int right,int bottom,float[] intervals,float phase,int[] texture,int textureWidth,int textureHeight,int tileX,int tileY,float[] inverse,boolean filter,float[] points,int[] colors,float[] positions,int filterColor,int filterMode,float[] filterMatrix);
}
