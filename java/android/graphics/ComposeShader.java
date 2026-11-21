// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

/** Bounded CPU shader composition in device coordinates. */
public class ComposeShader extends Shader {
 final Shader destination,source;final PorterDuff.Mode mode;
 public ComposeShader(Shader a,Shader b,PorterDuff.Mode mode){
  if(a==null||b==null||mode==null)throw new NullPointerException("shaders/mode");
  destination=a;source=b;this.mode=mode;
 }
 public ComposeShader(Shader a,Shader b,Xfermode mode){this(a,b,mode(mode));}
 private static PorterDuff.Mode mode(Xfermode value){
  if(value==null)return PorterDuff.Mode.SRC_OVER;
  if(!(value instanceof PorterDuffXfermode))throw new UnsupportedOperationException("ComposeShader transfer mode");
  return ((PorterDuffXfermode)value).mode;
 }
 Bitmap raster(int width,int height,float[] matrix,boolean filter,int color){return raster(this,width,height,matrix,filter,color,0,new long[]{0});}
 private static Bitmap raster(Shader shader,int width,int height,float[] matrix,boolean filter,int color,int depth,long[] budget){
  long pixels=(long)width*height;
  budget[0]+=pixels;
  if(depth>8||budget[0]>64L*1024*1024)throw new IllegalArgumentException("ComposeShader raster budget");
  if(shader instanceof ComposeShader){
   ComposeShader group=(ComposeShader)shader;
   Matrix combined=new Matrix();combined.setValues(new float[]{matrix[0],matrix[2],matrix[4],matrix[1],matrix[3],matrix[5],0,0,1});
   Matrix local=new Matrix();group.getLocalMatrix(local);combined.preConcat(local);
   float[] values=new float[9];combined.getValues(values);float[] child={values[0],values[3],values[1],values[4],values[2],values[5]};
   Bitmap a=raster(group.destination,width,height,child,filter,color,depth+1,budget);
   try{
    Bitmap b=raster(group.source,width,height,child,filter,color,depth+1,budget);
    try{int[] coverage=new int[width*height];java.util.Arrays.fill(coverage,-1);shiraz.runtime.NativeBitmap.composite(a.pixels,b.pixels,coverage,group.mode.ordinal());}
    finally{b.recycle();}
    return a;
   }catch(RuntimeException|Error failure){a.recycle();throw failure;}
  }
  Bitmap output=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
  try{
   Path full=new Path();full.addRect(0,0,width,height,Path.Direction.CW);
   float[] identity={1,0,0,1,0,0};float[] inverse=shader.inverse(matrix);
   if(shader instanceof BitmapShader){
    BitmapShader texture=(BitmapShader)shader;texture.bitmap.requireLive();
    shiraz.runtime.NativePath.drawShader(output.pixels,width,height,full.commands(),0,0xff000000,0,0,0,0,4,false,identity,0,0,width,height,null,0,texture.bitmap.colorPixels(color),texture.bitmap.width,texture.bitmap.height,texture.tileX.ordinal(),texture.tileY.ordinal(),inverse,filter,0,-1,null);
   }else if(shader instanceof SweepGradient){
    Bitmap swept=((SweepGradient)shader).raster(width,height,matrix);
    try{System.arraycopy(swept.pixels,0,output.pixels,0,output.pixels.length);}finally{swept.recycle();}
   }else if(shader instanceof RuntimeShader){
    Bitmap rt=((RuntimeShader)shader).rasterFallback(width,height,matrix);
    try{System.arraycopy(rt.pixels,0,output.pixels,0,output.pixels.length);}finally{rt.recycle();}
   }else{
    float[] points,positions;int[] colors;TileMode tile;
    if(shader instanceof LinearGradient){LinearGradient g=(LinearGradient)shader;points=g.points;positions=g.positions;colors=g.colors;tile=g.tile;}
    else if(shader instanceof RadialGradient){RadialGradient g=(RadialGradient)shader;points=g.points;positions=g.positions;colors=g.colors;tile=g.tile;}
    else throw new UnsupportedOperationException("ComposeShader child type");
    shiraz.runtime.NativePath.drawGradient(output.pixels,width,height,full.commands(),0,0xff000000,0,0,0,0,4,false,identity,0,0,width,height,null,0,points,colors,positions,tile.ordinal(),inverse);
   }
   return output;
  }catch(RuntimeException|Error failure){output.recycle();throw failure;}
 }
}
