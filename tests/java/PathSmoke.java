// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
import shiraz.runtime.PathData;
import shiraz.runtime.NativePath;
public final class PathSmoke {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    private static RectF sampleBounds(Path path){RectF out=new RectF(Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY);double[] points=path.shirazAnimationPoints();for(int i=0;i<points.length;i+=3){out.left=Math.min(out.left,(float)points[i+1]);out.right=Math.max(out.right,(float)points[i+1]);out.top=Math.min(out.top,(float)points[i+2]);out.bottom=Math.max(out.bottom,(float)points[i+2]);}return out;}
    private static Bitmap draw(String source) { Bitmap bitmap=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888); Paint paint=new Paint(); paint.setColor(Color.RED); new Canvas(bitmap).drawPath(PathData.parse(source),paint); return bitmap; }
    private static void shaders(){
        Bitmap texture=Bitmap.createBitmap(2,1,Bitmap.Config.ARGB_8888);texture.setPixel(0,0,Color.RED);texture.setPixel(1,0,Color.BLUE);
        BitmapShader shader=new BitmapShader(texture,Shader.TileMode.REPEAT,Shader.TileMode.CLAMP);Paint p=new Paint();check(p.setShader(shader)==shader,"shader setter result");
        Bitmap out=Bitmap.createBitmap(8,4,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);c.drawRect(0,0,8,4,p);
        for(int x=0;x<8;x++)check(out.getPixel(x,1)==(x%2==0?Color.RED:Color.BLUE),"repeating texture");
        Matrix local=new Matrix();local.setTranslate(1,0);shader.setLocalMatrix(local);local.reset();Matrix copy=new Matrix();check(shader.getLocalMatrix(copy),"local matrix copied");
        out.eraseColor(0);c.drawRect(0,0,8,4,p);check(out.getPixel(0,0)==Color.BLUE&&out.getPixel(1,0)==Color.RED,"negative coordinates wrap");
        shader.setLocalMatrix(null);Matrix unchanged=new Matrix(copy);check(!shader.getLocalMatrix(copy)&&copy.equals(unchanged),"identity query leaves output unchanged");c.save();c.translate(2,0);c.scale(2,1);out.eraseColor(0);c.drawRect(0,0,3,4,p);c.restore();check(out.getPixel(2,1)==Color.RED&&out.getPixel(3,1)==Color.RED&&out.getPixel(4,1)==Color.BLUE,"canvas transform texture");
        p.setShader(new BitmapShader(texture,Shader.TileMode.MIRROR,Shader.TileMode.CLAMP));out.eraseColor(0);c.drawRect(0,0,8,4,p);check(out.getPixel(2,1)==Color.BLUE&&out.getPixel(3,1)==Color.RED,"mirror tile");
        p.setShader(new BitmapShader(texture,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP));out.eraseColor(0);c.drawRect(0,0,8,4,p);check(out.getPixel(7,1)==Color.BLUE,"clamped tile");
        p.setShader(shader);p.setAlpha(128);out.eraseColor(0);c.drawRect(0,0,8,4,p);check(out.getPixel(0,1)==0x80ff0000,"shader paint opacity");
        p.setAlpha(255);p.setFilterBitmap(true);local.setTranslate(.5f,0);shader.setLocalMatrix(local);out.eraseColor(0);c.drawRect(0,0,8,4,p);check(out.getPixel(1,1)==0xff800080,"filtered texture");
        texture.setPixel(1,0,0x000000ff);out.eraseColor(0);c.drawRect(0,0,8,4,p);check(out.getPixel(1,1)==0x80ff0000,"filtering uses premultiplied alpha");texture.setPixel(1,0,Color.BLUE);
        p.setFilterBitmap(false);shader.setLocalMatrix(null);out.eraseColor(0);Path triangle=new Path();triangle.moveTo(0,0);triangle.lineTo(8,0);triangle.lineTo(0,4);triangle.close();c.drawPath(triangle,p);check(out.getPixel(1,1)==Color.BLUE&&out.getPixel(7,3)==0,"path coverage masks shader");
        c.save();c.clipRect(2,1,4,3);out.eraseColor(0);c.drawRect(0,0,8,4,p);c.restore();check(out.getPixel(1,1)==0&&out.getPixel(2,1)==Color.RED&&out.getPixel(4,1)==0,"shader clipping");
        local.setScale(0,1);shader.setLocalMatrix(local);try{c.drawRect(0,0,8,4,p);throw new AssertionError("singular shader accepted");}catch(IllegalArgumentException expected){}check(out.getPixel(2,1)==Color.RED,"invalid shader preserves target");shader.setLocalMatrix(null);
        Bitmap rows=Bitmap.createBitmap(1,2,Bitmap.Config.ARGB_8888);rows.setPixel(0,0,Color.RED);rows.setPixel(0,1,Color.BLUE);p.setShader(new BitmapShader(rows,Shader.TileMode.CLAMP,Shader.TileMode.MIRROR));out.eraseColor(0);c.drawRect(0,0,8,4,p);check(out.getPixel(7,2)==Color.BLUE&&out.getPixel(7,3)==Color.RED,"independent vertical mirror tiling");rows.recycle();p.setShader(shader);
        Paint duplicate=new Paint(p);p.reset();check(p.getShader()==null&&duplicate.getShader()==shader,"paint copy/reset shader state");
        texture.recycle();try{c.drawRect(0,0,8,4,duplicate);throw new AssertionError("recycled shader bitmap accepted");}catch(IllegalStateException expected){}
        out.recycle();
    }
    private static void gradients(){
        Bitmap out=Bitmap.createBitmap(10,4,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(out);Paint paint=new Paint();
        LinearGradient gradient=new LinearGradient(.5f,0,4.5f,0,Color.RED,Color.BLUE,Shader.TileMode.CLAMP);paint.setShader(gradient);canvas.drawRect(0,0,10,4,paint);
        check(out.getPixel(0,0)==Color.RED&&out.getPixel(2,0)==0xff800080&&out.getPixel(8,0)==Color.BLUE,"Linear gradient endpoints/midpoint/clamp");
        paint.setShader(new LinearGradient(.5f,0,4.5f,0,Color.RED,Color.BLUE,Shader.TileMode.REPEAT));out.eraseColor(0);canvas.drawRect(0,0,10,4,paint);check(out.getPixel(4,0)==Color.RED&&out.getPixel(6,0)==0xff800080,"Gradient repeat");
        paint.setShader(new LinearGradient(.5f,0,4.5f,0,Color.RED,Color.BLUE,Shader.TileMode.MIRROR));out.eraseColor(0);canvas.drawRect(0,0,10,4,paint);check(out.getPixel(4,0)==Color.BLUE&&out.getPixel(6,0)==0xff800080&&out.getPixel(8,0)==Color.RED,"Gradient mirror");
        int[] colors={Color.RED,Color.RED,Color.BLUE,Color.BLUE};float[] stops={0,.5f,.5f,1};paint.setShader(new LinearGradient(.5f,0,4.5f,0,colors,stops,Shader.TileMode.CLAMP));colors[2]=Color.GREEN;stops[2]=1;out.eraseColor(0);canvas.drawRect(0,0,10,4,paint);check(out.getPixel(1,0)==Color.RED&&out.getPixel(2,0)==Color.BLUE,"Repeated stops form hard edge and inputs copied");
        paint.setShader(new LinearGradient(.5f,0,4.5f,0,0x00ff0000,Color.BLUE,Shader.TileMode.CLAMP));out.eraseColor(0);canvas.drawRect(0,0,10,4,paint);check(out.getPixel(2,0)==0x80800080,"Gradient alpha and straight sRGB interpolation");
        Matrix local=new Matrix();local.setTranslate(2,0);gradient.setLocalMatrix(local);paint.setShader(gradient);out.eraseColor(0);canvas.drawRect(0,0,10,4,paint);check(out.getPixel(2,0)==Color.RED&&out.getPixel(4,0)==0xff800080,"Gradient local transform");gradient.setLocalMatrix(null);
        int save=canvas.save();canvas.translate(2,0);canvas.scale(2,1);canvas.clipRect(0,1,3,3);out.eraseColor(0);canvas.drawRect(0,0,4,4,paint);canvas.restoreToCount(save);check(out.getPixel(2,1)==Color.RED&&out.getPixel(1,1)==0&&out.getPixel(2,0)==0&&out.getPixel(8,1)==0,"Gradient canvas transform and clip");
        paint.setShader(new RadialGradient(.5f,.5f,4,Color.RED,Color.BLUE,Shader.TileMode.CLAMP));out.eraseColor(0);canvas.drawRect(0,0,10,4,paint);
        check(out.getPixel(0,0)==Color.RED&&out.getPixel(2,0)==0xff800080&&out.getPixel(8,0)==Color.BLUE,"Radial center midpoint clamp");
        paint.setShader(new RadialGradient(.5f,.5f,4,Color.RED,Color.BLUE,Shader.TileMode.REPEAT));canvas.drawRect(0,0,10,4,paint);check(out.getPixel(4,0)==Color.RED,"Radial repeat");
        try{new RadialGradient(0,0,0,Color.RED,Color.BLUE,Shader.TileMode.CLAMP);throw new AssertionError("Zero radius accepted");}catch(IllegalArgumentException expected){}
        try{new LinearGradient(0,0,1,0,new int[]{0,1},new float[]{.8f,.2f},Shader.TileMode.CLAMP);throw new AssertionError("Unordered stops accepted");}catch(IllegalArgumentException expected){}
        int[] pixel={0xff123456};float[] identity={1,0,0,1,0,0};try{NativePath.drawGradient(pixel,1,1,new float[0],0,-1,0,0,0,0,4,false,identity,0,0,1,1,null,0,new float[]{0,0,1,0},new int[]{0},new float[]{0},0,identity);throw new AssertionError("Native malformed gradient accepted");}catch(IllegalArgumentException expected){}check(pixel[0]==0xff123456,"Rejected native gradient preserves pixels");out.recycle();
    }
    private static void arcs(){
        RectF oval=new RectF(10,10,30,30);Path arc=new Path();arc.arcTo(oval,0,90);double[] points=arc.shirazAnimationPoints();check(Math.abs(points[1]-30)<.001&&Math.abs(points[2]-20)<.001,"empty arc starts at ellipse point");int end=points.length-3;check(Math.abs(points[end+1]-20)<.001&&Math.abs(points[end+2]-30)<.001&&Math.abs(points[end]-Math.PI*5)<.1,"clockwise quarter length/endpoint");
        Path joined=new Path();joined.moveTo(0,0);joined.arcTo(oval,0,-90);points=joined.shirazAnimationPoints();end=points.length-3;check(Math.abs(points[end+1]-20)<.001&&Math.abs(points[end+2]-10)<.001&&Math.abs(points[end]-Math.hypot(30,20)-Math.PI*5)<.1,"negative sweep with joining line");
        Path moved=new Path();moved.moveTo(0,0);moved.arcTo(oval,0,90,true);points=moved.shirazAnimationPoints();check(Math.abs(points[points.length-3]-Math.PI*5)<.1,"forceMoveTo excludes connecting segment");
        int before=moved.shirazCommandCount();try{moved.arcTo(oval,0,Float.NaN);throw new AssertionError("nonfinite arc accepted");}catch(IllegalArgumentException expected){}check(before==moved.shirazCommandCount(),"invalid arc preserves path");moved.arcTo(new RectF(30,10,10,30),0,90);check(before==moved.shirazCommandCount(),"inverted oval ignored");
        Path modulo=new Path();modulo.arcTo(oval,720,450);check(java.util.Arrays.equals(arc.shirazAnimationPoints(),modulo.shirazAnimationPoints()),"angles normalized modulo360");
    }
    private static void nonfinitePaths(){
        Bitmap b=Bitmap.createBitmap(12,12,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);Paint paint=new Paint();paint.setColor(Color.RED);
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}){
            Path path=new Path();path.addRect(1,1,10,10,Path.Direction.CW);path.moveTo(2,bad);path.lineTo(8,8);
            for(Path.FillType fill:Path.FillType.values()){
                path.setFillType(fill);b.eraseColor(Color.BLUE);canvas.drawPath(path,paint);
                for(int y=0;y<12;y++)for(int x=0;x<12;x++)check(b.getPixel(x,y)==Color.BLUE,"nonfinite path must skip entire draw");
            }
            Path copy=new Path(path);path.reset();path.setFillType(Path.FillType.WINDING);path.addRect(1,1,10,10,Path.Direction.CW);canvas.drawPath(path,paint);check(b.getPixel(5,5)==Color.RED,"reset recovers finite geometry");
            b.eraseColor(Color.BLUE);copy.offset(1,2);Matrix m=new Matrix();m.setScale(2,2);copy.transform(m);canvas.drawPath(copy,paint);check(b.getPixel(5,5)==Color.BLUE,"copy and transforms preserve nonfinite geometry");
            RectF bounds=new RectF(1,2,3,4);copy.computeBounds(bounds,false);check(bounds.isEmpty(),"nonfinite bounds empty");
            copy.rewind();copy.addRect(1,1,10,10,Path.Direction.CW);copy.setFillType(Path.FillType.WINDING);canvas.drawPath(copy,paint);check(b.getPixel(5,5)==Color.RED,"rewind recovers finite geometry");
        }
        b.recycle();
    }
    private static void pathOperations(){
        Path large=new Path();large.addRoundRect(0,0,720,24000,24,24,Path.Direction.CW);
        Path probe=new Path();probe.addRect(350.125f,18000.25f,350.375f,18000.5f,Path.Direction.CW);
        Path result=new Path();long begin=System.nanoTime();
        for(int i=0;i<100;i++)check(result.op(large,probe,Path.Op.INTERSECT),"large fractional hit-test");
        System.out.println("PATH_HIT_TEST_NS="+(System.nanoTime()-begin)/100);
        RectF bounds=new RectF();result.computeBounds(bounds,true);
        check(bounds.left==350.125f&&bounds.top==18000.25f&&bounds.right==350.375f&&bounds.bottom==18000.5f,"subpixel intersection preserved");
        check(result.op(result,result,Path.Op.DIFFERENCE)&&result.isEmpty(),"aliased operands");
        Path circle=new Path();circle.addCircle(16,16,12,Path.Direction.CW);
        Path box=new Path();box.addRect(16,0,32,32,Path.Direction.CW);
        for(Path.Op op:Path.Op.values()){
            check(result.op(circle,box,op),"curve operation "+op);
            Bitmap b=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);Paint paint=new Paint();paint.setColor(Color.RED);new Canvas(b).drawPath(result,paint);
            boolean left=op==Path.Op.DIFFERENCE||op==Path.Op.UNION||op==Path.Op.XOR;
            boolean right=op==Path.Op.INTERSECT||op==Path.Op.UNION;
            check((b.getPixel(10,16)!=0)==left&&(b.getPixel(22,16)!=0)==right,"curve boolean coverage "+op);b.recycle();
        }
        result.set(probe);Path invalid=new Path();invalid.moveTo(Float.NaN,0);
        check(!result.op(invalid,box,Path.Op.UNION),"invalid operation rejected");result.computeBounds(bounds,true);check(bounds.left==350.125f,"failed operation preserves result");
    }
    public static void main(String[] args) {
        Bitmap arcs=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);Canvas arcCanvas=new Canvas(arcs);Paint arcPaint=new Paint();arcPaint.setColor(Color.RED);
        arcCanvas.drawArc(new RectF(4,4,28,28),0,90,true,arcPaint);check(arcs.getPixel(18,18)==Color.RED&&arcs.getPixel(10,10)==0,"Arc wedge quadrant");
        arcs.eraseColor(0);arcCanvas.drawArc(4,4,28,28,0,90,false,arcPaint);check(arcs.getPixel(18,18)==0&&arcs.getPixel(23,23)==Color.RED,"Arc chord excludes center");
        int generation=arcs.getGenerationId();arcs.getPixel(0,0);check(arcs.getGenerationId()==generation,"Bitmap reads preserve generation");arcs.setPixel(0,0,Color.BLUE);check(arcs.getGenerationId()!=generation,"Bitmap write changes generation");generation=arcs.getGenerationId();
        arcs.eraseColor(0);check(arcs.getGenerationId()!=generation,"Bitmap clear changes generation");generation=arcs.getGenerationId();arcCanvas.drawArc(4,4,28,28,45,360,false,arcPaint);check(arcs.getGenerationId()!=generation,"Canvas writes change generation");check(arcs.getPixel(16,16)==Color.RED&&arcs.getPixel(1,1)==0,"Full sweep draws oval");arcs.recycle();
        pathOperations();
        nonfinitePaths();
        arcs();
        Path control=new Path();RectF controlBounds=new RectF(1,2,3,4);control.computeBounds(controlBounds,false);check(controlBounds.equals(new RectF()),"empty path bounds");control.moveTo(7,9);control.computeBounds(controlBounds,true);check(controlBounds.equals(new RectF()),"single point bounds");control.cubicTo(-10,40,50,-20,20,10);control.computeBounds(controlBounds,false);check(controlBounds.equals(new RectF(-10,-20,50,40)),"control-point bounds include cubic handles");RectF exactBounds=new RectF();control.computeBounds(exactBounds,true);check(exactBounds.equals(controlBounds),"legacy exact flag ignored");

        Path originalPath=new Path();originalPath.addRect(1,2,3,4,Path.Direction.CW);originalPath.setFillType(Path.FillType.EVEN_ODD);Path transformed=new Path();Matrix placement=new Matrix();placement.setScale(2,3);placement.postTranslate(4,1);originalPath.transform(placement,transformed);RectF bounds=new RectF();bounds=sampleBounds(transformed);check(bounds.left==6&&bounds.top==7&&bounds.right==10&&bounds.bottom==13&&transformed.getFillType()==Path.FillType.EVEN_ODD,"transformed bounds/fill");bounds=sampleBounds(originalPath);check(bounds.left==1&&bounds.top==2,"separate destination preserves originalPath");transformed.rLineTo(1,1);bounds=sampleBounds(transformed);check(bounds.right==10&&bounds.bottom==13,"closed contour cursor transformed");Matrix bad=new Matrix();bad.setScale(Float.POSITIVE_INFINITY,1);try{originalPath.transform(bad,transformed);throw new AssertionError("nonfinite transform accepted");}catch(IllegalArgumentException expected){}bounds=sampleBounds(transformed);check(bounds.left==6&&bounds.bottom==13,"invalid transform preserves destination");placement.setTranslate(2,3);originalPath.transform(placement);bounds=sampleBounds(originalPath);check(bounds.left==3&&bounds.top==5,"in-place transform");

        org.shiraz.fixture.PathFixture.check();
        shaders();gradients();
        Bitmap absolute=draw("M2 2 L10 2 L10 10 L2 10 Z"),relative=draw("m2,2h8v8h-8z");
        for (int y=0;y<24;y++) for (int x=0;x<24;x++) check(absolute.getPixel(x,y) == relative.getPixel(x,y),"relative and absolute SVG geometry");
        Bitmap implicit=draw("M2e0 2 1e1 2 10 10 2 10z"); check(implicit.getPixel(3,3) == Color.RED && implicit.getPixel(1,1) == 0,"implicit line pairs and exponents");
        Bitmap circle=draw("M4,12 A8 8 0 0112 4 A8 8 0 0120 12 A8 8 0 0112 20 A8 8 0 014 12z");
        check(circle.getPixel(12,12) == Color.RED && circle.getPixel(4,4) == 0 && circle.getPixel(12,5) == Color.RED,"elliptical arcs and packed flags");
        Bitmap corrected=draw("M4 12 A1 1 0 0 1 20 12 A1 1 0 0 1 4 12Z"); check(corrected.getPixel(12,12) == Color.RED,"arc radii corrected to span endpoints");
        Bitmap upper=draw("M4 12A8 8 0 0 1 20 12Z"),lower=draw("M4 12A8 8 0 0 0 20 12Z");
        check(upper.getPixel(12,8) == Color.RED && upper.getPixel(12,16) == 0 && lower.getPixel(12,8) == 0 && lower.getPixel(12,16) == Color.RED,"arc sweep selects opposite halves");
        Bitmap major=draw("M4 12A8 8 0 1 1 12 4Z"),minor=draw("M4 12A8 8 0 0 1 12 4Z"); check(major.getPixel(2,2) == Color.RED && minor.getPixel(2,2) == 0,"large-arc center selection");
        Bitmap rotated=draw("M6.343146 6.343146A8 4 45 0 1 17.656854 17.656854A8 4 45 0 1 6.343146 6.343146Z");
        check(rotated.getPixel(15,15) == Color.RED && rotated.getPixel(8,16) == 0,"rotated elliptical axes");
        Bitmap smooth=draw("M2 12 Q7 0 12 12 T22 12 L22 22H2Z"),explicit=draw("M2 12Q7 0 12 12Q17 24 22 12L22 22H2Z");
        for (int y=0;y<24;y++) for (int x=0;x<24;x++) check(smooth.getPixel(x,y) == explicit.getPixel(x,y),"smooth quadratic reflection");
        Bitmap cs=draw("M2 12C2 0 10 0 12 12S22 24 22 12L22 22H2Z"),ce=draw("M2 12C2 0 10 0 12 12C14 24 22 24 22 12L22 22H2Z");
        for (int y=0;y<24;y++) for (int x=0;x<24;x++) check(cs.getPixel(x,y) == ce.getPixel(x,y),"smooth cubic reflection");
        if(new PathMeasure(PathData.parse("L3 4"),false).getLength()!=5)throw new AssertionError("Implicit path origin");
        for (String malformed:new String[]{"M0","M0,,0","M0 0,","M0 0Z1 1","M0 0A1 1 0 2 0 1 1","M0 0Q1e 1 2 2","M0 0X1 1","M0 0L1e99 0"}) {
            try { PathData.parse(malformed); throw new AssertionError("malformed SVG accepted: "+malformed); } catch (IllegalArgumentException expected) { }
        }
        int[] pixel={0xff123456}; float[] identity={1,0,0,1,0,0};
        try { NativePath.draw(pixel,1,1,new float[]{3,1},0,0xffff0000,0,0,0,0,4,false,identity,0,0,1,1); throw new AssertionError("truncated native path accepted"); } catch (IllegalArgumentException expected) { }
        try { NativePath.draw(pixel,1,1,new float[]{1,Float.NaN,0},0,0xffff0000,0,0,0,0,4,false,identity,0,0,1,1); throw new AssertionError("nonfinite native geometry accepted"); } catch (IllegalArgumentException expected) { }
        check(pixel[0] == 0xff123456,"native geometry failure preserves target");
        java.util.Random random=new java.util.Random(53);
        for (int n=0;n<500;n++) {
            float[] malformed=new float[1+random.nextInt(40)]; for (int i=0;i<malformed.length;i++) malformed[i]=random.nextInt(10)-3;
            pixel[0]=0xff123456;
            try { NativePath.draw(pixel,1,1,malformed,0,0xffff0000,0,0,0,0,4,false,identity,0,0,1,1); }
            catch (IllegalArgumentException expected) { check(pixel[0] == 0xff123456,"invalid native stream leaves target unchanged"); }
        }
        Bitmap affine=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888); Paint paint=new Paint(); paint.setColor(Color.RED); Path rect=new Path(); rect.addRect(0,0,2,2,Path.Direction.CW);
        new Canvas(affine).shirazDrawPath(rect,paint,new float[]{0,1,-1,0,5,2}); check(affine.getPixel(3,2) == Color.RED && affine.getPixel(5,2) == 0,"additional affine path transform");
        Bitmap turn=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888);Canvas turnCanvas=new Canvas(turn);
        int saved=turnCanvas.save();turnCanvas.translate(2,3);turnCanvas.rotate(90,8,8);
        turnCanvas.drawRect(8,8,12,10,paint);
        check(turn.getPixel(9,12)==Color.RED && turn.getPixel(11,12)==0,"rotation around pivot after translation");
        turnCanvas.clipRect(0,0,4,4);turnCanvas.drawRect(-20,-20,40,40,paint);check(turn.getPixel(15,4)==Color.RED&&turn.getPixel(13,4)==0,"rotated clip coverage");
        turnCanvas.restoreToCount(saved);paint.setColor(Color.BLUE);turnCanvas.drawRect(0,0,2,2,paint);
        check(turn.getPixel(0,0)==Color.BLUE,"restore clears rotation");
        try{turnCanvas.rotate(Float.NaN);throw new AssertionError("nonfinite rotation");}catch(IllegalArgumentException expected){}
        turnCanvas.rotate(360);turnCanvas.drawRect(2,0,4,2,paint);check(turn.getPixel(2,0)==Color.BLUE,"full turn identity");
        Bitmap clipped=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888);Canvas clipCanvas=new Canvas(clipped);
        Path hole=new Path();hole.addCircle(12,12,5,Path.Direction.CW);int clipSave=clipCanvas.save();
        check(clipCanvas.clipPath(hole,Region.Op.DIFFERENCE),"difference clip nonempty");clipCanvas.drawColor(Color.RED);
        check(clipped.getPixel(12,12)==0 && clipped.getPixel(1,1)==Color.RED,"difference clips color fill");
        check(!clipCanvas.clipRect(10,10,14,14),"rectangle entirely inside excluded hole is empty");clipCanvas.drawColor(Color.BLUE);
        check(clipped.getPixel(12,12)==0,"rect intersection preserves path hole");clipCanvas.restoreToCount(clipSave);
        clipCanvas.drawRect(11,11,13,13,paint);check(clipped.getPixel(12,12)==Color.BLUE,"restore removes path clip");
        clipCanvas=new Canvas(clipped);clipCanvas.translate(4,0);Path box=new Path();box.addRect(0,0,2,2,Path.Direction.CW);
        check(clipCanvas.clipPath(box),"transformed clip nonempty");clipCanvas.translate(10,0);clipCanvas.drawColor(Color.GREEN);
        check(clipped.getPixel(4,0)==Color.GREEN && clipped.getPixel(14,0)==Color.RED,"clip remains in device space");
        for(Region.Op op:Region.Op.values()){
            Bitmap result=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);Canvas cc=new Canvas(result);
            cc.clipRect(0,0,4,8);Path side=new Path();side.addRect(2,0,6,8,Path.Direction.CW);cc.clipPath(side,op);
            Bitmap source=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);source.eraseColor(Color.GREEN);cc.drawBitmap(source,0,0,null);
            for(int xx=0;xx<8;xx++){boolean aa=xx<4,bb=xx>=2&&xx<6;
                boolean expected=op==Region.Op.DIFFERENCE?aa&&!bb:op==Region.Op.INTERSECT?aa&&bb:op==Region.Op.UNION?aa||bb:op==Region.Op.XOR?aa!=bb:op==Region.Op.REVERSE_DIFFERENCE?bb&&!aa:bb;
                check(result.getPixel(xx,3)==(expected?Color.GREEN:0),"bitmap region operation "+op);
            }
        }
        // Clip edges must retain the same fractional coverage as antialiased fills.
        Bitmap clipAA=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888),referenceAA=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888);
        Path round=new Path();round.addRoundRect(new RectF(3.25f,4.5f,36.75f,35.25f),9,9,Path.Direction.CW);
        Paint solidAA=new Paint( Paint.ANTI_ALIAS_FLAG);solidAA.setColor(Color.BLUE);new Canvas(referenceAA).drawPath(round,solidAA);
        Canvas rounded=new Canvas(clipAA);rounded.clipPath(round);rounded.clipPath(round);rounded.drawColor(Color.BLUE);
        int fractional=0;for(int yy=0;yy<40;yy++)for(int xx=0;xx<40;xx++){int got=clipAA.getPixel(xx,yy),expected=referenceAA.getPixel(xx,yy);check(Math.abs((got>>>24)-(expected>>>24))<=1,"antialiased repeated path clip");if((got>>>24)>0&&(got>>>24)<255)fractional++;}check(fractional>30,"rounded corners contain fractional coverage");
        clipAA.eraseColor(0);rounded=new Canvas(clipAA);rounded.clipPath(round);rounded.clipRect(12,12,20,20);rounded.clipPath(round);rounded.drawColor(Color.BLUE);
        check(clipAA.getPixel(15,15)==Color.BLUE&&clipAA.getPixel(25,20)==0,"repeated path must not widen an intervening rectangle clip");
        clipAA.eraseColor(0);rounded=new Canvas(clipAA);rounded.clipPath(round);int layer=rounded.saveLayerAlpha(null,128);rounded.drawColor(Color.BLUE);rounded.restoreToCount(layer);
        for(int yy=0;yy<40;yy++)for(int xx=0;xx<40;xx++)check(Math.abs((clipAA.getPixel(xx,yy)>>>24)-((referenceAA.getPixel(xx,yy)>>>24)*128+127)/255)<=1,"layer clip applied once");
        clipAA.eraseColor(Color.RED);rounded=new Canvas(clipAA);rounded.clipPath(round);Paint clear=new Paint();clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));rounded.drawRect(0,0,40,40,clear);
        for(int yy=0;yy<40;yy++)for(int xx=0;xx<40;xx++)check(Math.abs((clipAA.getPixel(xx,yy)>>>24)-(255-(referenceAA.getPixel(xx,yy)>>>24)))<=1,"clear respects fractional clip coverage");
        Bitmap[] lettering=new Bitmap[3];Paint letteringPaint=new Paint();letteringPaint.setTextSize(64);letteringPaint.setColor(0x80ffffff);
        letteringPaint.setTypeface(Typeface.create("monospace",Typeface.BOLD));letteringPaint.setStrokeWidth(2);
        float advance=letteringPaint.measureText("O");
        for(int mode=0;mode<3;mode++){
            lettering[mode]=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);letteringPaint.setStyle(Paint.Style.values()[mode]);
            new Canvas(lettering[mode]).drawText("O",16,72,letteringPaint);
            check(letteringPaint.measureText("O")==advance,"stroke preserves text advance");
        }
        int hollow=0,expanded=0;
        for(int yy=0;yy<96;yy++)for(int xx=0;xx<96;xx++){
            boolean fill=lettering[0].getPixel(xx,yy)!=0,stroke=lettering[1].getPixel(xx,yy)!=0,both=lettering[2].getPixel(xx,yy)!=0;
            if(fill&&!stroke)hollow++;if(stroke&&!fill)expanded++;
            check(!fill || both,"fill-and-stroke retains glyph interior");
            check((lettering[2].getPixel(xx,yy)>>>24)<=128,"fill-and-stroke blends once");
        }
        check(hollow>0 && expanded>0,"stroke outlines differ from filled glyphs");
        Bitmap guard=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);guard.eraseColor(Color.GREEN);letteringPaint.setStrokeWidth(513);
        try{new Canvas(guard).drawText("O",16,72,letteringPaint);throw new AssertionError("oversized text stroke accepted");}catch(IllegalArgumentException expected){}
        check(guard.getPixel(30,40)==Color.GREEN,"stroke rejection preserves target");
        Path large=new Path(); large.addRect(0,0,50,50,Path.Direction.CW); Paint smallPaint=new Paint(Paint.ANTI_ALIAS_FLAG); smallPaint.setColor(0xff123456);
        Bitmap screen=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888); Canvas screenCanvas=new Canvas(screen); long[] times=new long[5];
        for (int n=-2;n<5;n++) { long start=System.nanoTime(); for (int i=0;i<100;i++) screenCanvas.drawPath(large,smallPaint); if (n >= 0) times[n]=(System.nanoTime()-start)/100; }
        check(screen.getPixel(49,49) == 0xff123456 && screen.getPixel(50,50) == 0,"small icon renderer limits dirty region");
        java.util.Arrays.sort(times); System.out.println("PATH_50X50_IN_720X1280_MEDIAN_NS="+times[2]);
        System.out.println("SVG_PATH_GRAMMAR_AND_NATIVE_BOUNDS_OK");
    }
}
