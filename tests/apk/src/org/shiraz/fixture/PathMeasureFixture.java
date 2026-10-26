// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
public final class PathMeasureFixture {
    private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    private static void near(float a,float b,float tolerance,String m){check(Math.abs(a-b)<=tolerance,m+": "+a+" expected "+b);}
    public static void check(){
        Path path=new Path();path.moveTo(0,0);path.lineTo(3,4);path.lineTo(6,4);PathMeasure m=new PathMeasure(path,false);near(m.getLength(),8,0,"polyline length");check(!m.isClosed(),"open contour");float[] pos={99,99},tan={99,99};check(m.getPosTan(2.5f,pos,tan),"polyline sample");near(pos[0],1.5f,.00001f,"line x");near(pos[1],2,.00001f,"line y");near(tan[0],.6f,.00001f,"normalized tangent x");near(tan[1],.8f,.00001f,"normalized tangent y");
        m.getPosTan(-100,pos,tan);near(pos[0],0,0,"negative distance clamp");m.getPosTan(Float.POSITIVE_INFINITY,pos,tan);near(pos[0],6,0,"end clamp");check(m.getPosTan(4,null,null),"optional sample outputs");
        PathMeasure closed=new PathMeasure(path,true);near(closed.getLength(),8+(float)Math.sqrt(52),.00001f,"implicit closure length");check(closed.isClosed(),"forced closure");
        path.close();path.moveTo(20,0);path.lineTo(20,10);m.setPath(path,false);check(m.isClosed(),"explicit closure");check(m.nextContour(),"next contour");near(m.getLength(),10,0,"second contour length");m.getPosTan(5,pos,tan);near(pos[0],20,0,"second contour x");near(pos[1],5,0,"second contour y");check(!m.nextContour(),"contour exhaustion");
        Path cubic=new Path();cubic.moveTo(0,0);cubic.cubicTo(0,100,100,100,100,0);m.setPath(cubic,false);near(m.getLength(),200,.001f,"analytic cubic length");m.getPosTan(100,pos,tan);near(pos[0],50,.001f,"cubic midpoint x");near(pos[1],75,.001f,"cubic midpoint y");near(tan[0],1,.00001f,"cubic tangent x");near(tan[1],0,.00001f,"cubic tangent y");
        Path trimmed=new Path();check(m.getSegment(50,150,trimmed,true),"cubic segment extraction");PathMeasure part=new PathMeasure(trimmed,false);near(part.getLength(),100,.002f,"trimmed arc length");float[] expected=new float[2];for(float d:new float[]{0,10,25,75,100}){m.getPosTan(d+50,expected,null);part.getPosTan(d,pos,null);near(pos[0],expected[0],.002f,"trimmed position x");near(pos[1],expected[1],.002f,"trimmed position y");}
        Path nonuniform=new Path();nonuniform.moveTo(0,0);nonuniform.cubicTo(0,0,0,0,100,0);m.setPath(nonuniform,false);m.getPosTan(25,pos,tan);near(pos[0],25,.0001f,"nonuniform parameter inversion");near(tan[0],1,0,"nonuniform tangent");m.getPosTan(0,pos,tan);near(tan[0],1,0,"degenerate endpoint tangent");
        Path circle=new Path();circle.addCircle(20,20,10,Path.Direction.CW);m.setPath(circle,false);near(m.getLength(),(float)(Math.PI*20),.002f,"circle length");m.getPosTan(m.getLength()/4,pos,tan);near(pos[0],20,.002f,"circle quarter x");near(pos[1],30,.002f,"circle quarter y");
        Path line=new Path();line.moveTo(0,0);line.lineTo(10,0);m.setPath(line,false);Path joined=new Path();joined.moveTo(-10,0);joined.lineTo(-5,0);check(m.getSegment(2,5,joined,false),"append connected extraction");near(new PathMeasure(joined,false).getLength(),15,0,"destination connection");
        Path separated=new Path();separated.moveTo(-10,0);separated.lineTo(-5,0);m.getSegment(2,5,separated,true);PathMeasure separate=new PathMeasure(separated,false);near(separate.getLength(),5,0,"existing contour preserved");check(separate.nextContour(),"extracted contour appended");near(separate.getLength(),3,0,"new extracted contour length");
        float old=new PathMeasure(joined,false).getLength();check(!m.getSegment(6,2,joined,true),"inverted range rejected");near(new PathMeasure(joined,false).getLength(),old,0,"failed extraction preserves destination");check(m.getSegment(-100,100,new Path(),true),"pinned extraction");
        m.setPath(null,false);pos[0]=71;tan[0]=72;check(m.getLength()==0&&!m.getPosTan(0,pos,tan)&&pos[0]==71&&tan[0]==72,"empty measure leaves outputs");
        Path zero=new Path();zero.moveTo(1,1);zero.lineTo(1,1);m.setPath(zero,true);check(m.getLength()==0&&!m.getPosTan(0,pos,tan),"zero length contour");
        try{m.getPosTan(0,new float[1],null);throw new AssertionError("short position array accepted");}catch(ArrayIndexOutOfBoundsException expectedFailure){}
        System.out.println("SHIRAZ_FIXTURE_PATH_MEASURE_OK");
    }
}
