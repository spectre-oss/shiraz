// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.accounts.*;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded authenticator requests and one owner-pinned UI continuation. */
public final class HostAccountSessions {
    private HostAccountSessions(){}
    public interface Request<T> {
        default boolean addAccountOnly(){return false;}
        void send(IAccountAuthenticator service,IAccountAuthenticatorResponse response)throws RemoteException;
        default T prepare()throws IOException,AuthenticatorException{return null;}
        default void connected(ServiceBrokerClient.Resolution resolution)throws AuthenticatorException{}
        T decode(Bundle result)throws IOException,AuthenticatorException;
    }
    private static ThreadFactory threads(final String name){return new ThreadFactory(){public Thread newThread(Runnable task){Thread thread=new Thread(task,name);thread.setDaemon(true);return thread;}};}
    private static final ThreadPoolExecutor WORKERS=new ThreadPoolExecutor(4,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(4),threads("account-request"));
    private static final ScheduledThreadPoolExecutor DEADLINES=new ScheduledThreadPoolExecutor(4,threads("account-deadline"));
    private static final Set<Session<?>> ACTIVE=new HashSet<Session<?>>();
    static {WORKERS.allowCoreThreadTimeOut(true);DEADLINES.setRemoveOnCancelPolicy(true);}
    public static <T> AccountManagerFuture<T> start(String type,Request<T> request,AccountManagerCallback<T> callback,Handler handler){
        return start(type,request,callback,handler,null);
    }
    public static <T> AccountManagerFuture<T> start(String type,Request<T> request,AccountManagerCallback<T> callback,Handler handler,android.app.Activity activity){
        HostAccountFuture<T> future=new HostAccountFuture<T>(callback,handler);
        Session<T> session=new Session<T>(type,request,future,activity);
        synchronized(ACTIVE){if(WORKERS.isShutdown()||ACTIVE.size()>=4){future.fail(new AuthenticatorException("Account request capacity unavailable"));return future;}ACTIVE.add(session);}
        future.attach(session);
        try{session.arm();WORKERS.execute(session);}catch(RejectedExecutionException failure){future.fail(new AuthenticatorException("Account request scheduling unavailable"));}
        return future;
    }
    static void shutdown(){
        List<Session<?>> sessions;synchronized(ACTIVE){sessions=new ArrayList<Session<?>>(ACTIVE);}
        for(Session<?> session:sessions)session.future.cancel(true);
        WORKERS.shutdownNow();DEADLINES.shutdownNow();
    }
    private static final class Session<T> implements Runnable,Closeable,IBinder.DeathRecipient {
        final String type;final Request<T> request;final HostAccountFuture<T> future;
        ServiceBrokerClient.Connection connection;ServiceBrokerClient.ActivityConnection uiConnection;ScheduledFuture<?> deadline;Thread runner;boolean closed,uiStarted;final android.app.Activity activity;
        Session(String type,Request<T> request,HostAccountFuture<T> future,android.app.Activity activity){this.type=type;this.request=request;this.future=future;this.activity=activity;}
        void arm(){arm(30);}
        void arm(int seconds){ScheduledFuture<?> task=DEADLINES.schedule(new Runnable(){public void run(){future.cancel(true);}},seconds,TimeUnit.SECONDS);synchronized(this){if(closed)task.cancel(false);else{if(deadline!=null)deadline.cancel(false);deadline=task;}}}
        public void run(){
            synchronized(this){if(closed)return;runner=Thread.currentThread();}
            try{
                T cached=request.prepare();
                if(cached!=null){future.complete(cached);return;}
                if(future.isDone())return;
                ServiceBrokerClient.Connection opened=HostAccounts.open(type,request.addAccountOnly());
                boolean reject;synchronized(this){reject=closed;if(!reject)connection=opened;}
                if(reject){opened.close();return;}
                opened.binder.linkToDeath(this,0);
                if(future.isDone())return;
                request.connected(opened.resolution);
                request.send(IAccountAuthenticator.Stub.asInterface(opened.binder),new IAccountAuthenticatorResponse.Stub(){
                    public void onResult(final Bundle result){
                        if(future.isDone())return;
                        try{
                            if(result==null)throw new AuthenticatorException("Missing authenticator response");
                            if(result.containsKey(AccountManager.KEY_ERROR_CODE)){
                                Object code=result.get(AccountManager.KEY_ERROR_CODE);
                                if(!(code instanceof Integer))throw new AuthenticatorException("Invalid authenticator error code");
                                onError((Integer)code,null);return;
                            }
                            if(result.containsKey(AccountManager.KEY_INTENT)){continueUi(result.get(AccountManager.KEY_INTENT),this);return;}
                            future.completeUsing(new Callable<T>(){public T call()throws Exception{return request.decode(result);}});
                        }catch(IOException|RemoteException|AuthenticatorException|RuntimeException failure){future.fail(failure);}
                    }
                    public void onRequestContinued(){/* Original absolute deadline still applies. */}
                    public void onError(int code,String ignored){
                        if(code==AccountManager.ERROR_CODE_CANCELED||code==100||code==101)future.cancel(false);
                        else if(code==AccountManager.ERROR_CODE_NETWORK_ERROR)future.fail(new IOException("Authenticator network request failed"));
                        else if(code==AccountManager.ERROR_CODE_BAD_ARGUMENTS)future.fail(new IllegalArgumentException("Authenticator rejected request arguments"));
                        else future.fail(new AuthenticatorException("Authenticator request failed ("+code+")"));
                    }
                });
            }catch(IOException|AuthenticatorException|RemoteException|RuntimeException failure){future.fail(failure instanceof RemoteException?new AuthenticatorException("Authenticator connection failed"):failure);}
            finally{synchronized(this){runner=null;}}
        }
        private void continueUi(Object value,final IAccountAuthenticatorResponse response)throws IOException,RemoteException,AuthenticatorException{
            if(activity==null||!(value instanceof android.content.Intent))throw new AuthenticatorException("Authenticator UI requires an Activity and Intent");
            final ServiceBrokerClient.Resolution owner;
            synchronized(this){if(closed)return;if(uiStarted)throw new AuthenticatorException("Repeated authenticator UI continuation");uiStarted=true;owner=connection.resolution;}
            final android.content.Intent intent=((android.content.Intent)value).shirazBroadcastCopy();
            android.content.ComponentName component=intent.getComponent();
            if(component==null||!owner.packageName.equals(component.getPackageName())||intent.getPackage()!=null&&!owner.packageName.equals(intent.getPackage())||intent.getSelector()!=null||intent.getClipData()!=null||(intent.getFlags()&(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION|android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION|android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION))!=0||intent.getExtras()!=null&&intent.getExtras().hasFileDescriptors())throw new AuthenticatorException("Unsupported authenticator UI target or grants");
            ServiceBrokerClient broker=RuntimeBroker.client();if(broker==null)throw new IOException("Activity broker unavailable");
            ServiceBrokerClient.ActivityResolution target=broker.resolveActivity(owner.packageName,component.getClassName());
            if(target.uid!=owner.uid||!target.apkSha256.equals(owner.apkSha256))throw new AuthenticatorException("Authenticator UI owner changed");
            intent.putExtra(AccountManager.KEY_ACCOUNT_MANAGER_RESPONSE,new AccountAuthenticatorResponse(response));
            arm(300);
            if(owner.packageName.equals(activity.getPackageName())){
                if(!new Handler(Looper.getMainLooper()).post(new Runnable(){public void run(){
                    if(future.isDone())return;
                    try{if(activity.isFinishing())throw new IllegalStateException("Account caller Activity is finishing");activity.startActivity(intent);}catch(RuntimeException failure){future.fail(failure);}
                }}))throw new IOException("Activity queue unavailable");
                return;
            }
            if(activity.isFinishing())throw new AuthenticatorException("Account caller Activity is finishing");
            if(future.isDone())return;
            ServiceBrokerClient.ActivityConnection opened=broker.openActivity(target);
            boolean reject;synchronized(this){reject=closed;if(!reject)uiConnection=opened;}
            if(reject){opened.close();return;}
            opened.binder.linkToDeath(this,0);
            if(!future.isDone())opened.send(intent);
        }
        public void binderDied(){future.fail(new AuthenticatorException("Authenticator disconnected"));}
        public void close(){
            ServiceBrokerClient.Connection old;ServiceBrokerClient.ActivityConnection ui;Thread work;ScheduledFuture<?> timer;
            synchronized(this){if(closed)return;closed=true;old=connection;connection=null;ui=uiConnection;uiConnection=null;work=runner;timer=deadline;deadline=null;}
            if(timer!=null)timer.cancel(false);
            if(work!=null&&work!=Thread.currentThread()&&future.interruptRequested())work.interrupt();
            WORKERS.remove(this);
            try{try{if(ui!=null){ui.binder.unlinkToDeath(this,0);ui.close();}}finally{if(old!=null){old.binder.unlinkToDeath(this,0);old.close();}}}
            finally{synchronized(ACTIVE){ACTIVE.remove(this);}}
        }
    }
}
