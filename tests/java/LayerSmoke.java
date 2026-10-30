// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.Resources;
import android.graphics.*;
import android.view.View;
import android.widget.FrameLayout;
import android.view.Gravity;
public final class LayerSmoke {
    private static void opticalInsets(){
        android.graphics.drawable.ColorDrawable leaf=new android.graphics.drawable.ColorDrawable(Color.RED);
        if(leaf.getOpticalInsets()!=Insets.NONE)throw new AssertionError("Default optical alignment");
        android.graphics.drawable.InsetDrawable inner=new android.graphics.drawable.InsetDrawable(leaf,1,2,3,4);
        android.graphics.drawable.InsetDrawable outer=new android.graphics.drawable.InsetDrawable(inner,.1f,.2f,.3f,.4f);
        outer.setBounds(0,0,100,50);
        if(!outer.getOpticalInsets().equals(Insets.of(11,12,33,24)))throw new AssertionError("Nested optical geometry");
        outer.setBounds(0,0,200,100);
        if(!outer.getOpticalInsets().equals(Insets.of(21,22,63,44)))throw new AssertionError("Optical insets follow resize");
        android.graphics.drawable.DrawableWrapper wrapper=new android.graphics.drawable.DrawableWrapper(outer){};
        wrapper.setBounds(0,0,200,100);
        if(!wrapper.getOpticalInsets().equals(outer.getOpticalInsets()))throw new AssertionError("Wrapper optical delegation");
        wrapper.setDrawable(null);if(wrapper.getOpticalInsets()!=Insets.NONE)throw new AssertionError("Empty wrapper alignment");
        android.graphics.drawable.StateListDrawable state=new android.graphics.drawable.StateListDrawable();
        state.addState(new int[]{0x010100a7 /* state_pressed */},inner);state.addState(new int[0],leaf);
        if(state.getOpticalInsets()!=Insets.NONE)throw new AssertionError("Default selected alignment");
        state.setState(new int[]{0x010100a7 /* state_pressed */});
        if(!state.getOpticalInsets().equals(Insets.of(1,2,3,4)))throw new AssertionError("State-selected optical alignment");
    }
    private static void deferredCorners(Resources r){
        int id=r.getIdentifier("deferred_corners","drawable","org.shiraz.fixture");
        android.graphics.drawable.InsetDrawable wrapper=(android.graphics.drawable.InsetDrawable)r.getDrawable(id,r.newTheme());
        android.graphics.drawable.GradientDrawable shape=(android.graphics.drawable.GradientDrawable)wrapper.getDrawable();
        if(!wrapper.canApplyTheme()||shape.getCornerRadii()[0]!=3||shape.getCornerRadii()[4]!=0)throw new AssertionError("Deferred corner defaults and explicit value");
        wrapper.applyTheme(r.newTheme());if(!wrapper.canApplyTheme())throw new AssertionError("Unresolved corners discarded");
        Resources.Theme bad=r.newTheme();bad.applyStyle(r.getIdentifier("InvalidRadius","style","org.shiraz.fixture"),true);
        try{wrapper.applyTheme(bad);throw new AssertionError("Non-dimension corner accepted");}catch(IllegalArgumentException expected){}
        if(!wrapper.canApplyTheme()||shape.getCornerRadii()[2]!=0)throw new AssertionError("Rejected theme mutated corners");
        Resources.Theme partial=r.newTheme();partial.applyStyle(r.getIdentifier("DeferredRadius","style","org.shiraz.fixture"),true);wrapper.applyTheme(partial);
        float[] corners=shape.getCornerRadii();if(!wrapper.canApplyTheme()||corners[0]!=3||corners[2]!=6||corners[4]!=6)throw new AssertionError("Partial theme resolves radius and preserves explicit corner");
        Resources.Theme rest=r.newTheme();rest.applyStyle(r.getIdentifier("DeferredCorner","style","org.shiraz.fixture"),true);wrapper.applyTheme(rest);
        corners=shape.getCornerRadii();if(wrapper.canApplyTheme()||corners[0]!=3||corners[2]!=6||corners[4]!=2||corners[6]!=6)throw new AssertionError("Deferred corner resolution lost prior values");
        Bitmap pixels=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888);wrapper.setBounds(0,0,20,20);wrapper.draw(new Canvas(pixels));if(pixels.getPixel(19,0)!=0||pixels.getPixel(10,10)!=Color.RED)throw new AssertionError("Resolved corners not rendered");pixels.recycle();
    }
    private static void blendModes(){
        Paint paint=new Paint();if(paint.getBlendMode()!=null)throw new AssertionError("Default blend metadata");
        for(PorterDuff.Mode mode:PorterDuff.Mode.values()){
            paint.setColor(0x80904020);paint.setXfermode(new PorterDuffXfermode(mode));BlendMode modern=paint.getBlendMode();Bitmap sample=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);sample.eraseColor(0x606020a0);new Canvas(sample).drawRect(0,0,1,1,paint);int expected=sample.getPixel(0,0);
            paint.setBlendMode(modern);sample.eraseColor(0x606020a0);new Canvas(sample).drawRect(0,0,1,1,paint);
            if(sample.getPixel(0,0)!=expected||new Paint(paint).getBlendMode()!=modern)throw new AssertionError("Legacy blend pixel round trip and copy");sample.recycle();
        }
        paint.setBlendMode(BlendMode.MODULATE);paint.setColor(0x80ff0000);Bitmap pixels=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);pixels.eraseColor(0x800000ff);new Canvas(pixels).drawRect(0,0,1,1,paint);
        if(pixels.getPixel(0,0)!=0x40000000)throw new AssertionError("Premultiplied modulation pixels");
        paint.setBlendMode(BlendMode.SRC_OVER);try{paint.setBlendMode(BlendMode.MULTIPLY);throw new AssertionError("Unsupported multiply accepted as modulation");}catch(UnsupportedOperationException expected){}if(paint.getBlendMode()!=BlendMode.SRC_OVER)throw new AssertionError("Failed blend changed state");
        paint.setBlendMode(null);if(paint.getBlendMode()!=null||paint.getXfermode()!=null)throw new AssertionError("Clear blend");pixels.eraseColor(Color.BLUE);new Canvas(pixels).drawRect(0,0,1,1,paint);if(pixels.getPixel(0,0)!=0xff80007f)throw new AssertionError("Cleared blend restores source-over");pixels.recycle();
    }
    public static void main(String[] args){blendModes();opticalInsets();
        Resources r=Resources.shirazOpen(args[0],96,160);
        try {
            deferredCorners(r);
            org.shiraz.fixture.LayerFixture.check(r);
            android.content.Context context=new android.content.ContextWrapper(null);
            FrameLayout parent=new FrameLayout(context),group=new FrameLayout(context);
            group.setBackgroundColor(Color.RED);View child=new View(context);child.setBackgroundColor(Color.BLUE);
            group.addView(child,new FrameLayout.LayoutParams(4,4));parent.addView(group,new FrameLayout.LayoutParams(8,8));
            int spec=View.MeasureSpec.makeMeasureSpec(8,View.MeasureSpec.EXACTLY);parent.measure(spec,spec);parent.layout(0,0,8,8);
            Bitmap pixels=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);Paint layer=new Paint();layer.setAlpha(128);
            try{
                for(int type:new int[]{View.LAYER_TYPE_SOFTWARE,View.LAYER_TYPE_HARDWARE}){
                    pixels.eraseColor(0);group.setLayerType(type,layer);parent.draw(new Canvas(pixels));
                    if(group.getLayerType()!=type||pixels.getPixel(1,1)!=0x800000ff||pixels.getPixel(6,6)!=0x80ff0000)throw new AssertionError("Whole subtree layer alpha");
                }
                group.setAlpha(.5f);pixels.eraseColor(0);parent.draw(new Canvas(pixels));if(pixels.getPixel(1,1)!=0x800000ff||layer.getAlpha()!=128)throw new AssertionError("View alpha overrides layer paint without mutation");group.setAlpha(1);
                Paint filtered=new Paint();filtered.setColorFilter(new PorterDuffColorFilter(Color.GREEN,PorterDuff.Mode.SRC_IN));group.setLayerPaint(filtered);pixels.eraseColor(0);parent.draw(new Canvas(pixels));
                if(pixels.getPixel(1,1)!=Color.GREEN||pixels.getPixel(6,6)!=Color.GREEN)throw new AssertionError("Layer color filter");
                group.setLayerType(View.LAYER_TYPE_NONE,layer);pixels.eraseColor(0);parent.draw(new Canvas(pixels));if(pixels.getPixel(1,1)!=Color.BLUE)throw new AssertionError("Layer disabled");
                pixels.eraseColor(Color.RED);Canvas canvas=new Canvas(pixels);Paint clear=new Paint();clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                int saved=canvas.saveLayer(new RectF(2,2,4,4),clear);clear.setXfermode(null);canvas.drawColor(Color.BLUE);canvas.restoreToCount(saved);
                if(pixels.getPixel(2,2)!=0||pixels.getPixel(0,0)!=Color.RED||pixels.getPixel(5,5)!=Color.RED)throw new AssertionError("Layer transfer snapshot and bounded restore");
                try{group.setLayerType(3,null);throw new AssertionError("Invalid layer accepted");}catch(IllegalArgumentException expected){}
            }finally{pixels.recycle();}

            Rect box=new Rect(10,20,30,40),out=new Rect();
            Gravity.apply(Gravity.CENTER,4,6,box,out);if(!out.equals(new Rect(18,27,22,33)))throw new AssertionError("center gravity");
            Gravity.apply(Gravity.FILL,4,6,box,out);if(!out.equals(box))throw new AssertionError("fill gravity");
            Gravity.apply(Gravity.BOTTOM|Gravity.RIGHT,4,6,box,out);if(!out.equals(new Rect(26,34,30,40)))throw new AssertionError("edge gravity");
            Gravity.apply(Gravity.CENTER|Gravity.CLIP_HORIZONTAL|Gravity.CLIP_VERTICAL,40,40,box,out);if(!out.equals(box))throw new AssertionError("clip gravity");
            System.out.println("LAYER_COMPOSITION_AND_XML_BOUNDS_OK");
        } finally {r.shirazClose();}
    }
}
