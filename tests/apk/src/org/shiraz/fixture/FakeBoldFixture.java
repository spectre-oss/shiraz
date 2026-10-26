// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
import java.util.Arrays;
public final class FakeBoldFixture {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static int[] draw(Paint p){Bitmap b=Bitmap.createBitmap(180,64,Bitmap.Config.ARGB_8888);try{new Canvas(b).drawText("office Ag",8,48,p);int[] pixels=new int[180*64];b.getPixels(pixels,0,180,0,0,180,64);return pixels;}finally{b.recycle();}}
    private static long coverage(int[] image){long result=0;for(int pixel:image)result+=pixel>>>24;return result;}
    public static void check(){
        for(boolean aa:new boolean[]{false,true}){
            Paint p=new Paint(aa?Paint.ANTI_ALIAS_FLAG:0);p.setTextSize(32);p.setTypeface(Typeface.MONOSPACE);p.setColor(0x8040ff20);float advance=p.measureText("office Ag");
            int[] plain=draw(p);p.setDither(true);check(p.isDither()&&(p.getFlags()&Paint.DITHER_FLAG)!=0,"dither flag");check(Arrays.equals(plain,draw(p)),"8888 output retains exact channels with dither hint");check(new Paint(p).isDither(),"copy dither hint");p.setDither(false);p.setFakeBoldText(true);check(p.isFakeBoldText()&&(p.getFlags()&Paint.FAKE_BOLD_TEXT_FLAG)!=0,"bold flag");int[] bold=draw(p);
            check(!Arrays.equals(plain,bold)&&coverage(bold)>coverage(plain),"synthetic bold changes glyph coverage");for(int pixel:bold)check((pixel>>>24)<=128,"synthetic bold alpha");
            check(p.getTypeface()==Typeface.MONOSPACE && p.measureText("office Ag")==advance,"preserved typeface and shaping advances");
            Paint copy=new Paint(p);check(copy.isFakeBoldText()&&Arrays.equals(draw(copy),bold),"copied bold rendering");p.setFakeBoldText(false);check(Arrays.equals(draw(p),plain),"bold toggle restores rendering");
            copy.setDither(true);copy.reset();check(!copy.isDither()&&!copy.isFakeBoldText(),"reset clears fake bold");p.setFlags(Paint.FAKE_BOLD_TEXT_FLAG);check(p.isFakeBoldText()&&!p.isAntiAlias(),"setFlags bold");p.setFlags(0);check(!p.isFakeBoldText(),"setFlags clears bold");
            p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));int[] realBold=draw(p);p.setFakeBoldText(true);check(Arrays.equals(realBold,draw(p)),"already bold face is not emboldened again");
        }
        System.out.println("SHIRAZ_FIXTURE_FAKE_BOLD_OK");
    }
}
