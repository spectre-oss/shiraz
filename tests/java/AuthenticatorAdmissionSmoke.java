// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.accounts.*;
import android.app.Service;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;

/** Actual private management admission, service bind, scoped request, and unbind. */
public final class AuthenticatorAdmissionSmoke {
    public static void main(String[] args)throws Exception{
        int fd=Integer.parseInt(args[1]);
        final boolean accountAdd=args[0].endsWith("-add");
        if(args[0].startsWith("receive")){
            Looper.prepareMainLooper();final int[] bound={0},unbound={0};
            Context context=new ContextWrapper(null){public int checkCallingOrSelfPermission(String permission){
                return "android.permission.ACCOUNT_MANAGER".equals(permission)&&AuthenticatorEndpoint.permits(Binder.getCallingPid(),Binder.getCallingUid())?0:-1;
            }};
            final AbstractAccountAuthenticator provider=new AbstractAccountAuthenticator(context){
                public Bundle addAccount(AccountAuthenticatorResponse response,String type,String token,String[] features,Bundle options){
                    if(!accountAdd||!"test.accounts".equals(type)||Binder.getCallingUid()!=10000||options.getInt(AccountManager.KEY_CALLER_UID)!=10000||options.getInt(AccountManager.KEY_CALLER_PID)!=Binder.getCallingPid()||!"test.caller".equals(options.getString(AccountManager.KEY_ANDROID_PACKAGE_NAME)))throw new AssertionError("Account-add admission identity");
                    Bundle result=new Bundle();result.putString(AccountManager.KEY_ACCOUNT_NAME,"fixture");result.putString(AccountManager.KEY_ACCOUNT_TYPE,type);result.putString(AccountManager.KEY_AUTHTOKEN,"synthetic-must-be-filtered");return result;
                }
                public Bundle confirmCredentials(AccountAuthenticatorResponse response,Account account,Bundle options){throw new UnsupportedOperationException();}
                public Bundle getAuthToken(AccountAuthenticatorResponse response,Account account,String type,Bundle options){throw new UnsupportedOperationException();}
                public String getAuthTokenLabel(String type){if(Binder.getCallingUid()!=10000||Binder.getCallingPid()==android.os.Process.myPid())throw new AssertionError("Admitted caller identity");return "fixture-label";}
                public Bundle updateCredentials(AccountAuthenticatorResponse response,Account account,String type,Bundle options){throw new UnsupportedOperationException();}
                public Bundle editProperties(AccountAuthenticatorResponse response,String type){throw new UnsupportedOperationException();}
                public Bundle hasFeatures(AccountAuthenticatorResponse response,Account account,String[] features){throw new UnsupportedOperationException();}
            };
            Service service=new Service(){
                public IBinder onBind(Intent intent){
                    if(Looper.myLooper()!=Looper.getMainLooper()||!AccountManager.ACTION_AUTHENTICATOR_INTENT.equals(intent.getAction())||!new ComponentName("test.auth","test.auth.Service").equals(intent.getComponent()))throw new AssertionError("Authenticator bind routing/thread");
                    bound[0]++;return provider.getIBinder();
                }
                public boolean onUnbind(Intent intent){unbound[0]++;return false;}
            };
            try(SharedServiceHost host=new SharedServiceHost(fd,service,null,"test.auth","test.auth.Service")){Looper.loop();}
            finally{Looper.shirazShutdownMain();}
            if(bound[0]!=1||unbound[0]!=1)throw new AssertionError("Authenticator binding lifetime");
            System.out.println("AUTHENTICATOR_ADMISSION_RECEIVER_OK");
        }else{
            final CountDownLatch done=new CountDownLatch(1);
            IAccountAuthenticatorResponse.Stub response=new IAccountAuthenticatorResponse.Stub(){
                public void onResult(Bundle result){if(accountAdd){if(result.containsKey(AccountManager.KEY_AUTHTOKEN)||!"fixture".equals(result.getString(AccountManager.KEY_ACCOUNT_NAME)))throw new AssertionError("Account-add result filtering");}else if(!"fixture-label".equals(result.getString(AccountManager.KEY_AUTH_TOKEN_LABEL)))throw new AssertionError("Authenticator result");done.countDown();}
                public void onRequestContinued(){throw new AssertionError("Unexpected continuation");}
                public void onError(int code,String message){throw new AssertionError("Authenticator permission/dispatch failure");}
            };
            try(BinderTransport channel=new BinderTransport(fd,null)){
                IAccountAuthenticator client=IAccountAuthenticator.Stub.asInterface(channel.remoteRoot());
                if(accountAdd){Bundle options=new Bundle();options.putInt(AccountManager.KEY_CALLER_UID,0);options.putString(AccountManager.KEY_ANDROID_PACKAGE_NAME,"forged");client.addAccount(response,"test.accounts",null,null,options);}
                else client.getAuthTokenLabel(response,"fixture");
                if(!done.await(5,TimeUnit.SECONDS))throw new AssertionError("Authenticator admission callback timeout");
            }
            System.out.println("AUTHENTICATOR_ADMISSION_SENDER_OK");
        }
    }
}
