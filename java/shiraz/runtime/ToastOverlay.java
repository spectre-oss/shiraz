// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.graphics.Canvas;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import java.util.ArrayDeque;
import java.util.Iterator;

/** One process's bounded FIFO toast queue, composed into the existing SDL frame. */
public final class ToastOverlay {
    private static final class Entry {
        final Object token;
        View view;
        long duration;
        Entry(Object token, View view, long duration) { this.token = token; this.view = view; this.duration = duration; }
    }
    private static final ArrayDeque<Entry> pending = new ArrayDeque<Entry>();
    private static Entry active;
    private static long deadline, revision;
    private ToastOverlay() { }
    private static void main() { if (Looper.myLooper() == null || Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Toast overlay must run on the main Looper"); }
    private static void advance() {
        long now = SystemClock.uptimeMillis();
        if (active != null && now >= deadline) { active = null; ++revision; }
        if (active == null && !pending.isEmpty()) { active = pending.removeFirst(); deadline = now + active.duration; ++revision; }
    }
    public static void show(Object token, View view, long duration) {
        main(); advance();
        if (active != null && active.token == token) { active.view = view; active.duration = duration; deadline = SystemClock.uptimeMillis() + duration; ++revision; return; }
        for (Entry entry : pending) if (entry.token == token) { entry.view = view; entry.duration = duration; return; }
        if (pending.size() >= 32) throw new java.util.concurrent.RejectedExecutionException("Toast queue capacity exceeded");
        pending.addLast(new Entry(token, view, duration)); advance();
    }
    public static void cancel(Object token) {
        main();
        if (active != null && active.token == token) { active = null; ++revision; }
        for (Iterator<Entry> entries = pending.iterator(); entries.hasNext();) if (entries.next().token == token) entries.remove();
        advance();
    }
    public static void invalidate(Object token) { main(); if (active != null && active.token == token) ++revision; }
    public static long revision() { main(); advance(); if (active != null && active.view.shirazIsInvalidated()) ++revision; return revision; }
    public static void draw(Canvas canvas, int width, int height) {
        main();
        if (active != null) { active.view.layout(0, 0, width, height); active.view.shirazDrawWithAlpha(canvas); }
    }
    public static void clear() { main(); pending.clear(); active = null; ++revision; }
}
