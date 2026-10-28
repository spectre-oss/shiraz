// SPDX-License-Identifier: GPL-3.0-only
public class AltSignalStackSmoke {
 static{System.loadLibrary("shiraz-test-alt-stack");}
 private static native boolean replace();
 private static native void cleanup();
 private static native boolean attachWithStack();
 public static void main(String[] args)throws Exception{
  if(!attachWithStack())throw new AssertionError("ART replaced/freed an embedding thread's stack");
  for(int i=0;i<8;i++){
   final boolean[] installed={false};
   Thread thread=new Thread(new Runnable(){public void run(){installed[0]=replace();}},"alt-stack-owner");
   thread.start();thread.join();
   try{if(!installed[0])throw new AssertionError("replacement allocation");}finally{cleanup();}
  }
  System.out.println("ART_ALT_SIGNAL_STACK_OWNERSHIP_OK");
 }
}
