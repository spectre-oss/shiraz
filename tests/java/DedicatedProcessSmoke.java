// SPDX-License-Identifier: GPL-3.0-only
import shiraz.runtime.DedicatedProcess;
public final class DedicatedProcessSmoke {
 public static void main(final String[] args){
  Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){public void uncaughtException(Thread thread,Throwable error){System.err.println("APP_HANDLER_RETURNED");}});
  Thread worker=new Thread(new Runnable(){public void run(){for(;;)try{Thread.sleep(1000);}catch(InterruptedException ignored){}}});worker.setDaemon(false);worker.start();
  DedicatedProcess.run(new DedicatedProcess.Task(){public void run(){System.out.println("BODY_RAN");if(!args[0].equals("success")&&!args[0].equals("cleanup"))throw new IllegalStateException("PRIMARY_FAILURE");}},new DedicatedProcess.Task(){public void run(){System.out.println("CLEANUP_RAN");if(args[0].equals("both")||args[0].equals("cleanup"))throw new IllegalArgumentException("CLEANUP_FAILURE");}});
  throw new AssertionError("Process boundary returned");
 }
}
