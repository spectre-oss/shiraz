// SPDX-License-Identifier: GPL-3.0-only
import java.io.*;
import java.net.*;
public final class SocketPeerCloseSmoke {
 static void socketProtection(Socket socket)throws Exception{
  if(!System.getProperty("java.vm.name", "").equals("Dalvik"))return;
  if(!"1".equals(System.getenv("SHIRAZ_TEST_DARWIN_SOCKET")))return;
  Class<?> libcore;try{libcore=Class.forName("libcore.io.Libcore");}catch(ClassNotFoundException openjdk){return;}
  java.lang.reflect.Field impl=Socket.class.getDeclaredField("impl");impl.setAccessible(true);Object value=impl.get(socket);
  java.lang.reflect.Field fd=SocketImpl.class.getDeclaredField("fd");fd.setAccessible(true);
  Object os=libcore.getField("os").get(null);
  int enabled=(Integer)Class.forName("libcore.io.Os").getMethod("getsockoptInt",FileDescriptor.class,int.class,int.class).invoke(os,fd.get(value),0xffff,0x1022);
  if(enabled!=1)throw new AssertionError("Darwin socket lacks SO_NOSIGPIPE");
  System.out.println("SOCKET_NOSIGPIPE_VERIFIED");
 }
 static void brokenWrite(Socket socket)throws Exception{
  socket.setSoTimeout(3000);
  if(socket.getInputStream().read()!=-1)throw new AssertionError("Expected peer EOF");
  try{for(int i=0;i<100;i++){socket.getOutputStream().write(new byte[8192]);socket.getOutputStream().flush();Thread.sleep(5);}}
  catch(IOException expected){return;}
  throw new AssertionError("Closed peer never rejected writes");
 }
 static void exercise()throws Exception{
  for(int i=0;i<8;i++){
   try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));Socket client=new Socket("127.0.0.1",listener.getLocalPort());Socket accepted=listener.accept()){
    socketProtection(client);socketProtection(accepted);client.close();brokenWrite(accepted);
   }
   try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));Socket client=new Socket("127.0.0.1",listener.getLocalPort());Socket accepted=listener.accept()){
    accepted.close();brokenWrite(client);
   }
  }

 }
 public static void main(String[] args)throws Exception{
  System.out.println("SOCKET_RUNTIME "+System.getProperty("java.vm.name")+" "+System.getProperty("os.name"));
  final java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
  Thread worker=new Thread(new Runnable(){public void run(){try{exercise();}catch(Throwable error){failure.set(error);}}});
  worker.start();worker.join(10000);if(worker.isAlive())throw new AssertionError("Socket test timeout");
  if(failure.get()!=null)throw new AssertionError(failure.get());
  System.out.println("SOCKET_PEER_CLOSE_OK");
 }
}
