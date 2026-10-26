// SPDX-License-Identifier: GPL-3.0-only
package android.animation;
import android.util.Property;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
/** Numeric properties bound through typed Properties or public reflective accessors. */
public final class ObjectAnimator extends ValueAnimator {
 private static final ThreadLocal<ArrayList<WeakReference<ObjectAnimator>>> ACTIVE=new ThreadLocal<ArrayList<WeakReference<ObjectAnimator>>>(){protected ArrayList<WeakReference<ObjectAnimator>> initialValue(){return new ArrayList<WeakReference<ObjectAnimator>>();}};
 private WeakReference<Object> target=new WeakReference<Object>(null);
 private Property property,resolved;
 private String name;
 private String xName,yName;
 private Property xProperty,yProperty,xResolved,yResolved;
 private double[] pathPoints;
 private shiraz.runtime.PathData.Morph pathMorph;
 private shiraz.runtime.PathData.Morph.Evaluator morphEvaluator;
 private Property pointProperty;
 private TypeConverter<android.graphics.PointF,?> pointConverter;
 private final android.graphics.PointF pathPoint=new android.graphics.PointF();
 private Object pointValue;
 private float pathX,pathY;
 private boolean integer,single,configured,prepared,seekPrepared,autoCancel;
 public ObjectAnimator(){}
 public static ObjectAnimator ofFloat(Object target,String name,float... values){ObjectAnimator a=new ObjectAnimator();a.setTarget(target);a.setPropertyName(name);a.setFloatValues(values);return a;}
 public static <T> ObjectAnimator ofFloat(T target,Property<T,Float> property,float... values){ObjectAnimator a=new ObjectAnimator();a.setTarget(target);a.setProperty(property);a.setFloatValues(values);return a;}
 public static ObjectAnimator ofInt(Object target,String name,int... values){ObjectAnimator a=new ObjectAnimator();a.setTarget(target);a.setPropertyName(name);a.setIntValues(values);return a;}
 public static <T> ObjectAnimator ofInt(T target,Property<T,Integer> property,int... values){ObjectAnimator a=new ObjectAnimator();a.setTarget(target);a.setProperty(property);a.setIntValues(values);return a;}
 public static ObjectAnimator ofFloat(Object target,String x,String y,android.graphics.Path path){
  if(path==null)throw new IllegalArgumentException("Null animation path");
  if((x!=null&&(x.isEmpty()||x.length()>512))||(y!=null&&(y.isEmpty()||y.length()>512)))throw new IllegalArgumentException("Property name");
  if(x==null&&y==null)throw new IllegalArgumentException("At least one path property required");
  ObjectAnimator a=new ObjectAnimator();a.setTarget(target);a.setFloatValues(0,1);a.xName=x;a.yName=y;a.name=x!=null?x:y;a.pathPoints=path.shirazAnimationPoints();return a;
 }
 public static <T> ObjectAnimator ofFloat(T target,Property<T,Float> x,Property<T,Float> y,android.graphics.Path path){
  ObjectAnimator a=ofFloat(target,x==null?null:x.getName(),y==null?null:y.getName(),path);a.xProperty=x;a.yProperty=y;return a;
 }
 public static <T,V> ObjectAnimator ofObject(T target,Property<T,V> property,TypeConverter<android.graphics.PointF,V> converter,android.graphics.Path path){
  if(property==null||path==null)throw new NullPointerException("Path property/path");
  if(property.isReadOnly())throw new IllegalArgumentException("Path requires a writable property");
  Class<?> output=converter==null?android.graphics.PointF.class:converter.getTargetType();
  if(!property.getType().isAssignableFrom(output)||(converter!=null&&!converter.getSourceType().isAssignableFrom(android.graphics.PointF.class)))throw new IllegalArgumentException("Path converter/property type mismatch");
  ObjectAnimator a=new ObjectAnimator();a.setTarget(target);a.setProperty(property);a.setFloatValues(0,1);a.pointProperty=property;a.pointConverter=converter;a.pathPoints=path.shirazAnimationPoints();return a;
 }
 public static ObjectAnimator shirazOfPathData(String from,String to){
  ObjectAnimator a=new ObjectAnimator();a.setPropertyName("pathData");a.setFloatValues(0,1);a.pathMorph=new shiraz.runtime.PathData.Morph(from,to);a.morphEvaluator=a.pathMorph.newEvaluator();return a;
 }
 private static Property writable(Object object,final Class type,String key,boolean needsRead){
  if(needsRead)return Property.of(object.getClass(),type,key);
  String setter="set"+Character.toUpperCase(key.charAt(0))+key.substring(1);
  java.lang.reflect.Method found;
  try{found=object.getClass().getMethod(setter,type==Float.class?float.class:int.class);}
  catch(NoSuchMethodException primitive){try{found=object.getClass().getMethod(setter,type);}catch(NoSuchMethodException boxed){return Property.of(object.getClass(),type,key);}}
  final java.lang.reflect.Method method=found;method.setAccessible(true);
  return new Property(type,key){
   public Object get(Object target){throw new UnsupportedOperationException("Write-only animation binding");}
   public void set(Object target,Object value){try{method.invoke(target,value);}catch(IllegalAccessException failure){throw new IllegalStateException("Cannot invoke animation setter",failure);}catch(java.lang.reflect.InvocationTargetException failure){Throwable cause=failure.getCause();if(cause instanceof RuntimeException)throw (RuntimeException)cause;if(cause instanceof Error)throw (Error)cause;throw new IllegalStateException("Animation setter failed",cause);}}
  };
 }
 private Property bind(Object object,String key,Property supplied){if(key==null)return null;Property result=supplied==null?writable(object,Float.class,key,false):supplied;if(result.getType()!=Float.class||result.isReadOnly())throw new IllegalArgumentException("Path requires writable float properties");return result;}
 private void applyPath(Object object,float fraction){
  int last=pathPoints.length/3-1,lo=0,hi=last;double distance=fraction*pathPoints[last*3];
  if(fraction==0||last==0){hi=0;}else if(fraction==1){lo=hi=last;}else if(distance<0){hi=1;while(hi<last&&pathPoints[hi*3]==0)hi++;}else if(distance>pathPoints[last*3]){lo=last-1;while(lo>0&&pathPoints[lo*3]==pathPoints[last*3])lo--;}else {
   while(lo+1<hi){int mid=(lo+hi)/2;if(pathPoints[mid*3]<=distance)lo=mid;else hi=mid;}
  }
  double span=pathPoints[hi*3]-pathPoints[lo*3],t=span==0?0:(distance-pathPoints[lo*3])/span;
  pathX=(float)(pathPoints[lo*3+1]+t*(pathPoints[hi*3+1]-pathPoints[lo*3+1]));pathY=(float)(pathPoints[lo*3+2]+t*(pathPoints[hi*3+2]-pathPoints[lo*3+2]));
  if(pointProperty!=null){pathPoint.set(pathX,pathY);pointValue=pointConverter==null?pathPoint:pointConverter.convert(pathPoint);pointProperty.set(object,pointValue);}
  else{if(xResolved!=null)xResolved.set(object,pathX);if(yResolved!=null)yResolved.set(object,pathY);}
 }
 private void editable(){if(isStarted())throw new IllegalStateException("Cannot change animation binding while running");}
 public void setTarget(Object value){if(target.get()==value)return;if(isStarted())cancel();editable();target=new WeakReference<Object>(value);prepared=seekPrepared=false;resolved=null;}
 public Object getTarget(){return target.get();}
 public void setPropertyName(String value){editable();if(pathPoints!=null)throw new UnsupportedOperationException("Rebinding path animator");if(value==null||value.isEmpty()||value.length()>512)throw new IllegalArgumentException("Property name");name=value;property=resolved=null;prepared=seekPrepared=false;}
 public String getPropertyName(){return name;}
 public void setProperty(Property value){editable();if(pathPoints!=null)throw new UnsupportedOperationException("Rebinding path animator");if(value==null)throw new NullPointerException("property");property=value;name=value.getName();resolved=null;prepared=seekPrepared=false;}
 public void setFloatValues(float... values){if(pathPoints!=null)throw new UnsupportedOperationException("Replacing path values");super.setFloatValues(values);integer=false;single=values.length==1;configured=true;prepared=seekPrepared=false;}
 public void setIntValues(int... values){if(pathPoints!=null)throw new UnsupportedOperationException("Replacing path values");super.setIntValues(values);integer=true;single=values.length==1;configured=true;prepared=seekPrepared=false;}
 @Override protected void shirazInitializeValues(){if(prepared)return;Object object=target.get();if(object==null||!configured||name==null)throw new IllegalStateException("ObjectAnimator requires target, property and values");if(pathMorph!=null){if(!(object instanceof android.graphics.drawable.VectorDrawable.Target))throw new IllegalArgumentException("Path morph requires vector target");prepared=true;return;}if(pathPoints!=null){if(pointProperty==null){xResolved=bind(object,xName,xProperty);yResolved=bind(object,yName,yProperty);}prepared=true;return;}Class type=integer?Integer.class:Float.class;resolved=property==null?writable(object,type,name,single):property;if(resolved.getType()!=type)throw new IllegalArgumentException("Property numeric type mismatch");if(resolved.isReadOnly())throw new UnsupportedOperationException("Animated property is read-only");if(single){Object current=resolved.get(object);if(!(current instanceof Number))throw new IllegalArgumentException("Animated property is not numeric");shirazSetStartValue((Number)current);}prepared=true;}
 @Override protected void shirazApplyValue(Object value){Object object=target.get();if(object==null){cancel();return;}if(pathMorph!=null)((android.graphics.drawable.VectorDrawable.Target)object).shirazMorphPath(morphEvaluator,getAnimatedFraction());else if(pathPoints!=null)applyPath(object,getAnimatedFraction());else resolved.set(object,value);}
 public void setAutoCancel(boolean enabled){autoCancel=enabled;}
 @Override public void start(){shirazCheckThread();if(android.os.Looper.myLooper()==null)throw new IllegalStateException("Animator requires a Looper");editableOrCancel();Object object=target.get();ArrayList<WeakReference<ObjectAnimator>> registry=ACTIVE.get();for(WeakReference<ObjectAnimator> ref:new ArrayList<WeakReference<ObjectAnimator>>(registry)){ObjectAnimator old=ref.get();if(old==null||!old.isStarted()){registry.remove(ref);continue;}if(old!=this&&old.autoCancel&&old.getTarget()==object&&name!=null&&name.equals(old.name)&&java.util.Objects.equals(xName,old.xName)&&java.util.Objects.equals(yName,old.yName))old.cancel();}if(registry.size()>=4096)throw new IllegalStateException("Object animator registry limit");if(!seekPrepared)prepared=false;seekPrepared=false;WeakReference<ObjectAnimator> entry=new WeakReference<ObjectAnimator>(this);registry.add(entry);try{super.start();}finally{if(!isStarted())registry.remove(entry);}}
 private void editableOrCancel(){if(isStarted())cancel();editable();}
 @Override public void end(){if(!isStarted()&&!seekPrepared)prepared=false;super.end();seekPrepared=false;}
 @Override public void setCurrentFraction(float fraction){super.setCurrentFraction(fraction);seekPrepared=!isStarted();}
 @Override public Object getAnimatedValue(){return pointProperty!=null?pointValue:pathPoints==null?super.getAnimatedValue():Float.valueOf(xName!=null?pathX:pathY);}
 @Override public Object getAnimatedValue(String propertyName){if(pointProperty!=null)return propertyName!=null&&propertyName.equals(pointProperty.getName())?pointValue:null;if(pathPoints!=null)return propertyName!=null&&propertyName.equals(xName)?Float.valueOf(pathX):propertyName!=null&&propertyName.equals(yName)?Float.valueOf(pathY):null;return name!=null&&name.equals(propertyName)?getAnimatedValue():null;}
 @Override public ObjectAnimator setDuration(long duration){super.setDuration(duration);return this;}
 @Override public ObjectAnimator clone(){ObjectAnimator copy=new ObjectAnimator();shirazCopyTo(copy);copy.target=new WeakReference<Object>(target.get());copy.property=property;copy.name=name;copy.integer=integer;copy.single=single;copy.configured=configured;copy.autoCancel=autoCancel;copy.pathPoints=pathPoints;copy.pathMorph=pathMorph;copy.morphEvaluator=pathMorph==null?null:pathMorph.newEvaluator();copy.pointProperty=pointProperty;copy.pointConverter=pointConverter;copy.xName=xName;copy.yName=yName;copy.xProperty=xProperty;copy.yProperty=yProperty;return copy;}
}
