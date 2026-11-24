// SPDX-License-Identifier: GPL-3.0-only
import android.app.AlarmManager;
import android.os.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class AlarmListenerSmoke {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void pump(long millis) throws Exception {
        long end = System.nanoTime() + millis * 1000000L;
        do { Looper.getMainLooper().shirazDispatchDue(100); Thread.sleep(2); } while (System.nanoTime() < end);
    }
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        final AlarmManager alarms = new AlarmManager();
        HandlerThread worker = new HandlerThread("alarm-listener");worker.start();
        final Handler target = new Handler(worker.getLooper());
        final Thread main = Thread.currentThread();final int[] count = {0};
        AlarmManager.OnAlarmListener listener = new AlarmManager.OnAlarmListener(){public void onAlarm(){check(Thread.currentThread()==main,"default callback thread");count[0]++;}};
        try {
            alarms.setExact(AlarmManager.ELAPSED_REALTIME,SystemClock.elapsedRealtime()+100,"first",listener,null);
            pump(20);check(count[0]==0,"early listener delivery");pump(140);check(count[0]==1,"listener delivery");
            alarms.set(AlarmManager.RTC,0,"old",listener,null);
            alarms.setWindow(AlarmManager.RTC,System.currentTimeMillis()+200,50,"replacement",listener,null);
            pump(30);check(count[0]==1,"replaced listener delivered");alarms.cancel(listener);pump(220);check(count[0]==1,"canceled listener delivered");
            final CountDownLatch fired=new CountDownLatch(1);final boolean[] correct={false};
            alarms.setExact(AlarmManager.RTC,0,"worker",new AlarmManager.OnAlarmListener(){public void onAlarm(){correct[0]=Looper.myLooper()==target.getLooper();fired.countDown();}},target);
            check(fired.await(3,TimeUnit.SECONDS)&&correct[0],"selected callback handler");
            worker.quit();worker.join(3000);check(!worker.isAlive(),"worker quit");
            alarms.set(AlarmManager.RTC,0,"preserved",listener,null);
            try{alarms.setExact(AlarmManager.RTC,0,"rejected replacement",listener,target);throw new AssertionError("stopped handler accepted");}catch(IllegalStateException expected){}
            pump(20);check(count[0]==2,"failed replacement lost existing alarm");
            final AlarmManager.OnAlarmListener[] repeat={null};
            repeat[0]=new AlarmManager.OnAlarmListener(){public void onAlarm(){count[0]++;if(count[0]==3)alarms.set(AlarmManager.RTC,0,"self",repeat[0],null);}};
            alarms.set(AlarmManager.RTC,0,"self",repeat[0],null);pump(20);check(count[0]==4,"callback re-registration");
            alarms.set(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()-10,"wakeup-in-session",listener,null);pump(20);check(count[0]==5,"wakeup listener alarm missing");
            try{alarms.set(99,0,"invalid",listener,null);throw new AssertionError("invalid alarm type accepted");}catch(IllegalArgumentException expected){}
            try{alarms.setWindow(AlarmManager.RTC,0,-1,"invalid",listener,null);throw new AssertionError("negative window accepted");}catch(IllegalArgumentException expected){}
            AlarmManager.OnAlarmListener[] bounded=new AlarmManager.OnAlarmListener[256];
            for(int i=0;i<bounded.length;i++){bounded[i]=new AlarmManager.OnAlarmListener(){public void onAlarm(){throw new AssertionError("future bounded alarm delivered");}};alarms.set(AlarmManager.ELAPSED_REALTIME,Long.MAX_VALUE,"bounded",bounded[i],null);}
            try{alarms.set(AlarmManager.RTC,0,"overflow",listener,null);throw new AssertionError("listener limit bypassed");}catch(IllegalStateException expected){}
            alarms.set(AlarmManager.ELAPSED_REALTIME,Long.MAX_VALUE,"replace at capacity",bounded[0],null);
            for(AlarmManager.OnAlarmListener entry:bounded)alarms.cancel(entry);
            alarms.setExact(AlarmManager.ELAPSED_REALTIME,Long.MAX_VALUE,"distant",listener,null);pump(10);check(count[0]==5,"overflow delivered alarm");
            alarms.set(AlarmManager.RTC,0,"close",listener,null);alarms.shirazClose();pump(20);check(count[0]==5,"closed callback delivered");
            try{alarms.set(AlarmManager.RTC,0,"closed",listener,null);throw new AssertionError("closed manager accepted");}catch(IllegalStateException expected){}
            System.out.println("ALARM_LISTENER_OK");
        } finally {alarms.shirazClose();worker.quit();worker.join(3000);Looper.shirazShutdownMain();}
    }
}
