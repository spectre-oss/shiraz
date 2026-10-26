// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import java.util.Properties;
import java.io.*;
public final class ServiceMetadataSmoke {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static void missing(shiraz.runtime.ActivityLaunchResolver r,Intent i){try{r.resolve(i);throw new AssertionError("unavailable activity accepted");}catch(ActivityNotFoundException expected){}}
 private static void unsupported(shiraz.runtime.ActivityLaunchResolver r,Intent i){try{r.resolve(i);throw new AssertionError("unsupported launch accepted");}catch(UnsupportedOperationException expected){}}
 private static void launchResolution(Context context,Properties source){
  Properties p=new Properties();p.putAll(source);p.setProperty("activity.count","3");String pkg=context.getPackageName();
  for(int i=0;i<3;i++){
   String prefix="activity."+i+".";p.setProperty(prefix+"name",pkg+".Screen"+i);p.setProperty(prefix+"enabled",i==2?"false":"true");p.setProperty(prefix+"process",pkg);p.setProperty(prefix+"launch_mode",i==0?"2":"0");p.setProperty(prefix+"filter.count","1");
   String f=prefix+"filter.0.";p.setProperty(f+"action.count","1");p.setProperty(f+"action.0","sample.OPEN");p.setProperty(f+"category.count","1");p.setProperty(f+"category.0",Intent.CATEGORY_DEFAULT);p.setProperty(f+"priority",i==0?"10":"0");
  }
  shiraz.runtime.ActivityLaunchResolver r=new shiraz.runtime.ActivityLaunchResolver(pkg,new shiraz.runtime.HostPackageManager(p,context));
  Intent implicit=new Intent("sample.OPEN").putExtra("count",7);shiraz.runtime.ActivityLaunchResolver.Request resolved=r.resolve(implicit);implicit.putExtra("count",9);
  check(resolved.launchMode==2&&resolved.intent().getComponent().getClassName().equals(pkg+".Screen0")&&implicit.getComponent()==null,"default implicit resolution and input isolation");
  Intent returned=resolved.intent();returned.putExtra("count",10);check(resolved.intent().getIntExtra("count",0)==7&&resolved.bytes()>0,"request owns bounded intent snapshot");
  Intent explicit=new Intent().setComponent(new ComponentName(pkg,pkg+".Screen1"));check(r.resolve(explicit.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)).launchMode==1,"singleTop launch flag");
  missing(r,new Intent("absent"));missing(r,new Intent().setComponent(new ComponentName("external","external.Main")));missing(r,new Intent().setComponent(new ComponentName(pkg,pkg+".Screen2")));missing(r,new Intent(explicit).setPackage("external").setFlags(0));
  check((r.resolve(new Intent(explicit).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)).intent().getFlags()&Intent.FLAG_ACTIVITY_CLEAR_TOP)!=0,"supported CLEAR_TOP retained for activity stack");
  shiraz.runtime.ActivityLaunchQueue queue=new shiraz.runtime.ActivityLaunchQueue(r);Intent queued=new Intent(explicit).putExtra("order",1);queue.enqueue(queued);queued.putExtra("order",2);queue.enqueue(queued);check(queue.poll().intent().getIntExtra("order",0)==1&&queue.poll().intent().getIntExtra("order",0)==2&&queue.poll()==null,"launch queue FIFO snapshots");
  for(int i=0;i<128;i++)queue.enqueue(explicit);try{queue.enqueue(explicit);throw new AssertionError("launch queue budget bypassed");}catch(IllegalStateException expected){}check(queue.poll()!=null,"overflow retains accepted launches");queue.enqueue(explicit);
  final shiraz.runtime.ActivityLaunchQueue workerQueue=queue;final Throwable[] wrongThread={null};Thread worker=new Thread(new Runnable(){public void run(){try{workerQueue.poll();}catch(Throwable error){wrongThread[0]=error;}}});worker.start();try{worker.join();}catch(InterruptedException error){throw new AssertionError(error);}check(wrongThread[0] instanceof IllegalStateException&&queue.poll()!=null,"foreign dispatch rejected without consuming");
  queue.close();queue.close();try{queue.enqueue(explicit);throw new AssertionError("closed launch queue accepted intent");}catch(IllegalStateException expected){}
  shiraz.runtime.ActivityLaunchQueue payloadQueue=new shiraz.runtime.ActivityLaunchQueue(r);Intent large=new Intent(explicit).putExtra("payload",new byte[1024*1024]);int accepted=0;
  try{for(;accepted<20;accepted++)payloadQueue.enqueue(large);throw new AssertionError("aggregate launch bytes unbounded");}catch(IllegalStateException expected){}check(accepted>0&&accepted<20,"payload budget before count cap");payloadQueue.poll();payloadQueue.enqueue(large);payloadQueue.close();
  p.setProperty("activity.1.filter.0.priority","10");unsupported(new shiraz.runtime.ActivityLaunchResolver(pkg,new shiraz.runtime.HostPackageManager(p,context)),new Intent("sample.OPEN"));
  p.setProperty("activity.1.process",":separate");unsupported(new shiraz.runtime.ActivityLaunchResolver(pkg,new shiraz.runtime.HostPackageManager(p,context)),new Intent(explicit).setFlags(0));
  p.setProperty("activity.1.process",pkg);p.setProperty("activity.1.launch_mode","3");unsupported(new shiraz.runtime.ActivityLaunchResolver(pkg,new shiraz.runtime.HostPackageManager(p,context)),new Intent(explicit).setFlags(0));
 }
 private static void receiverProviderQueries(Context context,Properties source)throws Exception{
  Properties p=new Properties();p.putAll(source);String pkg=context.getPackageName();
  p.setProperty("application.metadata.count","1");p.setProperty("application.metadata.0.name","app.flag");p.setProperty("application.metadata.0.kind","boolean");p.setProperty("application.metadata.0.value","true");
  p.setProperty("receiver.count","1");p.setProperty("receiver.0.name",pkg+".Alarm");p.setProperty("receiver.0.enabled","false");
  p.setProperty("provider.count","1");p.setProperty("provider.0.name",pkg+".Documents");p.setProperty("provider.0.authorities","fixture.documents");
  p.setProperty("provider.0.filter.count","1");p.setProperty("provider.0.filter.0.action.count","1");p.setProperty("provider.0.filter.0.action.0","fixture.DOCUMENTS");
  p.setProperty("provider.0.filter.0.mime.count","1");p.setProperty("provider.0.filter.0.mime.0","text/plain");
  PackageManager pm=new shiraz.runtime.HostPackageManager(p,context);ComponentName receiver=new ComponentName(pkg,pkg+".Alarm");
  check(pm.getApplicationInfo(pkg,PackageManager.GET_META_DATA).metaData.getBoolean("app.flag")&&pm.getApplicationInfo(pkg,0).metaData==null,"Application metadata flag");
  try{pm.getReceiverInfo(receiver,0);throw new AssertionError("Disabled receiver returned");}catch(PackageManager.NameNotFoundException expected){}
  check(!pm.getReceiverInfo(receiver,PackageManager.MATCH_DISABLED_COMPONENTS).enabled,"Receiver disabled metadata");
  Intent intent=new Intent("fixture.DOCUMENTS").setType("text/plain");
  check(pm.queryIntentContentProviders(intent,0).size()==1&&pm.queryIntentContentProviders(intent,0).get(0).providerInfo.authority.equals("fixture.documents"),"Provider MIME/action resolution");
  check(pm.queryIntentContentProviders(new Intent("fixture.DOCUMENTS").setType("image/png"),0).isEmpty(),"Provider MIME mismatch rejected");
  check(pm.queryIntentContentProviders(new Intent(intent).addCategory("unknown"),0).isEmpty(),"Provider category mismatch rejected");
 }
 public static void main(String[] args)throws Exception {
  final Resources resources=Resources.shirazOpen(args[0],96,160);Properties metadata=new Properties();try(Reader reader=new InputStreamReader(new FileInputStream(args[1]),"UTF-8")){metadata.load(reader);}
  Context context=new ContextWrapper(null){private final Resources.Theme theme=resources.newTheme();private final android.view.LayoutInflater inflater=new shiraz.runtime.HostLayoutInflater(this);public Resources.Theme getTheme(){return theme;}public Object getSystemService(String name){if(LAYOUT_INFLATER_SERVICE.equals(name))return inflater;throw new UnsupportedOperationException(name);}private final PackageManager pm=new shiraz.runtime.HostPackageManager(metadata,this);public String getPackageName(){return "org.shiraz.fixture";}public Resources getResources(){return resources;}public PackageManager getPackageManager(){return pm;}};
  try{receiverProviderQueries(context,metadata);launchResolution(context,metadata);org.shiraz.fixture.ServiceMetadataFixture.check(context);org.shiraz.fixture.ActivityInfoFixture.check(context);org.shiraz.fixture.StyledAttributesFixture.check(context);}finally{resources.shirazClose();}
  System.out.println("SERVICE_METADATA_TYPES_AND_QUERY_FLAGS_OK");
 }
}
