// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.Arrays;
public final class LayoutListenerFixture {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static final class Traced extends View {
        final StringBuilder order=new StringBuilder();Traced(Context c){super(c);}
        protected void onSizeChanged(int w,int h,int oldw,int oldh){order.append('S');}
        protected void onLayout(boolean changed,int l,int t,int r,int b){order.append(changed?'L':'l');}
    }
    public static void check(Context context){
        final Traced v=new Traced(context);final ArrayList<int[]> events=new ArrayList<int[]>();check(!v.isLaidOut(),"initial layout state");
        View.OnLayoutChangeListener listener=new View.OnLayoutChangeListener(){public void onLayoutChange(View view,int l,int t,int r,int b,int ol,int ot,int or,int ob){check(view==v,"callback identity");v.order.append('E');events.add(new int[]{l,t,r,b,ol,ot,or,ob});}};
        v.addOnLayoutChangeListener(listener);v.addOnLayoutChangeListener(listener);v.layout(1,2,101,202);check(events.size()==1&&Arrays.equals(events.get(0),new int[]{1,2,101,202,0,0,0,0}),"initial geometry and duplicate registration");check(v.order.toString().equals("SLE")&&v.isLaidOut(),"size/layout/listener ordering");
        v.layout(1,2,101,202);check(events.size()==1,"unchanged unrequested layout does not notify");v.layout(3,4,203,104);check(events.size()==2&&Arrays.equals(events.get(1),new int[]{3,4,203,104,1,2,101,202}),"portrait/landscape old/new bounds");
        v.measure(View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY));v.layout(3,4,203,104);check(events.size()==3,"measured layout pass notifies with stable bounds");v.removeOnLayoutChangeListener(listener);v.layout(0,0,10,10);check(events.size()==3,"listener removed");
        final StringBuilder calls=new StringBuilder();final View mutation=new View(context);
        final View.OnLayoutChangeListener later=new View.OnLayoutChangeListener(){public void onLayoutChange(View a,int l,int t,int r,int b,int ol,int ot,int or,int ob){calls.append('C');}};
        final View.OnLayoutChangeListener second=new View.OnLayoutChangeListener(){public void onLayoutChange(View a,int l,int t,int r,int b,int ol,int ot,int or,int ob){calls.append('B');}};
        View.OnLayoutChangeListener first=new View.OnLayoutChangeListener(){public void onLayoutChange(View a,int l,int t,int r,int b,int ol,int ot,int or,int ob){calls.append('A');a.removeOnLayoutChangeListener(this);a.removeOnLayoutChangeListener(second);a.addOnLayoutChangeListener(later);a.requestLayout();}};
        mutation.addOnLayoutChangeListener(first);mutation.addOnLayoutChangeListener(second);mutation.layout(0,0,10,20);check(calls.toString().equals("AB")&&mutation.isLayoutRequested(),"snapshot dispatch and preserved callback layout request");mutation.measure(View.MeasureSpec.makeMeasureSpec(10,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(20,View.MeasureSpec.EXACTLY));mutation.layout(0,0,10,20);check(calls.toString().equals("ABC"),"listener modifications affect next pass");
        final FrameLayout root=new FrameLayout(context);final View child=new View(context);root.addView(child,new FrameLayout.LayoutParams(4,6));final boolean[] parentEvent={false};root.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){public void onLayoutChange(View a,int l,int t,int r,int b,int ol,int ot,int or,int ob){check(child.getWidth()==4&&child.getHeight()==6,"children laid out before parent listener");parentEvent[0]=true;}});root.measure(View.MeasureSpec.makeMeasureSpec(40,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY));root.layout(0,0,40,80);check(parentEvent[0],"parent listener dispatched");
        final Traced shifted=new Traced(context);root.addView(shifted,new FrameLayout.LayoutParams(4,6));root.measure(View.MeasureSpec.makeMeasureSpec(40,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY));root.layout(0,0,40,80);
        final int[] movedEvents={0},clicks={0};shifted.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){public void onLayoutChange(View a,int l,int t,int r,int b,int ol,int ot,int or,int ob){movedEvents[0]++;}});shifted.setOnClickListener(new View.OnClickListener(){public void onClick(View a){clicks[0]++;}});shifted.setBackgroundColor(android.graphics.Color.RED);root.measure(View.MeasureSpec.makeMeasureSpec(40,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY));root.layout(0,0,40,80);movedEvents[0]=0;String oldOrder=shifted.order.toString();
        shifted.offsetLeftAndRight(12);shifted.offsetTopAndBottom(20);check(shifted.getLeft()==12&&shifted.getTop()==20&&shifted.getRight()==16&&shifted.getBottom()==26,"offset geometry");check(!shifted.isLayoutRequested()&&movedEvents[0]==0&&shifted.order.toString().equals(oldOrder),"offset does not relayout or notify size/layout listeners");
        android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(40,80,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(image));check(image.getPixel(13,21)==android.graphics.Color.RED&&image.getPixel(1,1)!=android.graphics.Color.RED,"offset drawing position");image.recycle();
        android.view.MotionEvent down=android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_DOWN,13,21,0),up=android.view.MotionEvent.obtain(0,1,android.view.MotionEvent.ACTION_UP,13,21,0);try{root.dispatchTouchEvent(down);root.dispatchTouchEvent(up);}finally{down.recycle();up.recycle();}check(clicks[0]==1,"offset hit testing");
        shifted.offsetLeftAndRight(-12);shifted.offsetTopAndBottom(-20);check(shifted.getLeft()==0&&shifted.getTop()==0&&shifted.getWidth()==4&&shifted.getHeight()==6,"negative offset preserves size");
        System.out.println("SHIRAZ_FIXTURE_LAYOUT_LISTENERS_OK");
    }
}
