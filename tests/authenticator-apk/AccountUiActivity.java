// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.authenticatorfixture;
import android.accounts.*;
import android.os.Bundle;
public final class AccountUiActivity extends AccountAuthenticatorActivity {
 protected void onCreate(Bundle state){
  super.onCreate(state);
  String mode=getIntent().getStringExtra("fixture-mode");
  if(getIntent().getParcelableExtra(AccountManager.KEY_ACCOUNT_MANAGER_RESPONSE)==null)throw new AssertionError("Missing account callback");
  if(mode.startsWith("ui-local-")&&!getComponentName().getClassName().equals(getPackageName()+".PrivateAccountUi"))throw new AssertionError("Local UI alias identity");
  if("ui-token-die".equals(mode)){System.out.println("ACCOUNT_UI_ACTIVITY_DIED mode="+mode);System.out.flush();Runtime.getRuntime().halt(23);}
  if(mode.startsWith("ui-token-")&&!"ui-token-cancel".equals(mode)){
   Bundle result=new Bundle();result.putString(AccountManager.KEY_ACCOUNT_NAME,"ui-token-wrong-account".equals(mode)?"wrong":getIntent().getStringExtra("fixture-account"));
   result.putString(AccountManager.KEY_ACCOUNT_TYPE,getIntent().getStringExtra("fixture-type"));
   if(!"ui-token-missing".equals(mode))result.putString(AccountManager.KEY_AUTHTOKEN,"synthetic-ui-token");
   if("ui-token-custom".equals(mode))result.putLong(AbstractAccountAuthenticator.KEY_CUSTOM_TOKEN_EXPIRY,System.currentTimeMillis()+60000);
   if("ui-token-expiry".equals(mode))result.putString(AbstractAccountAuthenticator.KEY_CUSTOM_TOKEN_EXPIRY,"invalid");
   setAccountAuthenticatorResult(result);
  }else if("ui-result".equals(mode)||"ui-local-result".equals(mode)){
   Bundle result=new Bundle();result.putString(AccountManager.KEY_ACCOUNT_NAME,"ui-fixture");
   result.putString(AccountManager.KEY_ACCOUNT_TYPE,getIntent().getStringExtra("fixture-type"));setAccountAuthenticatorResult(result);
  }else if(!"ui-cancel".equals(mode)&&!"ui-token-cancel".equals(mode)&&!"ui-local-cancel".equals(mode))throw new AssertionError("Unknown UI mode");
  System.out.println("ACCOUNT_UI_ACTIVITY_OK mode="+mode);finish();
 }
}
