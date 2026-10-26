// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
public final class RegionFixture {
 private static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
 private static boolean expected(boolean a,boolean b,Region.Op op){switch(op){case DIFFERENCE:return a&&!b;case INTERSECT:return a&&b;case UNION:return a||b;case XOR:return a!=b;case REVERSE_DIFFERENCE:return b&&!a;default:return b;}}
 public static void check(){
  java.util.Random random=new java.util.Random(9182);for(int trial=0;trial<30;trial++){Region a=new Region(),b=new Region();boolean[][] aa=new boolean[20][20],bb=new boolean[20][20];for(int side=0;side<2;side++)for(int i=0;i<8;i++){int l=random.nextInt(15),t=random.nextInt(15),r=l+random.nextInt(5)+1,bot=t+random.nextInt(5)+1;(side==0?a:b).union(new Rect(l,t,r,bot));for(int y=t;y<bot;y++)for(int x=l;x<r;x++)(side==0?aa:bb)[y][x]=true;}
   for(Region.Op op:Region.Op.values()){Region out=new Region(a);out.op(out,b,op);for(int y=0;y<20;y++)for(int x=0;x<20;x++)check(out.contains(x,y)==expected(aa[y][x],bb[y][x],op),"Boolean region "+op);RegionIterator iterator=new RegionIterator(out);out.setEmpty();Rect rect=new Rect();boolean[][] visited=new boolean[20][20];while(iterator.next(rect))for(int y=rect.top;y<rect.bottom;y++)for(int x=rect.left;x<rect.right;x++){check(!visited[y][x],"Iterator spans disjoint");visited[y][x]=true;}for(int y=0;y<20;y++)for(int x=0;x<20;x++)check(visited[y][x]==expected(aa[y][x],bb[y][x],op),"Iterator snapshot coverage");}
  }
  Region ring=new Region(0,0,20,20);ring.op(new Rect(5,5,15,15),Region.Op.DIFFERENCE);check(ring.isComplex()&&ring.contains(1,1)&&!ring.contains(10,10),"Hole");Region copy=new Region(ring);ring.translate(-10,20);check(ring.contains(-9,21)&&!ring.contains(0,30)&&copy.contains(1,1),"Translation/copy");Region huge=new Region(Integer.MAX_VALUE-1,0,Integer.MAX_VALUE,1);try{huge.translate(1,0);throw new AssertionError("Overflow accepted");}catch(ArithmeticException expected){}check(huge.contains(Integer.MAX_VALUE-1,0),"Overflow atomic");
  Path circle=new Path();circle.addCircle(10,10,8,Path.Direction.CW);Region shape=new Region();check(shape.setPath(circle,new Region(0,0,20,20))&&shape.contains(10,10)&&!shape.contains(1,1),"Path rasterization");circle.setFillType(Path.FillType.INVERSE_WINDING);shape.setPath(circle,new Region(0,0,20,20));check(!shape.contains(10,10)&&shape.contains(1,1)&&!shape.contains(21,1),"Inverse path clipped");
  // The rectangular fast path must agree with a complex clip, including holes,
  // translated coordinates and destination aliasing the clip.
  for(Path.FillType fill:Path.FillType.values()){
   Path path=new Path();path.addCircle(-2,7,8,Path.Direction.CW);path.setFillType(fill);
   Region bounds=new Region(-15,-6,13,22),complex=new Region(bounds);
   complex.op(new Rect(-5,3,2,12),Region.Op.DIFFERENCE);
   Region full=new Region();full.setPath(path,bounds);
   Region clipped=new Region();clipped.setPath(path,complex);
   Region alias=new Region(complex);alias.setPath(path,alias);
   for(int y=-7;y<23;y++)for(int x=-16;x<14;x++){
    boolean wanted=full.contains(x,y)&&complex.contains(x,y);
    check(clipped.contains(x,y)==wanted,"Complex path clip "+fill);
    check(alias.contains(x,y)==wanted,"Aliased path clip "+fill);
   }
  }
  Region rect=new Region(2,3,8,9);Path boundary=rect.getBoundaryPath();Region restored=new Region();restored.setPath(boundary,new Region(0,0,20,20));check(restored.equals(rect),"Rect boundary round trip");check(rect.quickContains(3,4,6,7)&&rect.quickReject(8,3,10,9)&&!rect.contains(8,5),"Half-open edges");
  android.util.FloatProperty<float[]> property=new android.util.FloatProperty<float[]>("value"){public Float get(float[] target){return target[0];}public void setValue(float[] target,float value){target[0]=value;}};float[] target={0};property.set(target,Float.valueOf(2.5f));check(target[0]==2.5f&&property.getType()==Float.class,"Boxed float property bridge");property.setValue(target,3.5f);check(property.get(target)==3.5f,"Primitive float property bridge");
  System.out.println("REGION_GEOMETRY_OK");
 }
}
