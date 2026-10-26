// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;

public final class PathFixture {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    public static void check() {
        Bitmap target=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(target); Paint paint=new Paint(); paint.setColor(0x80ff0000);
        Path path=new Path(); path.addRect(1,1,7,7,Path.Direction.CW); target.eraseColor(Color.BLUE); canvas.drawPath(path,paint);
        check(target.getPixel(1,1) == 0xff80007f && target.getPixel(7,1) == Color.BLUE,"path fill and source-over bounds");
        path.addRect(3,3,5,5,Path.Direction.CW); paint.setColor(Color.RED); target.eraseColor(0); canvas.drawPath(path,paint); check(target.getPixel(3,3) == Color.RED,"nonzero winding overlap");
        path.setFillType(Path.FillType.EVEN_ODD); target.eraseColor(0); canvas.drawPath(path,paint); check(target.getPixel(3,3) == 0 && target.getPixel(2,2) == Color.RED,"even-odd hole");
        path.toggleInverseFillType(); target.eraseColor(0); canvas.drawPath(path,paint); check(target.getPixel(0,0) == Color.RED && target.getPixel(2,2) == 0 && target.getPixel(3,3) == Color.RED,"inverse fill coverage");
        Path copy=new Path(path); path.reset(); check(path.isEmpty() && path.getFillType() == Path.FillType.INVERSE_EVEN_ODD,"reset preserves fill type");
        copy.setFillType(Path.FillType.EVEN_ODD); Path translated=new Path(); copy.offset(1,1,translated); target.eraseColor(0); canvas.drawPath(translated,paint);
        check(target.getPixel(1,1) == 0 && target.getPixel(2,2) == Color.RED && target.getPixel(4,4) == 0,"independent translated path");
        path.rewind(); path.setFillType(Path.FillType.WINDING); path.addRect(1,1,7,7,Path.Direction.CW); path.addRect(3,3,5,5,Path.Direction.CCW); target.eraseColor(0); canvas.drawPath(path,paint); check(target.getPixel(3,3) == 0,"opposite winding cuts hole");
        path.reset(); path.moveTo(2,4); path.lineTo(6,4); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2); target.eraseColor(0); canvas.drawPath(path,paint);
        check(target.getPixel(2,3) == Color.RED && target.getPixel(1,3) == 0,"butt stroke cap");
        paint.setStrokeCap(Paint.Cap.ROUND); target.eraseColor(0); canvas.drawPath(path,paint); check(target.getPixel(1,3) == Color.RED,"round stroke cap");
        path.reset(); path.moveTo(2,6); path.lineTo(6,6); path.lineTo(6,2); paint.setStrokeCap(Paint.Cap.BUTT); paint.setStrokeWidth(4); target.eraseColor(0); canvas.drawPath(path,paint);
        check(target.getPixel(7,7) == Color.RED,"miter join extension"); paint.setStrokeJoin(Paint.Join.BEVEL); target.eraseColor(0); canvas.drawPath(path,paint); check(target.getPixel(7,7) == 0,"bevel join cut");
        paint.setStrokeJoin(Paint.Join.MITER); paint.setStrokeMiter(1); target.eraseColor(0); canvas.drawPath(path,paint); check(target.getPixel(7,7) == 0,"miter limit fallback");
        paint.reset(); paint.setColor(Color.RED); path.reset(); path.moveTo(1,7); path.quadTo(4,1,7,7); target.eraseColor(0); canvas.drawPath(path,paint);
        check(target.getPixel(4,5) == Color.RED && target.getPixel(4,2) == 0,"quadratic coverage");
        path.reset(); path.moveTo(1,7); path.cubicTo(1,1,7,1,7,7); target.eraseColor(0); canvas.drawPath(path,paint); check(target.getPixel(4,3) == Color.RED && target.getPixel(4,1) == 0,"cubic coverage");
        path.reset(); path.addRect(0,0,2,2,Path.Direction.CW); int saved=canvas.save(); canvas.translate(2,1); canvas.scale(2,3); canvas.clipRect(0,0,1,1); target.eraseColor(0); canvas.drawPath(path,paint); canvas.restoreToCount(saved);
        check(target.getPixel(2,1) == Color.RED && target.getPixel(3,3) == Color.RED && target.getPixel(4,3) == 0,"transformed path and fixed device clip");
        paint.setColorFilter(new PorterDuffColorFilter(Color.GREEN,PorterDuff.Mode.SRC_IN)); target.eraseColor(0); canvas.drawPath(path,paint); check(target.getPixel(0,0) == Color.GREEN,"path color filter");
        paint.reset(); paint.setColor(Color.RED); paint.setAntiAlias(true); path.reset(); path.moveTo(.25f,.25f); path.lineTo(7.25f,1.25f); path.lineTo(1.25f,7.25f); path.close(); target.eraseColor(0); canvas.drawPath(path,paint);
        int fractional=0; for (int y=0;y<10;y++) for (int x=0;x<10;x++) { int alpha=target.getPixel(x,y) >>> 24; if (alpha > 0 && alpha < 255) fractional++; } check(fractional > 0,"antialiased path edges");
        paint.setStyle(Paint.Style.FILL_AND_STROKE); paint.setStrokeWidth(2); paint.setColor(0x80ff0000); path.reset(); path.addRect(2,2,7,7,Path.Direction.CW); target.eraseColor(0); canvas.drawPath(path,paint);
        for (int y=0;y<10;y++) for (int x=0;x<10;x++) check((target.getPixel(x,y) >>> 24) <= 128,"fill and stroke apply paint alpha once");
        check(target.getPixel(4,4) == 0x80ff0000,"fill-stroke interior");
        int before=target.getPixel(0,0); path.lineTo(Float.NaN,0); canvas.drawPath(path,paint); check(target.getPixel(0,0) == before,"nonfinite path does not touch pixels");
        paint.reset();paint.setColor(Color.RED);float[] corners={4,4,0,0,2,2,0,0};Path rounded=new Path();rounded.addRoundRect(new RectF(0,0,10,10),corners,Path.Direction.CW);target.eraseColor(0);canvas.drawPath(rounded,paint);check(target.getPixel(0,0)==0&&target.getPixel(9,0)==Color.RED&&target.getPixel(0,9)==Color.RED&&target.getPixel(5,5)==Color.RED,"Independent corner radii");
        rounded.addRoundRect(0,0,10,10,corners,Path.Direction.CCW);target.eraseColor(0);canvas.drawPath(rounded,paint);for(int y=0;y<10;y++)for(int x=0;x<10;x++)check(target.getPixel(x,y)==0,"Opposite rounded winding cancels");
        rounded.reset();rounded.addRoundRect(0,0,10,10,new float[]{20,20,20,20,20,20,20,20},Path.Direction.CW);Path fitted=new Path();fitted.addRoundRect(0,0,10,10,5,5,Path.Direction.CW);Bitmap expected=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888);new Canvas(expected).drawPath(fitted,paint);target.eraseColor(0);canvas.drawPath(rounded,paint);for(int y=0;y<10;y++)for(int x=0;x<10;x++)check(target.getPixel(x,y)==expected.getPixel(x,y),"Overlapping corner radii scale proportionally");expected.recycle();
        try{rounded.addRoundRect(0,0,10,10,new float[7],Path.Direction.CW);throw new AssertionError("Short radii accepted");}catch(ArrayIndexOutOfBoundsException expectedError){}check(!rounded.isEmpty(),"Rejected radii preserve existing path");
        System.out.println("SHIRAZ_FIXTURE_PATHS_OK");
    }
}
