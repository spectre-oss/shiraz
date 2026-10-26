package android.app;

import java.util.IdentityHashMap;
import java.util.Map;

/** Accepts alarm registrations and keeps them in memory; alarms are never delivered. */
public class AlarmManager {
    public static final int RTC_WAKEUP = 0, RTC = 1, ELAPSED_REALTIME_WAKEUP = 2, ELAPSED_REALTIME = 3;
    public static final long INTERVAL_FIFTEEN_MINUTES = 900000L, INTERVAL_HALF_HOUR = 1800000L, INTERVAL_HOUR = 3600000L, INTERVAL_HALF_DAY = 43200000L, INTERVAL_DAY = 86400000L;

    private final Map<PendingIntent, Long> pending = new IdentityHashMap<PendingIntent, Long>();

    private synchronized void register(long triggerAtMillis, PendingIntent operation) {
        if (operation == null) throw new NullPointerException("operation");
        pending.put(operation, Long.valueOf(triggerAtMillis));
    }

    public void set(int type, long triggerAtMillis, PendingIntent operation) { register(triggerAtMillis, operation); }
    public void setExact(int type, long triggerAtMillis, PendingIntent operation) { register(triggerAtMillis, operation); }
    public void setAndAllowWhileIdle(int type, long triggerAtMillis, PendingIntent operation) { register(triggerAtMillis, operation); }
    public void setExactAndAllowWhileIdle(int type, long triggerAtMillis, PendingIntent operation) { register(triggerAtMillis, operation); }
    public void setWindow(int type, long windowStartMillis, long windowLengthMillis, PendingIntent operation) { register(windowStartMillis, operation); }
    public void setRepeating(int type, long triggerAtMillis, long intervalMillis, PendingIntent operation) { register(triggerAtMillis, operation); }
    public void setInexactRepeating(int type, long triggerAtMillis, long intervalMillis, PendingIntent operation) { register(triggerAtMillis, operation); }
    public void setAlarmClock(AlarmClockInfo info, PendingIntent operation) { register(info.getTriggerTime(), operation); }
    public synchronized void cancel(PendingIntent operation) { pending.remove(operation); }
    public boolean canScheduleExactAlarms() { return true; }

    public static final class AlarmClockInfo {
        private final long triggerTime;
        private final PendingIntent showIntent;
        public AlarmClockInfo(long triggerTime, PendingIntent showIntent) { this.triggerTime = triggerTime; this.showIntent = showIntent; }
        public long getTriggerTime() { return triggerTime; }
        public PendingIntent getShowIntent() { return showIntent; }
    }
}
