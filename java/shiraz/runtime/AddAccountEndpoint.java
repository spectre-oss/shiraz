// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.accounts.*;
import android.content.Intent;
import android.os.*;

/** Operation-limited capability. Construct only from host-authenticated admission metadata. */
final class AddAccountEndpoint extends Binder implements java.io.Closeable {
    private static final String DESCRIPTOR="android.accounts.IAccountAuthenticator";
    private final IAccountAuthenticator target;
    private final String accountType,callerPackage,ownerPackage;
    private final int callerUid;
    private final android.content.pm.PackageManager packages;
    private boolean used,closed,terminal,uiSent;

    AddAccountEndpoint(IBinder target,String type,String caller,String owner,int uid)throws RemoteException{this(target,type,caller,owner,uid,null);}
    AddAccountEndpoint(IBinder target,String type,String caller,String owner,int uid,android.content.pm.PackageManager packages)throws RemoteException{
        if(!text(type)||!packageName(caller)||!packageName(owner)||uid<10000||uid>19999)
            throw new IllegalArgumentException("Invalid account-add admission");
        this.target=IAccountAuthenticator.Stub.asInterface(new AuthenticatorEndpoint(target));
        accountType=type;callerPackage=caller;ownerPackage=owner;callerUid=uid;this.packages=packages;
    }
    private static boolean text(String value){return value!=null&&!value.isEmpty()&&value.length()<=1024&&value.indexOf(0)<0;}
    private static boolean argument(String value){return value!=null&&value.length()<=1024&&value.indexOf(0)<0;}
    private static boolean packageName(String value){return text(value)&&value.matches("[A-Za-z_][A-Za-z0-9_.]*");}
    private void resolveOwnedUi(Intent intent){
        if(intent.getComponent()!=null||packages==null||intent.getSelector()!=null||intent.getClipData()!=null
                ||intent.getPackage()!=null&&!ownerPackage.equals(intent.getPackage()))return;
        Intent query=new Intent(intent).setPackage(ownerPackage);
        java.util.List<android.content.pm.ResolveInfo> matches=packages.queryIntentActivities(query,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        android.content.pm.ResolveInfo best=null;boolean tied=false;
        for(android.content.pm.ResolveInfo match:matches){
            if(match.activityInfo==null||!ownerPackage.equals(match.activityInfo.packageName)||match.activityInfo.name==null)continue;
            if(best==null||match.priority>best.priority){best=match;tied=false;}
            else if(match.priority==best.priority&&!match.activityInfo.name.equals(best.activityInfo.name))tied=true;
        }
        if(best!=null&&!tied)intent.setComponent(new android.content.ComponentName(ownerPackage,best.activityInfo.name));
    }
    public synchronized void close(){closed=true;}
    @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
        if(code==IBinder.INTERFACE_TRANSACTION){if(reply!=null)reply.writeString(DESCRIPTOR);return true;}
        if(code!=1||flags!=IBinder.FLAG_ONEWAY||Binder.getCallingUid()!=callerUid)
            throw new SecurityException("Account-add capability does not authorize this request");
        data.enforceInterface(DESCRIPTOR);
        final IAccountAuthenticatorResponse response=IAccountAuthenticatorResponse.Stub.asInterface(data.readStrongBinder());
        String type=data.readString(),tokenType=data.readString();String[] features=data.createStringArray();
        Bundle supplied=data.readTypedObject(Bundle.CREATOR);data.enforceNoDataAvail();
        if(response==null||!accountType.equals(type)||(tokenType!=null&&!argument(tokenType))||features!=null&&features.length>128)
            throw new IllegalArgumentException("Invalid account-add request");
        if(features!=null)for(String feature:features)if(!argument(feature))throw new IllegalArgumentException("Invalid account feature");
        final Bundle options=supplied==null?new Bundle():new Bundle(supplied);
        if(options.hasFileDescriptors())throw new IllegalArgumentException("Account-add options contain descriptors");
        options.putString(AccountManager.KEY_ANDROID_PACKAGE_NAME,callerPackage);
        options.putInt(AccountManager.KEY_CALLER_UID,callerUid);
        options.putInt(AccountManager.KEY_CALLER_PID,Binder.getCallingPid());
        synchronized(this){if(closed||used)throw new SecurityException("Account-add capability already consumed");used=true;}
        target.addAccount(new IAccountAuthenticatorResponse.Stub(){
            public void onResult(Bundle result)throws RemoteException{
                synchronized(AddAccountEndpoint.this){
                    if(closed||terminal)return;
                    if(result==null){System.err.println("SHIRAZ_ACCOUNT_ADD_REJECT missing-result");terminal=true;response.onError(AccountManager.ERROR_CODE_INVALID_RESPONSE,"Missing account response");return;}
                    Bundle copy=new Bundle(result);copy.remove(AccountManager.KEY_AUTHTOKEN);
                    if(copy.containsKey(AccountManager.KEY_INTENT)){
                        Object value=copy.get(AccountManager.KEY_INTENT);
                        if(uiSent||!(value instanceof Intent)){System.err.println("SHIRAZ_ACCOUNT_ADD_REJECT repeated-or-invalid-ui");terminal=true;response.onError(AccountManager.ERROR_CODE_INVALID_RESPONSE,"Invalid account UI");return;}
                        Intent intent=((Intent)value).shirazBroadcastCopy();
                        resolveOwnedUi(intent);
                        int grants=Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
                        if(intent.getComponent()==null||!ownerPackage.equals(intent.getComponent().getPackageName())||intent.getPackage()!=null&&!ownerPackage.equals(intent.getPackage())||intent.getSelector()!=null||intent.getClipData()!=null||(intent.getFlags()&grants)!=0||intent.getExtras()!=null&&intent.getExtras().hasFileDescriptors()){
                            System.err.println("SHIRAZ_ACCOUNT_ADD_REJECT ui-target component="+(intent.getComponent()!=null)+" owner="+(intent.getComponent()!=null&&ownerPackage.equals(intent.getComponent().getPackageName()))+" package="+(intent.getPackage()==null||ownerPackage.equals(intent.getPackage()))+" selector="+(intent.getSelector()!=null)+" clip="+(intent.getClipData()!=null)+" grants="+((intent.getFlags()&grants)!=0)+" descriptors="+(intent.getExtras()!=null&&intent.getExtras().hasFileDescriptors()));
                            terminal=true;response.onError(AccountManager.ERROR_CODE_INVALID_RESPONSE,"Invalid account UI target");return;
                        }
                        intent.putExtra(AccountManager.KEY_ACCOUNT_MANAGER_RESPONSE,new AccountAuthenticatorResponse(this));
                        copy.putParcelable(AccountManager.KEY_INTENT,intent);uiSent=true;
                    }else terminal=true;
                    if(copy.containsKey(AccountManager.KEY_ERROR_CODE))System.err.println("SHIRAZ_ACCOUNT_ADD_PROVIDER_RESULT_ERROR");
                    response.onResult(copy);
                }
            }
            public void onRequestContinued()throws RemoteException{synchronized(AddAccountEndpoint.this){if(!closed&&!terminal)response.onRequestContinued();}}
            public void onError(int code,String message)throws RemoteException{synchronized(AddAccountEndpoint.this){if(closed||terminal)return;terminal=true;System.err.println("SHIRAZ_ACCOUNT_ADD_PROVIDER_ERROR code="+code);response.onError(code,"Authenticator request failed");}}
        },accountType,tokenType,features,options);
        return true;
    }
}
