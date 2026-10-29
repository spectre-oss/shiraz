// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.view.View;
import android.view.ViewGroup;
import java.io.PrintStream;
import java.util.IdentityHashMap;

/** Opt-in geometry diagnostics. Never includes text, descriptions, tags, or app data. */
public final class ViewDiagnostics {
 private ViewDiagnostics(){}
 private static final boolean TRACE="1".equals(System.getenv("SHIRAZ_TRACE_INPUT"));
 private static final boolean DRAW_TRACE="1".equals(System.getenv("SHIRAZ_TRACE_FRAMES"));
 private static int draws,layoutRequests;
 private static boolean layoutPass;
 public static void layoutPass(boolean active){if(DRAW_TRACE)layoutPass=active;}
 public static void layoutRequest(View view){
  if(!DRAW_TRACE||!layoutPass||layoutRequests>=64||!view.isAttachedToWindow())return;
  StackTraceElement[] stack=new Throwable().getStackTrace();
  if(stack.length<3||stack[2].getClassName().equals("android.view.View")&&stack[2].getMethodName().equals("requestLayout"))return;
  layoutRequests++;StringBuilder callers=new StringBuilder();for(int i=2;i<Math.min(6,stack.length);i++)callers.append(" <- ").append(stack[i]);
  System.err.println("SHIRAZ_LAYOUT_REQUEST class="+view.getClass().getName()+" id="+Integer.toHexString(view.getId())+callers);
 }

 public static long drawStart(){return DRAW_TRACE?System.nanoTime():0;}
 public static synchronized void drawEnd(View view,long start){
  if(!DRAW_TRACE||draws>=4096)return;
  long elapsed=(System.nanoTime()-start)/1000000;
  if(elapsed<10)return;
  draws++;System.err.println("SHIRAZ_VIEW_DRAW class="+view.getClass().getName()+" id="+Integer.toHexString(view.getId())+" elapsed_ms="+elapsed);
 }
 private static int events,inputTrees;
 public static synchronized void inputTree(View root){
  if(!TRACE||!"1".equals(System.getenv("SHIRAZ_DUMP_VIEWS"))||inputTrees>=32)return;
  inputTrees++;System.err.println("SHIRAZ_INPUT_TREE n="+inputTrees);dump(root,System.err);DialogOverlay.dumpVisible(System.err);
 }
 /** Bounded metadata only: no View text, listener data or event payload strings. */
 public static synchronized void input(View view,int action,boolean click){
  if(!TRACE||events>=4096)return;events++;
  String name=view.getClass().getName();if(name.length()>256)name=name.substring(0,256);
  System.err.println("SHIRAZ_INPUT_VIEW uptime="+android.os.SystemClock.uptimeMillis()+" action="+action+" click="+click+" class="+name+" id="+Integer.toHexString(view.getId())+" bounds="+view.getLeft()+","+view.getTop()+","+view.getRight()+","+view.getBottom()+" enabled="+view.isEnabled()+" clickable="+view.isClickable()+(click?" hasListener="+view.hasOnClickListeners():""));
  if(events==4096)System.err.println("SHIRAZ_INPUT_TRACE_TRUNCATED");
 }

 public static void dump(View root,PrintStream output){
  output.println("SHIRAZ_VIEW_TREE_BEGIN");
  int[] budget={4096};walk(root,-1,0,new IdentityHashMap<View,Integer>(),budget,output);
  if(budget[0]==0)output.println("SHIRAZ_VIEW_TREE_TRUNCATED");
  output.println("SHIRAZ_VIEW_TREE_END");
 }
 private static void walk(View view,int parent,int depth,IdentityHashMap<View,Integer> seen,int[] budget,PrintStream out){
  if(view==null||budget[0]==0)return;budget[0]--;
  if(depth>64||seen.size()>=4096){out.println("SHIRAZ_VIEW_TREE_TRUNCATED");return;}
  if(seen.containsKey(view)){out.println("SHIRAZ_VIEW_TREE_REPEATED");return;}
  int index=seen.size();seen.put(view,index);String name=view.getClass().getName();if(name.length()>256)name=name.substring(0,256);
  out.println("SHIRAZ_VIEW "+index+" parent="+parent+" class="+name+" id="+Integer.toHexString(view.getId())+
   " bounds="+view.getLeft()+","+view.getTop()+","+view.getRight()+","+view.getBottom()+" visibility="+view.getVisibility()+
   " overlayDrawables="+view.shirazOverlayDrawableCount()+" alpha="+view.getAlpha()+" translation="+view.getTranslationX()+","+view.getTranslationY()+" scroll="+view.getScrollX()+","+view.getScrollY()+
   " clickable="+view.isClickable()+" enabled="+view.isEnabled()+
   " focused="+view.isFocused()+" focusable="+view.isFocusable()+" touchFocusable="+view.isFocusableInTouchMode()+
   (view instanceof android.widget.TextView?" textColor=0x"+Integer.toHexString(((android.widget.TextView)view).getCurrentTextColor()):""));
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;java.util.List<View> children=group.shirazWindowChildren();int count=Math.min(children.size(),4096);out.println("SHIRAZ_VIEW_CHILDREN parent="+index+" regular="+group.getChildCount()+" window="+children.size());for(int i=0;i<count&&budget[0]>0;i++)walk(children.get(i),index,depth+1,seen,budget,out);if(children.size()>count)out.println("SHIRAZ_VIEW_TREE_TRUNCATED");}
 }
}
