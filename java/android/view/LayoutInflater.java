// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import org.xmlpull.v1.*;
import java.lang.reflect.*;
import java.util.*;

/** Compiled layout inflation with factories, reflective View construction and bounded includes. */
public abstract class LayoutInflater {
 public interface Factory {View onCreateView(String name,Context context,AttributeSet attrs);}
 public interface Factory2 extends Factory {View onCreateView(View parent,String name,Context context,AttributeSet attrs);}
 public interface Filter {boolean onLoadClass(Class clazz);}
 private final Context context;private Factory mFactory;private Factory2 mFactory2;private boolean factorySet;private Filter filter;private Factory2 privateFactory;private int privateFactories;
 private final HashMap<String,Constructor<? extends View>> constructors=new HashMap<String,Constructor<? extends View>>();
 private int active,nodes,treeDepth;private long encodedBytes;private final ArrayList<Integer> includes=new ArrayList<Integer>();
 protected LayoutInflater(Context context){if(context==null)throw new NullPointerException("context");this.context=context;}
 protected LayoutInflater(LayoutInflater original,Context context){this(context);mFactory=original.mFactory;mFactory2=original.mFactory2;filter=original.filter;privateFactory=original.privateFactory;privateFactories=original.privateFactories;}
 public abstract LayoutInflater cloneInContext(Context context);
 public Context getContext(){return context;}
 public static LayoutInflater from(Context context){Object service=context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);if(!(service instanceof LayoutInflater))throw new IllegalStateException("No layout inflater service");return (LayoutInflater)service;}
 public Factory getFactory(){return mFactory;}public Factory2 getFactory2(){return mFactory2;}
 private static View invoke(Factory factory,Factory2 second,View parent,String name,Context context,AttributeSet attrs){return second!=null?second.onCreateView(parent,name,context,attrs):factory.onCreateView(name,context,attrs);}
 private static Factory2 combine(final Factory first,final Factory2 first2,final Factory old,final Factory2 old2){return new Factory2(){public View onCreateView(String n,Context c,AttributeSet a){View v=first.onCreateView(n,c,a);return v!=null?v:old.onCreateView(n,c,a);}public View onCreateView(View p,String n,Context c,AttributeSet a){View v=invoke(first,first2,p,n,c,a);return v!=null?v:invoke(old,old2,p,n,c,a);}};}
 private void factory(final Factory value,boolean second){if(value==null)throw new NullPointerException("factory");if(factorySet)throw new IllegalStateException("A factory is already set");factorySet=true;Factory old=mFactory;Factory2 old2=mFactory2;
  if(old==null){mFactory=value;mFactory2=second?(Factory2)value:null;}else{Factory2 chain=combine(value,second?(Factory2)value:null,old,old2);mFactory=chain;mFactory2=second||old2!=null?chain:null;}}
 public void setPrivateFactory(Factory2 value){if(value==null)throw new NullPointerException("private factory");if(privateFactory==value)return;if(privateFactories>=32)throw new IllegalStateException("Private factory limit");privateFactory=privateFactory==null?value:combine(value,value,privateFactory,privateFactory);privateFactories++;}
 public void setFactory(Factory value){factory(value,false);}public void setFactory2(Factory2 value){factory(value,true);}
 public void setFilter(Filter value){filter=value;}public Filter getFilter(){return filter;}
 public final View createView(String name,String prefix,AttributeSet attrs)throws ClassNotFoundException{return createView(context,name,prefix,attrs);}
 public final View createView(Context viewContext,String name,String prefix,AttributeSet attrs)throws ClassNotFoundException{
  if(viewContext==null)throw new NullPointerException("viewContext");
  String full=prefix==null?name:prefix+name;
  try{Constructor<? extends View> constructor=constructors.get(full);Class<? extends View> type=constructor==null?viewContext.getClassLoader().loadClass(full).asSubclass(View.class):constructor.getDeclaringClass();if(filter!=null&&!filter.onLoadClass(type))throw new InflateException("View class rejected: "+full);if(constructor==null){constructor=type.getConstructor(Context.class,AttributeSet.class);if(constructors.size()<256)constructors.put(full,constructor);}View view=constructor.newInstance(viewContext,attrs);if(view instanceof ViewStub)((ViewStub)view).setLayoutInflater(cloneInContext(viewContext));return view;}
  catch(ClassNotFoundException missing){throw missing;}catch(InvocationTargetException failure){throw new InflateException("Creating "+full+" at "+(attrs==null?"<no attributes>":attrs.getPositionDescription()),failure.getCause());}catch(ReflectiveOperationException failure){throw new InflateException("Cannot construct view "+full,failure);}catch(ClassCastException failure){throw new InflateException("Not a View: "+full,failure);}
 }
 protected View onCreateView(String name,AttributeSet attrs)throws ClassNotFoundException{return onCreateView(context,name,attrs);}
 protected View onCreateView(Context viewContext,String name,AttributeSet attrs)throws ClassNotFoundException{for(String prefix:new String[]{"android.widget.","android.view.","android.webkit."})try{return createView(viewContext,name,prefix,attrs);}catch(ClassNotFoundException missing){}throw new ClassNotFoundException(name);}
 protected View onCreateView(View parent,String name,AttributeSet attrs)throws ClassNotFoundException{return onCreateView(name,attrs);}
 protected View onCreateView(View parent,Context viewContext,String name,AttributeSet attrs)throws ClassNotFoundException{return onCreateView(viewContext,name,attrs);}
 private View create(View parent,XmlResourceParser parser){
  String name=parser.getName();if(name.equals("view")){name=parser.getClassAttribute();if(name==null)name=parser.getAttributeValue("","class");if(name==null)throw new InflateException("view requires class");}
  Context viewContext=context;
  android.content.res.TypedArray themeAttrs=context.obtainStyledAttributes(parser,new int[]{0x01010000});
  try{int themeResId=themeAttrs.getResourceId(0,0);if(themeResId!=0)viewContext=new ContextThemeWrapper(context,themeResId);}finally{themeAttrs.recycle();}
  View result=mFactory==null?null:invoke(mFactory,mFactory2,parent,name,viewContext,parser);if(result==null&&privateFactory!=null)result=privateFactory.onCreateView(parent,name,viewContext,parser);if(result!=null)return result;
  try{return name.indexOf('.')>=0?createView(viewContext,name,null,parser):onCreateView(parent,viewContext,name,parser);}catch(ClassNotFoundException failure){throw new InflateException("Unknown view "+name+" at "+parser.getPositionDescription(),failure);}
 }
 public View inflate(int resource,ViewGroup root){return inflate(resource,root,root!=null);}
 public View inflate(int resource,ViewGroup root,boolean attach){if(includes.size()>=32||includes.contains(resource))throw new InflateException("Layout include cycle/depth");includes.add(resource);try(XmlResourceParser parser=context.getResources().getLayout(resource)){return inflate(parser,root,attach);}finally{includes.remove(includes.size()-1);}}
 public View inflate(XmlPullParser source,ViewGroup root){return inflate(source,root,root!=null);}
 public View inflate(XmlPullParser source,ViewGroup root,boolean attach){
  if(!(source instanceof XmlResourceParser))throw new UnsupportedOperationException("Only compiled resource layout parsers are implemented");XmlResourceParser parser=(XmlResourceParser)source;if(active>=64)throw new InflateException("Inflation recursion limit");if(active++==0){nodes=0;treeDepth=0;encodedBytes=0;}
  try{if(parser instanceof shiraz.runtime.CompiledXmlParser){encodedBytes+=((shiraz.runtime.CompiledXmlParser)parser).shirazEncodedSize();if(encodedBytes>16L*1024*1024)throw new InflateException("Inflation XML byte budget");}int type=parser.getEventType();while(type!=XmlPullParser.START_TAG&&type!=XmlPullParser.END_DOCUMENT)type=parser.next();if(type!=XmlPullParser.START_TAG)throw new InflateException("Layout has no root element");if(parser.getName().equals("merge")){if(root==null||!attach)throw new InflateException("merge requires an attached parent");children(parser,root,0);return root;}
   View result=create(root,parser);if(++nodes>4096)throw new InflateException("Inflation view budget");ViewGroup.LayoutParams params=root==null?null:root.generateLayoutParams(parser);if(params!=null)result.setLayoutParams(params);children(parser,result,0);result.onFinishInflate();if(root!=null&&attach){root.addView(result,params);return root;}return result;
  }catch(XmlPullParserException|java.io.IOException failure){throw new InflateException("Layout parsing at "+parser.getPositionDescription(),failure);}finally{active--;}
 }
 private void children(XmlResourceParser parser,View parent,int depth)throws XmlPullParserException,java.io.IOException{
  if(treeDepth>=64)throw new InflateException("Layout tree depth limit");treeDepth++;try{int start=parser.getDepth(),type;
  while((type=parser.next())!=XmlPullParser.END_DOCUMENT&&(type!=XmlPullParser.END_TAG||parser.getDepth()>start))if(type==XmlPullParser.START_TAG){
   if(parser.getName().equals("requestFocus")){parent.requestFocus();skip(parser);continue;}
   if(!(parent instanceof ViewGroup))throw new InflateException("Child tag in non-container "+parent.getClass().getName());ViewGroup group=(ViewGroup)parent;
   if(parser.getName().equals("include")){include(parser,group);skip(parser);continue;}
   if(parser.getName().equals("merge"))throw new InflateException("merge is only legal at the layout root");
   View child=create(group,parser);if(++nodes>4096)throw new InflateException("Inflation view budget");ViewGroup.LayoutParams params=group.generateLayoutParams(parser);child.setLayoutParams(params);children(parser,child,depth+1);child.onFinishInflate();group.addView(child,params);
  }
 }finally{treeDepth--;}
 }
 private void include(XmlResourceParser attrs,ViewGroup parent)throws XmlPullParserException,java.io.IOException{
  String android="http://schemas.android.com/apk/res/android";
  int id=attrs.getAttributeResourceValue("","layout",0);if(id==0)throw new InflateException("include requires layout reference");if(includes.size()>=32||includes.contains(id))throw new InflateException("Layout include cycle/depth");includes.add(id);
  try(XmlResourceParser parser=context.getResources().getLayout(id)){
   int type=parser.getEventType();while(type!=XmlPullParser.START_TAG&&type!=XmlPullParser.END_DOCUMENT)type=parser.next();if(type!=XmlPullParser.START_TAG)throw new InflateException("Included layout has no root");
   if(parser.getName().equals("merge")){if(parser instanceof shiraz.runtime.CompiledXmlParser){encodedBytes+=((shiraz.runtime.CompiledXmlParser)parser).shirazEncodedSize();if(encodedBytes>16L*1024*1024)throw new InflateException("Inflation XML byte budget");}children(parser,parent,0);}
   else{View child=inflate(parser,parent,false);ViewGroup.LayoutParams params=child.getLayoutParams();if(attrs.getAttributeValue(android,"layout_width")!=null&&attrs.getAttributeValue(android,"layout_height")!=null)params=parent.generateLayoutParams(attrs);int override=attrs.getAttributeResourceValue(android,"id",View.NO_ID);if(override!=View.NO_ID)child.setId(override);if(attrs.getAttributeValue(android,"visibility")!=null)child.setVisibility(View.shirazVisibilityFromXml(attrs.getAttributeIntValue(android,"visibility",0)));parent.addView(child,params);}
  }finally{includes.remove(includes.size()-1);}
 }
 private static void skip(XmlResourceParser parser)throws XmlPullParserException,java.io.IOException{int depth=parser.getDepth(),type;while((type=parser.next())!=XmlPullParser.END_DOCUMENT&&(type!=XmlPullParser.END_TAG||parser.getDepth()>depth)){} }
}
