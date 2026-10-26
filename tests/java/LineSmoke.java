// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
public final class LineSmoke {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static Bitmap image(){return Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);}
 private static void equal(Bitmap a,Bitmap b,String label){for(int y=0;y<64;y++)for(int x=0;x<64;x++)check(a.getPixel(x,y)==b.getPixel(x,y),label+" at "+x+","+y);}
 public static void main(String[] args){
  Bitmap batch=image();Paint batchPaint=new Paint();batchPaint.setColor(Color.RED);batchPaint.setStrokeWidth(2);new Canvas(batch).drawLines(new float[]{Float.NaN,4,4,12,4,4,8,12,8,Float.NaN},1,8,batchPaint);check(batch.getPixel(8,4)==Color.RED&&batch.getPixel(8,8)==Color.RED&&batch.getPixel(8,6)==0,"line batch offset and independent segments");try{new Canvas(batch).drawLines(new float[]{1,20,20,20,Float.NaN,1,2,3},batchPaint);throw new AssertionError("nonfinite batch accepted");}catch(IllegalArgumentException expected){}check(batch.getPixel(8,20)==0,"invalid batch validates before drawing");batch.recycle();
  Paint paint=new Paint();paint.setColor(Color.RED);paint.setStrokeWidth(6);
  for(Paint.Cap cap:Paint.Cap.values())for(Paint.Style style:Paint.Style.values()){
   paint.setStrokeCap(cap);paint.setStyle(style);Bitmap a=image(),b=image();new Canvas(a).drawLine(12,20,40,20,paint);Paint expected=new Paint(paint);expected.setStyle(Paint.Style.STROKE);Path path=new Path();path.moveTo(12,20);path.lineTo(40,20);new Canvas(b).drawPath(path,expected);equal(a,b,"forced stroke "+cap+style);check(paint.getStyle()==style&&paint.getStrokeWidth()==6&&paint.getStrokeCap()==cap,"caller paint unchanged");check(a.getPixel(20,20)==Color.RED&&a.getPixel(20,24)==0,"stroke coverage");check((a.getPixel(10,20)!=0)==(cap!=Paint.Cap.BUTT),"endpoint cap");a.recycle();b.recycle();
  }
  for(Paint.Cap cap:Paint.Cap.values()){Bitmap a=image();paint.setStrokeCap(cap);new Canvas(a).drawLine(24,24,24,24,paint);check((a.getPixel(24,24)!=0)==(cap!=Paint.Cap.BUTT),"zero-length cap center "+cap);check(a.getPixel(21,21)==(cap==Paint.Cap.SQUARE?Color.RED:0),"zero-length cap corner "+cap);a.recycle();}
  paint.setStrokeCap(Paint.Cap.ROUND);paint.setStyle(Paint.Style.FILL);paint.setAntiAlias(true);paint.setPathEffect(new DashPathEffect(new float[]{5,3},1));paint.setShader(new LinearGradient(0,0,40,0,Color.RED,Color.BLUE,Shader.TileMode.CLAMP));Bitmap a=image(),b=image();Canvas ca=new Canvas(a),cb=new Canvas(b);for(Canvas c:new Canvas[]{ca,cb}){c.clipRect(8,8,50,50);c.translate(20,8);c.rotate(30);c.scale(1.2f,.8f);}ca.drawLine(0,10,35,10,paint);Paint expected=new Paint(paint);expected.setStyle(Paint.Style.STROKE);Path path=new Path();path.moveTo(0,10);path.lineTo(35,10);cb.drawPath(path,expected);equal(a,b,"affine clipped gradient dash");
  paint.setShader(null);paint.setPathEffect(null);paint.setStrokeWidth(0);a.eraseColor(0);new Canvas(a).drawLine(8,8,40,8,paint);boolean ink=false;for(int y=6;y<10;y++)ink|=a.getPixel(20,y)!=0;check(ink,"hairline coverage");
  try{new Canvas(a).drawLine(Float.NaN,0,1,1,paint);throw new AssertionError("nonfinite accepted");}catch(IllegalArgumentException failure){}a.recycle();b.recycle();System.out.println("LINE_STROKE_CAPS_EFFECTS_OK");
 }
}
