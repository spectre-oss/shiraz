// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
public final class ComposeShaderSmoke {
 static Shader solid(int color){return new LinearGradient(0,0,8,0,color,color,Shader.TileMode.CLAMP);}
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  Bitmap out=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(out);Paint paint=new Paint();
  paint.setShader(new ComposeShader(solid(Color.RED),solid(Color.BLUE),PorterDuff.Mode.SRC));canvas.drawRect(0,0,8,8,paint);
  check(out.getPixel(4,4)==Color.BLUE,"B is source");
  paint.setShader(new ComposeShader(solid(Color.RED),solid(0x800000ff),(Xfermode)null));canvas.drawRect(0,0,8,8,paint);
  int blended=out.getPixel(4,4);check((blended>>>24)==255&&Math.abs(((blended>>16)&255)-127)<=1&&Math.abs((blended&255)-128)<=1,"source over premultiplied blend");
  Bitmap mask=Bitmap.createBitmap(2,1,Bitmap.Config.ARGB_8888);mask.setPixel(0,0,Color.WHITE);mask.setPixel(1,0,0);
  Shader bitmap=new BitmapShader(mask,Shader.TileMode.REPEAT,Shader.TileMode.CLAMP);
  ComposeShader composed=new ComposeShader(bitmap,solid(Color.BLUE),new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
  Matrix shift=new Matrix();shift.setTranslate(1,0);composed.setLocalMatrix(shift);
  out.eraseColor(0);paint.setShader(composed);canvas.drawRect(0,0,8,8,paint);
  check(out.getPixel(0,2)==0&&out.getPixel(1,2)==Color.BLUE,"group matrix and mask source in");
  shift.setTranslate(1,0);bitmap.setLocalMatrix(shift);out.eraseColor(0);canvas.drawRect(0,0,8,8,paint);
  check(out.getPixel(0,2)==Color.BLUE&&out.getPixel(1,2)==0,"child local matrix composes with group");
  paint.setShader(new ComposeShader(solid(Color.RED),composed,PorterDuff.Mode.SRC_OVER));out.eraseColor(0);canvas.clipRect(2,2,6,6);canvas.drawRect(0,0,8,8,paint);
  check(out.getPixel(0,0)==0&&out.getPixel(2,2)==Color.BLUE&&out.getPixel(3,2)==Color.RED,"nested composition clipped once");
  Shader deep=solid(Color.RED);for(int i=0;i<10;i++)deep=new ComposeShader(deep,solid(Color.BLUE),PorterDuff.Mode.SRC);
  paint.setShader(deep);try{canvas.drawRect(0,0,8,8,paint);throw new AssertionError("unbounded depth");}catch(IllegalArgumentException expected){}
  check(out.getPixel(3,2)==Color.RED,"failed composition preserves destination");
  paint.setShader(new ComposeShader(solid(Color.RED),solid(Color.BLUE),PorterDuff.Mode.SRC));
  paint.setColorFilter(new PorterDuffColorFilter(Color.GREEN,PorterDuff.Mode.SRC_IN));canvas.drawRect(0,0,8,8,paint);
  check(out.getPixel(3,2)==Color.GREEN,"filter applies to composed output");
  mask.recycle();out.recycle();System.out.println("COMPOSE_SHADER_OK");
 }
}
