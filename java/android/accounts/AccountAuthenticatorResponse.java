// SPDX-License-Identifier: GPL-3.0-only
package android.accounts;
import android.os.*;

/** Parcelable callback capability passed to authenticator UI and services. */
public class AccountAuthenticatorResponse implements Parcelable {
    private final IAccountAuthenticatorResponse callback;
    public AccountAuthenticatorResponse(IAccountAuthenticatorResponse callback){this.callback=callback;}
    public AccountAuthenticatorResponse(Parcel source){this(IAccountAuthenticatorResponse.Stub.asInterface(source.readStrongBinder()));}
    public void onResult(Bundle result){try{callback.onResult(result);}catch(RemoteException unavailable){}}
    public void onRequestContinued(){try{callback.onRequestContinued();}catch(RemoteException unavailable){}}
    public void onError(int code,String message){try{callback.onError(code,message);}catch(RemoteException unavailable){}}
    public int describeContents(){return 0;}
    public void writeToParcel(Parcel destination,int flags){destination.writeStrongBinder(callback.asBinder());}
    public static final Parcelable.Creator<AccountAuthenticatorResponse> CREATOR=new Parcelable.Creator<AccountAuthenticatorResponse>(){
        public AccountAuthenticatorResponse createFromParcel(Parcel source){return new AccountAuthenticatorResponse(source);}
        public AccountAuthenticatorResponse[] newArray(int size){return new AccountAuthenticatorResponse[size];}
    };
}
