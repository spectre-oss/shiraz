// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.Resources;
import android.graphics.*;
import android.view.View;
import android.widget.FrameLayout;
import android.view.Gravity;
public final class LayerSmoke {
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
    public static void main(String[] args){blendModes();
        Resources r=Resources.shirazOpen(args[0],96,160);
        try {
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
