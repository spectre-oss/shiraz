// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.animation.*;
import android.util.Property;
public final class ObjectAnimatorFixture {
 public static class Bean {public float value=2;public int count;public float getValue(){return value;}public void setValue(float value){this.value=value;}public int getCount(){return count;}public void setCount(int value){count=value;}}
 private static final class SetterOnly {float x,y;int count;public void setTranslateX(float value){x=value;}public void setTranslateY(float value){y=value;}public void setCount(int value){count=value;}}
 private static void setterOnly(){
  SetterOnly target=new SetterOnly();ObjectAnimator a=ObjectAnimator.ofFloat(target,"translateX",0,10);a.setInterpolator(null);a.setCurrentFraction(.5f);
  if(target.x!=5)throw new AssertionError("Explicit values need only setter");
  ObjectAnimator.ofInt(target,"count",0,7).end();if(target.count!=7)throw new AssertionError("Integer setter-only completion");
  android.graphics.Path path=new android.graphics.Path();path.moveTo(2,3);path.lineTo(8,9);
  ObjectAnimator along=ObjectAnimator.ofFloat(target,"translateX","translateY",path);along.setInterpolator(null);along.setCurrentFraction(.5f);
  if(target.x!=5||target.y!=6)throw new AssertionError("Path animation needs only setters");
  AnimatorSet set=new AnimatorSet();set.playTogether(along);set.end();if(target.x!=8||target.y!=9)throw new AssertionError("Unstarted path set completion");
 }
 public static final Property<Bean,Float> VALUE=new Property<Bean,Float>(Float.class,"value"){public Float get(Bean bean){return bean.value;}public void set(Bean bean,Float value){bean.value=value;}};
 private static final class Position {float x,y;}
 private static void pointPaths(){
  android.graphics.Path path=new android.graphics.Path();path.moveTo(0,0);path.lineTo(10,20);
  final Property<Position,android.graphics.PointF> position=new Property<Position,android.graphics.PointF>(android.graphics.PointF.class,"position"){
   public android.graphics.PointF get(Position target){throw new AssertionError("Explicit path must not read the property");}
   public void set(Position target,android.graphics.PointF value){target.x=value.x;target.y=value.y;}
  };
  Position target=new Position();ObjectAnimator animator=ObjectAnimator.ofObject(target,position,null,path);animator.setInterpolator(null);path.reset();animator.setCurrentFraction(.25f);
  if(target.x!=2.5f||target.y!=5||!((android.graphics.PointF)animator.getAnimatedValue("position")).equals(2.5f,5))throw new AssertionError("PointF path sample/snapshot");
  ObjectAnimator clone=animator.clone();Position other=new Position();clone.setTarget(other);clone.setCurrentFraction(.5f);
  if(target.x!=2.5f||other.x!=5||other.y!=10||animator.getAnimatedValue()==clone.getAnimatedValue())throw new AssertionError("Independent cloned PointF path state");
  clone.end();if(other.x!=10||other.y!=20)throw new AssertionError("PointF path end");
  android.graphics.Path convertedPath=new android.graphics.Path();convertedPath.moveTo(0,0);convertedPath.lineTo(10,20);
  Bean bean=new Bean();TypeConverter<android.graphics.PointF,Float> converter=new TypeConverter<android.graphics.PointF,Float>(android.graphics.PointF.class,Float.class){public Float convert(android.graphics.PointF value){return value.x+value.y;}};
  ObjectAnimator converted=ObjectAnimator.ofObject(bean,VALUE,converter,convertedPath);converted.setInterpolator(null);converted.setCurrentFraction(.5f);
  if(bean.value!=15||!converted.getAnimatedValue("value").equals(15f))throw new AssertionError("Converted point property value");
  converted.end();if(bean.value!=30)throw new AssertionError("Converted path end");
 }
 public static void check(){pointPaths();setterOnly();Bean bean=new Bean();ObjectAnimator a=ObjectAnimator.ofFloat(bean,"value",10);a.setInterpolator(null);a.setCurrentFraction(.5f);if(bean.value!=6||!a.getAnimatedValue("value").equals(6f))throw new AssertionError("Reflective single-value current-property interpolation");ObjectAnimator b=ObjectAnimator.ofInt(bean,"count",0,10);b.setInterpolator(null);b.setCurrentFraction(.5f);if(bean.count!=5)throw new AssertionError("Integer property binding");ObjectAnimator typed=ObjectAnimator.ofFloat(bean,VALUE,0,20);typed.setInterpolator(null);typed.setCurrentFraction(.25f);if(bean.value!=5)throw new AssertionError("Typed property binding");ObjectAnimator copy=typed.clone();Bean other=new Bean();copy.setTarget(other);copy.setCurrentFraction(.5f);if(bean.value!=5||other.value!=10)throw new AssertionError("Clone target independent");}
 public static void sets(){Bean bean=new Bean();ObjectAnimator x=ObjectAnimator.ofFloat(bean,"value",2,7);ObjectAnimator y=ObjectAnimator.ofInt(bean,"count",0,9);AnimatorSet set=new AnimatorSet();set.playTogether(x,y);set.setDuration(40);set.start();set.end();if(bean.value!=7||bean.count!=9||set.isStarted())throw new AssertionError("AnimatorSet property completion");AnimatorSet clone=set.clone();Bean other=new Bean();clone.setTarget(other);clone.start();clone.end();if(other.value!=7||other.count!=9)throw new AssertionError("AnimatorSet clone targets");}
 public static void install(){check();sets();final Bean bean=new Bean();ObjectAnimator a=ObjectAnimator.ofFloat(bean,VALUE,8);a.setDuration(0);a.addListener(new AnimatorListenerAdapter(){public void onAnimationEnd(Animator animation){if(bean.value!=8)throw new AssertionError("Object animation frame value");System.out.println("OBJECT_ANIMATOR_LIVE_FRAME_OK");}});a.start();}
}
