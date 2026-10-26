// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
public final class ChildClippingSmoke {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 private static Bitmap render(FrameLayout root){Bitmap b=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888);b.eraseColor(0xffffffff);root.draw(new Canvas(b));return b;}
 public static void main(String[] args) throws Exception {
  final Resources r=Resources.shirazOpen(args[0],80,80);
  final Resources.Theme theme=r.newTheme();
  Context context=new ContextWrapper(null){public Resources getResources(){return r;}public Resources.Theme getTheme(){return theme;}public String getPackageName(){return "org.shiraz.fixture";}};
  try{
   FrameLayout root=new FrameLayout(context),group;
   try(XmlResourceParser xml=r.getLayout(r.getIdentifier("unclipped_children","layout","org.shiraz.fixture"))){
    while(xml.next()!=org.xmlpull.v1.XmlPullParser.START_TAG){}
    group=new FrameLayout(context,xml);
   }
   check(root.getClipChildren()&&root.getClipToPadding(),"default clipping");
   check(!group.getClipChildren()&&!group.getClipToPadding(),"XML clipping flags");
   View circle=new View(context){protected void onDraw(Canvas canvas){Paint p=new Paint();p.setColor(0xffff8000);canvas.drawCircle(30,30,30,p);}};
   circle.setWillNotDraw(false);group.addView(circle,new FrameLayout.LayoutParams(60,60));root.addView(group,new FrameLayout.LayoutParams(40,40));
   root.setClipChildren(false);root.layout(0,0,80,80);group.layout(20,20,60,60);circle.layout(-10,-10,50,50);
   Bitmap b=render(root);check(b.getPixel(15,40)==0xffff8000&&b.getPixel(40,15)==0xffff8000,"expanded circle outside group must render");b.recycle();
   group.setClipChildren(true);b=render(root);check(b.getPixel(15,40)==0xffffffff&&b.getPixel(40,40)==0xffff8000,"explicit child clipping");b.recycle();
   group.setClipChildren(false);group.setClipToPadding(true);b=render(root);check(b.getPixel(22,40)==0xffffffff&&b.getPixel(26,40)==0xffff8000,"padding clipping remains independent");b.recycle();
  }finally{r.shirazClose();}
  System.out.println("CHILD_CLIPPING_OK");
 }
}
