// SPDX-License-Identifier: GPL-3.0-only
public final class FrameLayoutSmoke {
    public static void main(String[] args) {
        android.content.Context context=new android.content.ContextWrapper(null);
        org.shiraz.fixture.FrameLayoutFixture.check(context);
        final StringBuilder hierarchy=new StringBuilder();
        android.widget.FrameLayout watched=new android.widget.FrameLayout(context);
        watched.setOnHierarchyChangeListener(new android.view.ViewGroup.OnHierarchyChangeListener(){public void onChildViewAdded(android.view.View p,android.view.View c){hierarchy.append('+');}public void onChildViewRemoved(android.view.View p,android.view.View c){hierarchy.append('-');}});
        android.view.View watchedChild=new android.view.View(context);watched.addView(watchedChild);watched.removeView(watchedChild);
        if(!"+-".equals(hierarchy.toString()))throw new AssertionError("hierarchy listener "+hierarchy);
        final java.util.ArrayList<android.view.View> order=new java.util.ArrayList<android.view.View>();
        final class Reversed extends android.widget.FrameLayout{Reversed(android.content.Context c){super(c);}{setChildrenDrawingOrderEnabled(true);}protected int getChildDrawingOrder(int count,int position){return count-1-position;}public boolean orderEnabled(){return isChildrenDrawingOrderEnabled();}}
        Reversed reversed=new Reversed(context);
        android.view.View a=new android.view.View(context),b=new android.view.View(context),c=new android.view.View(context);reversed.addView(a);reversed.addView(b);reversed.addView(c);
        if(!reversed.orderEnabled())throw new AssertionError("drawing order flag");
        reversed.removeViews(1,2);if(reversed.getChildCount()!=1||reversed.getChildAt(0)!=a)throw new AssertionError("removeViews");
        android.view.View framed=new android.view.View(context);framed.layout(1,2,11,22);framed.setRight(30);android.graphics.Rect hit=new android.graphics.Rect();framed.getHitRect(hit);if(hit.left!=1||hit.top!=2||hit.right!=30||hit.bottom!=22)throw new AssertionError("setRight/getHitRect "+hit);
        android.view.View steady=new android.view.View(context);steady.setPadding(1,2,3,4);steady.setMinimumWidth(5);steady.setMinimumHeight(6);steady.measure(1073741844,1073741844);steady.layout(0,0,20,20);steady.setPadding(1,2,3,4);steady.setMinimumWidth(5);steady.setMinimumHeight(6);if(steady.isLayoutRequested())throw new AssertionError("unchanged geometry requests another layout");steady.setPaddingRelative(1,2,3,4);if(!steady.isLayoutRequested())throw new AssertionError("relative padding mode change lost");steady.measure(1073741844,1073741844);steady.layout(0,0,20,20);steady.setPaddingRelative(1,2,3,4);if(steady.isLayoutRequested())throw new AssertionError("unchanged relative padding relayout");steady.setPaddingRelative(2,2,3,4);if(!steady.isLayoutRequested())throw new AssertionError("changed padding does not relayout");
        android.widget.FrameLayout nested=new android.widget.FrameLayout(context);android.view.View target=new android.view.View(context);android.view.ViewParent parent=nested;
        if(parent.onStartNestedScroll(target,target,android.view.View.SCROLL_AXIS_VERTICAL)||nested.getNestedScrollAxes()!=0)throw new AssertionError("Base container claims nested scrolling");
        parent.onNestedScrollAccepted(target,target,3);if(nested.getNestedScrollAxes()!=3)throw new AssertionError("Accepted axes lost");int[] consumed={4,5};parent.onNestedPreScroll(target,20,30,consumed);parent.onNestedScroll(target,1,2,3,4);
        if(consumed[0]!=4||consumed[1]!=5||parent.onNestedFling(target,10,20,true)||parent.onNestedPreFling(target,10,20))throw new AssertionError("Disabled nested child consumed input");parent.onStopNestedScroll(target);if(nested.getNestedScrollAxes()!=0)throw new AssertionError("Stopped axes retained");
        final float[] touch={-1,-1};
        android.widget.FrameLayout mirroredRoot=new android.widget.FrameLayout(context);
        android.view.View mirrored=new android.view.View(context){protected void onDraw(android.graphics.Canvas c){android.graphics.Paint p=new android.graphics.Paint();p.setColor(android.graphics.Color.RED);c.drawRect(0,0,3,8,p);}public boolean onTouchEvent(android.view.MotionEvent e){touch[0]=e.getX();touch[1]=e.getRawX();return true;}};
        mirroredRoot.addView(mirrored,new android.widget.FrameLayout.LayoutParams(10,8));mirroredRoot.layout(0,0,20,10);mirrored.layout(0,0,10,8);mirrored.setTranslationX(2);mirrored.setRotationY(180);
        android.graphics.Bitmap mirroredPixels=android.graphics.Bitmap.createBitmap(20,10,android.graphics.Bitmap.Config.ARGB_8888);mirroredRoot.draw(new android.graphics.Canvas(mirroredPixels));
        if(mirroredPixels.getPixel(10,3)!=android.graphics.Color.RED||mirroredPixels.getPixel(3,3)!=0||mirrored.getRotationY()!=180)throw new AssertionError("Half-turn did not mirror pixels");
        float[] mapped={1,3};mirrored.getMatrix().mapPoints(mapped);if(mapped[0]!=11||mapped[1]!=3)throw new AssertionError("Half-turn matrix differs from draw");
        android.view.MotionEvent mirroredTouch=android.view.MotionEvent.obtain(0,0,0,11,3,0);mirroredRoot.dispatchTouchEvent(mirroredTouch);mirroredTouch.recycle();if(touch[0]!=1||touch[1]!=11)throw new AssertionError("Half-turn local/raw input coordinates");
        mirrored.layout(0,0,14,8);mapped[0]=1;mirrored.getMatrix().mapPoints(mapped);if(mapped[0]!=15)throw new AssertionError("Half-turn pivot stale after resize");
        try{mirrored.setRotationY(45);throw new AssertionError("Perspective rotation silently accepted");}catch(UnsupportedOperationException expected){}
        if(mirrored.getRotationY()!=180)throw new AssertionError("Rejected rotation mutated state");mirrored.setRotationY(360);if(mirrored.getMatrix().isIdentity())throw new AssertionError("Translation lost");mirroredPixels.recycle();
        final int[] measurements={0},layouts={0};
        android.view.View measured=new android.view.View(context){protected void onMeasure(int w,int h){measurements[0]++;super.onMeasure(w,h);}protected void onLayout(boolean changed,int l,int t,int r,int b){layouts[0]++;}};
        int spec=android.view.View.MeasureSpec.makeMeasureSpec(12,android.view.View.MeasureSpec.EXACTLY);
        measured.measure(spec,spec);measured.layout(0,0,12,12);measured.measure(spec,spec);measured.layout(0,0,12,12);
        if(measurements[0]!=1||layouts[0]!=1)throw new AssertionError("Unchanged measure/layout repeated callbacks");
        measured.requestLayout();measured.measure(spec,spec);measured.layout(0,0,12,12);
        if(measurements[0]!=2||layouts[0]!=2)throw new AssertionError("Layout request did not invalidate measurement");
        int wider=android.view.View.MeasureSpec.makeMeasureSpec(16,android.view.View.MeasureSpec.EXACTLY);measured.measure(wider,spec);measured.layout(0,0,16,12);
        if(measurements[0]!=3||measured.getMeasuredWidth()!=16)throw new AssertionError("Resize reused stale measurement");
        measured.forceLayout();measured.measure(wider,spec);measured.layout(0,0,16,12);
        if(measurements[0]!=4)throw new AssertionError("forceLayout ignored");
        android.widget.FrameLayout removal=new android.widget.FrameLayout(context);android.view.View removed=new android.view.View(context);removal.addView(removed);removal.measure(spec,spec);removal.layout(0,0,12,12);removal.removeViewInLayout(removed);
        if(removal.getChildCount()!=0||removed.getParent()!=null||removal.isLayoutRequested())throw new AssertionError("Removal during layout requested another pass");
        removal.addView(removed);removal.measure(spec,spec);removal.layout(0,0,12,12);
        if(removal.getDrawingTime()!=0||removed.getDrawingTime()!=0)throw new AssertionError("Detached drawing time");
        removal.shirazAttachTree(android.view.ViewTreeObserver.shirazCreateWindowObserver());android.graphics.Bitmap timing=android.graphics.Bitmap.createBitmap(12,12,android.graphics.Bitmap.Config.ARGB_8888);removal.shirazDrawWithAlpha(new android.graphics.Canvas(timing));
        if(removal.getDrawingTime()<=0||removal.getDrawingTime()!=removed.getDrawingTime())throw new AssertionError("Hierarchy drawing time differs");removal.shirazDetachTree();timing.recycle();if(removed.getDrawingTime()!=0)throw new AssertionError("Detached drawing time retained");
        final int[] paints={0};android.view.View painted=new android.view.View(context){public void draw(android.graphics.Canvas c){paints[0]++;c.drawColor(android.graphics.Color.RED);}};painted.layout(0,0,4,4);
        class DrawProbe extends android.widget.FrameLayout{DrawProbe(){super(context);}boolean render(android.graphics.Canvas c,android.view.View child){return drawChild(c,child,0);}}
        DrawProbe probe=new DrawProbe();android.graphics.Canvas placeholder=new android.graphics.Canvas(){public boolean isHardwareAccelerated(){return true;}};
        try{probe.render(placeholder,painted);throw new AssertionError("Hardware placeholder accepted as raster target");}catch(ClassCastException expected){}
        if(paints[0]!=0||painted.isHardwareAccelerated())throw new AssertionError("Hardware capability fabricated");
        android.graphics.Bitmap actual=android.graphics.Bitmap.createBitmap(4,4,android.graphics.Bitmap.Config.ARGB_8888);probe.render(new android.graphics.Canvas(actual),painted);if(paints[0]!=1||actual.getPixel(2,2)!=android.graphics.Color.RED)throw new AssertionError("Software rendering after rejected hardware probe");actual.recycle();
        final android.view.View[] seen=new android.view.View[2];final int[] calls={0};
        android.widget.FrameLayout root=new android.widget.FrameLayout(context){public void onDescendantInvalidated(android.view.View child,android.view.View target){seen[0]=child;seen[1]=target;calls[0]++;super.onDescendantInvalidated(child,target);}};
        android.widget.FrameLayout inner=new android.widget.FrameLayout(context);android.view.View leaf=new android.view.View(context);root.addView(inner);inner.addView(leaf);
        android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(image);
        root.draw(canvas);inner.draw(canvas);leaf.draw(canvas);
        ((android.view.ViewParent)inner).onDescendantInvalidated(leaf,leaf);
        if(!root.shirazIsInvalidated()||!inner.shirazIsInvalidated()||seen[0]!=inner||seen[1]!=leaf||calls[0]!=1)throw new AssertionError("Descendant invalidation propagation");
        inner.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE,null);root.draw(canvas);inner.draw(canvas);inner.onDescendantInvalidated(leaf,leaf);
        if(!root.shirazIsInvalidated()||seen[1]!=inner||calls[0]!=2)throw new AssertionError("Software layer invalidation target");image.recycle();
    }
}
