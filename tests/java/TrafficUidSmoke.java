// SPDX-License-Identifier: GPL-3.0-only
import android.net.TrafficStats;
public final class TrafficUidSmoke {
    static void check(boolean value){if(!value)throw new AssertionError("Traffic thread state");}
    public static void main(String[] args)throws Exception{
        check(TrafficStats.getThreadStatsUid()==-1);TrafficStats.setThreadStatsUid(12345);TrafficStats.setThreadStatsTag(99);
        final Throwable[] failure={null};Thread worker=new Thread(new Runnable(){public void run(){try{check(TrafficStats.getThreadStatsUid()==-1);TrafficStats.setThreadStatsUid(23456);TrafficStats.clearThreadStatsTag();check(TrafficStats.getThreadStatsUid()==23456);TrafficStats.clearThreadStatsUid();check(TrafficStats.getThreadStatsUid()==-1);}catch(Throwable error){failure[0]=error;}}});worker.start();worker.join();if(failure[0]!=null)throw new AssertionError(failure[0]);
        check(TrafficStats.getThreadStatsUid()==12345);TrafficStats.clearThreadStatsUid();check(TrafficStats.getThreadStatsUid()==-1&&TrafficStats.getThreadStatsTag()==99);TrafficStats.setThreadStatsUidSelf();check(TrafficStats.getThreadStatsUid()==android.os.Process.myUid());TrafficStats.clearThreadStatsUid();TrafficStats.clearThreadStatsTag();
        System.out.println("TRAFFIC_UID_OK");
    }
}
