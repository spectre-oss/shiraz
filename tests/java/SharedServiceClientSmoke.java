// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.os.*;
import java.io.*;
import shiraz.runtime.ServiceBrokerClient;
public final class SharedServiceClientSmoke {
 public static void main(String[] args)throws Exception{
  if(args.length==2){
   try(ServiceBrokerClient broker=new ServiceBrokerClient(Integer.parseInt(args[0]))){
    Intent target=new Intent("org.shiraz.servicefixture.BIND").setPackage("org.shiraz.servicefixture");
    if(args[1].equals("stop-once")){if(!broker.stop(target))throw new AssertionError("Retained service absent");System.out.println("STOPPED_AFTER_CALLER_EXIT");}
    else if(args[1].equals("start-once")){broker.start(target,false);System.out.println("STARTED_BEFORE_CALLER_EXIT");}
    else if(args[1].startsWith("redelivery-")){broker.start(new Intent(args[1].substring(11)).setClassName("org.shiraz.servicefixture","org.shiraz.servicefixture.Redelivery").putExtra("payload","original-extra"),false);System.out.println("REDELIVERY_QUEUED");}
    else if(args[1].equals("intent-first")||args[1].equals("intent-second")){broker.start(new Intent(args[1].equals("intent-first")?"first":"second").setClassName("org.shiraz.servicefixture","org.shiraz.servicefixture.IntentQueue"),false);System.out.println("INTENT_QUEUED");}
    else if(args[1].equals("sticky-once")){broker.start(new Intent().setClassName("org.shiraz.servicefixture","org.shiraz.servicefixture.Sticky"),false);System.out.println("STICKY_STARTED");}
    else if(args[1].equals("sticky-stop")){if(!broker.stop(new Intent().setClassName("org.shiraz.servicefixture","org.shiraz.servicefixture.Sticky")))throw new AssertionError("Sticky absent");System.out.println("STICKY_STOPPED");}
    else if(args[1].equals("sticky-absent")){if(broker.stop(new Intent().setClassName("org.shiraz.servicefixture","org.shiraz.servicefixture.Sticky")))throw new AssertionError("Absent sticky stop succeeded");System.out.println("STICKY_ABSENT");}
    else throw new AssertionError("Unknown one-shot mode");
   }return;
  }
  try(ServiceBrokerClient broker=new ServiceBrokerClient(Integer.parseInt(args[0]));
      ServiceBrokerClient.Connection connection=broker.bind(new Intent("org.shiraz.servicefixture.BIND").setPackage("org.shiraz.servicefixture").putExtra("caller-extra",android.os.Process.myUid()))){
   if(connection.binder==null)throw new AssertionError("Null service");
   ServiceBrokerClient.Resolution discovered=broker.resolveAction("org.shiraz.servicefixture","org.shiraz.servicefixture.BIND");
   if(!discovered.component.equals("org.shiraz.servicefixture.Worker")||discovered.uid!=connection.resolution.uid)throw new AssertionError("Action discovery identity");
   System.out.println("ACTION_DISCOVERY_OK");
   System.out.println("READY");System.out.flush();
   ServiceBrokerClient.Connection heldLeaf=null;
   BufferedReader commands=new BufferedReader(new InputStreamReader(System.in));String command;
   while((command=commands.readLine())!=null&&!command.equals("close")){
    if(command.equals("retired")){
     Parcel input=Parcel.obtain(),output=Parcel.obtain();boolean dead=false;
     try{input.writeInterfaceToken("shiraz.fixture.service");connection.binder.transact(3,input,output,0);}
     catch(RemoteException expected){dead=true;}
     finally{input.recycle();output.recycle();}
     if(!dead)throw new AssertionError("Retired service endpoint remained callable");
     boolean denied=false;
     try(ServiceBrokerClient.Connection again=broker.bind(new Intent("org.shiraz.servicefixture.BIND").setPackage("org.shiraz.servicefixture"))){}
     catch(SecurityException expected){denied=true;}
     if(!denied)throw new AssertionError("Retired service accepted new binding");
     System.out.println("RETIRED");System.out.flush();continue;
    }
    if(command.equals("stop")){boolean stopped=broker.stop(new Intent("org.shiraz.servicefixture.BIND").setPackage("org.shiraz.servicefixture"));System.out.println(stopped?"STOP_TRUE":"STOP_FALSE");System.out.flush();continue;}
    if(command.equals("foreground")){broker.start(new Intent("org.shiraz.servicefixture.BIND").setPackage("org.shiraz.servicefixture").putExtra("foreground",true),true);System.out.println("STARTED");System.out.flush();continue;}
    if(command.equals("start")){broker.start(new Intent("org.shiraz.servicefixture.BIND").setPackage("org.shiraz.servicefixture"),false);System.out.println("STARTED");System.out.flush();continue;}
    if(command.equals("stop-self")){
     try(ServiceBrokerClient.Connection again=broker.bind(new Intent("org.shiraz.servicefixture.BIND").setPackage("org.shiraz.servicefixture"))){
      Parcel input=Parcel.obtain(),output=Parcel.obtain();try{input.writeInterfaceToken("shiraz.fixture.service");if(!again.binder.transact(7,input,output,0))throw new AssertionError("Stop-self method");output.readException();if(output.readInt()!=2)throw new AssertionError("Start ID");output.enforceNoDataAvail();}finally{input.recycle();output.recycle();}
     }System.out.println("STOPPED");System.out.flush();continue;
    }
    if(command.equals("hold-leaf")){heldLeaf=broker.bind(new Intent().setComponent(new ComponentName("org.shiraz.backgroundfixture","org.shiraz.backgroundfixture.LeafService")));System.out.println("READY");System.out.flush();continue;}
    if(command.equals("unbind")){connection.close();if(heldLeaf!=null){heldLeaf.close();heldLeaf=null;}System.out.println("UNBOUND");System.out.flush();continue;}
    if(command.equals("rebind")){try(ServiceBrokerClient.Connection again=broker.bind(new Intent("org.shiraz.servicefixture.BIND").setPackage("org.shiraz.servicefixture"))){if(again.binder==null)throw new AssertionError("Rebind failed");}System.out.println("REBOUND");System.out.flush();continue;}
    if(!command.equals("call")&&!command.equals("nested")&&!command.equals("leaf"))throw new AssertionError("Unknown command");
    ServiceBrokerClient.Connection leaf=command.equals("leaf")?broker.bind(new Intent().setComponent(new ComponentName("org.shiraz.backgroundfixture","org.shiraz.backgroundfixture.LeafService"))):null;
    Parcel input=Parcel.obtain(),output=Parcel.obtain();
    try{
     input.writeInterfaceToken(leaf==null?"shiraz.fixture.service":"shiraz.fixture.leaf");if(!(leaf==null?connection.binder:leaf.binder).transact(leaf!=null?1:command.equals("nested")?4:3,input,output,0))throw new AssertionError("Unhandled method");output.readException();
     StringBuilder line=new StringBuilder("RESULT");for(int i=0;i<(command.equals("call")?6:4);i++)line.append(' ').append(output.readInt());output.enforceNoDataAvail();System.out.println(line.toString());System.out.flush();
    }finally{input.recycle();output.recycle();if(leaf!=null)leaf.close();}
   }
   if(heldLeaf!=null)heldLeaf.close();
  }
 }
}
