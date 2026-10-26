// SPDX-License-Identifier: GPL-3.0-only
import android.view.Choreographer;
import android.view.animation.AnimationUtils;
public final class InterpolatorSmoke {
 private static void compiledMorph(){
  String from="m2 3 4 5 h2 v3 c1 2 3 4 5 6 s2 3 4 5 q1 2 3 4 t5 6 a4 5 20 01 6 7 z m1 2 l3 4";
  String to="m4 5 6 7 h4 v5 c3 4 5 6 7 8 s4 5 6 7 q3 4 5 6 t7 8 a6 7 40 10 8 9 z m3 4 l5 6";
  String mid="m3 4 5 6 h3 v4 c2 3 4 5 6 7 s3 4 5 6 q2 3 4 5 t6 7 a5 6 30 11 7 8 z m2 3 l4 5";
  shiraz.runtime.PathData.Morph morph=new shiraz.runtime.PathData.Morph(from,to);
  shiraz.runtime.PathData.Morph.Evaluator evaluator=morph.newEvaluator(),other=morph.newEvaluator();
  android.graphics.Path output=new android.graphics.Path();output.setFillType(android.graphics.Path.FillType.EVEN_ODD);
  try{
   java.lang.reflect.Method commands=android.graphics.Path.class.getDeclaredMethod("commands");commands.setAccessible(true);
   for(int i=0;i<12;i++){float fraction=(i%3)*.5f;evaluator.apply(output,fraction);other.apply(new android.graphics.Path(),1-fraction);
    android.graphics.Path expected=shiraz.runtime.PathData.parse(fraction==0?from:fraction==1?to:mid);
    if(!java.util.Arrays.equals((float[])commands.invoke(output),(float[])commands.invoke(expected)))throw new AssertionError("Compiled SVG command parity at "+fraction);
    if(output.getFillType()!=android.graphics.Path.FillType.EVEN_ODD)throw new AssertionError("Morph fill lost");
   }
   float[] before=((float[])commands.invoke(output)).clone();
   try{evaluator.apply(output,Float.NaN);throw new AssertionError("Nonfinite fraction accepted");}catch(IllegalArgumentException expected){}
   if(!java.util.Arrays.equals(before,(float[])commands.invoke(output)))throw new AssertionError("Invalid fraction changed target");
   java.lang.reflect.Field data=android.graphics.Path.class.getDeclaredField("data");data.setAccessible(true);Object storage=data.get(output);
   for(int i=0;i<1000;i++)evaluator.apply(output,(i%101)/100f);
   if(storage!=data.get(output))throw new AssertionError("Path storage reallocated after warmup");
  }catch(ReflectiveOperationException e){throw new AssertionError(e);}
 }
 public static void main(String[] args){
  compiledMorph();
  final android.content.res.Resources resources=android.content.res.Resources.shirazOpen(args[0],96,160);
  android.content.Context context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return resources;}public android.content.res.Resources.Theme getTheme(){return resources.newTheme();}public android.content.pm.ApplicationInfo getApplicationInfo(){android.content.pm.ApplicationInfo info=new android.content.pm.ApplicationInfo();info.targetSdkVersion=27;return info;}public Object getSystemService(String name){if(LAYOUT_INFLATER_SERVICE.equals(name))return new shiraz.runtime.HostLayoutInflater(this);throw new UnsupportedOperationException(name);}};
  android.os.Looper.prepareMainLooper();
  try{
  org.shiraz.fixture.InterpolatorFixture.check(context);
  android.view.View view=android.view.LayoutInflater.from(context).inflate(resources.getIdentifier("state_animator","layout","org.shiraz.fixture"),null);
  android.view.ViewTreeObserver window=android.view.ViewTreeObserver.shirazCreateWindowObserver();view.shirazAttachTree(window);view.setPressed(true);view.jumpDrawablesToCurrentState();if(view.getTranslationZ()!=8||view.getElevation()!=2)throw new AssertionError("Nested XML pressed state values");view.setEnabled(false);view.jumpDrawablesToCurrentState();if(view.getTranslationZ()!=0)throw new AssertionError("XML animation reference fallback state");view.shirazDetachTree();window.shirazKill();

  try{android.animation.ValueAnimator.setDurationScale(0);android.graphics.drawable.Drawable scale=resources.getDrawable(resources.getIdentifier("scale_static","drawable","org.shiraz.fixture"));scale.setBounds(0,0,3,3);android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(3,3,android.graphics.Bitmap.Config.ARGB_8888);try{scale.draw(new android.graphics.Canvas(image));if(image.getPixel(1,1)!=android.graphics.Color.GREEN)throw new AssertionError("Scale-list XML last static child pixels");}finally{image.recycle();}android.animation.ValueAnimator.setDurationScale(1);scale.setState(new int[]{1});if(scale.getCurrent()!=null)throw new AssertionError("Missing animated branch substituted with static");}finally{android.animation.ValueAnimator.setDurationScale(1);}
  android.graphics.drawable.AnimatedVectorDrawable vector=(android.graphics.drawable.AnimatedVectorDrawable)resources.getDrawable(resources.getIdentifier("vector_animated","drawable","org.shiraz.fixture"));vector.setBounds(0,0,20,20);
  android.graphics.drawable.VectorDrawable source=(android.graphics.drawable.VectorDrawable)vector.getDrawable();android.graphics.drawable.VectorDrawable independent=(android.graphics.drawable.VectorDrawable)source.getConstantState().newDrawable();independent.setBounds(0,0,20,20);
  android.graphics.Bitmap pixels=android.graphics.Bitmap.createBitmap(20,20,android.graphics.Bitmap.Config.ARGB_8888);
  try{vector.draw(new android.graphics.Canvas(pixels));if(pixels.getPixel(5,10)!=android.graphics.Color.RED||pixels.getPixel(14,10)!=0)throw new AssertionError("Initial vector trim pixels");
   long start=android.os.SystemClock.uptimeMillis();vector.start();Choreographer frames=Choreographer.getInstance();frames.shirazDoFrame(start*1000000,null);frames.shirazDoFrame((start+50)*1000000,null);pixels.eraseColor(0);vector.draw(new android.graphics.Canvas(pixels));if(android.graphics.Color.alpha(pixels.getPixel(5,5))<200||pixels.getPixel(5,10)!=0)throw new AssertionError("Animated vector rotation and trim midpoint");
   frames.shirazDoFrame((start+100)*1000000,null);pixels.eraseColor(0);vector.draw(new android.graphics.Canvas(pixels));if(pixels.getPixel(10,5)!=android.graphics.Color.RED||pixels.getPixel(10,15)!=android.graphics.Color.RED||vector.isRunning())throw new AssertionError("Animated vector completion pixels");
   pixels.eraseColor(0);independent.draw(new android.graphics.Canvas(pixels));if(pixels.getPixel(5,10)!=android.graphics.Color.RED||pixels.getPixel(10,15)!=0)throw new AssertionError("Vector constant-state clone isolated from animation");
   android.graphics.drawable.VectorDrawable.Target bar=(android.graphics.drawable.VectorDrawable.Target)source.shirazTarget("bar");bar.setTrimPathStart(.75f);bar.setTrimPathEnd(.25f);pixels.eraseColor(0);vector.draw(new android.graphics.Canvas(pixels));if(pixels.getPixel(10,3)!=android.graphics.Color.RED||pixels.getPixel(10,16)!=android.graphics.Color.RED||pixels.getPixel(10,10)!=0)throw new AssertionError("Wrapped trim segments");
   vector.start();vector.setVisible(false,false);if(vector.isRunning())throw new AssertionError("Hidden vector continues animation");
  }finally{vector.stop();pixels.recycle();}
  android.graphics.drawable.AnimatedVectorDrawable morph=(android.graphics.drawable.AnimatedVectorDrawable)resources.getDrawable(resources.getIdentifier("vector_morph","drawable","org.shiraz.fixture"));morph.setBounds(0,0,20,20);
  android.graphics.drawable.VectorDrawable morphSource=(android.graphics.drawable.VectorDrawable)morph.getDrawable();
  android.animation.ObjectAnimator path=(android.animation.ObjectAnimator)android.animation.AnimatorInflater.loadAnimator(context,resources.getIdentifier("vector_morph","animator","org.shiraz.fixture"));path.setTarget(morphSource.shirazTarget("bar"));
  android.graphics.Bitmap shape=android.graphics.Bitmap.createBitmap(20,20,android.graphics.Bitmap.Config.ARGB_8888);
  try{path.setCurrentFraction(0);morph.draw(new android.graphics.Canvas(shape));if(shape.getPixel(5,10)!=android.graphics.Color.RED)throw new AssertionError("Morph start");
   shape.eraseColor(0);path.setCurrentFraction(.5f);morph.draw(new android.graphics.Canvas(shape));if(shape.getPixel(7,7)!=android.graphics.Color.RED||shape.getPixel(5,10)!=0)throw new AssertionError("Morph intermediate geometry/cache");
   shape.eraseColor(0);path.clone().setCurrentFraction(1);morph.draw(new android.graphics.Canvas(shape));if(shape.getPixel(10,5)!=android.graphics.Color.RED||shape.getPixel(5,10)!=0)throw new AssertionError("Morph endpoint and clone");
   final int[] ended={0};morph.registerAnimationCallback(new android.graphics.drawable.Animatable2.AnimationCallback(){public void onAnimationEnd(android.graphics.drawable.Drawable d){ended[0]++;}});
   long now=android.os.SystemClock.uptimeMillis();morph.start();Choreographer.getInstance().shirazDoFrame(now*1000000,null);Choreographer.getInstance().shirazDoFrame((now+150)*1000000,null);if(morph.isRunning()||ended[0]!=1)throw new AssertionError("Morph natural completion callback");morph.stop();if(ended[0]!=1)throw new AssertionError("Duplicate end callback");
  }finally{morph.stop();shape.recycle();}
  try{new shiraz.runtime.PathData.Morph("M0 0 L1 1","M0 0 Q1 1 2 2");throw new AssertionError("Incompatible morph accepted");}catch(IllegalArgumentException expected){}
  android.view.animation.Interpolator trimStart=AnimationUtils.loadInterpolator(context,resources.getIdentifier("trim_start_interpolator","interpolator","android")),trimEnd=AnimationUtils.loadInterpolator(context,resources.getIdentifier("trim_end_interpolator","interpolator","android"));
  if(trimStart.getInterpolation(.25f)!=0||trimEnd.getInterpolation(.75f)!=1||trimStart.getInterpolation(.75f)<=0||trimEnd.getInterpolation(.25f)<=0)throw new AssertionError("Framework interpolator implicit origin");
  long before=android.os.SystemClock.uptimeMillis(),time=AnimationUtils.currentAnimationTimeMillis(),after=android.os.SystemClock.uptimeMillis();if(time<before||time>after)throw new AssertionError("Animation uptime");
  Choreographer choreographer=Choreographer.getInstance();choreographer.shirazDoFrame(1234567890L,new Runnable(){public void run(){if(AnimationUtils.currentAnimationTimeMillis()!=1234)throw new AssertionError("Frame time not shared");}});}finally{android.os.Looper.shirazShutdownMain();resources.shirazClose();}
 }
}
