// SPDX-License-Identifier: GPL-3.0-only
package shiraz;
import android.graphics.*;
import shiraz.runtime.*;
public final class GpuCanvasSmoke {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  final Bitmap[] retained=new Bitmap[2];
  try(final NativeSurface surface=new NativeSurface(160,120)){
   surface.presentDispatched(4,new NativeSurface.ResizeCallback(){
    int frame;Bitmap bitmap;
    public void onResize(int w,int h){surface.resize(w,h);if(bitmap!=null){bitmap.recycle();bitmap=null;}}
    public boolean onFrame(){
     if(bitmap==null)bitmap=Bitmap.createBitmap(surface.getWidth(),surface.getHeight(),Bitmap.Config.ARGB_8888);
     bitmap.eraseColor(Color.WHITE);Canvas canvas=Canvas.shirazScreen(bitmap);
     check(canvas.isHardwareAccelerated()=="gpu".equals(System.getenv("EXPECT_CANVAS")),"backend selection");
     int broad=canvas.save();check(canvas.clipRect(-Float.MAX_VALUE,0,Float.MAX_VALUE,5),"Unbounded horizontal clip");canvas.drawColor(Color.MAGENTA);canvas.restoreToCount(broad);
     check(bitmap.getPixel(2,2)==Color.MAGENTA&&bitmap.getPixel(2,8)==Color.WHITE,"Large clip preserves bounded axis");
     if(canvas.isHardwareAccelerated()){broad=canvas.save();check(canvas.clipRect(Float.NaN,0,10,10),"Skia ignores nonfinite clip");canvas.restoreToCount(broad);}
     canvas.drawColor(Color.WHITE);
     Path compound=new Path();compound.addCircle(140,20,12,Path.Direction.CW);compound.addCircle(140,20,6,Path.Direction.CW);compound.setFillType(Path.FillType.EVEN_ODD);
     Paint geometry=new Paint(3);geometry.setColor(Color.GREEN);canvas.drawPath(compound,geometry);
     check(bitmap.getPixel(140,20)==Color.WHITE,"compound hole after resize");check(bitmap.getPixel(140,10)==Color.GREEN,"compound contour after resize");
     Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.RED);canvas.drawRoundRect(10,10,70,70,12,12,p);
     int saved=canvas.save();Path clip=new Path();clip.addCircle(100,40,22,Path.Direction.CW);canvas.clipPath(clip);p.setColor(Color.BLUE);canvas.drawRect(70,10,130,70,p);canvas.restoreToCount(saved);
     saved=canvas.saveLayerAlpha(new RectF(0,80,160,120),128);p.setColor(Color.BLACK);canvas.drawRect(0,80,160,120,p);canvas.restoreToCount(saved);
     check(bitmap.getPixel(30,30)==Color.RED,"path color");check(bitmap.getPixel(100,40)==Color.BLUE,"clip interior "+Integer.toHexString(bitmap.getPixel(100,40)));check(bitmap.getPixel(78,18)==Color.WHITE,"clip exterior");
     int gray=bitmap.getPixel(10,100)&255;check(gray>=126&&gray<=128,"layer alpha");
     Bitmap copy=bitmap.copy(Bitmap.Config.ARGB_8888,false);check(copy.getPixel(30,30)==Color.RED,"copy synchronization");copy.recycle();
     if(canvas.isHardwareAccelerated()){p.setColor(Color.WHITE);canvas.shirazPatternRipple(new float[]{80,60,100,.25f,20,160,120,80,60,70,0,0},0xff336699,0xffffffff,p);}
     p.setColor(Color.BLACK);p.setTextSize(18);canvas.drawText("GPU text",5,100,p);
     Bitmap source=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888);source.eraseColor(Color.RED);canvas.drawBitmap(source,0,0,null);
     source.eraseColor(Color.GREEN);canvas.drawBitmap(source,0,0,null);check(bitmap.getPixel(1,1)==Color.GREEN,"CPU image upload invalidation");source.recycle();
     // Fast primitives must preserve transformed/difference clips, strokes,
     // save/restore, and a shader returning to its identity local matrix.
     saved=canvas.save();canvas.translate(8,6);
     check(canvas.clipRect(10,10,60,60),"rectangle clip nonempty");
     check(canvas.clipRect(20,20,40,40,Region.Op.DIFFERENCE),"difference clip nonempty");
     p.setColor(Color.RED);canvas.drawRect(10,10,60,60,p);
     check(bitmap.getPixel(23,21)==Color.RED,"translated rectangle interior");
     int hole=bitmap.getPixel(38,36);p.setColor(Color.GREEN);canvas.drawOval(10,10,60,60,p);
     check(bitmap.getPixel(38,36)==hole,"difference hole preserved");
     check(!canvas.clipRect(70,70,80,80),"empty clip result");canvas.restoreToCount(saved);
     p.setColor(Color.BLUE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);
     canvas.drawRoundRect(80,80,120,115,8,8,p);
     check(bitmap.getPixel(100,81)==Color.BLUE,"round rectangle stroke");p.setStyle(Paint.Style.FILL);
     Bitmap tile=Bitmap.createBitmap(2,1,Bitmap.Config.ARGB_8888);tile.setPixel(0,0,Color.RED);tile.setPixel(1,0,Color.BLUE);
     BitmapShader shader=new BitmapShader(tile,Shader.TileMode.REPEAT,Shader.TileMode.CLAMP);
     Matrix local=new Matrix();local.setTranslate(1,0);shader.setLocalMatrix(local);p.setShader(shader);
     canvas.drawRect(0,110,10,115,p);check(bitmap.getPixel(0,112)==Color.BLUE,"translated shader");
     local.reset();shader.setLocalMatrix(local);canvas.drawRect(0,110,10,115,p);
     check(bitmap.getPixel(0,112)==Color.RED,"identity shader after translated shader");p.setShader(null);tile.recycle();
     p.setColor(Color.WHITE);p.setShader(new RadialGradient(130.5f,90.5f,10,Color.RED,Color.BLUE,Shader.TileMode.CLAMP));canvas.drawRect(120,80,150,110,p);
     int center=bitmap.getPixel(130,90),mid=bitmap.getPixel(135,90);check((center&255)<=1&&((center>>16)&255)>=254,"Radial center");
     check(Math.abs(((mid>>16)&255)-128)<=1&&Math.abs((mid&255)-128)<=1&&bitmap.getPixel(142,90)==Color.BLUE,"Radial midpoint and clamp");p.setShader(null);
     p.setColor(Color.WHITE);canvas.drawRect(0,116,60,120,p);
     p.setShader(new LinearGradient(0,0,30,0,Color.TRANSPARENT,Color.WHITE,Shader.TileMode.CLAMP));canvas.drawRect(0,116,30,120,p);p.setShader(null);
     android.graphics.drawable.GradientDrawable fade=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.TRANSPARENT,Color.WHITE});fade.setBounds(30,116,60,120);fade.draw(canvas);
     for(int x=0;x<60;x++)check(bitmap.getPixel(x,118)==Color.WHITE,"transparent fade has no dark fringe at "+x);
     if(frame==0){
      Bitmap stateImage=Bitmap.createBitmap(80,40,Bitmap.Config.ARGB_8888);
      Canvas first=Canvas.shirazScreen(stateImage);Paint statePaint=new Paint();statePaint.setColor(Color.RED);
      first.translate(10,0);first.drawRect(0,0,10,10,statePaint);
      int stateSave=first.save();first.translate(20,0);first.drawRect(0,0,10,10,statePaint);first.restoreToCount(stateSave);
      first.drawRect(0,10,10,20,statePaint);
      check(stateImage.getPixel(15,15)==Color.RED&&stateImage.getPixel(35,15)==0,"unchanged paint restores transform");
      Canvas second=Canvas.shirazScreen(stateImage);statePaint.setColor(Color.BLUE);second.drawRect(0,20,10,30,statePaint);
      statePaint.setColor(Color.RED);first.drawRect(0,20,10,30,statePaint);
      check(stateImage.getPixel(5,25)==Color.BLUE&&stateImage.getPixel(15,25)==Color.RED,"interleaved Canvas state remains independent");
      statePaint.setShader(new LinearGradient(0,0,10,0,Color.GREEN,Color.GREEN,Shader.TileMode.CLAMP));first.drawRect(0,30,10,40,statePaint);
      statePaint.setShader(null);first.drawRect(20,30,30,40,statePaint);
      check(stateImage.getPixel(15,35)==Color.GREEN&&stateImage.getPixel(35,35)==Color.RED,"simple paint after shader clears shader");
      stateImage.recycle();

      StringBuilder longRun=new StringBuilder();for(int i=0;i<1200;i++)longRun.append("Abc ");
      String text=longRun.toString();Paint tp=new Paint(3);tp.setTextSize(18);tp.setColor(Color.BLACK);
      check(tp.measureText(text)>8192,"regression exceeds bitmap dimension limit");
      Bitmap actual=Bitmap.createBitmap(160,80,Bitmap.Config.ARGB_8888),expected=Bitmap.createBitmap(160,80,Bitmap.Config.ARGB_8888);
      for(Paint.Align align:Paint.Align.values()){
       tp.setTextAlign(align);actual.eraseColor(Color.WHITE);expected.eraseColor(Color.WHITE);
       Canvas ac=Canvas.shirazScreen(actual),ec=new Canvas(expected);
       ac.translate(7,3);ec.translate(7,3);ac.clipRect(10,0,140,60);ec.clipRect(10,0,140,60);
       ac.drawText(text,75,35,tp);ec.drawText(text,75,35,tp);
       int ink=0;for(int yy=0;yy<80;yy++)for(int xx=0;xx<160;xx++){
        int a=actual.getPixel(xx,yy),e=expected.getPixel(xx,yy);if(e!=Color.WHITE)ink++;
        check(Math.abs((a&255)-(e&255))<=2,"oversized text pixels "+align+" at "+xx+","+yy);
       }
       check(ink>20,"oversized text remains visible "+align);
      }
      actual.recycle();expected.recycle();
     }
     bitmap.shirazPostToSurface(surface);frame++;
     if(frame==4){retained[0]=bitmap;retained[1]=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888);Canvas.shirazScreen(retained[1]).drawColor(Color.RED);new Canvas(retained[1]).drawColor(Color.BLUE);}
     return true;
    }
    public void onKey(int a,int b,int c,int d,int e,int f,long g,long h){}
    public void onText(String s,long t){}
    public void onPointer(int a,float x,float y,int source,long t){}
   });
  }
  check(retained[0].getPixel(1,1)==Color.GREEN,"GPU shutdown readback");
  check(retained[1].getPixel(1,1)==Color.BLUE,"shutdown must not overwrite CPU mutation");
  retained[0].recycle();retained[1].recycle();
  System.out.println("GPU_CANVAS_OK");
 }
}
