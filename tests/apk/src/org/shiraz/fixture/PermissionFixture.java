// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.*;
import android.content.pm.PackageManager;
public final class PermissionFixture {
 private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void check(Context context){
  String location="android.permission.ACCESS_FINE_LOCATION";
  check(context.checkPermission(location,android.os.Process.myPid(),android.os.Process.myUid())==PackageManager.PERMISSION_DENIED,"Location has no grant");
  check(context.checkSelfPermission(location)==-1&&context.checkCallingOrSelfPermission(location)==-1&&context.checkCallingPermission(location)==-1,"Permission query variants");
  check(context.checkPermission(location,1,0)==-1,"Host root is not an Android grant");
  try{context.checkPermission(null,0,0);throw new AssertionError("Null permission");}catch(IllegalArgumentException expected){}
  try{context.enforceCallingOrSelfPermission(location,"location required");throw new AssertionError("Denied enforcement");}catch(SecurityException expected){check(expected.getMessage().contains("location required"),"Permission diagnostic");}
  final int[] seen={0,0};Context policy=new ContextWrapper(context){public int checkPermission(String p,int pid,int uid){seen[0]=pid;seen[1]=uid;return p.equals("fixture.allowed")?0:-1;}};
  Context nested=new ContextWrapper(new ContextWrapper(policy));check(nested.checkPermission("fixture.allowed",17,23)==0&&seen[0]==17&&seen[1]==23,"Wrapper delegates permission and identity");check(nested.checkPermission("fixture.denied",17,23)==-1,"Wrapper preserves denial");
  System.out.println("PERMISSION_QUERY_OK");
 }
}
