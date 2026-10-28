import android.graphics.*;
import android.graphics.drawable.*;
public final class RotateDrawableSmoke {
 private static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  Bitmap bitmap=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
  Drawable child=new ColorDrawable(Color.RED){public void draw(Canvas c){Rect r=getBounds();Paint p=new Paint();p.setColor(Color.RED);c.drawRect(r.left,r.top,r.left+10,r.top+10,p);}};
  RotateDrawable rotate=new RotateDrawable();rotate.setDrawable(child);rotate.setBounds(10,10,30,30);rotate.setToDegrees(180);rotate.setLevel(5000);rotate.draw(canvas);
  check(bitmap.getPixel(25,15)==Color.RED);check(bitmap.getPixel(15,15)==0);check(child.getLevel()==5000);
  Paint green=new Paint();green.setColor(Color.GREEN);canvas.drawRect(0,0,4,4,green);check(bitmap.getPixel(2,2)==Color.GREEN);
  bitmap.eraseColor(0);rotate.setLevel(0);rotate.draw(canvas);check(bitmap.getPixel(15,15)==Color.RED);check(bitmap.getPixel(25,15)==0);
  rotate.setPivotXRelative(false);rotate.setPivotYRelative(false);rotate.setPivotX(0);rotate.setPivotY(0);rotate.setFromDegrees(90);rotate.setToDegrees(90);bitmap.eraseColor(0);rotate.draw(canvas);check(bitmap.getPixel(5,15)==Color.RED);
  bitmap.recycle();System.out.println("ROTATE_DRAWABLE_PIXELS_OK");
 }
}
