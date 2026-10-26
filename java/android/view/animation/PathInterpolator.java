// SPDX-License-Identifier: GPL-3.0-only
package android.view.animation;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Path;
import android.util.AttributeSet;
/** Bounded, immutable lookup of y at the input x along a monotonic path. */
public class PathInterpolator extends BaseInterpolator {
 private final double[] points;
 public PathInterpolator(Path path){
  if(path==null)throw new IllegalArgumentException("Null interpolation path");
  points=path.shirazAnimationPoints(.002);
  int last=points.length-3;
  if(points[1]!=0||points[2]!=0||points[last+1]!=1||points[last+2]!=1)throw new IllegalArgumentException("Path must start at (0,0) and end at (1,1)");
  for(int i=3;i<points.length;i+=3){
   if(points[i+1]<points[i-2])throw new IllegalArgumentException("Path loops back in x");
   if(points[i]==points[i-3]&&points[i+1]!=points[i-2])throw new IllegalArgumentException("Path has a gap in x");
  }
 }
 public PathInterpolator(float x,float y){this(curve(x,y,0,0,false));}
 public PathInterpolator(float x1,float y1,float x2,float y2){this(curve(x1,y1,x2,y2,true));}
 public PathInterpolator(Context context,AttributeSet attrs){this(context.getResources(),context.getTheme(),attrs);}
 public PathInterpolator(Resources resources,Resources.Theme theme,AttributeSet attrs){this(xml(resources,theme,attrs));}
 private static Path curve(float x1,float y1,float x2,float y2,boolean cubic){Path path=new Path();path.moveTo(0,0);if(cubic)path.cubicTo(x1,y1,x2,y2,1,1);else path.quadTo(x1,y1,1,1);return path;}
 private static Path xml(Resources resources,Resources.Theme theme,AttributeSet attrs){
  int[] ids={0x010103fc,0x010103fd,0x010103fe,0x010103ff,0x01010405};
  TypedArray values=theme==null?resources.obtainAttributes(attrs,ids):theme.obtainStyledAttributes(attrs,ids,0,0);
  try{
   if(values.hasValue(4))return shiraz.runtime.PathData.parse(values.getString(4));
   if(!values.hasValue(0)||!values.hasValue(1))throw new android.view.InflateException("PathInterpolator requires controlX1 and controlY1");
   if(values.hasValue(2)!=values.hasValue(3))throw new android.view.InflateException("PathInterpolator requires both second controls");
   return curve(values.getFloat(0,0),values.getFloat(1,0),values.getFloat(2,0),values.getFloat(3,0),values.hasValue(2));
  }finally{values.recycle();}
 }
 public float getInterpolation(float input){
  if(input<=0)return 0;if(input>=1)return 1;if(Float.isNaN(input))return Float.NaN;
  int low=0,high=points.length/3-1;
  while(high-low>1){int mid=(low+high)/2;if(points[mid*3+1]>input)high=mid;else low=mid;}
  double width=points[high*3+1]-points[low*3+1];
  return (float)(width==0?points[low*3+2]:points[low*3+2]+(input-points[low*3+1])/width*(points[high*3+2]-points[low*3+2]));
 }
}
