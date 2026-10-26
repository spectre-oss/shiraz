// SPDX-License-Identifier: GPL-3.0-only
package android.animation;
import android.content.Context;
import android.content.res.*;
import android.util.TypedValue;
import android.view.animation.AnimationUtils;
import org.xmlpull.v1.XmlPullParser;
import shiraz.runtime.*;
import java.util.ArrayList;
/** Bounded scalar/path-motion animator XML and state-selector loader. Unsupported keyframes fail explicitly. */
public final class AnimatorInflater {
 private AnimatorInflater(){}
 private static final class Loader {
  final Context context;int count;
  Loader(Context c){context=c;}
  void attributes(CompiledXmlParser parser,String names){for(BinaryXml.Attribute attr:parser.shirazNode().attributes)if(!BinaryXml.ANDROID.equals(attr.namespace)||!(" "+names+" ").contains(" "+attr.name+" "))throw new UnsupportedOperationException("Animator XML attribute "+attr.name);}
  void limit(int depth){if(depth>32||++count>1024)throw new IllegalArgumentException("Animator XML graph limit");}
  CompiledXmlParser open(int id)throws Exception{XmlResourceParser source=context.getResources().getAnimation(id);if(!(source instanceof CompiledXmlParser)){source.close();throw new UnsupportedOperationException("Compiled animator XML required");}CompiledXmlParser p=(CompiledXmlParser)source;try{while(p.next()!=XmlPullParser.START_TAG){if(p.getEventType()==XmlPullParser.END_DOCUMENT)throw new IllegalArgumentException("Missing animator root");}return p;}catch(Exception e){p.close();throw e;}}
  Animator load(int id,int depth)throws Exception{try(CompiledXmlParser p=open(id)){return animation(p,depth);}}
  float number(XmlAttributes attrs,String name){BinaryXml.Value v=attrs.raw(name);if(v!=null&&v.type==5)return attrs.dimensionFloat(name,0);return attrs.number(name,0);}
  int integerValue(XmlAttributes attrs,String name){BinaryXml.Value value=attrs.raw(name);if(value!=null&&value.type==5)return (int)attrs.dimensionFloat(name,0);return attrs.integer(name,0);}
  Animator animation(CompiledXmlParser p,int depth)throws Exception{
   limit(depth);if(!p.getNamespace().isEmpty())throw new IllegalArgumentException("Animator namespace");String tag=p.getName();XmlAttributes a=new XmlAttributes(context,p);int start=p.getDepth();
   if(tag.equals("set")){attributes(p,"ordering");int order=a.integer("ordering",0);if(order<0||order>1)throw new IllegalArgumentException("Animator ordering");AnimatorSet set=new AnimatorSet();ArrayList<Animator> children=new ArrayList<Animator>();while(p.next()!=XmlPullParser.END_DOCUMENT){if(p.getEventType()==XmlPullParser.END_TAG&&p.getDepth()==start)break;if(p.getEventType()==XmlPullParser.START_TAG)children.add(animation(p,depth+1));}if(order==0)set.playTogether(children);else set.playSequentially(children);return set;}
   if(!tag.equals("animator")&&!tag.equals("objectAnimator"))throw new UnsupportedOperationException("Animator XML element "+tag);
   attributes(p,tag.equals("objectAnimator")?"propertyName pathData propertyXName propertyYName duration startOffset startDelay valueFrom valueTo valueType repeatCount repeatMode interpolator":"duration startOffset startDelay valueFrom valueTo valueType repeatCount repeatMode interpolator");
   int type=a.integer("valueType",4);if(type!=0&&type!=1&&type!=2&&type!=4)throw new UnsupportedOperationException("Animator XML value type "+type);
   ValueAnimator result;
   if(type==2){
    if(!tag.equals("objectAnimator")||!"pathData".equals(a.text("propertyName"))||!a.has("valueFrom")||!a.has("valueTo")||a.has("pathData")||a.has("propertyXName")||a.has("propertyYName"))throw new UnsupportedOperationException("Path morph requires explicit pathData endpoints");
    result=ObjectAnimator.shirazOfPathData(a.text("valueFrom"),a.text("valueTo"));
   }else if(a.has("pathData")){
    if(type==1)throw new UnsupportedOperationException("Integer path motion is not implemented");
    if(a.has("valueFrom")||a.has("valueTo")||a.has("propertyName"))throw new IllegalArgumentException("Path animator cannot mix scalar properties");
    result=ObjectAnimator.ofFloat(null,a.text("propertyXName"),a.text("propertyYName"),PathData.parse(a.text("pathData")));
   }else{
    if(a.has("propertyXName")||a.has("propertyYName"))throw new IllegalArgumentException("Path properties require pathData");
    boolean from=a.has("valueFrom"),to=a.has("valueTo");if(!from&&!to)throw new UnsupportedOperationException("Animator XML without scalar values");
    String firstName=from?"valueFrom":"valueTo";for(String key:new String[]{"valueFrom","valueTo"}){BinaryXml.Value value=a.raw(key);if(type==4&&value!=null&&value.type>=28&&value.type<=31)throw new UnsupportedOperationException("Color animator evaluation is not implemented");}
    if(tag.equals("objectAnimator")){ObjectAnimator object=new ObjectAnimator();object.setPropertyName(a.text("propertyName"));result=object;}else result=new ValueAnimator();
    if(type==1){int first=integerValue(a,firstName);if(from&&to)result.setIntValues(first,integerValue(a,"valueTo"));else result.setIntValues(first);}else{float first=number(a,firstName);if(from&&to)result.setFloatValues(first,number(a,"valueTo"));else result.setFloatValues(first);}
   }
   result.setDuration(a.integer("duration",300));result.setStartDelay(a.integer("startOffset",a.integer("startDelay",0)));result.setRepeatCount(a.integer("repeatCount",0));result.setRepeatMode(a.integer("repeatMode",1));if(a.has("interpolator"))result.setInterpolator(AnimationUtils.loadInterpolator(context,a.reference("interpolator",0)));
   while(p.next()!=XmlPullParser.END_DOCUMENT){if(p.getEventType()==XmlPullParser.END_TAG&&p.getDepth()==start)break;if(p.getEventType()==XmlPullParser.START_TAG)throw new UnsupportedOperationException("Animator keyframes/propertyValuesHolder are not implemented");}return result;
  }
  StateListAnimator stateList(int id)throws Exception{
   try(CompiledXmlParser p=open(id)){if(!p.getNamespace().isEmpty()||!p.getName().equals("selector"))throw new IllegalArgumentException("State animator selector required");attributes(p,"");StateListAnimator result=new StateListAnimator();
    while(p.next()!=XmlPullParser.END_DOCUMENT){if(p.getEventType()!=XmlPullParser.START_TAG)continue;limit(1);if(!p.getNamespace().isEmpty()||!p.getName().equals("item")||p.getDepth()!=2)throw new IllegalArgumentException("State animator item required");XmlAttributes a=new XmlAttributes(context,p);ArrayList<Integer> states=new ArrayList<Integer>();
     for(BinaryXml.Attribute attr:p.shirazNode().attributes){if(BinaryXml.ANDROID.equals(attr.namespace)&&attr.name.equals("animation"))continue;if(attr.resourceId==0||attr.value.type!=18)throw new IllegalArgumentException("State animator requires boolean states");states.add(attr.value.data==0?-attr.resourceId:attr.resourceId);}
     Animator animation=a.has("animation")?load(a.reference("animation",0),2):null;
     while(p.next()!=XmlPullParser.END_DOCUMENT){if(p.getEventType()==XmlPullParser.END_TAG&&p.getDepth()==2)break;if(p.getEventType()==XmlPullParser.START_TAG){if(animation!=null)throw new IllegalArgumentException("Duplicate state animation");animation=animation(p,2);}}
     if(animation==null)throw new IllegalArgumentException("Missing state animation");int[] spec=new int[states.size()];for(int i=0;i<spec.length;i++)spec[i]=states.get(i);result.addState(spec,animation);
    }return result;
   }
  }
 }
 public static Animator loadAnimator(Context context,int id){try{return new Loader(context).load(id,0);}catch(Exception e){throw new Resources.NotFoundException("Cannot load animator #"+Integer.toHexString(id),e);}}
 public static StateListAnimator loadStateListAnimator(Context context,int id){try{return new Loader(context).stateList(id);}catch(Exception e){throw new Resources.NotFoundException("Cannot load state animator #"+Integer.toHexString(id),e);}}
}
