// SPDX-License-Identifier: GPL-3.0-only
import shiraz.runtime.NativePower;
public final class NativePowerSmoke {
    private static boolean inspectLogind;
    private static void inhibitors(int expected) throws Exception {
        if(!inspectLogind)return;
        Process query=new ProcessBuilder("busctl","--system","call","org.freedesktop.login1","/org/freedesktop/login1","org.freedesktop.login1.Manager","ListInhibitors").redirectErrorStream(true).start();
        if(!query.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)){query.destroyForcibly();throw new AssertionError("logind query timed out");}
        String output;
        try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(query.getInputStream(),"UTF-8"))){output=reader.readLine();}
        if(query.exitValue()!=0||output==null)throw new AssertionError("logind query failed: "+output);
        String pid=new java.io.File("/proc/self").getCanonicalFile().getName();
        java.util.regex.Matcher entries=java.util.regex.Pattern.compile("\"idle\" \"Shiraz\" \"Android background work\" \"block\" [0-9]+ "+pid+"(?: |$)").matcher(output);
        int actual=0;while(entries.find())actual++;
        if(actual!=expected)throw new AssertionError("Expected "+expected+" process inhibitors, got "+output);
    }
    public static void main(String[] args) throws Exception {
        inspectLogind=(args.length==0&&!shiraz.runtime.HostOs.darwin())||(args.length==1&&args[0].equals("logind"));
        inhibitors(0);
        if(args.length==1&&args[0].equals("missing-bus")) {
            android.content.Context context=new android.content.ContextWrapper(null){public int checkSelfPermission(String permission){return "android.permission.WAKE_LOCK".equals(permission)?0:-1;}};
            android.os.PowerManager manager=android.os.PowerManager.shirazForContext(context);
            android.os.PowerManager.WakeLock lock=manager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"missing-bus");
            try {
                for(int attempt=0;attempt<2;attempt++) {
                    try{lock.acquire();throw new AssertionError("Missing system bus accepted");}
                    catch(IllegalStateException expected){if(!expected.getMessage().contains("System bus unavailable"))throw expected;}
                    if(lock.isHeld())throw new AssertionError("Failed acquisition left app lock held");
                }
            }finally{manager.shirazClose();}
            System.out.println("NATIVE_POWER_MISSING_BUS_ROLLBACK_OK");return;
        }
        if(args.length==1&&args[0].equals("hold")) {
            NativePower held=NativePower.acquireIdleSleep();
            if(!held.isActive())throw new AssertionError("Crash probe assertion inactive");
            System.out.println("NATIVE_POWER_HELD");System.out.flush();
            System.in.read();
            held.close();return;
        }
        NativePower first=NativePower.acquireIdleSleep();
        inhibitors(1);
        try(NativePower second=NativePower.acquireIdleSleep()) {
            inhibitors(2);
            if(!first.isActive()||!second.isActive())throw new AssertionError("Host assertions inactive");
            first.close();first.close();
            inhibitors(1);
            if(first.isActive()||!second.isActive())throw new AssertionError("Independent power ownership lost");
        } finally { first.close(); }
        inhibitors(0);
        try(NativePower replacement=NativePower.acquireIdleSleep()) {
            if(!replacement.isActive())throw new AssertionError("Power reacquisition failed");
        }
        android.os.Looper.prepareMainLooper();
        android.content.Context context=new android.content.ContextWrapper(null){public int checkSelfPermission(String permission){return "android.permission.WAKE_LOCK".equals(permission)?0:-1;}};
        android.os.PowerManager manager=android.os.PowerManager.shirazForContext(context);
        android.os.PowerManager.WakeLock lock=manager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"native-app");
        java.lang.reflect.Field ownerField=lock.getClass().getDeclaredField("nativeOwner");ownerField.setAccessible(true);
        try{
            lock.acquire();NativePower owner=(NativePower)ownerField.get(lock);
            inhibitors(1);
            if(owner==null||!owner.isActive())throw new AssertionError("App lock lacks native ownership");
            lock.acquire();lock.release();if(!owner.isActive())throw new AssertionError("Nested release lost native ownership");
            inhibitors(1);
            lock.release();if(owner.isActive())throw new AssertionError("Final release retained native ownership");
            inhibitors(0);
            lock.acquire(0);owner=(NativePower)ownerField.get(lock);android.os.Looper.myLooper().shirazDispatchDue(256);
            if(lock.isHeld()||owner.isActive())throw new AssertionError("Timeout retained native assertion");lock.release();
            inhibitors(0);
            lock.acquire();owner=(NativePower)ownerField.get(lock);manager.shirazClose();manager.shirazClose();
            if(lock.isHeld()||owner.isActive())throw new AssertionError("Session teardown retained leaked lock");
            inhibitors(0);
            try{lock.acquire();throw new AssertionError("Closed power session reacquired");}catch(IllegalStateException expected){}
            android.os.PowerManager denied=android.os.PowerManager.shirazForContext(new android.content.ContextWrapper(null){public int checkSelfPermission(String permission){return -1;}});
            android.os.PowerManager.WakeLock rejected=denied.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"denied-native");
            try{rejected.acquire();throw new AssertionError("Denied native lock acquired");}catch(SecurityException expected){}
            if(ownerField.get(rejected)!=null||rejected.isHeld())throw new AssertionError("Denied native acquisition retained ownership");denied.shirazClose();
        }finally{manager.shirazClose();android.os.Looper.shirazShutdownMain();}
        System.out.println("NATIVE_POWER_ASSERTIONS_OK");
    }
}
