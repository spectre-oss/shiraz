// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
import java.util.Arrays;
public final class RoundedFixture {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static int[] image(Path path,Paint paint){Bitmap b=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888);try{new Canvas(b).drawPath(path,paint);int[] pixels=new int[1600];b.getPixels(pixels,0,40,0,0,40,40);return pixels;}finally{b.recycle();}}
    public static void check(){
        Paint p=new Paint();p.setColor(Color.RED);Path cw=new Path(),ccw=new Path();cw.addRoundRect(2,2,30,22,6,4,Path.Direction.CW);ccw.addRoundRect(new RectF(2,2,30,22),6,4,Path.Direction.CCW);
        int[] rounded=image(cw,p);check(Arrays.equals(rounded,image(ccw,p)),"rounded winding directions");check(rounded[2*40+2]==0&&rounded[2*40+16]==Color.RED&&rounded[10*40+16]==Color.RED&&rounded[22*40+16]==0,"rounded bounds and cutout corners");
        Path oval=new Path(),clamped=new Path();oval.addOval(new RectF(2,2,30,22),Path.Direction.CW);clamped.addRoundRect(2,2,30,22,100,100,Path.Direction.CW);Path scaled=new Path();scaled.addRoundRect(2,2,30,22,10,10,Path.Direction.CW);check(Arrays.equals(image(scaled,p),image(clamped,p)),"radii scale together preserving proportions");
        Path rect=new Path(),negative=new Path();rect.addRect(2,2,30,22,Path.Direction.CW);negative.addRoundRect(2,2,30,22,-4,5,Path.Direction.CW);check(negative.isEmpty(),"negative scalar radius ignored");
        Path circle=new Path(),opposite=new Path();circle.addCircle(16,16,10,Path.Direction.CW);opposite.addCircle(16,16,10,Path.Direction.CCW);check(Arrays.equals(image(circle,p),image(opposite,p)),"circle winding");
        Path hole=new Path(circle);hole.addPath(opposite);check(Arrays.equals(image(hole,p),new int[1600]),"opposed contours cancel winding");
        Path combined=new Path(circle);combined.addPath(combined);check(Arrays.equals(image(circle,p),image(combined,p)),"self append path");combined.setFillType(Path.FillType.EVEN_ODD);check(Arrays.equals(image(combined,p),new int[1600]),"self append even-odd cancellation");
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);int[] ring=image(circle,p);check(ring[16*40+16]==0&&ring[16*40+25]==Color.RED,"circle outline");p.setStyle(Paint.Style.FILL);p.setAntiAlias(true);boolean partial=false;for(int pixel:image(circle,p)){int alpha=pixel>>>24;if(alpha>0&&alpha<255)partial=true;}check(partial,"curved antialias coverage");
        Bitmap b=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888);try{Canvas c=new Canvas(b);c.drawRoundRect(new RectF(2,2,30,22),6,4,p);int[] actual=new int[1600];b.getPixels(actual,0,40,0,0,40,40);check(Arrays.equals(actual,image(cw,p)),"Canvas round rect uses path geometry");b.eraseColor(0);c.drawOval(2,2,30,22,p);b.getPixels(actual,0,40,0,0,40,40);check(Arrays.equals(actual,image(oval,p)),"Canvas oval geometry");b.eraseColor(0);c.drawCircle(16,16,10,p);b.getPixels(actual,0,40,0,0,40,40);check(Arrays.equals(actual,image(circle,p)),"Canvas circle geometry");
            b.eraseColor(0);c.scale(2,2);c.clipRect(0,0,10,20);c.drawRoundRect(2,2,15,15,3,3,p);check(b.getPixel(12,12)==Color.RED&&b.getPixel(22,12)==0,"scaled clipped rounded shape");
        }finally{b.recycle();}
        Path empty=new Path();empty.addCircle(2,2,0,Path.Direction.CW);empty.addOval(10,10,5,5,Path.Direction.CW);check(empty.isEmpty(),"empty curved shapes");
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY}){
            Path square=new Path();square.addRoundRect(2,2,30,22,bad,2,Path.Direction.CW);check(Arrays.equals(image(rect,p),image(square,p)),"nonfinite scalar radius becomes square");
        }
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}){
            Path square=new Path();square.addRoundRect(30,22,2,2,new float[]{6,4,6,4,bad,4,6,4},Path.Direction.CCW);check(Arrays.equals(image(rect,p),image(square,p)),"array nonfinite radius and sorted bounds");
        }
        Path preserved=new Path(cw);int[] before=image(preserved,p);preserved.addRoundRect(Float.NaN,1,20,20,2,2,Path.Direction.CW);check(Arrays.equals(before,image(preserved,p)),"invalid rectangle does not poison prior geometry");
        System.out.println("SHIRAZ_FIXTURE_ROUNDED_GEOMETRY_OK");
    }
}
