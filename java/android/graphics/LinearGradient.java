// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
/** Bounded sRGB linear gradient, sampled in shader-local coordinates. */
public final class LinearGradient extends Shader {
 final float[] points,positions;final int[] colors;final TileMode tile;
 public LinearGradient(float x0,float y0,float x1,float y1,int color0,int color1,TileMode tile){this(x0,y0,x1,y1,new int[]{color0,color1},null,tile);}
 public LinearGradient(float x0,float y0,float x1,float y1,int[] colors,float[] positions,TileMode tile){
  if(colors==null||tile==null)throw new NullPointerException("colors/tile");
  if(colors.length<2||colors.length>256||positions!=null&&positions.length!=colors.length)throw new IllegalArgumentException("Gradient stop count");
  points=new float[]{x0,y0,x1,y1};for(float v:points)if(!Float.isFinite(v)||Math.abs(v)>10000000)throw new IllegalArgumentException("Gradient coordinate range");
  this.colors=colors.clone();this.positions=positions==null?new float[colors.length]:positions.clone();this.tile=tile;
  for(int i=0;i<colors.length;i++){
   if(positions==null)this.positions[i]=i/(float)(colors.length-1);
   float v=this.positions[i];if(!Float.isFinite(v)||v<0||v>1||i>0&&v<this.positions[i-1])throw new IllegalArgumentException("Gradient stop order/range");
  }
 }
}
