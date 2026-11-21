// SPDX-License-Identifier: GPL-3.0-only
import android.telephony.SubscriptionManager;
import android.os.Looper;
import java.util.ArrayList;
import java.util.concurrent.Executor;
public final class SubscriptionListenerSmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        Looper.prepareMainLooper();SubscriptionManager manager=new SubscriptionManager();final int[] calls={0};
        SubscriptionManager.OnSubscriptionsChangedListener listener=new SubscriptionManager.OnSubscriptionsChangedListener(){public void onSubscriptionsChanged(){calls[0]++;}};
        manager.addOnSubscriptionsChangedListener(listener);check(calls[0]==0,"legacy callback was synchronous");Looper.myLooper().shirazDispatchDue(256);check(calls[0]==1,"initial callback absent");
        manager.addOnSubscriptionsChangedListener(listener);manager.removeOnSubscriptionsChangedListener(listener);Looper.myLooper().shirazDispatchDue(256);check(calls[0]==1,"removed callback delivered");
        final ArrayList<Runnable> queue=new ArrayList<Runnable>();Executor executor=new Executor(){public void execute(Runnable task){queue.add(task);}};
        manager.addOnSubscriptionsChangedListener(executor,listener);manager.addOnSubscriptionsChangedListener(executor,listener);queue.remove(0).run();queue.remove(0).run();check(calls[0]==2,"replaced registration delivered twice");
        manager.addOnSubscriptionsChangedListener(executor,listener);
        try{manager.addOnSubscriptionsChangedListener(new Executor(){public void execute(Runnable task){throw new IllegalStateException("rejected");}},listener);throw new AssertionError("rejection swallowed");}catch(IllegalStateException expected){}
        queue.remove(0).run();check(calls[0]==3,"failed registration discarded prior callback");manager.removeOnSubscriptionsChangedListener(listener);
        check(manager.getActiveSubscriptionInfoCount()==0&&manager.getActiveSubscriptionInfoList().isEmpty()&&manager.getActiveSubscriptionInfo(0)==null&&!manager.isActiveSubscriptionId(0)&&SubscriptionManager.getDefaultDataSubscriptionId()==-1,"invented subscription");
        System.out.println("SUBSCRIPTION_LISTENER_OK");
    }
}
