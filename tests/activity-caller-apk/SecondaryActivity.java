// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.activitycaller;
public final class SecondaryActivity extends android.app.Activity {
 protected void onCreate(android.os.Bundle state){
  super.onCreate(state);
  String expected=getPackageName()+":secondary";
  if(!expected.equals(android.app.Application.getProcessName()))throw new AssertionError("Secondary process identity lost");
  if(!getComponentName().getClassName().equals(getPackageName()+".SecondaryActivity"))throw new AssertionError("Secondary activity identity lost");
  System.out.println("SECONDARY_ACTIVITY_PROCESS_OK pid="+android.os.Process.myPid()+" process="+expected);
  if(System.getenv("SHIRAZ_TEST_GUEST_ACTIVITY")!=null){
   if(!"original".equals(getIntent().getStringExtra("guest-handoff")))throw new AssertionError("Guest launch extras lost");
   if(System.getenv("SHIRAZ_PROCESS_CONTROL_FD")!=null)throw new AssertionError("Private worker control leaked to guest");
   if("kill".equals(System.getenv("SHIRAZ_TEST_GUEST_ACTIVITY"))){
    try{android.os.Process.killProcess(1);throw new AssertionError("Unowned PID accepted");}catch(SecurityException denied){}
    android.os.Process.killProcess(getIntent().getIntExtra("parent-pid",-1));
    System.out.println("OWNED_PARENT_TERMINATED_OK");
    android.content.Intent restart=new android.content.Intent(this,MainActivity.class)
      .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
      .putExtra("restarted",true).putExtra("previous-pid",getIntent().getIntExtra("parent-pid",-1));
    startActivity(restart);
   }
   System.out.println("GUEST_ACTIVITY_RECEIVED_OK");
  }
  finish();
 }
}
