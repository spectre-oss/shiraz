// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable.shapes;
import android.graphics.*;
public class RoundRectShape extends RectShape {
 private float[] outer,inner;private RectF inset;private Path path=new Path();private boolean hole;
 private static float[] radii(float[] values){if(values==null)return null;if(values.length<8)throw new ArrayIndexOutOfBoundsException("Eight corner radii required");float[] copy=java.util.Arrays.copyOf(values,8);for(float value:copy)if(!Float.isFinite(value)||value<0||value>16384)throw new IllegalArgumentException("Corner radius bound");return copy;}
 public RoundRectShape(float[] outerRadii,RectF inset,float[] innerRadii){outer=radii(outerRadii);inner=radii(innerRadii);this.inset=inset==null?null:new RectF(inset);if(inset!=null)for(float v:new float[]{inset.left,inset.top,inset.right,inset.bottom})if(!Float.isFinite(v)||Math.abs(v)>16384)throw new IllegalArgumentException("Shape inset bound");}
 private void rounded(float l,float t,float r,float b,float[] radii){
  if(radii==null){path.addRect(l,t,r,b,Path.Direction.CW);return;}float[] q=radii.clone();float scale=1;float[] sums={q[0]+q[2],q[6]+q[4],q[1]+q[7],q[3]+q[5]};for(int i=0;i<4;i++)if(sums[i]>0)scale=Math.min(scale,(i<2?r-l:b-t)/sums[i]);for(int i=0;i<8;i++)q[i]*=scale;float k=.55228475f;
  path.moveTo(l+q[0],t);path.lineTo(r-q[2],t);path.cubicTo(r-q[2]+k*q[2],t,r,t+q[3]-k*q[3],r,t+q[3]);path.lineTo(r,b-q[5]);path.cubicTo(r,b-q[5]+k*q[5],r-q[4]+k*q[4],b,r-q[4],b);path.lineTo(l+q[6],b);path.cubicTo(l+q[6]-k*q[6],b,l,b-q[7]+k*q[7],l,b-q[7]);path.lineTo(l,t+q[1]);path.cubicTo(l,t+q[1]-k*q[1],l+q[0]-k*q[0],t,l+q[0],t);path.close();
 }
 protected void onResize(float w,float h){super.onResize(w,h);path.reset();path.setFillType(Path.FillType.EVEN_ODD);rounded(0,0,w,h,outer);hole=inset!=null&&w-inset.left-inset.right>0&&h-inset.top-inset.bottom>0&&inset.left+inset.right>0&&inset.top+inset.bottom>0;if(hole)rounded(inset.left,inset.top,w-inset.right,h-inset.bottom,inner);}
 public void draw(Canvas canvas,Paint paint){canvas.drawPath(path,paint);}
 public void getOutline(Outline outline){if(hole){outline.setEmpty();return;}float radius=outer==null?0:outer[0];if(outer!=null)for(float v:outer)if(v!=radius){outline.setEmpty();return;}outline.setRoundRect(0,0,(int)Math.ceil(getWidth()),(int)Math.ceil(getHeight()),radius);}
 public RoundRectShape clone() throws CloneNotSupportedException{RoundRectShape result=(RoundRectShape)super.clone();result.outer=outer==null?null:outer.clone();result.inner=inner==null?null:inner.clone();result.inset=inset==null?null:new RectF(inset);result.path=new Path(path);return result;}
}
