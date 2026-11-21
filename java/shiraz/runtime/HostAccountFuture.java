// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.accounts.*;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.util.concurrent.*;

/** Single terminal account result and ownership of its eventual connection. */
public final class HostAccountFuture<T> implements AccountManagerFuture<T> {
    private final AccountManagerCallback<T> callback;
    private final Handler handler;
    private Closeable resource;
    private boolean attached;
    private volatile boolean interruptOnCancel;
    private final Result result=new Result();
    private final class Result extends FutureTask<T> {
        Result(){super(new Callable<T>(){public T call(){throw new UnsupportedOperationException("Externally completed account result");}});}
        void succeed(T value){set(value);}
        void fail(Throwable failure){setException(failure);}
        @Override protected void done(){finished();}
    }
    public HostAccountFuture(AccountManagerCallback<T> callback,Handler handler){
        this.callback=callback;
        this.handler=callback==null?null:handler!=null?handler:new Handler(Looper.getMainLooper());
    }
    /** Late admission after cancellation must be revoked immediately. */
    public void attach(Closeable connection){
        if(connection==null)throw new NullPointerException("connection");
        boolean release;
        synchronized(this){if(attached)throw new IllegalStateException("Account connection already attached");attached=true;release=result.isDone();if(!release)resource=connection;}
        if(release)closeResource(connection);
    }
    private static void closeResource(Closeable connection){
        if(connection!=null)try{connection.close();}catch(IOException|RuntimeException failure){System.err.println("SHIRAZ_ACCOUNT_CONNECTION_CLEANUP_FAILED");}
    }
    private void finished(){
        Closeable connection;synchronized(this){connection=resource;resource=null;}
        try{closeResource(connection);}finally{
            if(callback!=null&&!handler.post(new Runnable(){public void run(){callback.run(HostAccountFuture.this);}}))System.err.println("SHIRAZ_ACCOUNT_CALLBACK_QUEUE_STOPPED");
        }
    }
    public synchronized void complete(T value){result.succeed(value);}
    public synchronized void fail(Throwable failure){if(failure==null)throw new NullPointerException("failure");result.fail(failure);}
    /** Serialize result side effects with every terminal outcome. */
    public synchronized void completeUsing(Callable<T> completion){
        if(result.isDone())return;
        try{result.succeed(completion.call());}catch(Exception failure){result.fail(failure);}
    }
    public synchronized boolean cancel(boolean mayInterruptIfRunning){if(result.isDone())return false;interruptOnCancel=mayInterruptIfRunning;return result.cancel(mayInterruptIfRunning);}
    boolean interruptRequested(){return result.isCancelled()&&interruptOnCancel;}
    public boolean isCancelled(){return result.isCancelled();}
    public boolean isDone(){return result.isDone();}
    public T getResult()throws OperationCanceledException,IOException,AuthenticatorException{return await(0,null);}
    public T getResult(long timeout,TimeUnit unit)throws OperationCanceledException,IOException,AuthenticatorException{
        if(unit==null)throw new NullPointerException("unit");return await(timeout,unit);
    }
    private T await(long timeout,TimeUnit unit)throws OperationCanceledException,IOException,AuthenticatorException{
        if(!isDone()&&Looper.getMainLooper()!=null&&Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Account result cannot block the main thread");
        try{return unit==null?result.get():result.get(timeout,unit);}
        catch(InterruptedException interrupted){cancel(true);Thread.currentThread().interrupt();throw new OperationCanceledException("Account wait interrupted");}
        catch(TimeoutException timeoutFailure){cancel(true);throw new OperationCanceledException("Account wait timed out");}
        catch(CancellationException canceled){throw new OperationCanceledException("Account request canceled");}
        catch(ExecutionException failed){
            Throwable cause=failed.getCause();
            if(cause instanceof IOException)throw (IOException)cause;
            if(cause instanceof OperationCanceledException)throw (OperationCanceledException)cause;
            if(cause instanceof AuthenticatorException)throw (AuthenticatorException)cause;
            if(cause instanceof RuntimeException)throw (RuntimeException)cause;
            if(cause instanceof Error)throw (Error)cause;
            throw new AuthenticatorException("Authenticator request failed",cause);
        }
    }
}
