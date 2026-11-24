// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Session-owned main-queue alarms. Wakeup alarm types ({@link #RTC_WAKEUP} and
 * {@link #ELAPSED_REALTIME_WAKEUP}) execute on their respective active-session clock while Shiraz
 * is running; waking a suspended host OS and persisting alarms across process restarts are not
 * implemented.
 */
public class AlarmManager {
    public static final int RTC_WAKEUP = 0, RTC = 1, ELAPSED_REALTIME_WAKEUP = 2, ELAPSED_REALTIME = 3;
    public static final long INTERVAL_FIFTEEN_MINUTES = 900000L, INTERVAL_HALF_HOUR = 1800000L, INTERVAL_HOUR = 3600000L, INTERVAL_HALF_DAY = 43200000L, INTERVAL_DAY = 86400000L;

    private final Map<PendingIntent, Alarm> pending = new IdentityHashMap<PendingIntent, Alarm>();
    public interface OnAlarmListener { void onAlarm(); }
    private final Map<OnAlarmListener, ListenerAlarm> listeners = new IdentityHashMap<OnAlarmListener, ListenerAlarm>();
    private android.os.Handler handler;
    private boolean closed;
    private static boolean loggedWakeupLimit;

    private static boolean isRtcType(int type) { return type == RTC || type == RTC_WAKEUP; }

    private static void noteWakeupBestEffort(int type) {
        if ((type == RTC_WAKEUP || type == ELAPSED_REALTIME_WAKEUP) && !loggedWakeupLimit) {
            loggedWakeupLimit = true;
            System.err.println(
                "SHIRAZ_UNSUPPORTED AlarmManager.WAKEUP: host sleep wakeup is not implemented; "
                    + "delivering alarm on active-session clock");
        }
    }

    private final class Alarm implements Runnable {
        final int type;
        final PendingIntent operation;
        final long interval;
        long trigger;
        Alarm(int type, long trigger, long interval, PendingIntent operation) {
            this.type = type; this.trigger = trigger; this.interval = interval; this.operation = operation;
        }
        long now() { return isRtcType(type) ? System.currentTimeMillis() : android.os.SystemClock.elapsedRealtime(); }
        boolean enqueue() {
            long now = now();
            // Recheck clock changes and resume without placing unbounded delays on the queue.
            long remaining = trigger - now;
            long delay = trigger <= now ? 0 : remaining < 0 ? 1000 : Math.min(1000, remaining);
            return handler.postDelayed(this, Math.max(0, delay));
        }
        public void run() {
            synchronized (AlarmManager.this) {
                if (closed || pending.get(operation) != this) return;
                long now = now();
                if (trigger > now) {
                    if (!enqueue()) pending.remove(operation);
                    return;
                }
                if (interval == 0) pending.remove(operation);
                else {
                    // Collapse missed intervals into one delivery; retain the original cadence.
                    long remainder = (now - trigger) % interval;
                    long delay = interval - remainder;
                    if (now > Long.MAX_VALUE - delay) pending.remove(operation);
                    else { trigger = now + delay; if (!enqueue()) pending.remove(operation); }
                }
                try { operation.send(); }
                catch (PendingIntent.CanceledException canceled) {
                    if (pending.get(operation) == this) pending.remove(operation);
                    handler.removeCallbacks(this);
                }
            }
        }
    }

    private final class ListenerAlarm implements Runnable {
        final int type;
        final long trigger;
        final OnAlarmListener listener;
        final android.os.Handler target;
        ListenerAlarm(int type, long trigger, OnAlarmListener listener, android.os.Handler target) {
            this.type = type; this.trigger = Math.max(0, trigger); this.listener = listener; this.target = target;
        }
        long now() { return isRtcType(type) ? System.currentTimeMillis() : android.os.SystemClock.elapsedRealtime(); }
        boolean enqueue() {
            long now = now(), remaining = trigger - now;
            return target.postDelayed(this, trigger <= now ? 0 : remaining < 0 ? 1000 : Math.min(1000, remaining));
        }
        public void run() {
            synchronized (AlarmManager.this) {
                if (closed || listeners.get(listener) != this) return;
                if (trigger > now()) {
                    try { if (!enqueue()) listeners.remove(listener); }
                    catch (RuntimeException error) { listeners.remove(listener); throw error; }
                    return;
                }
                listeners.remove(listener);
            }
            listener.onAlarm();
        }
    }

    private synchronized void registerListener(int type, long trigger, OnAlarmListener listener, android.os.Handler target) {
        if (closed) throw new IllegalStateException("Alarm session closed");
        if (listener == null) throw new NullPointerException("listener");
        if (type < RTC_WAKEUP || type > ELAPSED_REALTIME) throw new IllegalArgumentException("Alarm type");
        noteWakeupBestEffort(type);
        if (!listeners.containsKey(listener) && pending.size() + listeners.size() >= 256) throw new IllegalStateException("Alarm limit");
        if (target == null) target = new android.os.Handler(android.os.Looper.getMainLooper());
        ListenerAlarm alarm = new ListenerAlarm(type, trigger, listener, target);
        if (!alarm.enqueue()) throw new IllegalStateException("Alarm callback queue stopped");
        ListenerAlarm old = listeners.put(listener, alarm);
        if (old != null) old.target.removeCallbacks(old);
    }

    public void set(int type, long trigger, String tag, OnAlarmListener listener, android.os.Handler target) { registerListener(type, trigger, listener, target); }
    public void setExact(int type, long trigger, String tag, OnAlarmListener listener, android.os.Handler target) { registerListener(type, trigger, listener, target); }
    public void setWindow(int type, long start, long length, String tag, OnAlarmListener listener, android.os.Handler target) {
        if (length < 0) throw new IllegalArgumentException("Alarm window length");
        registerListener(type, start, listener, target);
    }
    public synchronized void cancel(OnAlarmListener listener) {
        if (listener == null) throw new NullPointerException("listener");
        ListenerAlarm old = listeners.remove(listener);
        if (old != null) old.target.removeCallbacks(old);
    }

    private synchronized void register(int type, long trigger, long interval, PendingIntent operation) {
        if (closed) throw new IllegalStateException("Alarm session closed");
        if (operation == null) throw new NullPointerException("operation");
        if (type < RTC_WAKEUP || type > ELAPSED_REALTIME) throw new IllegalArgumentException("Alarm type");
        noteWakeupBestEffort(type);
        if (!pending.containsKey(operation) && pending.size() + listeners.size() >= 256) throw new IllegalStateException("Alarm limit");
        if (handler == null) handler = new android.os.Handler(android.os.Looper.getMainLooper());
        Alarm alarm = new Alarm(type, Math.max(0, trigger), interval, operation);
        if (!alarm.enqueue()) throw new IllegalStateException("Alarm main queue stopped");
        Alarm old = pending.put(operation, alarm);
        if (old != null) handler.removeCallbacks(old);
    }

    public void set(int type, long trigger, PendingIntent operation) { register(type, trigger, 0, operation); }
    public void setExact(int type, long trigger, PendingIntent operation) { register(type, trigger, 0, operation); }
    public void setAndAllowWhileIdle(int type, long trigger, PendingIntent operation) { register(type, trigger, 0, operation); }
    public void setExactAndAllowWhileIdle(int type, long trigger, PendingIntent operation) { register(type, trigger, 0, operation); }
    public void setWindow(int type, long start, long length, PendingIntent operation) {
        if (length < 0) throw new IllegalArgumentException("Alarm window length");
        register(type, start, 0, operation);
    }
    public void setRepeating(int type, long trigger, long interval, PendingIntent operation) {
        if (interval <= 0) throw new IllegalArgumentException("Alarm interval");
        register(type, trigger, interval, operation);
    }
    public void setInexactRepeating(int type, long trigger, long interval, PendingIntent operation) { setRepeating(type, trigger, interval, operation); }
    public void setAlarmClock(AlarmClockInfo info, PendingIntent operation) { setExact(RTC_WAKEUP, info.getTriggerTime(), operation); }
    public synchronized void cancel(PendingIntent operation) {
        if (operation == null) return;
        Alarm old = pending.remove(operation);
        if (old != null) handler.removeCallbacks(old);
    }
    public boolean canScheduleExactAlarms() { return true; }
    public synchronized void shirazClose() {
        closed = true;
        if (handler != null) for (Alarm alarm : pending.values()) handler.removeCallbacks(alarm);
        pending.clear();
        for (ListenerAlarm alarm : listeners.values()) alarm.target.removeCallbacks(alarm);
        listeners.clear();
    }

    public static final class AlarmClockInfo {
        private final long triggerTime;
        private final PendingIntent showIntent;
        public AlarmClockInfo(long triggerTime, PendingIntent showIntent) { this.triggerTime = triggerTime; this.showIntent = showIntent; }
        public long getTriggerTime() { return triggerTime; }
        public PendingIntent getShowIntent() { return showIntent; }
    }
}
