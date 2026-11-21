// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.content.*;
import android.os.*;
import shiraz.runtime.HostServices;
import java.util.Properties;

/** Real service lifecycle on a windowless main queue; no presentation fixture. */
public final class BackgroundServiceSmoke {
    static int starts, destroys;
    static boolean delayed;
    public static final class Worker extends Service {
        public IBinder onBind(Intent intent) { return null; }
        public int onStartCommand(Intent intent, int flags, int id) {
            starts++;
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                public void run() { delayed = true; stopSelf(); }
            }, 40);
            return START_NOT_STICKY;
        }
        public void onDestroy() { destroys++; }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        Looper.prepareMainLooper();
        final HostServices[] host = new HostServices[1];
        Application app = new Application();
        Context context = new ContextWrapper(null) {
            public String getPackageName() { return "org.shiraz.background"; }
            public ClassLoader getClassLoader() { return BackgroundServiceSmoke.class.getClassLoader(); }
            protected HostServices shirazServices() { return host[0]; }
        };
        Properties metadata = new Properties();
        metadata.setProperty("service.count", "1");
        metadata.setProperty("service.0.name", Worker.class.getName());
        host[0] = new HostServices(context, app, metadata);
        try {
            check(!host[0].hasWork(), "empty host retained");
            host[0].start(new Intent().setComponent(new ComponentName(context.getPackageName(), Worker.class.getName())));
            check(host[0].hasWork() && starts == 0, "queued service lifetime");
            Looper.myLooper().shirazLoopWhile(new java.util.function.BooleanSupplier() {
                public boolean getAsBoolean() { return host[0].hasWork(); }
            });
            check(starts == 1 && delayed && destroys == 1, "background lifecycle incomplete");
            check(!host[0].hasWork() && !Looper.myLooper().shirazUsesBlockingLoop(), "host retained after stopSelf");
            final boolean[] delivered = {false};
            new Handler(Looper.getMainLooper()).post(new Runnable() { public void run() { delivered[0] = true; } });
            Looper.myLooper().shirazDispatchDue(16);
            check(delivered[0], "background exit destroyed main queue");
            System.out.println("BACKGROUND_SERVICE_LIFETIME_OK");
        } finally { host[0].close(); Looper.shirazShutdownMain(); }
    }
}
