// SPDX-License-Identifier: GPL-3.0-only
import android.accounts.*;
import android.content.Intent;
import android.os.*;

public final class AuthenticatorActivitySmoke {
    private static final class Screen extends AccountAuthenticatorActivity {
        void create(Intent intent){setIntent(intent);onCreate(null);}
    }
    private static final class Reply extends IAccountAuthenticatorResponse.Stub {
        int continued,results,errors,code;Bundle value;Screen reenter;boolean unavailable;
        public void onRequestContinued()throws RemoteException{continued++;if(unavailable)throw new RemoteException("fixture");}
        public void onResult(Bundle result)throws RemoteException{results++;value=result;if(reenter!=null)reenter.finish();if(unavailable)throw new RemoteException("fixture");}
        public void onError(int code,String message)throws RemoteException{errors++;this.code=code;if(reenter!=null)reenter.finish();if(unavailable)throw new RemoteException("fixture");}
    }
    private static Intent intent(Reply reply){
        // Exercise the actual Parcelable response path, not just a direct setter.
        Parcel parcel=Parcel.obtain();try{
            new AccountAuthenticatorResponse(reply).writeToParcel(parcel,0);parcel.setDataPosition(0);
            return new Intent().putExtra(AccountManager.KEY_ACCOUNT_MANAGER_RESPONSE,AccountAuthenticatorResponse.CREATOR.createFromParcel(parcel));
        }finally{parcel.recycle();}
    }
    public static void run(){
        Reply success=new Reply();Screen screen=new Screen();success.reenter=screen;screen.create(intent(success));
        Bundle result=new Bundle();result.putString(AccountManager.KEY_ACCOUNT_NAME,"synthetic-user");
        screen.setAccountAuthenticatorResult(result);
        if(success.results!=0||success.continued!=1)throw new AssertionError("Premature account result");
        screen.finish();screen.finish();
        if(!screen.isFinishing()||success.results!=1||success.errors!=0||!"synthetic-user".equals(success.value.getString(AccountManager.KEY_ACCOUNT_NAME)))throw new AssertionError("Account result lifecycle");
        for(boolean explicitNull:new boolean[]{false,true}){
            Reply canceled=new Reply();Screen empty=new Screen();canceled.reenter=empty;empty.create(intent(canceled));
            if(explicitNull){empty.setAccountAuthenticatorResult(result);empty.setAccountAuthenticatorResult(null);}
            empty.finish();empty.finish();
            if(canceled.errors!=1||canceled.code!=AccountManager.ERROR_CODE_CANCELED||canceled.results!=0||!empty.isFinishing())throw new AssertionError("Account activity cancellation");
        }
        for(boolean hasResult:new boolean[]{false,true}){
            Reply dead=new Reply();dead.unavailable=true;Screen lost=new Screen();lost.create(intent(dead));
            if(hasResult)lost.setAccountAuthenticatorResult(result);lost.finish();lost.finish();
            if(!lost.isFinishing()||dead.results+dead.errors!=1)throw new AssertionError("Unavailable response lifecycle");
        }
        Screen standalone=new Screen();standalone.create(new Intent());standalone.finish();
        if(!standalone.isFinishing())throw new AssertionError("Missing response finish");
        System.out.println("AUTHENTICATOR_ACTIVITY_LIFECYCLE_OK");
    }
}
