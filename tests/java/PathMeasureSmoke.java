// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
public final class PathMeasureSmoke {
    public static void main(String[] args){
        org.shiraz.fixture.PathMeasureFixture.check();
        Path p=new Path();p.moveTo(0,0);p.cubicTo(0,100,100,100,100,0);PathMeasure m=new PathMeasure(p,false);Path dst=new Path();m.getSegment(50,150,dst,true);if(dst.shirazCommandCount()!=10)throw new AssertionError("cubic extraction flattened geometry");
        p.reset();if(m.getLength()!=200)throw new AssertionError("measurement snapshot changed after source mutation");
        float[] pos={17,18},tan={19,20};if(m.getPosTan(Float.NaN,pos,tan)||pos[0]!=17||tan[0]!=19)throw new AssertionError("NaN sample changed output");
        if(m.getSegment(Float.NaN,100,dst,true))throw new AssertionError("NaN range accepted");
        Path large=new Path();large.moveTo(0,0);for(int n=0;n<3000;n++)large.cubicTo(0,100,100,100,100,0);
        try{m.setPath(large,false);throw new AssertionError("integration work budget was not enforced");}catch(IllegalArgumentException expected){}
        if(m.getLength()!=200)throw new AssertionError("failed setPath changed existing measurement");
        System.out.println("PATH_MEASURE_CURVES_AND_SNAPSHOT_OK");
    }
}
