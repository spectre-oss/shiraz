// SPDX-License-Identifier: GPL-3.0-only
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.*;
import android.os.*;

public final class AlarmDeliverySmoke {
    private static final class Receiver extends ContextWrapper {
        int count;
        final Thread main = Thread.currentThread();
        Receiver() { super(null); }
        @Override public String getPackageName() { return "org.shiraz.alarms"; }
        @Override public Context getApplicationContext() { return this; }
        @Override public void sendBroadcast(Intent intent) {
            check(Thread.currentThread() == main, "alarm delivered off main thread"); count++;
        }
        @Override public ComponentName startService(Intent intent) {
            sendBroadcast(intent); return intent.getComponent();
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void pump(long millis) throws Exception {
        long end = System.nanoTime() + millis * 1000000L;
        do { Looper.getMainLooper().shirazDispatchDue(100); Thread.sleep(2); } while (System.nanoTime() < end);
    }
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        AlarmManager alarms = new AlarmManager(); Receiver receiver = new Receiver();
        PendingIntent token = PendingIntent.getBroadcast(receiver, 0, new Intent("tick"), PendingIntent.FLAG_IMMUTABLE);
        try {
            alarms.setExact(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 100, token);
            pump(20); check(receiver.count == 0, "early delivery");
            pump(160); check(receiver.count == 1, "missing delivery");
            alarms.set(AlarmManager.RTC, System.currentTimeMillis() + 100, token);
            alarms.set(AlarmManager.RTC, System.currentTimeMillis() + 300, token);
            pump(160); check(receiver.count == 1, "old replaced alarm delivered");
            alarms.cancel(token); pump(200); check(receiver.count == 1, "canceled alarm delivered");
            alarms.set(AlarmManager.RTC, System.currentTimeMillis() - 100, token);
            pump(20); check(receiver.count == 2, "past RTC alarm missing");
            alarms.setRepeating(AlarmManager.ELAPSED_REALTIME, 0, 100, token);
            Looper.getMainLooper().shirazDispatchDue(1);
            check(receiver.count == 3, "missed intervals not collapsed");
            pump(220); check(receiver.count >= 5 && receiver.count <= 6, "repeat cadence");
            alarms.cancel(token);
            PendingIntent service = PendingIntent.getService(receiver, 1,
                new Intent().setComponent(new ComponentName(receiver.getPackageName(), "Worker")), PendingIntent.FLAG_IMMUTABLE);
            int before = receiver.count;
            alarms.set(AlarmManager.ELAPSED_REALTIME, 0, service);
            pump(20); check(receiver.count == before + 1, "service alarm not dispatched");
            token.cancel(); alarms.setRepeating(AlarmManager.RTC, 0, 10, token);
            pump(40); check(receiver.count == before + 1, "canceled pending intent delivered");
            int beforeWakeup = receiver.count;
            alarms.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() - 10, service);
            pump(20);
            check(receiver.count == beforeWakeup + 1, "RTC_WAKEUP in-session alarm missing");
            alarms.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() - 10, service);
            pump(20);
            check(receiver.count == beforeWakeup + 2, "ELAPSED_REALTIME_WAKEUP in-session alarm missing");
            try { alarms.set(99, 0, service); throw new AssertionError("invalid alarm type accepted"); }
            catch (IllegalArgumentException expected) {}
            try { alarms.setRepeating(AlarmManager.RTC, 0, 0, service); throw new AssertionError("zero interval"); }
            catch (IllegalArgumentException expected) {}
            int beforeOverflow = receiver.count;
            alarms.set(AlarmManager.ELAPSED_REALTIME, Long.MAX_VALUE, service);
            pump(10); check(receiver.count == beforeOverflow, "overflow delivered future alarm");
            alarms.set(AlarmManager.ELAPSED_REALTIME, 0, service);
            alarms.shirazClose(); pump(20);
            check(receiver.count == beforeOverflow, "delivery after session close");
            try { alarms.set(AlarmManager.RTC, 0, service); throw new AssertionError("closed manager accepted alarm"); }
            catch (IllegalStateException expected) {}
            System.out.println("ALARM_DELIVERY_OK");
        } finally { alarms.shirazClose(); Looper.shirazShutdownMain(); }
    }
}
