// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.Rect;
import android.view.Gravity;
import shiraz.runtime.PopupPlacement;
public final class PopupPlacementSmoke {
 private static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
 private static void bounds(Rect expected,PopupPlacement.Result actual){check(expected.equals(actual.bounds),expected+" != "+actual.bounds);}
 public static void main(String[] args){
  Rect a=new Rect(100,120,180,150),v=new Rect(0,0,600,400);
  bounds(new Rect(100,150,300,250),PopupPlacement.dropdown(a,v,200,100,0,0,Gravity.START,0,false,true));
  bounds(new Rect(0,150,200,250),PopupPlacement.dropdown(a,v,200,100,0,0,Gravity.START,1,false,true));
  bounds(new Rect(-20,150,180,250),PopupPlacement.dropdown(a,v,200,100,0,0,Gravity.END,0,false,false));
  bounds(new Rect(100,150,300,250),PopupPlacement.dropdown(a,v,200,100,0,0,Gravity.END,1,false,true));
  a=new Rect(100,350,180,380);PopupPlacement.Result r=PopupPlacement.dropdown(a,v,200,100,3,4,Gravity.LEFT,0,false,true);
  bounds(new Rect(103,254,303,354),r);check(r.above,"Above state");
  bounds(new Rect(100,350,300,390),PopupPlacement.dropdown(a,v,200,40,0,0,Gravity.LEFT,0,true,true));
  bounds(new Rect(0,0,600,400),PopupPlacement.dropdown(a,v,900,700,0,0,Gravity.LEFT,0,false,true));
  check(PopupPlacement.availableHeight(a,v,4,false)==354,"Available above");
  check(PopupPlacement.availableHeight(new Rect(10,20,30,40),v,0,true)==380,"Overlap height");
  java.util.Random random=new java.util.Random(481);
  for(int i=0;i<1000;i++){
   int x=random.nextInt(1200)-300,y=random.nextInt(800)-200,w=random.nextInt(900),h=random.nextInt(700);
   Rect anchor=new Rect(x,y,x+30,y+20);r=PopupPlacement.dropdown(anchor,v,w,h,random.nextInt(101)-50,random.nextInt(101)-50,Gravity.START,i%2,i%3==0,true);
   check(r.bounds.left>=0&&r.bounds.top>=0&&r.bounds.right<=600&&r.bounds.bottom<=400,"Clipped bounds");
   check(r.bounds.width()==Math.min(600,w)&&r.bounds.height()==Math.min(400,h),"Clipped size");
  }
  try{PopupPlacement.dropdown(new Rect(Integer.MAX_VALUE-1,0,Integer.MAX_VALUE,1),v,20,20,10,0,Gravity.LEFT,0,false,false);throw new AssertionError("Overflow accepted");}catch(IllegalArgumentException expected){}
  System.out.println("POPUP_PLACEMENT_OK");
 }
}
