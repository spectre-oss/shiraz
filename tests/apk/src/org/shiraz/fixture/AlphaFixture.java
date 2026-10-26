// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.FrameLayout;
public final class AlphaFixture {
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void check(Context context){
  Bitmap b=Bitmap.createBitmap(40,30,Bitmap.Config.ARGB_8888);try{Canvas canvas=new Canvas(b);Paint p=new Paint();p.setColor(Color.RED);int layer=canvas.saveLayerAlpha(new RectF(0,0,20,20),128);canvas.drawRect(0,0,20,20,p);p.setColor(Color.BLUE);canvas.drawRect(5,5,15,15,p);canvas.restoreToCount(layer);check(b.getPixel(7,7)==0x800000ff&&b.getPixel(2,2)==0x80ff0000,"Layer opacity applies once to overlapping content");check(canvas.getSaveCount()==1,"Layer stack restored");b.eraseColor(0);
   canvas.translate(5,3);layer=canvas.saveLayerAlpha(new RectF(0,0,5,5),128);int nested=canvas.saveLayerAlpha((RectF)null,128);p.setColor(Color.GREEN);canvas.drawRect(-10,-10,30,30,p);canvas.restoreToCount(nested);canvas.restoreToCount(layer);check(b.getPixel(5,3)==0x4000ff00&&b.getPixel(4,3)==0&&b.getPixel(10,3)==0,"Nested opacity and transformed clip");canvas.translate(-5,-3);b.eraseColor(0);
   FrameLayout root=new FrameLayout(context);View child=new View(context){public void draw(Canvas c){Paint paint=new Paint();paint.setColor(Color.RED);c.drawRect(0,0,20,20,paint);paint.setColor(Color.BLUE);c.drawRect(5,5,15,15,paint);}};child.setAlpha(.5f);root.addView(child,new FrameLayout.LayoutParams(20,20));root.measure(View.MeasureSpec.makeMeasureSpec(40,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(30,View.MeasureSpec.EXACTLY));root.layout(0,0,40,30);root.draw(canvas);check(b.getPixel(7,7)==0x800000ff&&child.getAlpha()==.5f,"Parent applies view opacity around overridden draw");child.setAlpha(0);b.eraseColor(0);root.draw(canvas);check(b.getPixel(7,7)==0,"Zero opacity hides drawing");child.setAlpha(1);root.draw(canvas);check(b.getPixel(7,7)==Color.BLUE,"Opaque drawing restored");try{child.setAlpha(Float.NaN);throw new AssertionError("Nonfinite alpha accepted");}catch(IllegalArgumentException expected){}check(child.getAlpha()==1,"Invalid alpha preserves property");
   int id=context.getResources().getIdentifier("alpha_view","layout","org.shiraz.fixture");View xml=LayoutInflater.from(context).inflate(id,null);check(xml.getAlpha()==.25f,"Compiled alpha attribute");
   try{canvas.saveLayerAlpha((RectF)null,256);throw new AssertionError("Invalid layer alpha accepted");}catch(IllegalArgumentException expected){}check(canvas.getSaveCount()==1,"Invalid layer leaves stack intact");
  }finally{b.recycle();}System.out.println("ALPHA_LAYER_VIEW_COMPOSITION_OK");
 }
}
