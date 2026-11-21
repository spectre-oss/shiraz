// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.*;
import java.util.concurrent.*;

public final class ContentObserverDeliverySmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static class Observer extends ContentObserver {
        volatile int count,flags;volatile boolean self;volatile Uri uri;volatile Thread thread;
        Observer(Handler handler){super(handler);}
        public void onChange(boolean self,Uri uri){count++;this.self=self;this.uri=uri;thread=Thread.currentThread();}
        public void onChange(boolean self,Uri uri,int flags){this.flags=flags;super.onChange(self,uri,flags);}
    }
    static Uri uri(String path){return Uri.parse("content://fixture"+path);}
    public static void main(String[] args)throws Exception{
        flags();
        Context root=new ContextWrapper(null);ContentResolver resolver=new ContentResolver(root);
        ContentResolver sibling=new ContentResolver(new ContextWrapper(root));
        Observer exact=new Observer(null),tree=new Observer(null),leaf=new Observer(null),other=new Observer(null);
        resolver.registerContentObserver(uri("/items"),false,exact);
        resolver.registerContentObserver(uri("/items"),true,tree);
        resolver.registerContentObserver(uri("/items/1"),false,leaf);
        resolver.registerContentObserver(Uri.parse("content://other/items"),true,other);
        sibling.notifyChange(uri("/items/1"),null,false);
        check(exact.count==0&&tree.count==1&&leaf.count==1&&other.count==0,"descendant routing");
        sibling.notifyChange(uri("/items?query=ignored#fragment"),null,false);
        check(exact.count==1&&tree.count==2&&leaf.count==2,"parent invalidates registered children");
        sibling.notifyChange(uri("/items2"),null,false);check(tree.count==2,"path prefix confused with segment");
        sibling.notifyChange(uri("/items"),exact,false);check(exact.count==1,"self exclusion");
        Observer self=new Observer(null){public boolean deliverSelfNotifications(){return true;}};
        resolver.registerContentObserver(uri("/self"),false,self);sibling.notifyChange(uri("/self"),self,false);
        check(self.count==1&&self.self,"self opt-in");
        new ContentResolver(new ContextWrapper(null)).notifyChange(uri("/self"),null,false);
        check(self.count==1,"independent owner leaked notification");
        resolver.registerContentObserver(uri("/extra"),false,exact);sibling.unregisterContentObserver(exact);
        sibling.notifyChange(uri("/extra"),null,false);check(exact.count==1,"unregister must remove all observer entries");
        HandlerThread worker=new HandlerThread("ContentObserverFixture");worker.start();Handler handler=new Handler(worker.getLooper());
        try{
            Observer asynchronous=new Observer(handler);resolver.registerContentObserver(uri("/async"),false,asynchronous);
            sibling.notifyChange(uri("/async"),null,ContentResolver.NOTIFY_INSERT|ContentResolver.NOTIFY_UPDATE);
            CountDownLatch drained=new CountDownLatch(1);handler.post(new Runnable(){public void run(){drained.countDown();}});
            check(drained.await(3,TimeUnit.SECONDS)&&asynchronous.count==1&&asynchronous.thread==worker&&asynchronous.flags==12,"handler delivery and flags");
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            handler.post(new Runnable(){public void run(){entered.countDown();try{release.await();}catch(InterruptedException ignored){}}});
            check(entered.await(3,TimeUnit.SECONDS),"handler barrier");
            sibling.notifyChange(uri("/async"),null,false);root.shirazContentObservers().close();release.countDown();
            CountDownLatch done=new CountDownLatch(1);handler.post(new Runnable(){public void run(){done.countDown();}});
            check(done.await(3,TimeUnit.SECONDS)&&asynchronous.count==1,"queued callback survived owner close");
            try{resolver.registerContentObserver(uri("/new"),false,exact);throw new AssertionError("closed owner reopened");}catch(IllegalStateException expected){}
        }finally{worker.quit();worker.join(3000);check(!worker.isAlive(),"handler cleanup");}
        System.out.println("CONTENT_OBSERVER_DELIVERY_OK");
    }
    static void flags(){
        ContentResolver resolver=new ContentResolver(new ContextWrapper(null));
        Observer exact=new Observer(null),tree=new Observer(null),child=new Observer(null),childTree=new Observer(null);
        resolver.registerContentObserver(uri("/rows"),false,exact);resolver.registerContentObserver(uri("/rows"),true,tree);
        resolver.registerContentObserver(uri("/rows/1"),false,child);resolver.registerContentObserver(uri("/rows/1"),true,childTree);
        resolver.notifyChange(uri("/rows"),null,ContentResolver.NOTIFY_SKIP_NOTIFY_FOR_DESCENDANTS|ContentResolver.NOTIFY_UPDATE);
        check(exact.count==1&&exact.flags==10&&tree.count==0&&child.count==1&&childTree.count==0,"general update skipped descendant subscribers");
        resolver.notifyChange(uri("/rows/1"),null,ContentResolver.NOTIFY_SKIP_NOTIFY_FOR_DESCENDANTS|ContentResolver.NOTIFY_DELETE);
        check(exact.count==1&&tree.count==1&&tree.flags==18&&child.count==2&&childTree.count==0,"specific update reaches ancestor descendant subscriber");
        resolver.notifyChange(uri("/rows/1"),null,0);check(tree.count==2&&childTree.count==1,"zero flags preserve delivery");
        try{resolver.notifyChange(uri("/rows"),null,32);throw new AssertionError("unsupported flags accepted");}catch(UnsupportedOperationException expected){}
        check(exact.count==1&&tree.count==2,"unsupported flags delivered partial notifications");
        Observer direct=new Observer(null);direct.dispatchChange(true,uri("/direct"),ContentResolver.NOTIFY_INSERT);
        check(direct.count==1&&direct.self&&direct.flags==4,"direct flags dispatch");
    }
}
