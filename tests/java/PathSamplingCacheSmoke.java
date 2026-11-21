// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.Path;
public final class PathSamplingCacheSmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static Path curve(){Path p=new Path();p.moveTo(0,0);p.cubicTo(.4f,0,.2f,1,1,1);return p;}
 public static void main(String[] args)throws Exception{
  Path path=curve();double[] expected=path.shirazAnimationPoints(.002);
  double[] first=curve().shirazAnimationPoints(.002);check(java.util.Arrays.equals(first,expected),"equal geometry");
  first[1]=99;check(java.util.Arrays.equals(curve().shirazAnimationPoints(.002),expected),"caller cannot mutate cached samples");
  path.lineTo(2,3);double[] changed=path.shirazAnimationPoints(.002);check(changed[changed.length-2]==2&&changed[changed.length-1]==3,"mutation changes geometry key");
  check(java.util.Arrays.equals(curve().shirazAnimationPoints(.002),expected),"path mutation does not affect equal path");
  check(curve().shirazAnimationPoints(.02).length<expected.length,"sampling precision in key");
  Path reverse=new Path();reverse.moveTo(0,0);reverse.lineTo(.8f,.2f);reverse.lineTo(.3f,.8f);reverse.lineTo(1,1);
  for(int i=0;i<2;i++)try{new android.view.animation.PathInterpolator(reverse);throw new AssertionError("cached invalid interpolator accepted");}catch(IllegalArgumentException wanted){}
  long start=System.nanoTime();for(int i=0;i<100;i++)new android.view.animation.PathInterpolator(curve());
  System.out.println("PATH_SAMPLE_WARM_100_MS="+(System.nanoTime()-start)/1000000);
  for(int i=0;i<90;i++){Path p=new Path();p.moveTo(0,0);p.lineTo(i+1,1);p.shirazAnimationPoints(.002);}
  java.lang.reflect.Field field=Path.class.getDeclaredField("SAMPLE_CACHE");field.setAccessible(true);
  check(((java.util.Map<?,?>)field.get(null)).size()<=64,"bounded cache entry count");
  check(java.util.Arrays.equals(curve().shirazAnimationPoints(.002),expected),"eviction preserves resampling semantics");
  System.out.println("PATH_SAMPLING_CACHE_OK");
 }
}
