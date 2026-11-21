// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.sessionfixture;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
public final class DetailActivity extends Activity {
 protected void onCreate(Bundle state){
  super.onCreate(state);
  if(!getComponentName().getClassName().equals(getPackageName()+".DetailAlias"))throw new AssertionError("Alias component identity lost");
  if(!getIntent().getComponent().equals(getComponentName()))throw new AssertionError("Alias intent identity lost");
  setResult(RESULT_OK,new Intent().putExtra("alias",true));
  finish();
 }
}
