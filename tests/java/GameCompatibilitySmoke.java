// SPDX-License-Identifier: GPL-3.0-only
import android.os.CountDownTimer;
import android.os.Looper;
import android.content.res.Configuration;
public class GameCompatibilitySmoke {
    public static void main(String[] args) throws Exception {
  if(android.os.Debug.waitingForDebugger())throw new AssertionError("Unexpected debugger wait");
  if(android.content.pm.ActivityInfo.class.getField("SCREEN_ORIENTATION_UNSPECIFIED").getInt(null)!=-1)throw new AssertionError("JNI orientation constant");
        Configuration source=new Configuration(); source.mcc=310; source.mnc=Configuration.MNC_ZERO;
        Configuration copy=new Configuration(source);
        if (!copy.equals(source)||copy.hashCode()!=source.hashCode()) throw new AssertionError("copy");
        Configuration delta=new Configuration(); delta.mnc=260;
        if (copy.updateFrom(delta)!=2||copy.mcc!=310||copy.mnc!=260||copy.equals(source)) throw new AssertionError("merge");
        if (copy.updateFrom(new Configuration())!=0||copy.mnc!=260) throw new AssertionError("undefined");
        if (!android.webkit.WebSettings.getDefaultUserAgent(null).equals("Shiraz-WebView")) throw new AssertionError("provider");
        Looper.prepare();
        final int[] received={0};
        android.os.Messenger messenger=new android.os.Messenger(new android.os.Handler(){public void handleMessage(android.os.Message message){received[0]=message.what;}});
        android.os.Bundle extra=new android.os.Bundle();extra.putParcelable("messenger",messenger);
        if(!messenger.equals(extra.shirazBroadcastCopy().getParcelable("messenger")))throw new AssertionError("Local messenger copy");
        android.os.Parcel parcel=android.os.Parcel.obtain();
        messenger.writeToParcel(parcel,0);parcel.setDataPosition(0);
        android.os.Messenger restored=android.os.Messenger.CREATOR.createFromParcel(parcel);parcel.recycle();
        android.os.Message message=android.os.Message.obtain();message.what=42;restored.send(message);pump(10);
        if(received[0]!=42||!messenger.equals(restored))throw new AssertionError("Local messenger parcel and delivery");

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
