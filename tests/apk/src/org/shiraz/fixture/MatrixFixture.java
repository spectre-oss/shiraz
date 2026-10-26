// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
public final class MatrixFixture {
 private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 private static void near(float a,float b){check(Math.abs(a-b)<.001f,"Expected "+b+" got "+a);}
 private static void point(Matrix m,float x,float y,float ex,float ey){float[] p={x,y};m.mapPoints(p);near(p[0],ex);near(p[1],ey);}
 public static void check(){
  Matrix m=new Matrix();check(m.isIdentity()&&m.rectStaysRect(),"Identity");m.setTranslate(10,20);m.preScale(2,3);point(m,1,1,12,23);m.setTranslate(10,20);m.postScale(2,3);point(m,1,1,22,63);
  m.setRotate(90,5,6);point(m,6,6,5,7);check(m.rectStaysRect(),"Quarter rotation preserves rectangles");m.setRotate(45);check(!m.rectStaysRect(),"Diagonal rotation");
  m.setSkew(2,3,5,6);point(m,5,6,5,6);m.postTranslate(4,5);Matrix inverse=new Matrix();check(m.invert(inverse),"Affine invert");float[] p={1,2,8,9};m.mapPoints(p);inverse.mapPoints(p);near(p[0],1);near(p[1],2);near(p[2],8);near(p[3],9);
  m.setValues(new float[]{2,1,4,0,3,5,.01f,.02f,1});check(!m.isAffine()&&m.invert(inverse),"Perspective invert");point(m,10,20,44/1.5f,65/1.5f);p=new float[]{10,20};m.mapPoints(p);inverse.mapPoints(p);near(p[0],10);near(p[1],20);
  Matrix old=new Matrix(inverse);m.setScale(0,1);check(!m.invert(inverse)&&inverse.equals(old),"Singular inverse leaves output unchanged");
  m.setTranslate(9,10);p=new float[]{1,2,3,4,5,6};m.mapPoints(p,2,p,0,2);near(p[2],10);near(p[3],12);near(p[4],12);near(p[5],14);p=new float[]{2,3};m.mapVectors(p);near(p[0],2);near(p[1],3);
  m.setScale(2,8);near(m.mapRadius(3),12);RectF rect=new RectF(1,2,3,4);check(m.mapRect(rect),"Rect mapping");check(rect.equals(new RectF(2,16,6,32)),"Rect corners");
  check(m.setRectToRect(new RectF(0,0,10,20),new RectF(10,20,50,60),Matrix.ScaleToFit.CENTER),"Rect fit");point(m,0,0,20,20);point(m,10,20,40,60);
  Matrix copy=new Matrix(m);m.setConcat(m,m);point(m,0,0,60,60);check(!m.equals(copy),"Aliased concatenation");
  float[] values=new float[9];copy.getValues(values);values[0]=99;point(copy,0,0,20,20);try{m.mapPoints(new float[2],0,new float[2],0,Integer.MAX_VALUE);throw new AssertionError("Overflow range accepted");}catch(ArrayIndexOutOfBoundsException expected){}
  System.out.println("MATRIX_GEOMETRY_OK");
 }
}
