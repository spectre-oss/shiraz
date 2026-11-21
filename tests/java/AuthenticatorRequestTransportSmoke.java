// SPDX-License-Identifier: GPL-3.0-only
import android.accounts.*;
import android.os.*;
import java.util.concurrent.*;
import java.util.*;

/** Round-trip requests and callback capabilities between two ART VMs. */
public final class AuthenticatorRequestTransportSmoke {
    private static final Set<String> expected=new HashSet<String>(Arrays.asList(
        "addAccount","confirmCredentials","getAuthToken","getAuthTokenLabel","updateCredentials",
        "editProperties","hasFeatures","getAccountRemovalAllowed","getAccountCredentialsForCloning",
        "addAccountFromCredentials","startAddAccountSession","startUpdateCredentialsSession",
        "finishSession","isCredentialsUpdateSuggested"));
    private static void check(Object value,String field){
        boolean valid;
        if(value instanceof Account)valid=value.equals(new Account("fixture-user","fixture-type"));
        else if(value instanceof Bundle)valid="fixture-option".equals(((Bundle)value).getString("value"));
        else if(value instanceof String[])valid=Arrays.equals((String[])value,new String[]{"feature-one","feature-two"});
        else valid=("fixture-"+field).equals(value);
        if(!valid)throw new AssertionError("Request field: "+field);
    }
    public static void main(String[] args)throws Exception{
        int fd=Integer.parseInt(args[1]);
        if("receive".equals(args[0])){
            final Set<String> invoked=Collections.synchronizedSet(new HashSet<String>());
            IAccountAuthenticator.Stub service=new IAccountAuthenticator.Stub(){
                private void complete(String method,IAccountAuthenticatorResponse response)throws RemoteException{
                    if(Binder.getCallingPid()==android.os.Process.myPid()||!invoked.add(method))throw new AssertionError("Request identity/duplicate");
                    Bundle result=new Bundle();result.putString("method",method);result.putInt("pid",android.os.Process.myPid());response.onResult(result);
                }
                public void addAccount(IAccountAuthenticatorResponse response,String accountType,String authTokenType,String[] requiredFeatures,Bundle options)throws RemoteException{
                    check(accountType,"accountType");
                    check(authTokenType,"authTokenType");
                    check(requiredFeatures,"requiredFeatures");
                    check(options,"options");
                    complete("addAccount",response);
                }
                public void confirmCredentials(IAccountAuthenticatorResponse response,Account account,Bundle options)throws RemoteException{
                    check(account,"account");
                    check(options,"options");
                    complete("confirmCredentials",response);
                }
                public void getAuthToken(IAccountAuthenticatorResponse response,Account account,String authTokenType,Bundle options)throws RemoteException{
                    check(account,"account");
                    check(authTokenType,"authTokenType");
                    check(options,"options");
                    complete("getAuthToken",response);
                }
                public void getAuthTokenLabel(IAccountAuthenticatorResponse response,String authTokenType)throws RemoteException{
                    check(authTokenType,"authTokenType");
                    complete("getAuthTokenLabel",response);
                }
                public void updateCredentials(IAccountAuthenticatorResponse response,Account account,String authTokenType,Bundle options)throws RemoteException{
                    check(account,"account");
                    check(authTokenType,"authTokenType");
                    check(options,"options");
                    complete("updateCredentials",response);
                }
                public void editProperties(IAccountAuthenticatorResponse response,String accountType)throws RemoteException{
                    check(accountType,"accountType");
                    complete("editProperties",response);
                }
                public void hasFeatures(IAccountAuthenticatorResponse response,Account account,String[] features)throws RemoteException{
                    check(account,"account");
                    check(features,"features");
                    complete("hasFeatures",response);
                }
                public void getAccountRemovalAllowed(IAccountAuthenticatorResponse response,Account account)throws RemoteException{
                    check(account,"account");
                    complete("getAccountRemovalAllowed",response);
                }
                public void getAccountCredentialsForCloning(IAccountAuthenticatorResponse response,Account account)throws RemoteException{
                    check(account,"account");
                    complete("getAccountCredentialsForCloning",response);
                }
                public void addAccountFromCredentials(IAccountAuthenticatorResponse response,Account account,Bundle accountCredentials)throws RemoteException{
                    check(account,"account");
                    check(accountCredentials,"accountCredentials");
                    complete("addAccountFromCredentials",response);
                }
                public void startAddAccountSession(IAccountAuthenticatorResponse response,String accountType,String authTokenType,String[] requiredFeatures,Bundle options)throws RemoteException{
                    check(accountType,"accountType");
                    check(authTokenType,"authTokenType");
                    check(requiredFeatures,"requiredFeatures");
                    check(options,"options");
                    complete("startAddAccountSession",response);
                }
                public void startUpdateCredentialsSession(IAccountAuthenticatorResponse response,Account account,String authTokenType,Bundle options)throws RemoteException{
                    check(account,"account");
                    check(authTokenType,"authTokenType");
                    check(options,"options");
                    complete("startUpdateCredentialsSession",response);
                }
                public void finishSession(IAccountAuthenticatorResponse response,String accountType,Bundle sessionBundle)throws RemoteException{
                    check(accountType,"accountType");
                    check(sessionBundle,"sessionBundle");
                    complete("finishSession",response);
                }
                public void isCredentialsUpdateSuggested(IAccountAuthenticatorResponse response,Account account,String statusToken)throws RemoteException{
                    check(account,"account");
                    check(statusToken,"statusToken");
                    complete("isCredentialsUpdateSuggested",response);
                }
            };
            // Validate malformed frames directly; rejected one-way calls cannot report errors remotely.
            Parcel bad=Parcel.obtain();try{
                bad.writeInterfaceToken("android.accounts.IAccountAuthenticator");bad.writeStrongBinder(null);bad.writeString("fixture-authTokenType");bad.writeInt(99);
                try{service.transact(4,bad,null,1);throw new AssertionError("Trailing request accepted");}catch(IllegalArgumentException correct){}
            }finally{bad.recycle();}
            try(BinderTransport transport=new BinderTransport(fd,service)){transport.awaitClosed();}
            if(!invoked.equals(expected))throw new AssertionError("Missing authenticator requests");
            System.out.println("AUTHENTICATOR_REQUEST_RECEIVER_OK");
        }else{
            final CountDownLatch done=new CountDownLatch(14);
            final Set<String> received=Collections.synchronizedSet(new HashSet<String>());
            IAccountAuthenticatorResponse.Stub response=new IAccountAuthenticatorResponse.Stub(){
                public void onResult(Bundle result){
                    if(Binder.getCallingPid()==android.os.Process.myPid()||result.getInt("pid")!=Binder.getCallingPid()||!expected.contains(result.getString("method"))||!received.add(result.getString("method")))throw new AssertionError("Callback identity/duplicate");
                    done.countDown();
                }
                public void onRequestContinued(){throw new AssertionError("Unexpected continuation");}
                public void onError(int code,String message){throw new AssertionError("Unexpected error");}
            };
            try(BinderTransport transport=new BinderTransport(fd,null)){
                IAccountAuthenticator remote=IAccountAuthenticator.Stub.asInterface(transport.remoteRoot());
                Account account=new Account("fixture-user","fixture-type");Bundle options=new Bundle();options.putString("value","fixture-option");
                String[] features={"feature-one","feature-two"};
                remote.addAccount(response,"fixture-accountType","fixture-authTokenType",features,options);
                remote.confirmCredentials(response,account,options);
                remote.getAuthToken(response,account,"fixture-authTokenType",options);
                remote.getAuthTokenLabel(response,"fixture-authTokenType");
                remote.updateCredentials(response,account,"fixture-authTokenType",options);
                remote.editProperties(response,"fixture-accountType");
                remote.hasFeatures(response,account,features);
                remote.getAccountRemovalAllowed(response,account);
                remote.getAccountCredentialsForCloning(response,account);
                remote.addAccountFromCredentials(response,account,options);
                remote.startAddAccountSession(response,"fixture-accountType","fixture-authTokenType",features,options);
                remote.startUpdateCredentialsSession(response,account,"fixture-authTokenType",options);
                remote.finishSession(response,"fixture-accountType",options);
                remote.isCredentialsUpdateSuggested(response,account,"fixture-statusToken");
                if(!done.await(8,TimeUnit.SECONDS)||!received.equals(expected))throw new AssertionError("Authenticator callbacks incomplete");
            }
            System.out.println("AUTHENTICATOR_REQUEST_SENDER_OK");
        }
    }
}
