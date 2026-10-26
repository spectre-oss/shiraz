// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.XmlResourceParser;
import org.xmlpull.v1.XmlPullParser;
import shiraz.runtime.XmlAttributes;

/** Resource menu inflation with bounded nesting and explicit unsupported attributes. */
public class MenuInflater {
 private final Context context;
 public MenuInflater(Context context){if(context==null)throw new NullPointerException("context");this.context=context;}
 private static final class Group {int id,category,order,checkable;boolean visible=true,enabled=true;}
 private static final class Budget {int items;}
 public void inflate(int resource,Menu menu){
  if(menu==null)throw new NullPointerException("menu");
  try(XmlResourceParser parser=context.getResources().getLayout(resource)){
   int event;do{event=parser.next();}while(event!=XmlPullParser.START_TAG&&event!=XmlPullParser.END_DOCUMENT);
   if(event!=XmlPullParser.START_TAG||!"menu".equals(parser.getName()))throw new InflateException("Expected menu root");
   attributes(parser,"");read(parser,menu,new Group(),0,new Budget(),false);
  }catch(InflateException e){throw e;}catch(Exception e){throw new InflateException("Inflating menu #"+Integer.toHexString(resource),e);}
 }
 private XmlAttributes attributes(XmlResourceParser p,String allowed){
  for(int i=0;i<p.getAttributeCount();i++)if(!"http://schemas.android.com/apk/res/android".equals(p.getAttributeNamespace(i))||!(" "+allowed+" ").contains(" "+p.getAttributeName(i)+" "))throw new UnsupportedOperationException("Menu attribute "+p.getAttributeName(i));
  XmlAttributes result=new XmlAttributes(context,p);for(String name:allowed.split(" "))result.raw(name);return result;
 }
 private void read(XmlResourceParser p,Menu menu,Group group,int depth,Budget budget,boolean inGroup)throws Exception{
  if(depth>16)throw new InflateException("Menu nesting limit");
  while(true){int event=p.next();if(event==XmlPullParser.END_TAG)return;if(event==XmlPullParser.END_DOCUMENT)throw new InflateException("Unclosed menu");if(event!=XmlPullParser.START_TAG)continue;
   if("group".equals(p.getName())&&!inGroup){XmlAttributes a=attributes(p,"id menuCategory orderInCategory checkableBehavior visible enabled");Group g=new Group();g.id=a.reference("id",0);g.category=a.integer("menuCategory",0);g.order=a.integer("orderInCategory",0);g.checkable=a.integer("checkableBehavior",0);if(g.checkable<0||g.checkable>2)throw new InflateException("Group checkable behavior");g.visible=a.bool("visible",true);g.enabled=a.bool("enabled",true);read(p,menu,g,depth+1,budget,true);}
   else if("item".equals(p.getName())){
    if(++budget.items>4096)throw new InflateException("Menu item limit");
    XmlAttributes a=attributes(p,"id menuCategory orderInCategory title titleCondensed icon checkable checked visible enabled onClick showAsAction");
    int id=a.reference("id",0),order=(a.integer("menuCategory",group.category)&0xffff0000)|(a.integer("orderInCategory",group.order)&65535);
    CharSequence title=a.text("title");MenuItem item=null;
    while(true){event=p.next();if(event==XmlPullParser.END_TAG)break;if(event==XmlPullParser.END_DOCUMENT)throw new InflateException("Unclosed item");if(event!=XmlPullParser.START_TAG)continue;if(item!=null||!"menu".equals(p.getName()))throw new InflateException("Item requires at most one submenu");attributes(p,"");SubMenu sub=menu.addSubMenu(group.id,id,order,title);item=sub.getItem();apply(item,a,group);read(p,sub,new Group(),depth+1,budget,false);}
    if(item==null){item=menu.add(group.id,id,order,title);apply(item,a,group);}
   }else throw new InflateException("Unexpected menu element "+p.getName());
  }
 }
 private void apply(MenuItem item,XmlAttributes a,Group group){
  if(a.has("showAsAction"))item.setShowAsAction(a.integer("showAsAction",0));
  boolean checkable=a.bool("checkable",group.checkable>0);item.setCheckable(checkable);
  boolean checked=a.bool("checked",false);item.setChecked(checked);if(checkable&&group.checkable==2){shiraz.runtime.HostContextMenu.shirazSetExclusive(item,true);if(checked)item.setChecked(true);}
  item.setVisible(a.bool("visible",group.visible)).setEnabled(a.bool("enabled",group.enabled));
  if(a.has("titleCondensed"))item.setTitleCondensed(a.text("titleCondensed"));if(a.has("icon"))item.setIcon(a.drawable("icon"));
  String click=a.text("onClick");if(click!=null){if(context.isRestricted())throw new IllegalStateException("Menu onClick in restricted context");Object current=context;java.util.HashSet<Object> seen=new java.util.HashSet<Object>();while(current instanceof ContextWrapper&&!(current instanceof android.app.Activity)){if(!seen.add(current)||seen.size()>32)throw new InflateException("Context wrapper cycle");Context next=((ContextWrapper)current).getBaseContext();if(next==null)break;current=next;}final Object owner=current;
   final java.lang.reflect.Method method;try{method=owner.getClass().getMethod(click,MenuItem.class);}catch(ReflectiveOperationException e){throw new InflateException("Menu click method "+click,e);}
   item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener(){public boolean onMenuItemClick(MenuItem value){try{Object result=method.invoke(owner,value);return method.getReturnType()==Boolean.TYPE?(Boolean)result:true;}catch(java.lang.reflect.InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof RuntimeException)throw (RuntimeException)cause;if(cause instanceof Error)throw (Error)cause;throw new RuntimeException(cause);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}});
  }
 }
}
