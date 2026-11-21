// SPDX-License-Identifier: GPL-3.0-only
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;

/** Exercises dispatch without an activity window or a native presentation surface. */
public final class PendingIntentServiceSmoke {
    private static final class RecordingContext extends ContextWrapper {
        int activities, broadcasts, services, foreground;
        Intent delivered;
        RecordingContext() { super(null); }
        @Override public String getPackageName() { return "org.shiraz.pending"; }
        @Override public Context getApplicationContext() { return this; }
        @Override public void startActivity(Intent intent) { activities++; delivered = intent; }
        @Override public void sendBroadcast(Intent intent) { broadcasts++; delivered = intent; }
        @Override public ComponentName startService(Intent intent) {
            services++; delivered = intent; return intent.getComponent();
        }
        @Override public ComponentName startForegroundService(Intent intent) {
            foreground++; delivered = intent; return intent.getComponent();
        }
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        RecordingContext context = new RecordingContext();
        Intent intent = new Intent("work").setComponent(new ComponentName(context.getPackageName(), "Worker"));
        int immutable = PendingIntent.FLAG_IMMUTABLE;
        PendingIntent activity = PendingIntent.getActivity(context, 1, intent, immutable);
        PendingIntent broadcast = PendingIntent.getBroadcast(context, 1, intent, immutable);
        PendingIntent service = PendingIntent.getService(context, 1, intent, immutable);
        PendingIntent foreground = PendingIntent.getForegroundService(context, 1, intent, immutable);
        check(activity != service && service != foreground && broadcast != service, "operation identities collide");
        service.send(); foreground.send(); activity.send(); broadcast.send();
        check(context.services == 1 && context.foreground == 1 && context.activities == 1 && context.broadcasts == 1, "wrong dispatch");
        check(PendingIntent.getService(context, 1, intent, immutable | PendingIntent.FLAG_NO_CREATE) == service, "lookup lost token");
        Intent updated = new Intent(intent).putExtra("value", "updated");
        check(PendingIntent.getService(context, 1, updated, immutable | PendingIntent.FLAG_UPDATE_CURRENT) == service, "update replaced token");
        service.shirazSend(new Intent().putExtra("value", "injected"));
        check("updated".equals(context.delivered.getStringExtra("value")), "immutable update/fill semantics");
        PendingIntent replacement = PendingIntent.getService(context, 1, intent, immutable | PendingIntent.FLAG_CANCEL_CURRENT);
        try { service.send(); throw new AssertionError("canceled token sent"); } catch (PendingIntent.CanceledException expected) {}
        foreground.send(); activity.send(); replacement.send();
        check(context.foreground == 2 && context.activities == 2, "cancel affected another operation");
        PendingIntent once = PendingIntent.getForegroundService(context, 2, intent, immutable | PendingIntent.FLAG_ONE_SHOT);
        once.send();
        try { once.send(); throw new AssertionError("one-shot delivered twice"); } catch (PendingIntent.CanceledException expected) {}
        PendingIntent mutable = PendingIntent.getService(context, 3, intent, PendingIntent.FLAG_MUTABLE);
        mutable.shirazSend(new Intent().putExtra("value", "filled"));
        check("filled".equals(context.delivered.getStringExtra("value")), "mutable extras lost");
        try {
            PendingIntent.getService(context, 4, new Intent("implicit"), immutable);
            throw new AssertionError("unsupported implicit service accepted");
        } catch (UnsupportedOperationException expected) {}
        int before = context.activities;
        Intent implicit = new Intent("org.shiraz.OPEN").putExtra("value", "original");
        PendingIntent deferred = PendingIntent.getActivity(context, 5, implicit, immutable);
        check(context.activities == before, "token creation launched activity");
        implicit.setAction("changed").putExtra("value", "changed");
        deferred.shirazSend(new Intent().putExtra("value", "injected"));
        check(context.activities == before + 1, "implicit activity not dispatched");
        check("org.shiraz.OPEN".equals(context.delivered.getAction()) && context.delivered.getComponent() == null,
                "implicit activity must resolve at dispatch");
        check("original".equals(context.delivered.getStringExtra("value")), "immutable snapshot changed");
        System.out.println("PENDING_INTENT_SERVICE_DISPATCH_OK");
    }
}
