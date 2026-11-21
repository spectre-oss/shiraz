// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.content.*;
import android.os.*;
import shiraz.runtime.HostServices;
import java.util.*;
import java.util.concurrent.*;

public final class IntentServiceSmoke {
    static final List<String> handled = Collections.synchronizedList(new ArrayList<String>());
    static CountDownLatch entered, release, finished;
    static volatile Thread worker;
    static volatile int destroys;
    public static final class Worker extends IntentService {
        public Worker() { super("queue-test"); }
        protected void onHandleIntent(Intent intent) {
            check(Looper.myLooper()!=Looper.getMainLooper(), "work ran on main thread");
            if(worker!=null)check(worker==Thread.currentThread(), "multiple worker threads");
            worker=Thread.currentThread();
            if("first".equals(intent.getAction())) {
                entered.countDown();
                try { check(release.await(5,TimeUnit.SECONDS),"release timeout"); }
                catch(InterruptedException e){throw new AssertionError(e);}
            }
            handled.add(intent.getAction()); finished.countDown();
        }
        public void onDestroy(){super.onDestroy();destroys++;}
    }
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static void drain(){for(int i=0;i<5;i++)Looper.myLooper().shirazDispatchDue(100);}
    static void waitDestroyed() throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(destroys==0&&System.nanoTime()<deadline){drain();Thread.sleep(5);}
        check(destroys==1,"service did not destroy");
    }
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        final HostServices[] host=new HostServices[1];
        Context context=new ContextWrapper(null){
            public String getPackageName(){return "org.shiraz.intentservice";}
            public ClassLoader getClassLoader(){return IntentServiceSmoke.class.getClassLoader();}
            protected HostServices shirazServices(){return host[0];}
        };
        Properties metadata=new Properties();
        metadata.setProperty("service.count","1");
        metadata.setProperty("service.0.name",Worker.class.getName());
        host[0]=new HostServices(context,new Application(),metadata);
        Intent base=new Intent().setComponent(new ComponentName(context.getPackageName(),Worker.class.getName()));
        try {
            Worker unattached=new Worker();
            check(unattached.onBind(base)==null,"bound binder");
            unattached.setIntentRedelivery(false);
            unattached.setIntentRedelivery(true);
            unattached.setIntentRedelivery(false);
            for(int round=0;round<2;round++){
                entered=new CountDownLatch(1);release=new CountDownLatch(1);finished=new CountDownLatch(round==0?2:1);
                handled.clear();worker=null;destroys=0;
                host[0].start(new Intent(base).setAction("first"));drain();
                check(entered.await(5,TimeUnit.SECONDS),"first did not start");
                host[0].start(new Intent(base).setAction("second"));drain();
                check(handled.isEmpty()&&destroys==0,"parallel work or premature stop");
                if(round==1){host[0].stop(base);waitDestroyed();}
                release.countDown();
                check(finished.await(5,TimeUnit.SECONDS),"work did not finish");
                waitDestroyed();
                worker.join(5000);check(!worker.isAlive(),"worker leaked");
                check(handled.equals(round==0?Arrays.asList("first","second"):Arrays.asList("first")),"wrong queue order/cancellation: "+handled);
                check(!host[0].hasWork(),"service work leaked");
            }
            System.out.println("INTENT_SERVICE_OK");
        } finally {release.countDown();host[0].close();Looper.shirazShutdownMain();}
    }
}
