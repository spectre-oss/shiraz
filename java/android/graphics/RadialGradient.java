// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
/** Bounded sRGB radial gradient in shader-local coordinates. */
public final class RadialGradient extends Shader {
 final float[] points,positions;final int[] colors;final TileMode tile;
 public RadialGradient(float x,float y,float radius,long[] colors,float[] positions,TileMode tile){this(x,y,radius,srgb(colors),positions,tile);}
 private static int[] srgb(long[] colors){if(colors==null)throw new NullPointerException("colors");if(colors.length<2||colors.length>256)throw new IllegalArgumentException("Gradient stop count");int[] result=new int[colors.length];for(int i=0;i<colors.length;i++){if((colors[i]&0xffffffffL)!=0)throw new UnsupportedOperationException("Gradient color space requires sRGB");result[i]=(int)(colors[i]>>>32);}return result;}
 public RadialGradient(float x,float y,float radius,int centerColor,int edgeColor,TileMode tile){this(x,y,radius,new int[]{centerColor,edgeColor},null,tile);}
 public RadialGradient(float x,float y,float radius,int[] colors,float[] positions,TileMode tile){
  points=new float[]{x,y,radius};
  for(float v:points)if(!Float.isFinite(v)||Math.abs(v)>10000000)throw new IllegalArgumentException("Gradient coordinate range");
  if(radius<=0)throw new IllegalArgumentException("Gradient radius must be positive");
  LinearGradient stops=new LinearGradient(0,0,1,0,colors,positions,tile);
  this.colors=stops.colors;this.positions=stops.positions;this.tile=tile;
 }
}
