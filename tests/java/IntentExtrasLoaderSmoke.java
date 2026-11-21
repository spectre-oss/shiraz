// SPDX-License-Identifier: GPL-3.0-only
import android.content.Intent;
import android.os.Bundle;
public final class IntentExtrasLoaderSmoke {
 public static void main(String[] args){
  ClassLoader loader=new ClassLoader(IntentExtrasLoaderSmoke.class.getClassLoader()){};
  Intent intent=new Intent();intent.setExtrasClassLoader(loader);
  if(intent.getExtras()!=null)throw new AssertionError("Allocated absent extras");
  intent.putExtra("value","retained");intent.setExtrasClassLoader(loader);
  if(intent.getExtras().getClassLoader()!=loader||!"retained".equals(intent.getStringExtra("value")))throw new AssertionError("Loader or data missing");
  Intent copy=new Intent(intent);if(copy.getExtras().getClassLoader()!=loader)throw new AssertionError("Copy lost loader");
  intent.setExtrasClassLoader(null);
  if(intent.getExtras().getClassLoader()!=null||copy.getExtras().getClassLoader()!=loader)throw new AssertionError("Copy isolation/null loader");
  System.out.println("INTENT_EXTRAS_LOADER_OK");
 }
}
