// SPDX-License-Identifier: GPL-3.0-only
package android.accounts;

import android.app.Activity;
import android.os.Bundle;

/** Result lifecycle for authenticator-owned UI; launching that UI is separate. */
public class AccountAuthenticatorActivity extends Activity {
    private AccountAuthenticatorResponse response;
    private Bundle authenticatorResult;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        response=getIntent()==null?null:getIntent().<AccountAuthenticatorResponse>getParcelableExtra(AccountManager.KEY_ACCOUNT_MANAGER_RESPONSE);
        if(response!=null)response.onRequestContinued();
    }

    public final void setAccountAuthenticatorResult(Bundle result){authenticatorResult=result;}

    @Override public void finish(){
        AccountAuthenticatorResponse pending=response;response=null;
        try{
            if(pending!=null){
                if(authenticatorResult!=null)pending.onResult(authenticatorResult);
                else pending.onError(AccountManager.ERROR_CODE_CANCELED,"Account activity canceled");
            }
        }finally{super.finish();}
    }
}
