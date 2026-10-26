// SPDX-License-Identifier: GPL-3.0-only
public final class DrawableSmoke {
    private static void activation(){
        android.content.Context context=new android.content.ContextWrapper(null);
        android.widget.FrameLayout root=new android.widget.FrameLayout(context),nested=new android.widget.FrameLayout(context);
        android.view.View child=new android.view.View(context);root.addView(nested);nested.addView(child);child.layout(0,0,8,8);
        android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable();
        background.addState(new int[]{16843518},new android.graphics.drawable.ColorDrawable(0xffff0000));
        background.addState(new int[0],new android.graphics.drawable.ColorDrawable(0xff0000ff));child.setBackground(background);
        android.graphics.Bitmap pixels=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888);
        try{
            child.draw(new android.graphics.Canvas(pixels));if(pixels.getPixel(4,4)!=0xff0000ff)throw new AssertionError("Inactive selector pixels");
            root.setActivated(true);child.draw(new android.graphics.Canvas(pixels));
            if(!child.isActivated()||!nested.isActivated()||child.isSelected()||pixels.getPixel(4,4)!=0xffff0000)throw new AssertionError("Activated descendant selector pixels");
            child.setActivated(false);root.setActivated(true);if(child.isActivated())throw new AssertionError("Unchanged parent must not redispatch activation");
            root.setActivated(false);if(nested.isActivated())throw new AssertionError("Deactivation propagation");
            child.draw(new android.graphics.Canvas(pixels));if(pixels.getPixel(4,4)!=0xff0000ff)throw new AssertionError("Deactivated selector pixels");
        }finally{pixels.recycle();}
    }
    private static void transition(){
        android.graphics.drawable.ColorDrawable red=new android.graphics.drawable.ColorDrawable(0xffff0000),blue=new android.graphics.drawable.ColorDrawable(0xff0000ff);
        class Fade extends android.graphics.drawable.TransitionDrawable {long now=100;Fade(){super(new android.graphics.drawable.Drawable[]{red,blue});}protected long shirazTransitionTime(){return now;}}
        Fade fade=new Fade();fade.setBounds(0,0,2,2);android.graphics.Bitmap pixels=android.graphics.Bitmap.createBitmap(2,2,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(pixels);
        final int[] invalidations={0};fade.setCallback(new android.graphics.drawable.Drawable.Callback(){public void invalidateDrawable(android.graphics.drawable.Drawable d){invalidations[0]++;}public void scheduleDrawable(android.graphics.drawable.Drawable d,Runnable r,long t){}public void unscheduleDrawable(android.graphics.drawable.Drawable d,Runnable r){}});
        fade.draw(canvas);if(pixels.getPixel(0,0)!=0xffff0000)throw new AssertionError("initial transition layer");
        fade.startTransition(100);fade.draw(canvas);fade.now=150;fade.draw(canvas);int mid=pixels.getPixel(0,0);if((mid&255)<120||((mid>>>16)&255)<120)throw new AssertionError("transition midpoint blend");
        fade.reverseTransition(100);fade.now=200;pixels.eraseColor(0);fade.draw(canvas);if(pixels.getPixel(0,0)!=0xffff0000)throw new AssertionError("mid-flight reversal");
        fade.setCrossFadeEnabled(true);fade.startTransition(0);pixels.eraseColor(0);fade.draw(canvas);if(pixels.getPixel(0,0)!=0xff0000ff||red.getAlpha()!=255||blue.getAlpha()!=255)throw new AssertionError("crossfade endpoint/alpha restoration");
        int before=invalidations[0];fade.draw(canvas);if(invalidations[0]!=before)throw new AssertionError("settled transition keeps requesting frames");
        fade.resetTransition();pixels.eraseColor(0);fade.draw(canvas);if(pixels.getPixel(0,0)!=0xffff0000)throw new AssertionError("reset transition");pixels.recycle();
    }
    public static void main(String[] args) { org.shiraz.fixture.DrawableFixture.check(); activation(); transition();
        android.graphics.drawable.Drawable child=new android.graphics.drawable.ColorDrawable(0xffff0000){public int getIntrinsicWidth(){return 4;}public int getIntrinsicHeight(){return 6;}};
        android.graphics.drawable.ScaleDrawable scaled=new android.graphics.drawable.ScaleDrawable(child,android.view.Gravity.LEFT|android.view.Gravity.TOP,1,1);scaled.shirazSetIntrinsicMinimum(true);scaled.setBounds(0,0,20,20);scaled.setLevel(5000);if(!child.getBounds().equals(new android.graphics.Rect(0,0,12,13)))throw new AssertionError("Intrinsic minimum scale");
        try{new android.graphics.drawable.ScaleDrawable(null,3,Float.NaN,1);throw new AssertionError("Invalid scale accepted");}catch(IllegalArgumentException expected){} }
}
