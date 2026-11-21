// SPDX-License-Identifier: GPL-3.0-only
import android.content.ComponentName;
import android.content.Intent;
import android.os.Parcel;
import shiraz.runtime.ServiceBrokerClient;
public final class ServiceBrokerBindSmoke {
 public static void main(String[] args)throws Exception{
  try(ServiceBrokerClient broker=new ServiceBrokerClient(Integer.parseInt(args[0]))){
   try{
    ServiceBrokerClient.Connection missing=broker.bind(new Intent().setComponent(new ComponentName("org.shiraz.servicefixture","org.shiraz.servicefixture.Missing")));
    missing.close();throw new AssertionError("Missing class binding succeeded");
   }catch(android.os.RemoteException|java.io.IOException|SecurityException expected){System.out.println("ART_BROKER_START_FAILURE_REJECTED");}
   try(ServiceBrokerClient.Connection service=broker.bind(new Intent().setComponent(new ComponentName("org.shiraz.servicefixture","org.shiraz.servicefixture.Worker")))){
   if(service.binder==null)throw new AssertionError("Null binding");
   Parcel request=Parcel.obtain(),reply=Parcel.obtain();
   try{
    request.writeInterfaceToken("shiraz.fixture.service");request.writeInt(41);
    if(!service.binder.transact(1,request,reply,0))throw new AssertionError("Unhandled call");
    reply.readException();if(reply.readInt()!=42)throw new AssertionError("Wrong target result");
    if(!"org.shiraz.servicefixture".equals(reply.readString()))throw new AssertionError("Wrong target package");
    reply.enforceNoDataAvail();
   }finally{request.recycle();reply.recycle();}
  }
  }
  System.out.println("ART_CROSS_PACKAGE_BINDER_OK");
 }
}
