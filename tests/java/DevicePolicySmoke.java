// SPDX-License-Identifier: GPL-3.0-only
import android.app.admin.DevicePolicyManager;
import android.app.admin.SystemUpdatePolicy;
import android.content.ComponentName;
import android.os.Parcel;

public final class DevicePolicySmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void denied(Runnable operation){try{operation.run();throw new AssertionError("Unmanaged authority accepted");}catch(SecurityException expected){}}
 public static void main(String[] args){
  final DevicePolicyManager manager=new DevicePolicyManager();
  final ComponentName admin=new ComponentName("sample.app","sample.app.Admin");
  check(manager.getActiveAdmins()==null&&!manager.isAdminActive(admin),"no admin enrollment");
  check(manager.getDeviceOwner()==null&&manager.getProfileOwner()==null&&!manager.isDeviceOwnerApp("sample.app")&&!manager.isProfileOwnerApp("sample.app"),"no owners");
  check(!manager.isUninstallBlocked(null,"sample.app"),"no policy uninstall block");
  check(manager.getStorageEncryptionStatus()==DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED,"no encryption authority claim");
  denied(new Runnable(){public void run(){manager.isUninstallBlocked(admin,"sample.app");}});
  denied(new Runnable(){public void run(){manager.setUninstallBlocked(admin,"sample.app",true);}});
  denied(new Runnable(){public void run(){manager.wipeData(0);}});
  denied(new Runnable(){public void run(){manager.lockNow();}});
  denied(new Runnable(){public void run(){manager.getProfileOwnerNameAsUser(1);}});
  final SystemUpdatePolicy policy=SystemUpdatePolicy.createWindowedInstallPolicy(1380,60);
  denied(new Runnable(){public void run(){manager.setSystemUpdatePolicy(admin,policy);}});
  check(manager.getSystemUpdatePolicy()==null,"denied update leaves no policy");
  Parcel parcel=Parcel.obtain();policy.writeToParcel(parcel,0);parcel.setDataPosition(0);
  SystemUpdatePolicy decoded=SystemUpdatePolicy.CREATOR.createFromParcel(parcel);parcel.recycle();
  check(decoded.getPolicyType()==2&&decoded.getInstallWindowStart()==1380&&decoded.getInstallWindowEnd()==60,"overnight window parcel");
  check(SystemUpdatePolicy.createAutomaticInstallPolicy().getInstallWindowStart()==-1,"nonwindow sentinel");
  for(int invalid:new int[]{-1,1440})try{SystemUpdatePolicy.createWindowedInstallPolicy(invalid,0);throw new AssertionError("invalid minutes");}catch(IllegalArgumentException expected){}
  System.out.println("DEVICE_POLICY_OK");
 }
}
