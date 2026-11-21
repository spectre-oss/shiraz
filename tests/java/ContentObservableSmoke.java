// SPDX-License-Identifier: GPL-3.0-only
import android.database.*;
import android.net.Uri;
import android.os.*;
import java.util.concurrent.*;

public final class ContentObservableSmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static class Observer extends ContentObserver {
        int count;Thread thread;Uri uri;boolean self;
        Observer(Handler handler){super(handler);}
        public void onChange(boolean self){count++;this.self=self;thread=Thread.currentThread();}
        public void onChange(boolean self,Uri uri){this.uri=uri;onChange(self);}
    }
    public static void main(String[] args)throws Exception{
        ContentObservable observable=new ContentObservable();
        Observer regular=new Observer(null),self=new Observer(null){public boolean deliverSelfNotifications(){return true;}};
        observable.registerObserver(regular);observable.registerObserver(self);
        try{observable.registerObserver(regular);throw new AssertionError("Duplicate observer");}catch(IllegalStateException expected){}
        try{observable.registerObserver(null);throw new AssertionError("Null observer");}catch(IllegalArgumentException expected){}
        Uri uri=Uri.parse("content://fixture/1");observable.dispatchChange(true,uri);
        check(regular.count==0&&self.count==1&&uri.equals(self.uri)&&self.self,"self filtering");
        observable.dispatchChange(false,uri);check(regular.count==1&&self.count==2,"ordinary dispatch");
        observable.notifyChange(true);check(regular.count==2&&self.count==3,"legacy direct callback filtering");
        observable.unregisterObserver(regular);observable.unregisterAll();observable.dispatchChange(false);check(self.count==3,"unregister all");
        ContentObserver removing=new ContentObserver(null){public void onChange(boolean change){observable.unregisterObserver(this);}};
        observable.registerObserver(removing);observable.registerObserver(regular);observable.dispatchChange(false);
        check(regular.count==3,"self-unregister interrupted dispatch");observable.dispatchChange(false);check(regular.count==4,"remaining observer lost");
        HandlerThread thread=new HandlerThread("ContentObservableFixture");thread.start();Handler handler=new Handler(thread.getLooper());
        try{
            ContentObservable asynchronous=new ContentObservable();Observer target=new Observer(handler);asynchronous.registerObserver(target);
            asynchronous.dispatchChange(false,uri);CountDownLatch drained=new CountDownLatch(1);handler.post(new Runnable(){public void run(){drained.countDown();}});
            check(drained.await(3,TimeUnit.SECONDS)&&target.count==1&&target.thread==thread,"handler dispatch");
            asynchronous.notifyChange(true);check(target.count==2&&target.thread==Thread.currentThread()&&target.self,"legacy direct thread");
        }finally{thread.quit();thread.join(3000);check(!thread.isAlive(),"handler shutdown");}
        System.out.println("CONTENT_OBSERVABLE_OK");
    }
}
