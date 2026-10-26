// SPDX-License-Identifier: GPL-3.0-only
import android.view.View;
public final class LayoutListenerSmoke {
 public static void main(String[] args){
  android.content.Context context=new android.content.ContextWrapper(null);
  org.shiraz.fixture.LayoutListenerFixture.check(context);
  View bounded=new View(context);
  for(int n=0;n<1024;n++)bounded.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){}});
  try{bounded.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){}});throw new AssertionError("listener budget accepted");}catch(IllegalStateException expected){}
  final View reentrant=new View(context);final int[] count={0};reentrant.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){if(++count[0]==1)v.layout(0,0,20,10);}});reentrant.layout(0,0,10,20);if(count[0]!=2||reentrant.getWidth()!=20||reentrant.getHeight()!=10)throw new AssertionError("reentrant layout corrupted geometry");
  View edge=new View(context);edge.layout(Integer.MAX_VALUE-10,0,Integer.MAX_VALUE,10);try{edge.offsetLeftAndRight(1);throw new AssertionError("offset overflow accepted");}catch(IllegalArgumentException expected){}if(edge.getRight()!=Integer.MAX_VALUE||edge.getWidth()!=10)throw new AssertionError("failed offset mutated bounds");
  System.out.println("LAYOUT_LISTENER_DISPATCH_OK");
 }
}
