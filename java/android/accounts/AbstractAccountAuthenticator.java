// SPDX-License-Identifier: GPL-3.0-only
package android.accounts;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.*;

/** App-facing authenticator dispatch. Host account-manager authorization is required. */
public abstract class AbstractAccountAuthenticator {
    public static final String KEY_CUSTOM_TOKEN_EXPIRY="android.accounts.expiry";
    private final Context context;
    public AbstractAccountAuthenticator(Context context){if(context==null)throw new NullPointerException("context");this.context=context;}
    private interface Request {Bundle run() throws NetworkErrorException;}
    private void dispatch(IAccountAuthenticatorResponse response,Request request)throws RemoteException{
        if(context.checkCallingOrSelfPermission("android.permission.ACCOUNT_MANAGER")!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Account manager permission required");
        if(response==null)throw new IllegalArgumentException("Authenticator response required");
        Bundle result;
        try{result=request.run();}
        catch(NetworkErrorException error){response.onError(AccountManager.ERROR_CODE_NETWORK_ERROR,"Authenticator network request failed");return;}
        catch(UnsupportedOperationException error){response.onError(AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION,"Authenticator operation unsupported");return;}
        catch(IllegalArgumentException error){response.onError(AccountManager.ERROR_CODE_BAD_ARGUMENTS,"Invalid authenticator arguments");return;}
        catch(RuntimeException error){diagnose(error);response.onError(AccountManager.ERROR_CODE_REMOTE_EXCEPTION,"Authenticator operation failed");return;}
        // A null result leaves completion to the supplied response capability.
        if(result!=null)response.onResult(result);
    }
    /** Never log exception messages, account arguments or token-bearing result bundles. */
    private static void diagnose(RuntimeException error){
        System.err.println("SHIRAZ_AUTHENTICATOR_RUNTIME_FAILURE "+error.getClass().getName());
        StackTraceElement[] frames=error.getStackTrace();
        for(int i=0;i<Math.min(frames.length,32);i++)System.err.println("  at "+frames[i].getClassName()+"."+frames[i].getMethodName()+":"+frames[i].getLineNumber());
    }
    private final IAccountAuthenticator.Stub transport=new IAccountAuthenticator.Stub(){
        public void addAccount(final IAccountAuthenticatorResponse response,final String accountType,final String authTokenType,final String[] requiredFeatures,final Bundle options)throws RemoteException{
            dispatch(response,new Request(){public Bundle run()throws NetworkErrorException{return AbstractAccountAuthenticator.this.addAccount(new AccountAuthenticatorResponse(response),accountType,authTokenType,requiredFeatures,options);}});
        }
        public void confirmCredentials(final IAccountAuthenticatorResponse response,final Account account,final Bundle options)throws RemoteException{
            dispatch(response,new Request(){public Bundle run()throws NetworkErrorException{return AbstractAccountAuthenticator.this.confirmCredentials(new AccountAuthenticatorResponse(response),account,options);}});
        }
        public void getAuthToken(final IAccountAuthenticatorResponse response,final Account account,final String authTokenType,final Bundle options)throws RemoteException{
            dispatch(response,new Request(){public Bundle run()throws NetworkErrorException{return AbstractAccountAuthenticator.this.getAuthToken(new AccountAuthenticatorResponse(response),account,authTokenType,options);}});
        }
        public void getAuthTokenLabel(final IAccountAuthenticatorResponse response,final String authTokenType)throws RemoteException{
            dispatch(response,new Request(){public Bundle run()throws NetworkErrorException{Bundle value=new Bundle();value.putString(AccountManager.KEY_AUTH_TOKEN_LABEL,AbstractAccountAuthenticator.this.getAuthTokenLabel(authTokenType));return value;}});
        }
        public void updateCredentials(final IAccountAuthenticatorResponse response,final Account account,final String authTokenType,final Bundle options)throws RemoteException{
            dispatch(response,new Request(){public Bundle run()throws NetworkErrorException{return AbstractAccountAuthenticator.this.updateCredentials(new AccountAuthenticatorResponse(response),account,authTokenType,options);}});
        }
        public void editProperties(final IAccountAuthenticatorResponse response,final String accountType)throws RemoteException{
            dispatch(response,new Request(){public Bundle run()throws NetworkErrorException{return AbstractAccountAuthenticator.this.editProperties(new AccountAuthenticatorResponse(response),accountType);}});
        }
        public void hasFeatures(final IAccountAuthenticatorResponse response,final Account account,final String[] features)throws RemoteException{
            dispatch(response,new Request(){public Bundle run()throws NetworkErrorException{return AbstractAccountAuthenticator.this.hasFeatures(new AccountAuthenticatorResponse(response),account,features);}});
        }
        public void getAccountRemovalAllowed(final IAccountAuthenticatorResponse response,final Account account)throws RemoteException{
            dispatch(response,new Request(){public Bundle run()throws NetworkErrorException{return AbstractAccountAuthenticator.this.getAccountRemovalAllowed(new AccountAuthenticatorResponse(response),account);}});
        }
        public void getAccountCredentialsForCloning(final IAccountAuthenticatorResponse response,final Account account)throws RemoteException{
            dispatch(response,new Request(){public Bundle run()throws NetworkErrorException{return AbstractAccountAuthenticator.this.getAccountCredentialsForCloning(new AccountAuthenticatorResponse(response),account);}});
        }
        public void addAccountFromCredentials(final IAccountAuthenticatorResponse response,final Account account,final Bundle accountCredentials)throws RemoteException{
            dispatch(response,new Request(){public Bundle run()throws NetworkErrorException{return AbstractAccountAuthenticator.this.addAccountFromCredentials(new AccountAuthenticatorResponse(response),account,accountCredentials);}});
        }
        public void startAddAccountSession(final IAccountAuthenticatorResponse response,final String accountType,final String authTokenType,final String[] requiredFeatures,final Bundle options)throws RemoteException{
            dispatch(response,new Request(){public Bundle run()throws NetworkErrorException{return AbstractAccountAuthenticator.this.startAddAccountSession(new AccountAuthenticatorResponse(response),accountType,authTokenType,requiredFeatures,options);}});
        }
        public void startUpdateCredentialsSession(final IAccountAuthenticatorResponse response,final Account account,final String authTokenType,final Bundle options)throws RemoteException{
            dispatch(response,new Request(){public Bundle run()throws NetworkErrorException{return AbstractAccountAuthenticator.this.startUpdateCredentialsSession(new AccountAuthenticatorResponse(response),account,authTokenType,options);}});
        }
        public void finishSession(final IAccountAuthenticatorResponse response,final String accountType,final Bundle sessionBundle)throws RemoteException{
            dispatch(response,new Request(){public Bundle run()throws NetworkErrorException{return AbstractAccountAuthenticator.this.finishSession(new AccountAuthenticatorResponse(response),accountType,sessionBundle);}});
        }
        public void isCredentialsUpdateSuggested(final IAccountAuthenticatorResponse response,final Account account,final String statusToken)throws RemoteException{
            dispatch(response,new Request(){public Bundle run()throws NetworkErrorException{return AbstractAccountAuthenticator.this.isCredentialsUpdateSuggested(new AccountAuthenticatorResponse(response),account,statusToken);}});
        }
    };
    public final IBinder getIBinder(){return transport;}
    public abstract Bundle addAccount(AccountAuthenticatorResponse response,String accountType,String authTokenType,String[] requiredFeatures,Bundle options) throws NetworkErrorException;
    public abstract Bundle confirmCredentials(AccountAuthenticatorResponse response,Account account,Bundle options) throws NetworkErrorException;
    public abstract Bundle getAuthToken(AccountAuthenticatorResponse response,Account account,String authTokenType,Bundle options) throws NetworkErrorException;
    public abstract String getAuthTokenLabel(String authTokenType);
    public abstract Bundle updateCredentials(AccountAuthenticatorResponse response,Account account,String authTokenType,Bundle options) throws NetworkErrorException;
    public abstract Bundle editProperties(AccountAuthenticatorResponse response,String accountType);
    public abstract Bundle hasFeatures(AccountAuthenticatorResponse response,Account account,String[] features) throws NetworkErrorException;
    private static Bundle booleanResult(boolean value){Bundle result=new Bundle();result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT,value);return result;}
    public Bundle getAccountRemovalAllowed(AccountAuthenticatorResponse response,Account account)throws NetworkErrorException{return booleanResult(true);}
    public Bundle isCredentialsUpdateSuggested(AccountAuthenticatorResponse response,Account account,String statusToken)throws NetworkErrorException{return booleanResult(false);}
    private static Bundle deferred(final AccountAuthenticatorResponse response,final Bundle result){
        new Thread(new Runnable(){public void run(){response.onResult(result);}},"account-authenticator-response").start();return null;
    }
    public Bundle getAccountCredentialsForCloning(AccountAuthenticatorResponse response,Account account)throws NetworkErrorException{return deferred(response,booleanResult(false));}
    public Bundle addAccountFromCredentials(AccountAuthenticatorResponse response,Account account,Bundle accountCredentials)throws NetworkErrorException{return deferred(response,booleanResult(false));}
    private static final String TOKEN="android.accounts.AbstractAccountAuthenticato.KEY_AUTH_TOKEN_TYPE";
    private static final String FEATURES="android.accounts.AbstractAccountAuthenticator.KEY_REQUIRED_FEATURES";
    private static final String OPTIONS="android.accounts.AbstractAccountAuthenticator.KEY_OPTIONS";
    private static final String ACCOUNT="android.accounts.AbstractAccountAuthenticator.KEY_ACCOUNT";
    private static Bundle session(String token,Bundle options){Bundle result=new Bundle();result.putString(TOKEN,token);result.putBundle(OPTIONS,options);return result;}
    private static Bundle sendSession(AccountAuthenticatorResponse response,Bundle session){Bundle result=new Bundle();result.putBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE,session);return deferred(response,result);}
    public Bundle startAddAccountSession(AccountAuthenticatorResponse response,String accountType,String authTokenType,String[] requiredFeatures,Bundle options)throws NetworkErrorException{
        Bundle value=session(authTokenType,options);value.putStringArray(FEATURES,requiredFeatures);return sendSession(response,value);
    }
    public Bundle startUpdateCredentialsSession(AccountAuthenticatorResponse response,Account account,String authTokenType,Bundle options)throws NetworkErrorException{
        Bundle value=session(authTokenType,options);value.putParcelable(ACCOUNT,account);return sendSession(response,value);
    }
    private static Bundle error(int code,String message){Bundle result=new Bundle();result.putInt(AccountManager.KEY_ERROR_CODE,code);result.putString(AccountManager.KEY_ERROR_MESSAGE,message);return result;}
    public Bundle finishSession(AccountAuthenticatorResponse response,String accountType,Bundle sessionBundle)throws NetworkErrorException{
        if(accountType==null||accountType.isEmpty()||sessionBundle==null)return error(AccountManager.ERROR_CODE_BAD_ARGUMENTS,"Account type and session required");
        if(!sessionBundle.containsKey(TOKEN))return error(AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION,"Custom sessions require a finishSession implementation");
        Bundle merged=new Bundle();Bundle options=sessionBundle.getBundle(OPTIONS);if(options!=null)merged.putAll(options);
        Bundle additional=new Bundle(sessionBundle);additional.remove(TOKEN);additional.remove(FEATURES);additional.remove(OPTIONS);additional.remove(ACCOUNT);merged.putAll(additional);
        String token=sessionBundle.getString(TOKEN);
        if(sessionBundle.containsKey(ACCOUNT))return updateCredentials(response,(Account)sessionBundle.getParcelable(ACCOUNT),token,merged);
        return addAccount(response,accountType,token,sessionBundle.getStringArray(FEATURES),merged);
    }
}
