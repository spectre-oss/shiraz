// SPDX-License-Identifier: GPL-3.0-only
package android.animation;
import android.util.Property;
/**
 * One animated property: a name or Property plus float, int or evaluated object values.
 * Keyframe, multi-value and path holders are not implemented. A holder with a single value
 * takes its start from the target when the owning ObjectAnimator initializes.
 */
public class PropertyValuesHolder implements Cloneable {
 String propertyName;
 Property property;
 private double[] numbers;
 private Object[] objects;
 private Keyframe[] keyframes;
 private boolean integers,single;
 private TypeEvaluator evaluator;
 private TypeConverter converter;
 private Object animatedValue;
 Property shirazResolved;
 private PropertyValuesHolder(String name){setPropertyName(name);}
 private PropertyValuesHolder(Property value){setProperty(value);}
 public static PropertyValuesHolder ofFloat(String name,float... values){PropertyValuesHolder h=new PropertyValuesHolder(name);h.setFloatValues(values);return h;}
 public static PropertyValuesHolder ofFloat(Property<?,Float> property,float... values){PropertyValuesHolder h=new PropertyValuesHolder(property);h.setFloatValues(values);return h;}
 public static PropertyValuesHolder ofInt(String name,int... values){PropertyValuesHolder h=new PropertyValuesHolder(name);h.setIntValues(values);return h;}
 public static PropertyValuesHolder ofInt(Property<?,Integer> property,int... values){PropertyValuesHolder h=new PropertyValuesHolder(property);h.setIntValues(values);return h;}
 public static PropertyValuesHolder ofObject(String name,TypeEvaluator evaluator,Object... values){PropertyValuesHolder h=new PropertyValuesHolder(name);h.setObjectValues(values);h.setEvaluator(evaluator);return h;}
 public static <V> PropertyValuesHolder ofObject(Property property,TypeEvaluator<V> evaluator,V... values){PropertyValuesHolder h=new PropertyValuesHolder(property);h.setObjectValues((Object[])values);h.setEvaluator(evaluator);return h;}
 public static <T,V> PropertyValuesHolder ofObject(Property<?,V> property,TypeConverter<T,V> converter,TypeEvaluator<T> evaluator,T... values){PropertyValuesHolder h=new PropertyValuesHolder(property);h.setConverter(converter);h.setObjectValues((Object[])values);h.setEvaluator(evaluator);return h;}
 public static PropertyValuesHolder ofKeyframe(String name,Keyframe... values){PropertyValuesHolder h=new PropertyValuesHolder(name);h.setKeyframes(values);return h;}
 public static PropertyValuesHolder ofKeyframe(Property property,Keyframe... values){PropertyValuesHolder h=new PropertyValuesHolder(property);h.setKeyframes(values);return h;}
 /** Holder for an animator's existing value array; single-value arrays keep their target start slot. */
 static PropertyValuesHolder shirazOf(String name,Property property,double[] values,boolean integers,boolean single){PropertyValuesHolder h=property!=null?new PropertyValuesHolder(property):new PropertyValuesHolder(name==null?"":name);h.numbers=values.clone();h.integers=integers;h.single=single;return h;}
 static PropertyValuesHolder shirazEmpty(String name,Property property){return property!=null?new PropertyValuesHolder(property):new PropertyValuesHolder(name==null?"":name);}
 private static void count(int n){if(n<1||n>4096)throw new IllegalArgumentException("Animation value count");}
 public void setFloatValues(float... values){if(values==null)throw new NullPointerException("values");count(values.length);double[] next=new double[Math.max(2,values.length)];for(int i=0;i<values.length;i++){if(!Float.isFinite(values[i]))throw new IllegalArgumentException("Animation value must be finite");next[i+(values.length==1?1:0)]=values[i];}numbers=next;objects=null;keyframes=null;integers=false;single=values.length==1;}
 public void setIntValues(int... values){if(values==null)throw new NullPointerException("values");count(values.length);double[] next=new double[Math.max(2,values.length)];for(int i=0;i<values.length;i++)next[i+(values.length==1?1:0)]=values[i];numbers=next;objects=null;keyframes=null;integers=true;single=values.length==1;}
 public void setObjectValues(Object... values){if(values==null)throw new NullPointerException("values");count(values.length);for(Object value:values)if(value==null)throw new NullPointerException("Animation object value");Object[] next=new Object[Math.max(2,values.length)];System.arraycopy(values,0,next,values.length==1?1:0,values.length);objects=next;numbers=null;keyframes=null;single=values.length==1;}
 public void setKeyframes(Keyframe... values){
  if(values==null)throw new NullPointerException("values");
  count(values.length);
  for(Keyframe frame:values){if(frame==null)throw new NullPointerException("Keyframe");if(!Float.isFinite(frame.getFraction()))throw new IllegalArgumentException("Keyframe fraction must be finite");}
  Keyframe[] next;
  if(values.length==1){
   Keyframe target=values[0].clone();
   Keyframe start;
   if(target.getType()==int.class)start=Keyframe.ofInt(0f);
   else if(target.getType()==float.class)start=Keyframe.ofFloat(0f);
   else start=Keyframe.ofObject(0f);
   if(target.getFraction()==0f)target.setFraction(1f);
   next=new Keyframe[]{start,target};
  }else{
   next=new Keyframe[values.length];
   for(int i=0;i<values.length;i++)next[i]=values[i].clone();
  }
  keyframes=next;numbers=null;objects=null;
  integers=next[next.length-1].getType()==int.class;
  single=!next[0].hasValue();
 }
 public void setConverter(TypeConverter value){converter=value;}
 public void setEvaluator(TypeEvaluator value){evaluator=value;}
 public void setPropertyName(String value){if(value==null||value.length()>512)throw new IllegalArgumentException("Property name");propertyName=value;property=null;shirazResolved=null;}
 public String getPropertyName(){return propertyName;}
 public void setProperty(Property value){if(value==null)throw new NullPointerException("property");property=value;propertyName=value.getName();shirazResolved=null;}
 @Override public PropertyValuesHolder clone(){try{PropertyValuesHolder copy=(PropertyValuesHolder)super.clone();if(numbers!=null)copy.numbers=numbers.clone();if(objects!=null)copy.objects=objects.clone();if(keyframes!=null){copy.keyframes=new Keyframe[keyframes.length];for(int i=0;i<keyframes.length;i++)copy.keyframes[i]=keyframes[i].clone();}copy.animatedValue=null;copy.shirazResolved=null;return copy;}catch(CloneNotSupportedException impossible){throw new AssertionError(impossible);}}
 @Override public String toString(){return propertyName+": "+(numbers!=null?java.util.Arrays.toString(numbers):keyframes!=null?java.util.Arrays.toString(keyframes):java.util.Arrays.toString(objects));}
 boolean shirazConfigured(){return numbers!=null||objects!=null||keyframes!=null;}
 boolean shirazSingle(){return single;}
 /** Value class the bound property must accept. */
 Class<?> shirazValueType(){
  if(converter!=null)return converter.getTargetType();
  if(keyframes!=null){Class<?> type=keyframes[keyframes.length-1].getType();return type==int.class?Integer.class:type==float.class?Float.class:type;}
  if(objects!=null)return objects[1].getClass();
  return integers?Integer.class:Float.class;
 }
 void shirazSetStart(Object value){
  Object source=value;
  if(converter instanceof BidirectionalTypeConverter)source=((BidirectionalTypeConverter)converter).convertBack(value);
  if(keyframes!=null){
   if(!keyframes[0].hasValue()){
    if(keyframes[0].getType()==int.class)keyframes[0].setValue(Integer.valueOf(((Number)source).intValue()));
    else if(keyframes[0].getType()==float.class)keyframes[0].setValue(Float.valueOf(((Number)source).floatValue()));
    else keyframes[0].setValue(source);
   }
   return;
  }
  if(objects!=null){if(source==null)throw new IllegalArgumentException("Null property start value");objects[0]=source;return;}
  if(!(source instanceof Number)||!Double.isFinite(((Number)source).doubleValue()))throw new IllegalArgumentException("Animated property is not numeric");
  numbers[0]=integers?((Number)source).intValue():((Number)source).floatValue();
 }
 private Object box(int index){return objects!=null?objects[index]:integers?(Object)Integer.valueOf((int)numbers[index]):(Object)Float.valueOf((float)numbers[index]);}
 private Object evaluateKeyframes(float fraction){
  Keyframe first=keyframes[0],last=keyframes[keyframes.length-1];
  if(fraction<=first.getFraction())return first.getValue();
  if(fraction>=last.getFraction())return last.getValue();
  Keyframe prev=first;
  for(int i=1;i<keyframes.length;i++){
   Keyframe next=keyframes[i];
   if(fraction<next.getFraction()||i==keyframes.length-1){
    TimeInterpolator interp=next.getInterpolator();
    float span=next.getFraction()-prev.getFraction();
    float local=span==0f?0f:(fraction-prev.getFraction())/span;
    if(interp!=null)local=interp.getInterpolation(local);
    Object a=prev.getValue(),b=next.getValue();
    if(evaluator!=null)return evaluator.evaluate(local,a,b);
    if(next.getType()==int.class){int s=((Number)a).intValue(),e=((Number)b).intValue();return Integer.valueOf((int)(s+local*(e-s)));}
    if(next.getType()==float.class){float s=((Number)a).floatValue(),e=((Number)b).floatValue();return Float.valueOf(s+local*(e-s));}
    throw new IllegalStateException("Object keyframe animation requires a TypeEvaluator");
   }
   prev=next;
  }
  return last.getValue();
 }
 Object shirazAnimate(float fraction){
  if(!shirazConfigured())throw new IllegalStateException("PropertyValuesHolder "+propertyName+" has no values");
  Object raw;
  if(keyframes!=null){
   if(!keyframes[0].hasValue())throw new IllegalStateException("Leading keyframe needs a value");
   raw=evaluateKeyframes(fraction);
  }else{
   if(objects!=null&&objects[0]==null)throw new IllegalStateException("Single object value needs a target start value");
   int length=objects!=null?objects.length:numbers.length;double position=fraction*(length-1);
   int index=Math.max(0,Math.min(length-2,(int)Math.floor(position)));float local=(float)(position-index);
   if(evaluator!=null)raw=evaluator.evaluate(local,box(index),box(index+1));
   else if(objects!=null)throw new IllegalStateException("Object animation requires a TypeEvaluator");
   else{double result=numbers[index]+(numbers[index+1]-numbers[index])*local;raw=integers?(Object)Integer.valueOf((int)result):(Object)Float.valueOf((float)result);}
  }
  animatedValue=converter!=null?converter.convert(raw):raw;
  return animatedValue;
 }
 Object shirazAnimatedValue(){return animatedValue;}
}
