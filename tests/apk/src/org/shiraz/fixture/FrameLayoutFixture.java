// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;

public final class FrameLayoutFixture {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    private static int exact(int value) { return View.MeasureSpec.makeMeasureSpec(value,View.MeasureSpec.EXACTLY); }
    private static int loose(int value) { return View.MeasureSpec.makeMeasureSpec(value,View.MeasureSpec.AT_MOST); }
    private static void layout(FrameLayout root,int width,int height) { root.measure(exact(width),exact(height)); root.layout(0,0,width,height); }
    private static final class Leaf extends View {
        final int desiredWidth,desiredHeight,color; int clicks,cancels; float x,y,rawX,rawY; Runnable onDown;
        final ArrayList<Integer> actions=new ArrayList<Integer>();
        Leaf(Context context,int width,int height,int color) {
            super(context); desiredWidth=width; desiredHeight=height; this.color=color;
            setOnClickListener(new OnClickListener() { public void onClick(View view) { clicks++; } });
        }
        protected void onMeasure(int w,int h) { setMeasuredDimension(resolveSizeAndState(desiredWidth,w,0),resolveSizeAndState(desiredHeight,h,0)); }
        protected void onDraw(Canvas canvas) { canvas.drawColor(color); }
        public boolean onTouchEvent(MotionEvent event) {
            actions.add(event.getActionMasked()); x=event.getX(); y=event.getY(); rawX=event.getRawX(); rawY=event.getRawY();
            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) cancels++;
            boolean result=super.onTouchEvent(event); if (event.getActionMasked() == MotionEvent.ACTION_DOWN && onDown != null) onDown.run(); return result;
        }
    }
    private static final class DrawResultFrame extends FrameLayout {
        boolean more;
        DrawResultFrame(Context context) { super(context); }
        protected boolean drawChild(Canvas canvas,View child,long time) { more=super.drawChild(canvas,child,time); return more; }
    }
    private static class InterceptFrame extends FrameLayout {
        boolean intercept; int ownMoves;
        InterceptFrame(Context context) { super(context); }
        public boolean onInterceptTouchEvent(MotionEvent event) { return intercept && event.getActionMasked() == MotionEvent.ACTION_MOVE; }
        public boolean onTouchEvent(MotionEvent event) { if (event.getActionMasked() == MotionEvent.ACTION_MOVE) ownMoves++; return true; }
    }
    private static boolean event(View root,int action,float x,float y) {
        MotionEvent input=MotionEvent.obtain(1,2,action,x,y,0);
        try { boolean handled=root.dispatchTouchEvent(input); check(input.getX() == x && input.getY() == y && input.getActionMasked() == action,"caller input preserved"); return handled; }
        finally { input.recycle(); }
    }
    private static void tap(View root,float x,float y) { check(event(root,MotionEvent.ACTION_DOWN,x,y),"tap down handled"); check(event(root,MotionEvent.ACTION_UP,x,y),"tap up handled"); }
    public static void checkForegroundXml(Context context){
        int id=context.getResources().getIdentifier("foreground_frame","layout",context.getPackageName());FrameLayout root=(FrameLayout)LayoutInflater.from(context).inflate(id,null,false);layout(root,8,8);
        Bitmap pixels=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);root.draw(new Canvas(pixels));
        check(pixels.getPixel(0,0)==Color.BLUE&&pixels.getPixel(2,2)==Color.BLUE&&pixels.getPixel(5,5)==Color.BLUE&&pixels.getPixel(6,6)==Color.BLUE,"XML fill foreground overlays padding and child");pixels.recycle();
        final boolean[] stale={false};android.graphics.drawable.Drawable old=root.getForeground();old.scheduleSelf(new Runnable(){public void run(){stale[0]=true;}},0);root.setForeground(null);
        check(old.getCallback()==null,"XML foreground detached");new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable(){public void run(){check(!stale[0],"old foreground scheduled callback survived replacement");System.out.println("SHIRAZ_FOREGROUND_QUEUE_OK");}});
        int theme=context.getResources().getIdentifier("ForegroundEmpty","style",context.getPackageName());Context themed=new android.view.ContextThemeWrapper(context,theme);
        int emptyId=context.getResources().getIdentifier("foreground_empty","layout",context.getPackageName());FrameLayout empty=(FrameLayout)LayoutInflater.from(themed).inflate(emptyId,null,false);layout(empty,4,4);
        check(empty.getForeground()==null,"theme null foreground");Bitmap clearForeground=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888);empty.draw(new Canvas(clearForeground));check(clearForeground.getPixel(0,0)==Color.GREEN,"null foreground preserves background");clearForeground.recycle();
        System.out.println("SHIRAZ_FOREGROUND_XML_OK");
    }
    public static void check(Context context) {
        MotionEvent finite=MotionEvent.obtain(1,1,MotionEvent.ACTION_DOWN,-Float.MAX_VALUE,0,0);
        try {
            finite.setLocation(Float.MAX_VALUE,1); check(finite.getX() == Float.MAX_VALUE && finite.getRawX() == -Float.MAX_VALUE,"finite location transition preserves raw input");
            try { finite.offsetLocation(Float.MAX_VALUE,0); throw new AssertionError("offset overflow accepted"); } catch (IllegalArgumentException expected) { }
            check(finite.getX() == Float.MAX_VALUE && finite.getY() == 1,"failed offset is atomic");
        } finally { finite.recycle(); }
        FrameLayout root=new FrameLayout(context); root.setPadding(1,1,1,1);
        Leaf red=new Leaf(context,3,2,Color.RED),blue=new Leaf(context,2,2,Color.BLUE);
        FrameLayout.LayoutParams redParams=new FrameLayout.LayoutParams(-2,-2); redParams.setMargins(1,1,0,0);
        FrameLayout.LayoutParams blueParams=new FrameLayout.LayoutParams(2,2,Gravity.RIGHT|Gravity.BOTTOM); blueParams.setMargins(0,0,1,1);
        root.addView(red,redParams); root.addView(blue,blueParams); red.setId(123); layout(root,8,6);
        check(red.getParent() == root && root.getChildCount() == 2 && root.getChildAt(-1) == null && root.getChildAt(2) == null && root.findViewById(123) == red,"hierarchy identity and lookup");
        check(red.getLeft() == 2 && red.getTop() == 2 && blue.getLeft() == 4 && blue.getTop() == 2,"margins and bottom-right gravity");
        Bitmap pixels=Bitmap.createBitmap(8,6,Bitmap.Config.ARGB_8888); root.draw(new Canvas(pixels));
        check(pixels.getPixel(2,2) == Color.RED && pixels.getPixel(4,2) == Color.BLUE && pixels.getPixel(6,2) == 0 && pixels.getPixel(0,0) == 0,"children composited with bounds clipping");
        check(!root.isLayoutRequested(),"layout request cleared"); red.requestLayout(); check(root.isLayoutRequested(),"child layout request reaches root"); layout(root,8,6);
        tap(root,4.5f,2.5f); check(blue.clicks == 1 && red.clicks == 0 && blue.x == .5f && blue.y == .5f && blue.rawX == 4.5f && blue.rawY == 2.5f,"topmost input local/raw coordinates");
        root.bringChildToFront(red); tap(root,4.5f,2.5f); check(red.clicks == 1,"changed z-order input");
        for (int n=0;n<5;n++) { layout(root,12,8); tap(root,8.5f,4.5f); layout(root,8,12); tap(root,4.5f,8.5f); }
        check(blue.clicks == 11,"touch after portrait/landscape relayout");
        event(root,MotionEvent.ACTION_DOWN,4.5f,8.5f); event(root,MotionEvent.ACTION_MOVE,0,0); event(root,MotionEvent.ACTION_UP,0,0); check(blue.clicks == 11 && !blue.isPressed(),"captured drag outside cancels click");
        event(root,MotionEvent.ACTION_DOWN,4.5f,8.5f); root.removeView(blue); check(blue.cancels == 1 && !blue.isPressed() && blue.getParent() == null,"removal cancels active child");
        root.addView(blue,blueParams); layout(root,8,12); event(root,MotionEvent.ACTION_DOWN,4.5f,8.5f); blue.setVisibility(View.INVISIBLE);
        check(blue.cancels == 2 && !blue.isPressed(),"hidden child receives cancel"); blue.setVisibility(View.VISIBLE);
        event(root,MotionEvent.ACTION_DOWN,4.5f,8.5f); root.setVisibility(View.INVISIBLE); check(blue.cancels == 3 && !blue.isPressed(),"hidden root cancels child"); root.setVisibility(View.VISIBLE);
        check(blue.requestFocus() && root.hasFocus() && root.findFocus() == blue && root.getFocusedChild() == blue,"child focus branch");
        check(red.requestFocus() && !blue.hasFocus() && root.findFocus() == red,"sibling focus exclusive");
        KeyEvent keyDown=new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER),keyUp=new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER);
        check(root.dispatchKeyEvent(keyDown) && root.dispatchKeyEvent(keyUp) && red.clicks == 2,"keyboard reaches focused child");
        root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS); check(!red.hasFocus() && !red.requestFocus(),"blocked descendant focus");
        root.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS); root.setFocusable(true); red.requestFocus(); check(root.requestFocus() && root.isFocused() && !red.hasFocus(),"own focus clears child focus");
        try { root.addView(red); throw new AssertionError("double parent accepted"); } catch (IllegalStateException expected) { }
        FrameLayout nested=new FrameLayout(context); root.addView(nested,new FrameLayout.LayoutParams(1,1));
        try { nested.addView(root); throw new AssertionError("cycle accepted"); } catch (IllegalArgumentException expected) { }
        FrameLayout wrap=new FrameLayout(context); wrap.setPadding(1,1,1,1);
        Leaf fixed=new Leaf(context,3,2,Color.RED),fill=new Leaf(context,0,0,Color.BLUE),gone=new Leaf(context,10,9,Color.GREEN);
        FrameLayout.LayoutParams fixedParams=new FrameLayout.LayoutParams(-2,-2); fixedParams.setMargins(1,1,1,1);
        wrap.addView(fixed,fixedParams); wrap.addView(fill,new FrameLayout.LayoutParams(-1,-1)); gone.setVisibility(View.GONE); wrap.addView(gone,new FrameLayout.LayoutParams(-2,-2));
        wrap.measure(loose(100),loose(100)); check(wrap.getMeasuredWidth() == 7 && wrap.getMeasuredHeight() == 6 && fill.getMeasuredWidth() == 5 && fill.getMeasuredHeight() == 4,"wrap and match-parent second pass");
        wrap.setMeasureAllChildren(true); wrap.measure(loose(100),loose(100)); check(wrap.getMeasureAllChildren() && wrap.getMeasuredWidth() == 12 && wrap.getMeasuredHeight() == 11,"gone measuring policy");
        FrameLayout.LayoutParams relative=new FrameLayout.LayoutParams(2,1,Gravity.START|Gravity.TOP); relative.setMargins(3,0,4,0); relative.setMarginStart(1);
        FrameLayout rtl=new FrameLayout(context); Leaf start=new Leaf(context,2,1,Color.RED); rtl.addView(start,relative); layout(rtl,10,4);
        check(start.getLeft() == 1 && relative.rightMargin == 4,"LTR relative margin"); rtl.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); layout(rtl,10,4);
        check(start.getLeft() == 7 && relative.leftMargin == 3 && relative.rightMargin == 1,"RTL start placement and margin"); rtl.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); layout(rtl,10,4); check(start.getLeft() == 1 && relative.rightMargin == 4,"relative margin direction reversal");
        start.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); layout(rtl,10,4); check(relative.leftMargin == 3 && relative.rightMargin == 1 && start.getLeft() == 3,"explicit child direction resolves margins independently of parent gravity");
        start.setLayoutDirection(View.LAYOUT_DIRECTION_INHERIT); layout(rtl,10,4);
        FrameLayout.LayoutParams copied=new FrameLayout.LayoutParams(relative); check(copied.gravity == relative.gravity && copied.getMarginStart() == 1 && copied.width == 2,"layout params copy");
        InterceptFrame intercept=new InterceptFrame(context); Leaf target=new Leaf(context,4,4,Color.RED); intercept.addView(target,new FrameLayout.LayoutParams(4,4)); layout(intercept,8,8);
        event(intercept,MotionEvent.ACTION_DOWN,1,1); intercept.intercept=true; event(intercept,MotionEvent.ACTION_MOVE,2,2); check(target.cancels == 1 && intercept.ownMoves == 1,"interception cancels child then routes parent"); event(intercept,MotionEvent.ACTION_UP,2,2);
        event(intercept,MotionEvent.ACTION_DOWN,1,1); target.getParent().requestDisallowInterceptTouchEvent(true); event(intercept,MotionEvent.ACTION_MOVE,2,2); event(intercept,MotionEvent.ACTION_UP,2,2); check(target.cancels == 1 && target.clicks == 1,"disallow interception preserves child gesture");
        final FrameLayout reentrant=new FrameLayout(context); final Leaf transientChild=new Leaf(context,3,3,Color.RED);
        transientChild.onDown=new Runnable() { public void run() { reentrant.removeView(transientChild); } };
        reentrant.addView(transientChild,new FrameLayout.LayoutParams(3,3)); layout(reentrant,4,4); event(reentrant,MotionEvent.ACTION_DOWN,1,1);
        check(transientChild.getParent() == null && transientChild.cancels == 1 && !transientChild.isPressed(),"removal during down does not retain pressed target");
        FrameLayout outer=new FrameLayout(context),inner=new FrameLayout(context); Leaf innerLeaf=new Leaf(context,2,2,Color.RED); inner.addView(innerLeaf,new FrameLayout.LayoutParams(2,2));
        FrameLayout.LayoutParams innerParams=new FrameLayout.LayoutParams(4,4); innerParams.setMargins(2,3,0,0); outer.addView(inner,innerParams); layout(outer,8,9); tap(outer,2.5f,3.5f);
        check(innerLeaf.clicks == 1 && innerLeaf.x == .5f && innerLeaf.y == .5f && innerLeaf.rawX == 2.5f,"nested input transform");
        FrameLayout textFrame=new FrameLayout(context); final Paint textPaint=new Paint(); textPaint.setColor(Color.RED); textPaint.setTextSize(16);
        View text=new View(context) { protected void onDraw(Canvas value) { value.drawText("Map",0,16,textPaint); } };
        FrameLayout.LayoutParams textParams=new FrameLayout.LayoutParams(8,8); textParams.setMargins(4,4,0,0); textFrame.addView(text,textParams); layout(textFrame,64,32);
        Bitmap actualText=Bitmap.createBitmap(64,32,Bitmap.Config.ARGB_8888),referenceText=Bitmap.createBitmap(64,32,Bitmap.Config.ARGB_8888);
        textFrame.draw(new Canvas(actualText)); new Canvas(referenceText).drawText("Map",4,20,textPaint); int ink=0;
        for (int py=0;py<32;py++) for (int px=0;px<64;px++) {
            int expected=px >= 4 && px < 12 && py >= 4 && py < 12 ? referenceText.getPixel(px,py) : 0;
            check(actualText.getPixel(px,py) == expected,"translated/clipped child text pixels"); if (expected != 0) ink++;
        }
        check(ink > 0,"child text produced visible ink");
        DrawResultFrame drawResult=new DrawResultFrame(context); drawResult.addView(new Leaf(context,1,1,Color.RED),new FrameLayout.LayoutParams(1,1)); layout(drawResult,2,2);
        drawResult.draw(new Canvas(Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888))); check(!drawResult.more,"static child draw does not request invalidation");
        drawResult.addView(new View(context) { protected void onDraw(Canvas value) { invalidate(); } },new FrameLayout.LayoutParams(1,1)); layout(drawResult,2,2);
        drawResult.draw(new Canvas(Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888))); check(drawResult.more,"child invalidation reported from drawChild");
        final FrameLayout hiddenRoot=new FrameLayout(context); Leaf hiddenDuringDown=new Leaf(context,2,2,Color.RED);
        hiddenDuringDown.onDown=new Runnable() { public void run() { hiddenRoot.setVisibility(View.INVISIBLE); } }; hiddenRoot.addView(hiddenDuringDown,new FrameLayout.LayoutParams(2,2)); layout(hiddenRoot,3,3);
        event(hiddenRoot,MotionEvent.ACTION_DOWN,1,1); check(hiddenDuringDown.cancels == 1 && !hiddenDuringDown.isPressed(),"hide during down cancels candidate target");
        root.removeAllViews(); check(root.getChildCount() == 0 && red.getParent() == null && nested.getParent() == null,"remove all hierarchy cleanup");
        FrameLayout negative=new FrameLayout(context);negative.setPadding(-2,-1,1,0);Leaf expanded=new Leaf(context,1,1,Color.GREEN);
        negative.addView(expanded,new FrameLayout.LayoutParams(-1,-1));layout(negative,6,4);
        check(expanded.getLeft()==-2&&expanded.getTop()==-1&&expanded.getMeasuredWidth()==7&&expanded.getMeasuredHeight()==5,"negative padded child bounds");
        Bitmap clipped=Bitmap.createBitmap(6,4,Bitmap.Config.ARGB_8888);negative.draw(new Canvas(clipped));
        check(clipped.getPixel(0,0)==Color.GREEN&&clipped.getPixel(4,3)==Color.GREEN&&clipped.getPixel(5,0)==0,"negative padded parent clipping");
        tap(negative,1,1);check(expanded.clicks==1&&expanded.x==3&&expanded.y==2,"negative padded pointer transform");
        Object owner=new Object(),other=new Object();int ownerKey=0x7f010123,otherKey=0x7f010124;
        negative.setTag("unkeyed");negative.setTag(ownerKey,owner);negative.setTag(otherKey,other);
        check(negative.getTag(ownerKey)==owner&&negative.getTag(otherKey)==other&&negative.getTag().equals("unkeyed"),"independent keyed and unkeyed tags");
        View ancestor=expanded;while(ancestor!=null&&ancestor.getTag(ownerKey)==null)ancestor=ancestor.getParent() instanceof View?(View)ancestor.getParent():null;
        check(ancestor==negative&&ancestor.getTag(ownerKey)==owner,"view tree owner lookup");
        expanded.setTag(ownerKey,other);check(expanded.getTag(ownerKey)==other&&negative.getTag(ownerKey)==owner,"view-local tag ownership");
        expanded.setTag(ownerKey,null);check(expanded.getTag(ownerKey)==null,"tag removal");
        negative.setTag(ownerKey,other);check(negative.getTag(ownerKey)==other,"tag replacement");
        for(int key:new int[]{0,1,0x01010000}){try{negative.setTag(key,owner);throw new AssertionError("invalid tag key accepted");}catch(IllegalArgumentException expected){}}
        View bounded=new View(context);for(int i=0;i<4096;i++)bounded.setTag(0x7f020000+i,owner);
        try{bounded.setTag(0x7f030000,other);throw new AssertionError("unbounded view tags");}catch(IllegalStateException expected){}
        bounded.setTag(0x7f020000,other);bounded.setTag(0x7f020001,null);bounded.setTag(0x7f030000,other);
        check(bounded.getTag(0x7f020000)==other&&bounded.getTag(0x7f030000)==other,"tag replacement/removal at capacity");
        final int[] foregroundStates={0};final android.graphics.drawable.ColorDrawable foreground=new android.graphics.drawable.ColorDrawable(Color.BLUE){public int getIntrinsicWidth(){return 2;}public int getIntrinsicHeight(){return 2;}public boolean isStateful(){return true;}protected boolean onStateChange(int[] state){foregroundStates[0]++;return true;}};
        FrameLayout layered=new FrameLayout(context);layered.setBackgroundColor(Color.GREEN);layered.addView(new Leaf(context,4,4,Color.RED),new FrameLayout.LayoutParams(4,4));layered.setForeground(foreground);layered.setForegroundGravity(Gravity.BOTTOM|Gravity.END);layout(layered,6,6);layered.setWillNotDraw(true);
        Bitmap overlay=Bitmap.createBitmap(6,6,Bitmap.Config.ARGB_8888);layered.draw(new Canvas(overlay));check(overlay.getPixel(0,0)==Color.RED&&overlay.getPixel(4,0)==Color.GREEN&&overlay.getPixel(5,5)==Color.BLUE,"foreground draws after children and despite willNotDraw");
        layered.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);layered.draw(new Canvas(overlay));check(foreground.getBounds().left==0&&foreground.getBounds().top==4,"foreground RTL gravity");
        layout(layered,8,4);layered.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);Bitmap resizedForeground=Bitmap.createBitmap(8,4,Bitmap.Config.ARGB_8888);layered.draw(new Canvas(resizedForeground));check(foreground.getBounds().left==6&&foreground.getBounds().top==2,"foreground bounds after resize");
        int oldState=foregroundStates[0];layered.setPressed(true);check(foregroundStates[0]>oldState&&foreground.getCallback()==layered,"foreground state and callback");layered.setVisibility(View.INVISIBLE);check(!foreground.isVisible(),"foreground visibility");layered.setVisibility(View.VISIBLE);
        layered.setForeground(null);check(foreground.getCallback()==null&&layered.getForeground()==null,"foreground detach");
        FrameLayout padded=new FrameLayout(context);padded.setPadding(1,1,1,1);padded.setForeground(new android.graphics.drawable.ColorDrawable(0){public boolean getPadding(Rect out){out.set(3,2,4,1);return true;}});View paddedChild=new View(context);padded.addView(paddedChild,new FrameLayout.LayoutParams(-1,-1));layout(padded,10,8);check(paddedChild.getLeft()==3&&paddedChild.getTop()==2&&paddedChild.getWidth()==3&&paddedChild.getHeight()==5,"foreground padding participates in FrameLayout measurement");
        overlay.recycle();resizedForeground.recycle();
        System.out.println("SHIRAZ_FIXTURE_FRAMELAYOUT_OK");
    }
}
