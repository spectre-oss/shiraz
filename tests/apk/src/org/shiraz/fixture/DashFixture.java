// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
import java.util.Arrays;
public final class DashFixture {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static int[] image(Path path,Paint paint){Bitmap b=Bitmap.createBitmap(40,20,Bitmap.Config.ARGB_8888);try{new Canvas(b).drawPath(path,paint);int[] pixels=new int[800];b.getPixels(pixels,0,40,0,0,40,20);return pixels;}finally{b.recycle();}}
    public static void check(){
        Paint p=new Paint();p.setColor(Color.RED);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);
        Path path=new Path();path.moveTo(1,5);path.lineTo(33,5);path.moveTo(1,10);path.lineTo(33,10);
        float[] values={4,4};DashPathEffect dash=new DashPathEffect(values,0);values[0]=99;check(p.setPathEffect(dash)==dash&&p.getPathEffect()==dash,"paint effect identity");
        int[] pixels=image(path,p);check(pixels[5*40+2]==Color.RED&&pixels[5*40+6]==0&&pixels[5*40+10]==Color.RED&&pixels[10*40+2]==Color.RED,"dash on/off and contour reset");
        p.setPathEffect(new DashPathEffect(new float[]{4,Float.MAX_VALUE},0));pixels=image(path,p);check(pixels[5*40+2]==Color.RED&&pixels[5*40+6]==0&&pixels[5*40+30]==0&&pixels[10*40+2]==Color.RED,"large finite gap and contour reset");
        p.setPathEffect(new DashPathEffect(new float[]{4,4},2));pixels=image(path,p);check(pixels[5*40+2]==Color.RED&&pixels[5*40+4]==0&&pixels[5*40+8]==Color.RED,"dash phase");
        p.setPathEffect(new DashPathEffect(new float[]{4,4},-2));int[] negative=image(path,p);p.setPathEffect(new DashPathEffect(new float[]{4,4},6));check(Arrays.equals(negative,image(path,p)),"negative phase normalization");
        Paint copy=new Paint(p);check(copy.getPathEffect()==p.getPathEffect()&&Arrays.equals(image(path,p),image(path,copy)),"copied effect draws identically");copy.reset();check(copy.getPathEffect()==null,"reset clears effect");
        PathEffect previous=p.getPathEffect();try{p.setPathEffect(new PathEffect());throw new AssertionError("unknown effect accepted");}catch(UnsupportedOperationException expected){}check(p.getPathEffect()==previous,"failed effect assignment preserves paint");
        p.setPathEffect(dash);Bitmap b=Bitmap.createBitmap(80,40,Bitmap.Config.ARGB_8888);try{Canvas c=new Canvas(b);c.scale(2,2);c.clipRect(0,0,12,20);c.drawPath(path,p);check(b.getPixel(5,10)==Color.RED&&b.getPixel(13,10)==0&&b.getPixel(21,10)==Color.RED&&b.getPixel(25,10)==0,"scaled dash coordinates and clip");}finally{b.recycle();}
        b=Bitmap.createBitmap(40,20,Bitmap.Config.ARGB_8888);try{Canvas c=new Canvas(b);c.drawRect(1,5,33,15,p);check(b.getPixel(2,5)==Color.RED&&b.getPixel(6,5)==0,"rect stroke uses dash renderer");}finally{b.recycle();}
        Path box=new Path();box.addRect(1,1,20,15,Path.Direction.CW);p.setStyle(Paint.Style.FILL);int[] filled=image(box,p);p.setPathEffect(null);check(Arrays.equals(filled,image(box,p)),"fill ignores dash effect");
        for(float[] invalid:new float[][]{{},{1},{1,2,3},{0,0},{-1,2},{Float.NaN,2},{Float.POSITIVE_INFINITY,2}}){try{new DashPathEffect(invalid,0);throw new AssertionError("invalid interval accepted");}catch(IllegalArgumentException expected){}}
        try{new DashPathEffect(new float[]{1,2},Float.NaN);throw new AssertionError("invalid phase accepted");}catch(IllegalArgumentException expected){}
        p.setStyle(Paint.Style.STROKE);p.setPathEffect(new DashPathEffect(new float[]{0,4},0));p.setStrokeCap(Paint.Cap.ROUND);check(!Arrays.equals(image(path,p),new int[800]),"zero on interval produces round-cap dots");
        System.out.println("SHIRAZ_FIXTURE_DASH_EFFECT_OK");
    }
}
