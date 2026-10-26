// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.text.TextPaint;
import android.graphics.*;
import java.util.Arrays;
public final class TextPaintFixture {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    public static void check() {
        TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);
        check(text.density==1 && text.bgColor==0 && text.baselineShift==0 && text.drawableState==null,"TextPaint defaults");
        text.setTextSize(32); text.setTypeface(Typeface.MONOSPACE); text.setColor(Color.RED); text.setStrokeWidth(2); text.setTextAlign(Paint.Align.CENTER);
        text.bgColor=Color.BLUE; text.baselineShift=-3; text.linkColor=Color.GREEN; text.drawableState=new int[]{android.R.attr.state_pressed}; text.density=2; text.underlineColor=Color.RED; text.underlineThickness=1.5f;
        TextPaint copy=new TextPaint(); copy.set(text);
        check(copy.bgColor==text.bgColor && copy.baselineShift==-3 && copy.linkColor==Color.GREEN && copy.drawableState==text.drawableState && copy.density==2 && copy.underlineColor==Color.RED && copy.underlineThickness==1.5f,"TextPaint metadata copy");
        check(copy.getTypeface()==Typeface.MONOSPACE && copy.getTextSize()==32 && copy.getColor()==Color.RED && copy.getStrokeWidth()==2 && copy.getTextAlign()==Paint.Align.CENTER && copy.isAntiAlias(),"inherited paint copy");
        check(copy.measureText("Shiraz")==text.measureText("Shiraz") && copy.measureText("Shiraz")>0,"real font measurement");
        Bitmap first=Bitmap.createBitmap(160,64,Bitmap.Config.ARGB_8888),second=Bitmap.createBitmap(160,64,Bitmap.Config.ARGB_8888);
        try {
            new Canvas(first).drawText("Shiraz",80,40,text); new Canvas(second).drawText("Shiraz",80,40,copy);
            int[] a=new int[160*64],b=new int[a.length]; first.getPixels(a,0,160,0,0,160,64); second.getPixels(b,0,160,0,0,160,64);
            check(Arrays.equals(a,b),"copied text draws identically"); boolean visible=false; for(int pixel:a)if(pixel!=0)visible=true;check(visible,"text rasterizes");
        } finally {first.recycle();second.recycle();}
        TextPaint fromPaint=new TextPaint((Paint)text); check(fromPaint.getColor()==Color.RED && fromPaint.bgColor==0 && fromPaint.density==1 && fromPaint.drawableState==null,"Paint constructor copies Paint fields only");
        copy.setColor(Color.BLUE); check(text.getColor()==Color.RED,"independent inherited properties");
        System.out.println("SHIRAZ_FIXTURE_TEXT_PAINT_OK");
    }
}
