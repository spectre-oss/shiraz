// SPDX-License-Identifier: GPL-3.0-only
import android.os.CountDownTimer;
import android.os.Looper;
import android.content.res.Configuration;
public class GameCompatibilitySmoke {
    public static void main(String[] args) throws Exception {
        Configuration source=new Configuration(); source.mcc=310; source.mnc=Configuration.MNC_ZERO;
        Configuration copy=new Configuration(source);
        if (!copy.equals(source)||copy.hashCode()!=source.hashCode()) throw new AssertionError("copy");
        Configuration delta=new Configuration(); delta.mnc=260;
        if (copy.updateFrom(delta)!=2||copy.mcc!=310||copy.mnc!=260||copy.equals(source)) throw new AssertionError("merge");
        if (copy.updateFrom(new Configuration())!=0||copy.mnc!=260) throw new AssertionError("undefined");
        if (!android.webkit.WebSettings.getDefaultUserAgent(null).equals("Shiraz-WebView")) throw new AssertionError("provider");
        Looper.prepare();
        final int[] ticks={0}, finishes={0};
        CountDownTimer cancelled=new CountDownTimer(30,5) {
            public void onTick(long left) { ticks[0]++; cancel(); }
            public void onFinish() { throw new AssertionError("cancelled timer finished"); }
        };
        cancelled.start(); pump(80);
        if (ticks[0]!=1) throw new AssertionError("cancel from callback");
        CountDownTimer timer=new CountDownTimer(50,5) {
            public void onTick(long left) { if (left<=0) throw new AssertionError("tick"); }
            public void onFinish() { finishes[0]++; }
        };
        timer.start(); timer.cancel(); timer.start(); pump(150);
        if (finishes[0]!=1) throw new AssertionError("restart/finish count");
        System.out.println("GAME_COMPATIBILITY_OK");
    }
    private static void pump(long milliseconds) throws Exception {
        long end=System.nanoTime()+milliseconds*1000000;
        while(System.nanoTime()<end) { Looper.myLooper().shirazDispatchDue(100); Thread.sleep(2); }
    }
}
