// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
public final class PopupAnchorSmoke {
 private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 private static void frame(Bitmap b,int w,int h){PopupWindow.shirazRevision(w,h);check(PopupWindow.shirazPrepare(),"preDraw");b.eraseColor(0);PopupWindow.shirazDraw(new Canvas(b));}
 public static void main(String[] args){Looper.prepareMainLooper();try{
  Context c=new ContextWrapper(null){private final android.content.res.Resources.Theme theme=android.content.res.Resources.getSystem().newTheme();public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}public android.content.res.Resources.Theme getTheme(){return theme;}};
  FrameLayout root=new FrameLayout(c);View anchor=new View(c);root.addView(anchor,new FrameLayout.LayoutParams(20,20));root.shirazAttachTree(ViewTreeObserver.shirazCreateWindowObserver());root.layout(0,0,200,200);anchor.layout(30,10,50,30);
  Bitmap bitmap=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888);
  final ListView menu=new ListView(c);
  menu.setAdapter(new BaseAdapter(){public int getCount(){return 4;}public Object getItem(int p){return p;}public long getItemId(int p){return p;}public View getView(int p,View recycled,ViewGroup parent){View row=recycled==null?new View(c):recycled;row.setLayoutParams(new AbsListView.LayoutParams(-1,30));return row;}});
  PopupWindow parentMenu=new PopupWindow(menu,100,120,true);parentMenu.showAsDropDown(anchor);frame(bitmap,200,200);
  final int[] detached={0};View menuAnchor=menu.getChildAt(2);
  menuAnchor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){detached[0]++;}});
  View submenuContent=new View(c);submenuContent.setBackgroundColor(Color.BLUE);
  PopupWindow submenu=new PopupWindow(submenuContent,40,30,true);submenu.setOverlapAnchor(true);submenu.showAsDropDown(menuAnchor,0,0);frame(bitmap,200,200);
  for(int i=0;i<3;i++){menu.getChildAt(0).requestLayout();menu.requestLayout();PopupWindow.shirazWindowState(true,true,true);frame(bitmap,200,200);check(submenu.isShowing()&&menu.getChildAt(2)==menuAnchor&&detached[0]==0,"remeasuring parent menu preserves submenu anchor");}
  menu.offsetTopAndBottom(1);menu.layout(0,1,100,121);check(menu.getChildAt(2)==menuAnchor&&detached[0]==0,"moving list preserves rows");
  menu.setAdapter(null);check(!submenu.isShowing()&&detached[0]==1,"actual row removal dismisses submenu");parentMenu.dismiss();
  final int[] wheels={0};View drawer=new View(c){public boolean onGenericMotionEvent(MotionEvent e){check(e.getX()==10&&e.getY()==10&&e.getAxisValue(MotionEvent.AXIS_VSCROLL)==-1,"popup local wheel coordinates");wheels[0]++;return true;}};
  PopupWindow bottom=new PopupWindow(drawer,200,60,true);bottom.showAtLocation(root,Gravity.BOTTOM|Gravity.LEFT,0,0);frame(bitmap,200,200);
  MotionEvent wheel=MotionEvent.shirazScroll(1,10,150,0,-1);check(PopupWindow.shirazGenericMotion(wheel)&&wheels[0]==1,"drawer wheel routed");wheel.recycle();
  wheel=MotionEvent.shirazScroll(2,10,50,0,-1);check(!PopupWindow.shirazGenericMotion(wheel)&&bottom.isShowing(),"wheel above drawer reaches application without dismissal");wheel.recycle();bottom.dismiss();
  root.shirazDetachTree();bitmap.recycle();System.out.println("POPUP_ANCHOR_OK");
 }finally{try{PopupWindow.shirazClear();}finally{Looper.shirazShutdownMain();}}}
}
