// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.Gravity;
public final class LayerFixture {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static int id(Resources r,String name,String type){int id=r.getIdentifier(name,type,"org.shiraz.fixture");check(id!=0,"layer resource "+name);return id;}
    private static final class Owner implements Drawable.Callback {
        int invalidations,schedules,cancellations; Drawable source;
        public void invalidateDrawable(Drawable d){invalidations++;source=d;}
        public void scheduleDrawable(Drawable d,Runnable task,long when){schedules++;source=d;}
        public void unscheduleDrawable(Drawable d,Runnable task){cancellations++;source=d;}
    }
    private static final class Padded extends ColorDrawable {
        Padded(){super(Color.BLUE);}
        public boolean getPadding(Rect r){r.set(1,2,3,4);return true;}
    }
    public static void check(Resources r){
        Drawable nestedColor=r.getDrawable(id(r,"color_nested","drawable"));nestedColor.setBounds(0,0,12,12);Bitmap colorPixels=Bitmap.createBitmap(12,12,Bitmap.Config.ARGB_8888);try{nestedColor.draw(new Canvas(colorPixels));check(colorPixels.getPixel(0,0)==0xffff0000&&colorPixels.getPixel(8,8)==0xff7f8000,"Nested color XML alpha composition");}finally{colorPixels.recycle();}

        Drawable d=r.getDrawable(id(r,"layer_fixture","drawable"));check(d instanceof LayerDrawable,"layer-list type");LayerDrawable xml=(LayerDrawable)d;
        check(xml.getNumberOfLayers()==3 && xml.findDrawableByLayerId(id(r,"layer_icon","id")) instanceof BitmapDrawable,"layer IDs");
        check(xml.findIndexByLayerId(id(r,"layer_front","id"))==2 && xml.findDrawableByLayerId(123)==null,"layer lookup");
        Bitmap image=Bitmap.createBitmap(20,12,Bitmap.Config.ARGB_8888);xml.setBounds(0,0,20,12);xml.draw(new Canvas(image));
        check(image.getPixel(0,0)==Color.RED && image.getPixel(10,10)==0xff7f8000 && image.getPixel(9,11)==Color.BLUE,"layer z-order and bottom bitmap gravity");
        image.eraseColor(0);xml.setBounds(0,0,12,20);Bitmap portrait=Bitmap.createBitmap(12,20,Bitmap.Config.ARGB_8888);xml.draw(new Canvas(portrait));check(portrait.getPixel(6,18)==0xff7f8000 && portrait.getPixel(5,19)==Color.BLUE,"resized layer bitmap placement");image.recycle();portrait.recycle();
        ColorDrawable back=new ColorDrawable(Color.RED),front=new ColorDrawable(Color.BLUE);LayerDrawable layers=new LayerDrawable(new Drawable[]{back,front});layers.setLayerSize(1,4,2);layers.setLayerGravity(1,Gravity.BOTTOM|Gravity.END);layers.setBounds(0,0,10,8);check(front.getBounds().equals(new Rect(6,6,10,8)),"bottom end bounds");layers.setLayoutDirection(1);check(front.getBounds().equals(new Rect(0,6,4,8)),"RTL layer gravity");layers.setLayerInsetRelative(1,1,0,2,1);check(front.getBounds().equals(new Rect(2,5,6,7)),"RTL relative insets");
        Owner owner=new Owner();layers.setCallback(owner);front.setColor(Color.GREEN);check(owner.invalidations>0 && owner.source==layers,"child invalidation reaches owner");Runnable task=new Runnable(){public void run(){}};front.scheduleSelf(task,123);front.unscheduleSelf(task);check(owner.schedules==1&&owner.cancellations==1&&owner.source==layers,"child schedule propagation");
        layers.setAlpha(128);check(back.getAlpha()==128&&front.getAlpha()==128&&layers.getAlpha()==128,"layer alpha propagation");layers.setColorFilter(new PorterDuffColorFilter(Color.RED,PorterDuff.Mode.SRC_IN));check(back.getColorFilter()!=null&&front.getColorFilter()!=null,"layer filter propagation");layers.setVisible(false,false);check(!back.isVisible()&&!front.isVisible(),"layer visibility propagation");
        GradientDrawable shape=new GradientDrawable();shape.setColor(Color.WHITE);
        LayerDrawable tinted=new LayerDrawable(new Drawable[]{null,new LayerDrawable(new Drawable[]{shape})});tinted.setBounds(0,0,4,4);
        tinted.setTintList(new android.content.res.ColorStateList(new int[][]{{16842919},{}},new int[]{Color.RED,Color.GREEN}));
        check(tinted.isStateful(),"nested layer tint is stateful");Bitmap tintPixels=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888);Canvas tintCanvas=new Canvas(tintPixels);
        tinted.draw(tintCanvas);check(tintPixels.getPixel(2,2)==Color.GREEN,"layer tint default");
        tinted.setState(new int[]{16842919});tinted.draw(tintCanvas);check(tintPixels.getPixel(2,2)==Color.RED,"layer tint pressed");
        tinted.setColorFilter(new PorterDuffColorFilter(Color.BLUE,PorterDuff.Mode.SRC_IN));tinted.draw(tintCanvas);check(tintPixels.getPixel(2,2)==Color.BLUE,"layer explicit filter precedence");
        tinted.setColorFilter(null);tinted.draw(tintCanvas);check(tintPixels.getPixel(2,2)==Color.RED,"layer tint restored");
        tinted.setTintMode(null);tinted.draw(tintCanvas);check(tintPixels.getPixel(2,2)==Color.WHITE,"null mode disables nested tint");
        tinted.setTintMode(PorterDuff.Mode.SRC_IN);tinted.setTintList(null);tinted.draw(tintCanvas);check(tintPixels.getPixel(2,2)==Color.WHITE&&!tinted.isStateful(),"clear layer tint");tintPixels.recycle();
        LayerDrawable padded=new LayerDrawable(new Drawable[]{new Padded(),new Padded()});padded.setBounds(0,0,20,20);check(padded.getDrawable(1).getBounds().equals(new Rect(1,2,17,16)),"nested child padding");Rect padding=new Rect();padded.getPadding(padding);check(padding.equals(new Rect(2,4,6,8)),"nested combined padding");padded.setPaddingMode(LayerDrawable.PADDING_MODE_STACK);padded.getPadding(padding);check(padding.equals(new Rect(1,2,3,4))&&padded.getDrawable(1).getBounds().equals(new Rect(0,0,20,20)),"stack padding");
        try{r.getDrawable(id(r,"layer_cycle","drawable"));throw new AssertionError("resource cycle accepted");}catch(Resources.NotFoundException expected){}
        try{r.getDrawable(id(r,"layer_fanout","drawable"));throw new AssertionError("resource fanout budget accepted");}catch(IllegalArgumentException expected){}
        check(r.getDrawable(id(r,"layer_fixture","drawable")) instanceof LayerDrawable,"failed inflation releases recursion guard and budgets");
        System.out.println("SHIRAZ_FIXTURE_LAYERS_OK");
    }
}
