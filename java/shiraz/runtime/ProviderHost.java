// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.ContentProvider;
import android.content.Context;
import android.content.pm.ProviderInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
/** Instantiates manifest providers in the application process before Application.onCreate, ordered by initOrder. */
final class ProviderHost {
 private static final class Entry { String name,authority,prefix;int order; }
 private ProviderHost(){}
 static List<ContentProvider> attach(Context context,Properties metadata){
  int count=Integer.parseInt(metadata.getProperty("provider.count","0"));
  if(count<0||count>256)throw new IllegalArgumentException("Provider declaration count");
  List<Entry> entries=new ArrayList<Entry>();
  for(int i=0;i<count;i++){
   String prefix="provider."+i+".";
   String name=metadata.getProperty(prefix+"name");
   if(name==null||"false".equals(metadata.getProperty(prefix+"enabled")))continue;
   String process=metadata.getProperty(prefix+"process");
   if(process==null)process=context.getPackageName();else if(process.startsWith(":"))process=context.getPackageName()+process;
   String current=android.app.Application.getProcessName();if(current==null)current=context.getPackageName();
   if(!process.equals(current))continue;
   Entry entry=new Entry();entry.name=name;entry.prefix=prefix;entry.authority=metadata.getProperty(prefix+"authorities");
   String order=metadata.getProperty(prefix+"initOrder");
   try{entry.order=order==null?0:Integer.parseInt(order);}catch(NumberFormatException error){entry.order=0;}
   entries.add(entry);
  }
  Collections.sort(entries,new Comparator<Entry>(){public int compare(Entry a,Entry b){return b.order<a.order?-1:b.order==a.order?0:1;}});
  List<ContentProvider> providers=new ArrayList<ContentProvider>();
  for(Entry entry:entries){
   try{
    ContentProvider provider=Class.forName(entry.name).asSubclass(ContentProvider.class).newInstance();
    ProviderInfo info=context.getPackageManager().getProviderInfo(
      new android.content.ComponentName(context.getPackageName(),entry.name),
      android.content.pm.PackageManager.GET_META_DATA);
    provider.attachInfo(context,info);
    providers.add(provider);
   }catch(ReflectiveOperationException | android.content.pm.PackageManager.NameNotFoundException error){
    throw new IllegalStateException("Cannot instantiate provider "+entry.name,error);
   }
  }
  return providers;
 }
}
