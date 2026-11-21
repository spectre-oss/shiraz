// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;import android.content.res.*;import android.graphics.*;import android.text.*;import android.text.style.*;import android.view.*;import android.widget.*;
public final class TextViewBulletSmoke {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static void layout(TextView view,int width){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.AT_MOST));view.layout(0,0,view.getMeasuredWidth(),view.getMeasuredHeight());}
 static int red(Bitmap bitmap,int left,int top,int right,int bottom){int count=0;for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)if((bitmap.getPixel(x,y)&0xffffff)==0xff0000&&(bitmap.getPixel(x,y)>>>24)!=0)count++;return count;}
 public static void main(String[] args){
  android.os.Looper.prepare();Context context=new ContextWrapper(null){public Resources getResources(){return Resources.getSystem();}public Object getSystemService(String name){return null;}};
  TextView view=new TextView(context);view.setPadding(0,0,0,0);view.setIncludeFontPadding(false);view.setTextSize(16);view.setTextColor(Color.BLUE);
  String first="First bullet wraps across several lines of text";
  SpannableString text=new SpannableString(first+"\nPlain\nSecond");BulletSpan one=new BulletSpan(8,Color.RED),two=new BulletSpan(8,Color.RED);
  text.setSpan(one,0,first.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);text.setSpan(two,first.length()+7,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  view.setText(text,TextView.BufferType.SPANNABLE);layout(view,110);Layout geometry=view.getLayout();int plain=-1,last=-1;
  for(int i=0;i<geometry.getLineCount();i++){
   int start=geometry.getLineStart(i);if(start==first.length()+1)plain=i;if(start==first.length()+7)last=i;
   check(geometry.getLineLeft(i)==(start<=first.length()||start>=first.length()+7?16:0),"paragraph margin and wrapped indentation");
   check(geometry.getLineRight(i)<=110,"margin included in wrapping");
  }
  check(plain>1&&last>plain,"wrapped and unstyled paragraphs");
  check(view.getOffsetForPosition(16,geometry.getLineTop(1)+2)==geometry.getLineStart(1),"margin hit test");
  Bitmap pixels=Bitmap.createBitmap(110,view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(pixels));
  check(red(pixels,0,0,8,geometry.getLineTop(1))>0,"first bullet pixels");
  check(red(pixels,0,geometry.getLineTop(1),16,geometry.getLineTop(last))==0,"no repeated bullet on wrapped or plain lines");
  check(red(pixels,0,geometry.getLineTop(last),8,view.getHeight())>0,"second bullet pixels");
  check(view.getPaint().getColor()==Color.BLUE,"bullet restores paint color");pixels.recycle();
  Spannable live=(Spannable)view.getText();live.removeSpan(one);layout(view,110);check(view.getLayout().getLineLeft(0)==0,"span removal reflows");
  SpannableString rtl=new SpannableString("سلام دنیا");rtl.setSpan(new BulletSpan(8,Color.RED),0,rtl.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);view.setGravity(Gravity.RIGHT);view.setText(rtl);layout(view,110);
  check(view.getLayout().getParagraphDirection(0)==Layout.DIR_RIGHT_TO_LEFT&&Math.abs(view.getLayout().getLineRight(0)-94)<0.01f,"RTL margin geometry");
  pixels=Bitmap.createBitmap(110,view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(pixels));check(red(pixels,102,0,110,view.getHeight())>0,"RTL bullet pixels");pixels.recycle();
  view.setGravity(Gravity.LEFT);view.setMaxLines(1);view.setEllipsize(TextUtils.TruncateAt.END);view.setText(text);layout(view,110);
  check(view.getLayout().getLineCount()==1&&view.getLayout().getLineLeft(0)==16&&view.getLayout().getLineRight(0)<=110,"ellipsis retains bullet margin");
  pixels=Bitmap.createBitmap(110,view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(pixels));check(red(pixels,0,0,8,view.getHeight())>0,"ellipsis keeps first bullet");pixels.recycle();
  SpannableString shortText=new SpannableString("M");shortText.setSpan(new BulletSpan(8),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);view.setText(shortText);
  view.measure(View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.AT_MOST));
  check(view.getMeasuredWidth()==(int)Math.ceil(view.getPaint().measureText("M")+16),"wrap-content includes margin");
  System.out.println("TEXTVIEW_BULLET_PIXELS_WRAP_HIT_OK");
 }
}
