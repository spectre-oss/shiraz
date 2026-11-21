// SPDX-License-Identifier: GPL-3.0-only
import android.accounts.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;

public final class AbstractAuthenticatorSmoke {
    private static final class Reply extends IAccountAuthenticatorResponse.Stub {
        final CountDownLatch ready=new CountDownLatch(1);Bundle result;int error;int count;
        public synchronized void onResult(Bundle result){this.result=result;count++;ready.countDown();}
        public void onRequestContinued(){throw new AssertionError("Unexpected continuation");}
        public synchronized void onError(int code,String message){if(message.contains("private-fixture"))throw new AssertionError("Exception data exposed");error=code;count++;ready.countDown();}
        void await()throws Exception{if(!ready.await(3,TimeUnit.SECONDS)||count!=1)throw new AssertionError("Callback missing or duplicate");}
    }
    private static final class Provider extends AbstractAccountAuthenticator {
        int calls;AccountAuthenticatorResponse pending;
        Provider(final boolean granted){super(new ContextWrapper(null){public int checkCallingOrSelfPermission(String permission){if(!permission.equals("android.permission.ACCOUNT_MANAGER"))throw new AssertionError("Permission name");return granted?0:-1;}});}
        public Bundle addAccount(AccountAuthenticatorResponse response,String type,String token,String[] features,Bundle options){calls++;return options;}
        public Bundle confirmCredentials(AccountAuthenticatorResponse response,Account account,Bundle options){calls++;pending=response;return null;}
        public Bundle getAuthToken(AccountAuthenticatorResponse response,Account account,String type,Bundle options)throws NetworkErrorException{
            calls++;if("network".equals(type))throw new NetworkErrorException("private-fixture");
            if("unsupported".equals(type))throw new UnsupportedOperationException("private-fixture");
            if("bad".equals(type))throw new IllegalArgumentException("private-fixture");
            if("runtime".equals(type))throw new IllegalStateException("private-fixture");return options;
        }
        public String getAuthTokenLabel(String type){calls++;return "fixture-label";}
        public Bundle updateCredentials(AccountAuthenticatorResponse response,Account account,String type,Bundle options){calls++;return options;}
        public Bundle editProperties(AccountAuthenticatorResponse response,String type){calls++;return new Bundle();}
        public Bundle hasFeatures(AccountAuthenticatorResponse response,Account account,String[] features){calls++;return new Bundle();}
    }
    public static void run()throws Exception {
        Provider denied=new Provider(false);IAccountAuthenticator forbidden=IAccountAuthenticator.Stub.asInterface(denied.getIBinder());
        try{forbidden.getAuthTokenLabel(new Reply(),"fixture");throw new AssertionError("Permission denial bypassed");}catch(SecurityException expected){}
        if(denied.calls!=0)throw new AssertionError("Unauthorized provider invoked");
        Provider provider=new Provider(true);IAccountAuthenticator client=IAccountAuthenticator.Stub.asInterface(provider.getIBinder());
        Reply reply=new Reply();client.getAuthTokenLabel(reply,"fixture");reply.await();
        if(!"fixture-label".equals(reply.result.getString(AccountManager.KEY_AUTH_TOKEN_LABEL)))throw new AssertionError("Token label");
        Account account=new Account("fixture-user","fixture-type");Bundle options=new Bundle();options.putString("value","fixture-option");
        reply=new Reply();client.getAuthToken(reply,account,"normal",options);reply.await();if(!"fixture-option".equals(reply.result.getString("value")))throw new AssertionError("Synchronous result");
        String[] failures={"network","unsupported","bad","runtime"};int[] codes={3,6,7,1};
        java.io.ByteArrayOutputStream diagnostic=new java.io.ByteArrayOutputStream();java.io.PrintStream previous=System.err;
        try{System.setErr(new java.io.PrintStream(diagnostic));
            for(int i=0;i<failures.length;i++){reply=new Reply();client.getAuthToken(reply,account,failures[i],null);reply.await();if(reply.error!=codes[i])throw new AssertionError("Error mapping");}
        }finally{System.setErr(previous);}
        String logged=diagnostic.toString("UTF-8");
        if(logged.contains("private-fixture")||logged.contains("fixture-user")||!logged.contains("SHIRAZ_AUTHENTICATOR_RUNTIME_FAILURE java.lang.IllegalStateException"))throw new AssertionError("Unsafe or missing authenticator diagnostic");
        reply=new Reply();client.confirmCredentials(reply,account,null);if(reply.count!=0)throw new AssertionError("Null result completed early");provider.pending.onResult(options);reply.await();
        reply=new Reply();client.getAccountRemovalAllowed(reply,account);reply.await();if(!reply.result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT))throw new AssertionError("Removal default");
        reply=new Reply();client.getAccountCredentialsForCloning(reply,account);reply.await();if(reply.result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT))throw new AssertionError("Clone default");
        for(boolean update:new boolean[]{false,true}){
            reply=new Reply();if(update)client.startUpdateCredentialsSession(reply,account,"fixture",options);else client.startAddAccountSession(reply,"fixture-type","fixture",new String[]{"feature"},options);
            reply.await();Bundle session=reply.result.getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);if(session==null)throw new AssertionError("Session missing");
            session.putString("value","current-value");Reply finish=new Reply();client.finishSession(finish,"fixture-type",session);finish.await();
            if(!"current-value".equals(finish.result.getString("value")))throw new AssertionError("Session current options precedence");
            if(!"fixture-option".equals(options.getString("value")))throw new AssertionError("Original options mutated");
        }
        reply=new Reply();client.finishSession(reply,"fixture-type",new Bundle());reply.await();if(reply.result.getInt(AccountManager.KEY_ERROR_CODE)!=6)throw new AssertionError("Unknown session accepted");
        int before=provider.calls;try{client.getAuthTokenLabel(null,"fixture");throw new AssertionError("Null callback accepted");}catch(IllegalArgumentException expected){}if(provider.calls!=before)throw new AssertionError("Null callback invoked provider");
    }
}
