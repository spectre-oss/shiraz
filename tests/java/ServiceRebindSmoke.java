// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.content.*;
import android.os.*;
import shiraz.runtime.HostServices;
import java.util.Properties;

public final class ServiceRebindSmoke {
    static int binds, unbinds, rebinds, destroys;
    public static final class Worker extends Service {
        final IBinder binder = new Binder();
        public IBinder onBind(Intent intent) { binds++; return binder; }
        public boolean onUnbind(Intent intent) { unbinds++; return true; }
        public void onRebind(Intent intent) { rebinds++; }
        public void onDestroy() { destroys++; }
        public int onStartCommand(Intent intent, int flags, int id) { return START_NOT_STICKY; }
    }
    public static final class NullWorker extends Service {
        public IBinder onBind(Intent intent) { return null; }
    }
    private static final class Connection implements ServiceConnection {
        int connected, nulls;
        IBinder binder;
        public void onServiceConnected(ComponentName name, IBinder value) { connected++; binder = value; }
        public void onServiceDisconnected(ComponentName name) { throw new AssertionError("unexpected death"); }
        public void onNullBinding(ComponentName name) { nulls++; }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void drain() { for(int i=0;i<5;i++)Looper.myLooper().shirazDispatchDue(100); }
    public static void main(String[] args) {
        Looper.prepareMainLooper();
        final HostServices[] host = new HostServices[1];
        Context context = new ContextWrapper(null) {
            public String getPackageName() { return "org.shiraz.rebind"; }
            public ClassLoader getClassLoader() { return ServiceRebindSmoke.class.getClassLoader(); }
            protected HostServices shirazServices() { return host[0]; }
        };
        Properties metadata = new Properties();
        metadata.setProperty("service.count", "2");
        metadata.setProperty("service.0.name", Worker.class.getName());
        metadata.setProperty("service.1.name", NullWorker.class.getName());
        host[0] = new HostServices(context, new Application(), metadata);
        Intent worker = new Intent().setComponent(new ComponentName(context.getPackageName(),Worker.class.getName()));
        try {
            host[0].start(worker); drain();
            Connection first = new Connection();
            check(context.bindService(worker,first,Context.BIND_AUTO_CREATE),"first bind rejected"); drain();
            check(first.connected==1&&binds==1,"first binder missing");
            context.unbindService(first); drain();
            check(unbinds==1&&destroys==0,"started service destroyed on unbind");
            Connection second=new Connection(),third=new Connection();
            context.bindService(worker,second,Context.BIND_AUTO_CREATE);
            context.bindService(worker,third,Context.BIND_AUTO_CREATE); drain();
            check(rebinds==1&&binds==1,"onRebind missing or duplicated");
            check(second.binder==first.binder&&third.binder==first.binder,"rebind changed binder");
            context.unbindService(second); drain(); check(unbinds==1,"unbound while client remains");
            context.unbindService(third); drain(); check(unbinds==2,"last unbind missing");
            host[0].stop(worker); drain(); check(destroys==1,"started service did not stop");
            Connection nil=new Connection();
            Intent nullIntent=new Intent().setComponent(new ComponentName(context.getPackageName(),NullWorker.class.getName()));
            context.bindService(nullIntent,nil,Context.BIND_AUTO_CREATE); drain();
            check(nil.nulls==1&&nil.connected==0,"null binder callback missing");
            context.unbindService(nil); drain(); check(!host[0].hasWork(),"null binding leaked service");
            System.out.println("SERVICE_REBIND_OK");
        } finally { host[0].close(); Looper.shirazShutdownMain(); }
    }
}
