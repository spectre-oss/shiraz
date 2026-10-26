// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.processfixture;
public final class ProcessActivity extends android.app.Activity {
 @Override public void onCreate(android.os.Bundle state){super.onCreate(state);
  try{android.os.Process.class.getMethod("setArgV0",String.class).invoke(null,"org.shiraz.processfixture:worker");}catch(Exception e){throw new AssertionError(e);}
  try{java.io.FileInputStream in=new java.io.FileInputStream("/proc/self/cmdline");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();int b;while((b=in.read())>0)out.write(b);in.close();if(!"org.shiraz.processfixture:worker".equals(out.toString("UTF-8")))throw new AssertionError("process argv identity");System.out.println("PROCESS_ARGV_IDENTITY_OK");}catch(java.io.IOException e){throw new AssertionError(e);}
  Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){public void uncaughtException(Thread t,Throwable error){System.err.println("PROCESS_APP_HANDLER_RETURNED");}});
  Thread worker=new Thread(new Runnable(){public void run(){for(;;)try{Thread.sleep(1000);}catch(InterruptedException ignored){}}});worker.setDaemon(false);worker.start();System.out.println("PROCESS_WORKER_STARTED");
  android.view.View view=new android.view.View(this);view.setBackgroundColor(0xff123456);setContentView(view);
  if("1".equals(System.getenv("SHIRAZ_PROCESS_TEST_FAIL")))new android.os.Handler().post(new Runnable(){public void run(){throw new IllegalStateException("PROCESS_CALLBACK_FAILURE");}});
 }
 @Override public void onDestroy(){System.out.println("PROCESS_ACTIVITY_DESTROYED");super.onDestroy();}
}
