// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
public final class PathLastPointSmoke {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  Path path=new Path();path.setLastPoint(10,20);path.lineTo(30,20);path.setLastPoint(40,20);PathMeasure measure=new PathMeasure(path,false);check(Math.abs(measure.getLength()-30)<.01,"line endpoint replaced");
  path.rewind();path.moveTo(0,0);path.quadTo(10,20,20,0);path.setLastPoint(30,10);float[] end=new float[2];measure.setPath(path,false);measure.getPosTan(measure.getLength(),end,null);check(Math.abs(end[0]-30)<.01&&Math.abs(end[1]-10)<.01,"quadratic endpoint reencoded");
  path.rewind();path.moveTo(0,0);path.lineTo(10,0);path.lineTo(10,10);path.close();path.setLastPoint(20,10);measure.setPath(path,false);check(measure.isClosed()&&measure.getLength()>40,"closed contour retained");
  System.out.println("PATH_LAST_POINT_OK");
 }
}
