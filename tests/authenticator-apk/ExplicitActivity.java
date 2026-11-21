// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.authenticatorfixture;
public final class ExplicitActivity extends android.accounts.AccountAuthenticatorActivity {
 protected void onCreate(android.os.Bundle state){
  super.onCreate(state);
  String component=getComponentName().getClassName();
  if(!component.equals(getPackageName()+".ExplicitActivity")&&!component.equals(getPackageName()+".ExplicitAlias"))throw new AssertionError("Explicit activity identity");
  if(!getIntent().getComponent().equals(getComponentName()))throw new AssertionError("Explicit launch component lost");
  if("test.SIGNED_LOGIN".equals(getIntent().getAction())){
   if(!"https://example.invalid/login".equals(getIntent().getDataString())||!getIntent().hasCategory("test.CATEGORY")||getIntent().getIntArrayExtra("fixture-values")[1]!=42||getIntent().getParcelableExtra(android.accounts.AccountManager.KEY_ACCOUNT_MANAGER_RESPONSE)==null)throw new AssertionError("Admitted Intent fields/callback lost");
   String mode=getIntent().getStringExtra("fixture-mode");
   if("result".equals(mode)){android.os.Bundle result=new android.os.Bundle();result.putString("fixture-result","synthetic-result");setAccountAuthenticatorResult(result);}
   else if(!"cancel".equals(mode))throw new AssertionError("Unexpected callback mode");
   System.out.println("SIGNED_ACTIVITY_INTENT_OK mode="+mode);
  }
  System.out.println("SIGNED_EXPLICIT_ACTIVITY_OK component="+component);
  finish();
 }
}
