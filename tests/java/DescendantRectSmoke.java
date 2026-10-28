// SPDX-License-Identifier: GPL-3.0-only
import android.view.*;
import android.graphics.Rect;
public class DescendantRectSmoke {
 private static final class Group extends ViewGroup {Group(android.content.Context context){super(context);}protected void onLayout(boolean changed,int left,int top,int right,int bottom){}}
 public static void main(String[] args){
  android.os.Looper.prepare();android.content.Context context=new android.content.ContextWrapper(null);
  ViewGroup root=new Group(context),middle=new Group(context);View child=new View(context);root.addView(middle);middle.addView(child);
  root.layout(100,100,300,300);middle.layout(10,20,110,120);child.layout(5,7,25,27);middle.scrollTo(3,4);child.scrollTo(1,2);
  Rect rect=new Rect(0,0,20,20);root.offsetDescendantRectToMyCoords(child,rect);
  if(!rect.equals(new Rect(11,21,31,41)))throw new AssertionError("nested offsets: "+rect);
  root.offsetRectIntoDescendantCoords(child,rect);if(!rect.equals(new Rect(0,0,20,20)))throw new AssertionError("inverse");
  root.offsetDescendantRectToMyCoords(root,rect);if(rect.left!=0||rect.top!=0)throw new AssertionError("self");
  try{root.offsetDescendantRectToMyCoords(new View(context),rect);throw new AssertionError("unrelated view");}catch(IllegalArgumentException expected){}
  if(rect.left!=0||rect.top!=0)throw new AssertionError("failed conversion mutated rectangle");
  System.out.println("DESCENDANT_RECT_COORDINATES_OK");
 }
}
