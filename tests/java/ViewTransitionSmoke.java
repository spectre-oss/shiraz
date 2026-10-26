// SPDX-License-Identifier: GPL-3.0-only
import android.view.*;
import android.widget.FrameLayout;
import org.shiraz.fixture.ViewTransitionFixture;
public final class ViewTransitionSmoke {
 private static void xmlNames(String apk)throws Exception{
  final android.content.res.Resources resources=android.content.res.Resources.shirazOpen(apk,100,100);
  android.content.Context context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return resources;}public android.content.res.Resources.Theme getTheme(){return resources.newTheme();}};
  try{android.content.res.XmlResourceParser parser=resources.getLayout(resources.getIdentifier("view_transition_names","layout","org.shiraz.fixture"));int index=0;String[] expected={"route:حافظ","",null};try{while(parser.next()!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){if(parser.getEventType()==org.xmlpull.v1.XmlPullParser.START_TAG&&"View".equals(parser.getName())){View view=new View(context,parser);ViewTransitionFixture.check(java.util.Objects.equals(expected[index++],view.getTransitionName()),"XML transition name");}}ViewTransitionFixture.check(index==3,"All XML names tested");}finally{parser.close();}}finally{resources.shirazClose();}
 }
 private static void windowIds(android.content.Context context){
  View child=new View(context),other=new View(context);FrameLayout root=new FrameLayout(context);root.addView(child);ViewTransitionFixture.check(child.getWindowId()==null&&child.getWindowToken()==null,"Detached window identity");
  ViewTreeObserver first=ViewTreeObserver.shirazCreateWindowObserver(),second=ViewTreeObserver.shirazCreateWindowObserver();root.shirazAttachTree(first);other.shirazAttachTree(second);
  WindowId identity=child.getWindowId();android.os.IBinder token=child.getWindowToken();ViewTransitionFixture.check(identity!=null&&identity==root.getWindowId()&&token==root.getWindowToken(),"Shared window identity");ViewTransitionFixture.check(!identity.equals(other.getWindowId())&&token!=other.getWindowToken(),"Different windows distinct");
  ViewTransitionFixture.check(child.hasOverlappingRendering(),"Default overlapping rendering");
  ViewTransitionFixture.check(!identity.isFocused(),"Window initially unfocused");root.shirazSetWindowFocus(true);ViewTransitionFixture.check(identity.isFocused()&&child.getWindowId().isFocused()&&!other.getWindowId().isFocused(),"Focus belongs to shared window");root.shirazSetWindowFocus(false);ViewTransitionFixture.check(!identity.isFocused(),"Focus loss reflected by identity");root.shirazSetWindowFocus(true);
  root.shirazDetachTree();ViewTransitionFixture.check(child.getWindowId()==null&&child.getWindowToken()==null,"Detach clears window identity");root.shirazAttachTree(first);ViewTransitionFixture.check(identity==child.getWindowId()&&token==child.getWindowToken(),"Reattach same live window retains identity");root.shirazDetachTree();root.shirazAttachTree(second);ViewTransitionFixture.check(child.getWindowId()==other.getWindowId()&&child.getWindowToken()==other.getWindowToken(),"Reparent to another window");root.shirazDetachTree();other.shirazDetachTree();first.shirazKill();ViewTransitionFixture.check(!identity.isFocused(),"Killed window identity cannot remain focused");second.shirazKill();
 }
 private static void removalDispatch(android.content.Context context){
  class Tracking extends FrameLayout {
   int callbacks;
   Tracking(){super(context);}
   public void removeView(View v){callbacks++;super.removeView(v);}
   public void removeViewAt(int i){callbacks++;super.removeViewAt(i);}
   public void removeViews(int s,int n){callbacks++;super.removeViews(s,n);}
   public void removeAllViews(){callbacks++;super.removeAllViews();}
   public void removeViewsInLayout(int s,int n){callbacks++;super.removeViewsInLayout(s,n);}
   public void removeAllViewsInLayout(){callbacks++;super.removeAllViewsInLayout();}
  }
  for(int operation=0;operation<6;operation++){
   Tracking group=new Tracking();View child=new View(context);group.addView(child);group.startViewTransition(child);
   switch(operation){case 0:group.removeView(child);break;case 1:group.removeViewAt(0);break;case 2:group.removeViews(0,1);break;case 3:group.removeAllViews();break;case 4:group.removeViewsInLayout(0,1);break;default:group.removeAllViewsInLayout();}
   ViewTransitionFixture.check(group.callbacks==1&&group.getChildCount()==0,"Removal reentered subclass bookkeeping");group.endViewTransition(child);
   ViewTransitionFixture.check(child.getParent()==null,"Transition removal released child");
  }
 }
 private static void deepTransition(android.content.Context ignored){
  android.content.Context context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}};
  FrameLayout root=new FrameLayout(context),fading=new FrameLayout(context),parent=fading;root.addView(fading,new FrameLayout.LayoutParams(32,32));
  for(int i=0;i<32;i++){FrameLayout next=new FrameLayout(context);parent.addView(next,new FrameLayout.LayoutParams(32,32));parent=next;}
  android.widget.TextView label=new android.widget.TextView(context);label.setText("Hafez");label.setTextSize(8);label.setBackgroundColor(0xffff0000);parent.addView(label,new FrameLayout.LayoutParams(32,32));
  root.measure(View.MeasureSpec.makeMeasureSpec(40,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(40,View.MeasureSpec.EXACTLY));root.layout(0,0,40,40);root.startViewTransition(fading);root.removeView(fading);fading.setAlpha(.5f);
  android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(40,40,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(image);
  try{for(int i=0;i<10;i++){image.eraseColor(0);root.shirazDrawWithAlpha(canvas);ViewTransitionFixture.check(canvas.getSaveCount()==1&&image.getPixel(25,25)==0x80ff0000,"Deep disappearing subtree draws and restores every frame");}
   root.endViewTransition(fading);image.eraseColor(0);root.shirazDrawWithAlpha(canvas);ViewTransitionFixture.check(canvas.getSaveCount()==1&&image.getPixel(25,25)==0,"Deep transition releases disappearing child");
   for(int i=0;i<256;i++){canvas.save();canvas.translate(1,0);}
   try{canvas.save();throw new AssertionError("Unbounded canvas save stack");}catch(IllegalStateException expected){}ViewTransitionFixture.check(canvas.getSaveCount()==257,"Rejected save leaves state intact");canvas.restoreToCount(1);canvas.drawColor(0xff123456);ViewTransitionFixture.check(image.getPixel(0,0)==0xff123456&&canvas.getSaveCount()==1,"Bounded stack fully restores");
  }finally{canvas.restoreToCount(1);image.recycle();}
 }
 private static final class FrameProbe extends FrameLayout {
  int layouts,sizes;FrameProbe(android.content.Context context){super(context);}
  protected void onLayout(boolean changed,int l,int t,int r,int b){layouts++;super.onLayout(changed,l,t,r,b);}
  protected void onSizeChanged(int w,int h,int oldW,int oldH){sizes++;super.onSizeChanged(w,h,oldW,oldH);}
 }
 private static void transitionFrames(android.content.Context context){
  FrameProbe root=new FrameProbe(context);View child=new View(context);root.addView(child,new FrameLayout.LayoutParams(4,4));root.measure(View.MeasureSpec.makeMeasureSpec(20,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(20,View.MeasureSpec.EXACTLY));root.layout(0,0,20,20);
  root.setLeftTopRightBottom(10,20,40,60);ViewTransitionFixture.check(root.getLeft()==10&&root.getWidth()==30&&root.getHeight()==40&&root.layouts==1&&root.sizes==2&&child.getRight()==4&&!root.isLayoutRequested(),"Animated frame updates size without relaying out children");
  try{root.setLeftTopRightBottom(10,20,9,60);throw new AssertionError("Invalid frame accepted");}catch(IllegalArgumentException expected){}ViewTransitionFixture.check(root.getLeft()==10&&root.getRight()==40,"Invalid frame leaves geometry intact");
  root.suppressLayout(true);root.layout(0,0,100,100);ViewTransitionFixture.check(root.isLayoutSuppressed()&&root.getLeft()==10&&root.layouts==1,"Suppression preserves animation geometry");root.suppressLayout(false);ViewTransitionFixture.check(root.isLayoutRequested(),"Unsuppress schedules deferred layout");root.measure(View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY));root.layout(0,0,100,100);ViewTransitionFixture.check(root.layouts==2&&root.getWidth()==100&&!root.isLayoutRequested(),"Deferred layout completes");
 }
 private static void transitionPixels(android.content.Context context){
  View view=new View(context);view.setBackgroundColor(0xffff0000);view.measure(View.MeasureSpec.makeMeasureSpec(4,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(4,View.MeasureSpec.EXACTLY));view.layout(0,0,4,4);
  view.setAlpha(.5f);view.setTransitionAlpha(.5f);
  android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(4,4,android.graphics.Bitmap.Config.ARGB_8888);
  try{view.shirazDrawWithAlpha(new android.graphics.Canvas(image));ViewTransitionFixture.check(image.getPixel(2,2)==0x40ff0000&&view.getAlpha()==.5f,"Transition alpha multiplies app alpha");
   view.setTransitionAlpha(1);image.eraseColor(0);view.shirazDrawWithAlpha(new android.graphics.Canvas(image));ViewTransitionFixture.check(image.getPixel(2,2)==0x80ff0000&&view.getAlpha()==.5f,"Fade restores app alpha");
   view.setTransitionVisibility(View.GONE);ViewTransitionFixture.check(view.getVisibility()==View.GONE&&!view.isLayoutRequested(),"Transition visibility preserves captured layout");image.eraseColor(0);view.shirazDrawWithAlpha(new android.graphics.Canvas(image));ViewTransitionFixture.check(image.getPixel(2,2)==0,"Transition visibility hides pixels");
   view.setTransitionVisibility(View.VISIBLE);ViewTransitionFixture.check(view.getVisibility()==View.VISIBLE&&!view.isLayoutRequested(),"Transition visibility restores without relayout");
  }finally{image.recycle();}
 }
 public static void main(String[] args)throws Exception{android.os.Looper.prepareMainLooper();xmlNames(args[0]);android.content.Context c=new android.content.ContextWrapper(null);windowIds(c);removalDispatch(c);deepTransition(c);transitionFrames(c);transitionPixels(c);ViewTransitionFixture.check(c);ViewTransitionFixture.Child child=new ViewTransitionFixture.Child(c);FrameLayout root=ViewTransitionFixture.root(c,child);ViewTreeObserver observer=ViewTreeObserver.shirazCreateWindowObserver();root.shirazAttachTree(observer);android.os.IBinder token=child.getWindowToken();ViewTransitionFixture.removed(root,child);ViewTransitionFixture.check(child.isAttachedToWindow()&&child.getWindowToken()==token&&child.detaches==0,"Window attachment retained until transition end");root.endViewTransition(child);ViewTransitionFixture.check(!child.isAttachedToWindow()&&child.detaches==1&&child.getWindowToken()==null,"Explicit transition completion detaches once");root.addView(child);root.startViewTransition(child);root.removeView(child);root.shirazDetachTree();ViewTransitionFixture.check(!child.isAttachedToWindow()&&child.getParent()==null&&child.detaches==2,"Window teardown clears disappearing child");observer.shirazKill();
  root.addView(child);observer=ViewTreeObserver.shirazCreateWindowObserver();root.shirazAttachTree(observer);root.startViewTransition(child);root.removeView(child);child.failDetach=true;try{root.shirazDetachTree();throw new AssertionError("Detach failure swallowed");}catch(IllegalStateException expected){}ViewTransitionFixture.check(!child.isAttachedToWindow()&&!root.isAttachedToWindow()&&child.getParent()==null,"Failing child callback still releases tree");observer.shirazKill();System.out.println("VIEW_TRANSITION_WINDOW_CLEANUP_OK");
 }
}
