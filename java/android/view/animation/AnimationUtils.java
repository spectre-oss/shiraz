// SPDX-License-Identifier: GPL-3.0-only
package android.view.animation;
import android.content.Context;
import android.content.res.*;
import org.xmlpull.v1.XmlPullParser;
public final class AnimationUtils {
 private AnimationUtils(){}
 public static Animation loadAnimation(Context context,int id){
  try(XmlResourceParser parser=context.getResources().getAnimation(id)){
   int event;do{event=parser.next();}while(event!=XmlPullParser.START_TAG&&event!=XmlPullParser.END_DOCUMENT);
   if(event!=XmlPullParser.START_TAG)throw new IllegalArgumentException("Missing animation root");
   Animation result=readAnimation(context,parser,0,new int[]{0});
   while((event=parser.next())!=XmlPullParser.END_DOCUMENT)if(event==XmlPullParser.START_TAG)throw new IllegalArgumentException("Multiple animation roots");return result;
  }catch(Exception failure){throw new Resources.NotFoundException("Cannot load animation #"+Integer.toHexString(id),failure);}
 }
 private static Animation readAnimation(Context context,XmlResourceParser parser,int depth,int[] nodes)throws Exception{
  if(depth>64||++nodes[0]>1024)throw new IllegalArgumentException("Animation XML limit");Animation result;
  switch(parser.getName()){
   case "set":result=new AnimationSet(context,parser);break;
   case "alpha":result=new AlphaAnimation(context,parser);break;
   case "translate":result=new TranslateAnimation(context,parser);break;
   case "scale":result=new ScaleAnimation(context,parser);break;
   case "rotate":result=new RotateAnimation(context,parser);break;
   default:throw new UnsupportedOperationException("Animation XML element: "+parser.getName());
  }
  int event;while((event=parser.next())!=XmlPullParser.END_TAG){if(event==XmlPullParser.END_DOCUMENT)throw new IllegalArgumentException("Unclosed animation");if(event==XmlPullParser.START_TAG){if(!(result instanceof AnimationSet))throw new IllegalArgumentException("Only animation sets have children");((AnimationSet)result).addAnimation(readAnimation(context,parser,depth+1,nodes));}else if(event==XmlPullParser.TEXT&&!parser.getText().trim().isEmpty())throw new IllegalArgumentException("Animation text content");}
  return result;
 }
 public static long currentAnimationTimeMillis(){return android.view.Choreographer.shirazAnimationTimeMillis();}
 public static Interpolator loadInterpolator(Context context,int id){return loadInterpolator(context.getResources(),context.getTheme(),id);}
 public static Interpolator loadInterpolator(Resources resources,Resources.Theme theme,int id){
  try(XmlResourceParser parser=resources.getAnimation(id)){
   int event;do{event=parser.next();}while(event!=XmlPullParser.START_TAG&&event!=XmlPullParser.END_DOCUMENT);
   if(event!=XmlPullParser.START_TAG)throw new IllegalArgumentException("Missing interpolator root");
   Interpolator result;String name=parser.getName();
   switch(name){
    case "pathInterpolator":result=new PathInterpolator(resources,theme,parser);break;
    case "linearInterpolator":result=new LinearInterpolator();break;
    case "accelerateDecelerateInterpolator":result=new AccelerateDecelerateInterpolator();break;
    case "bounceInterpolator":result=new BounceInterpolator();break;
    case "accelerateInterpolator":result=new AccelerateInterpolator(resources,theme,parser);break;
    case "decelerateInterpolator":result=new DecelerateInterpolator(resources,theme,parser);break;
    case "cycleInterpolator":result=new CycleInterpolator(resources,theme,parser);break;
    case "anticipateInterpolator":result=new AnticipateInterpolator(resources,theme,parser);break;
    case "overshootInterpolator":result=new OvershootInterpolator(resources,theme,parser);break;
    case "anticipateOvershootInterpolator":result=new AnticipateOvershootInterpolator(resources,theme,parser);break;
    default:throw new UnsupportedOperationException("Interpolator XML element: "+name);
   }
   while((event=parser.next())!=XmlPullParser.END_DOCUMENT){if(event==XmlPullParser.START_TAG)throw new IllegalArgumentException("Interpolator cannot contain child elements");if(event==XmlPullParser.TEXT&&!parser.getText().trim().isEmpty())throw new IllegalArgumentException("Interpolator cannot contain text");}
   return result;
  }catch(Exception failure){throw new Resources.NotFoundException("Cannot load interpolator #"+Integer.toHexString(id),failure);}
 }
}
