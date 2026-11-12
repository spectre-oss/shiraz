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
 private boolean integers,single;
 private TypeEvaluator evaluator;
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
 /** Holder for an animator's existing value array; single-value arrays keep their target start slot. */
 static PropertyValuesHolder shirazOf(String name,Property property,double[] values,boolean integers,boolean single){PropertyValuesHolder h=property!=null?new PropertyValuesHolder(property):new PropertyValuesHolder(name==null?"":name);h.numbers=values.clone();h.integers=integers;h.single=single;return h;}
 static PropertyValuesHolder shirazEmpty(String name,Property property){return property!=null?new PropertyValuesHolder(property):new PropertyValuesHolder(name==null?"":name);}
 private static void count(int n){if(n<1||n>4096)throw new IllegalArgumentException("Animation value count");}
 public void setFloatValues(float... values){if(values==null)throw new NullPointerException("values");count(values.length);double[] next=new double[Math.max(2,values.length)];for(int i=0;i<values.length;i++){if(!Float.isFinite(values[i]))throw new IllegalArgumentException("Animation value must be finite");next[i+(values.length==1?1:0)]=values[i];}numbers=next;objects=null;integers=false;single=values.length==1;}
 public void setIntValues(int... values){if(values==null)throw new NullPointerException("values");count(values.length);double[] next=new double[Math.max(2,values.length)];for(int i=0;i<values.length;i++)next[i+(values.length==1?1:0)]=values[i];numbers=next;objects=null;integers=true;single=values.length==1;}
 public void setObjectValues(Object... values){if(values==null)throw new NullPointerException("values");count(values.length);for(Object value:values)if(value==null)throw new NullPointerException("Animation object value");Object[] next=new Object[Math.max(2,values.length)];System.arraycopy(values,0,next,values.length==1?1:0,values.length);objects=next;numbers=null;single=values.length==1;}
 public void setEvaluator(TypeEvaluator value){evaluator=value;}
 public void setPropertyName(String value){if(value==null||value.length()>512)throw new IllegalArgumentException("Property name");propertyName=value;property=null;shirazResolved=null;}
 public String getPropertyName(){return propertyName;}
 public void setProperty(Property value){if(value==null)throw new NullPointerException("property");property=value;propertyName=value.getName();shirazResolved=null;}
 @Override public PropertyValuesHolder clone(){try{PropertyValuesHolder copy=(PropertyValuesHolder)super.clone();if(numbers!=null)copy.numbers=numbers.clone();if(objects!=null)copy.objects=objects.clone();copy.animatedValue=null;copy.shirazResolved=null;return copy;}catch(CloneNotSupportedException impossible){throw new AssertionError(impossible);}}
 @Override public String toString(){return propertyName+": "+(numbers!=null?java.util.Arrays.toString(numbers):java.util.Arrays.toString(objects));}
 boolean shirazConfigured(){return numbers!=null||objects!=null;}
 boolean shirazSingle(){return single;}
 /** Value class the bound property must accept. */
 Class<?> shirazValueType(){if(objects!=null)return objects[1].getClass();return integers?Integer.class:Float.class;}
 void shirazSetStart(Object value){
  if(objects!=null){if(value==null)throw new IllegalArgumentException("Null property start value");objects[0]=value;return;}
  if(!(value instanceof Number)||!Double.isFinite(((Number)value).doubleValue()))throw new IllegalArgumentException("Animated property is not numeric");
  numbers[0]=integers?((Number)value).intValue():((Number)value).floatValue();
 }
 private Object box(int index){return objects!=null?objects[index]:integers?(Object)Integer.valueOf((int)numbers[index]):(Object)Float.valueOf((float)numbers[index]);}
 Object shirazAnimate(float fraction){
  if(!shirazConfigured())throw new IllegalStateException("PropertyValuesHolder "+propertyName+" has no values");
  if(objects!=null&&objects[0]==null)throw new IllegalStateException("Single object value needs a target start value");
  int length=objects!=null?objects.length:numbers.length;double position=fraction*(length-1);
  int index=Math.max(0,Math.min(length-2,(int)Math.floor(position)));float local=(float)(position-index);
  if(evaluator!=null)animatedValue=evaluator.evaluate(local,box(index),box(index+1));
  else if(objects!=null)throw new IllegalStateException("Object animation requires a TypeEvaluator");
  else{double result=numbers[index]+(numbers[index+1]-numbers[index])*local;animatedValue=integers?(Object)Integer.valueOf((int)result):(Object)Float.valueOf((float)result);}
  return animatedValue;
 }
 Object shirazAnimatedValue(){return animatedValue;}
}
