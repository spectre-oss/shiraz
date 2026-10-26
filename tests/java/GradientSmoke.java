// SPDX-License-Identifier: GPL-3.0-only
public final class GradientSmoke {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 static void zeroAxis(){
  android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(b);android.graphics.Paint paint=new android.graphics.Paint();
  try{for(android.graphics.Shader.TileMode mode:android.graphics.Shader.TileMode.values()){
   android.graphics.LinearGradient gradient=new android.graphics.LinearGradient(3,4,3,4,new int[]{0xffff0000,0xff00ff00,0xff0000ff},new float[]{.2f,.6f,.9f},mode);android.graphics.Matrix local=new android.graphics.Matrix();local.setTranslate(20,-30);gradient.setLocalMatrix(local);paint.setShader(gradient);b.eraseColor(0);canvas.drawRect(new android.graphics.RectF(0,0,8,8),paint);for(int y=0;y<8;y++)for(int x=0;x<8;x++)check(b.getPixel(x,y)==0xffff0000,"zero axis origin color across tile modes and matrix");
  }
  paint.setAlpha(128);b.eraseColor(0);canvas.drawRect(new android.graphics.Rect(0,0,8,8),paint);check((b.getPixel(4,4)>>>24)==128,"zero gradient respects paint alpha");
  paint.setAlpha(255);paint.setShader(new android.graphics.LinearGradient(4,0,4.00001f,0,0xffff0000,0xff0000ff,android.graphics.Shader.TileMode.CLAMP));b.eraseColor(0);canvas.drawRect(0,0,8,8,paint);check(b.getPixel(1,4)==0xffff0000&&b.getPixel(6,4)==0xff0000ff,"tiny nonzero axes retain gradient");
  }finally{b.recycle();}
 }
 public static void main(String[] args){zeroAxis();org.shiraz.fixture.GradientFixture.check();
 android.content.res.Resources r=android.content.res.Resources.shirazOpen(System.getProperty("shiraz.fixture"),40,40);
 try{
 android.graphics.drawable.Drawable d=r.getDrawable(r.getIdentifier("shape_null_solid","drawable","org.shiraz.fixture"),null);d.setBounds(0,0,40,40);android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(40,40,android.graphics.Bitmap.Config.ARGB_8888);d.draw(new android.graphics.Canvas(b));check(b.getPixel(20,20)==0&&b.getPixel(1,20)==0xffff0000,"null solid transparent interior and real stroke");
 b.eraseColor(0);d=r.getDrawable(r.getIdentifier("shape_null_after_gradient","drawable","org.shiraz.fixture"),null);d.setBounds(0,0,40,40);d.draw(new android.graphics.Canvas(b));check((b.getPixel(20,20)>>>24)==255&&b.getPixel(5,20)!=b.getPixel(35,20),"null solid preserves prior gradient; null stroke allowed");
 for(String name:new String[]{"ripple_null_color","gradient_null_color"})try{r.getDrawable(r.getIdentifier(name,"drawable","org.shiraz.fixture"),null);throw new AssertionError("required color accepted "+name);}catch(IllegalArgumentException expected){}
 d=r.getDrawable(r.getIdentifier("shape_null_selector","drawable","org.shiraz.fixture"),null);d.setBounds(0,0,40,40);d.setState(new int[]{16842910});b.eraseColor(0);d.draw(new android.graphics.Canvas(b));check(b.getPixel(20,20)==0,"enabled default null solid");d.setState(new int[]{16842910,16842919});b.eraseColor(0);d.draw(new android.graphics.Canvas(b));check(b.getPixel(20,20)==0xff00ff00,"pressed selector fill");d.setState(new int[0]);b.eraseColor(0);d.draw(new android.graphics.Canvas(b));check(b.getPixel(20,20)==0xff0000ff,"disabled selector fill");
 b.eraseColor(0);d=r.getDrawable(r.getIdentifier("shape_stroke_radius","drawable","org.shiraz.fixture"),null);d.setBounds(0,0,40,40);d.draw(new android.graphics.Canvas(b));
 android.graphics.drawable.GradientDrawable reference=new android.graphics.drawable.GradientDrawable();reference.setColor(0xff00ff00);reference.setStroke(2,0xffff0000);reference.setBounds(0,0,40,40);android.graphics.Bitmap expected=android.graphics.Bitmap.createBitmap(40,40,android.graphics.Bitmap.Config.ARGB_8888);reference.draw(new android.graphics.Canvas(expected));for(int y=0;y<40;y++)for(int x=0;x<40;x++)check(b.getPixel(x,y)==expected.getPixel(x,y),"misplaced stroke radius must not round corners");expected.recycle();
 b.recycle();System.out.println("OPTIONAL_SHAPE_COLOR_OK");
 }finally{r.shirazClose();}}
}
