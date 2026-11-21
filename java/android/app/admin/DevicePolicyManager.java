// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import android.content.ComponentName;
import java.util.List;

/** Policy queries for Shiraz's unmanaged, single-user application environment.
 * No Android admin enrollment or host storage encryption authority is exposed.
 */
public class DevicePolicyManager {
 public static final int ENCRYPTION_STATUS_UNSUPPORTED=0,ENCRYPTION_STATUS_INACTIVE=1,
  ENCRYPTION_STATUS_ACTIVATING=2,ENCRYPTION_STATUS_ACTIVE=3,
  ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY=4,ENCRYPTION_STATUS_ACTIVE_PER_USER=5;
 public List<ComponentName> getActiveAdmins(){return null;}
 public boolean isAdminActive(ComponentName admin){return false;}
 public String getDeviceOwner(){return null;}
 public ComponentName getDeviceOwnerComponentOnAnyUser(){return null;}
 public String getDeviceOwnerNameOnAnyUser(){return null;}
 public ComponentName getProfileOwner(){return null;}
 public String getProfileOwnerNameAsUser(int user){requireCurrentUser(user);return null;}
 public boolean isDeviceOwnerApp(String packageName){return false;}
 public boolean isProfileOwnerApp(String packageName){return false;}
 public boolean isOrganizationOwnedDeviceWithManagedProfile(){return false;}
 public boolean isUninstallBlocked(ComponentName admin,String packageName){requireNoAdmin(admin);return false;}
 public int getStorageEncryptionStatus(){return ENCRYPTION_STATUS_UNSUPPORTED;}
 public SystemUpdatePolicy getSystemUpdatePolicy(){return null;}
 public void setSystemUpdatePolicy(ComponentName admin,SystemUpdatePolicy policy){throw unmanaged();}
 public void setUninstallBlocked(ComponentName admin,String packageName,boolean blocked){throw unmanaged();}
 public void wipeData(int flags){throw unmanaged();}
 public void lockNow(){throw unmanaged();}
 public void acknowledgeDeviceCompliant(){throw unmanaged();}
 public DevicePolicyResourcesManager getResources(){return new DevicePolicyResourcesManager();}
 public abstract static class InstallSystemUpdateCallback {
  public static final int UPDATE_ERROR_UNKNOWN=1,UPDATE_ERROR_INCORRECT_OS_VERSION=2,
   UPDATE_ERROR_UPDATE_FILE_INVALID=3,UPDATE_ERROR_FILE_NOT_FOUND=4,UPDATE_ERROR_BATTERY_LOW=5;
  public InstallSystemUpdateCallback(){}
  public void onInstallUpdateError(int errorCode,String errorMessage){}
 }
 public interface OnClearApplicationUserDataListener {
  void onApplicationUserDataCleared(String packageName,boolean succeeded);
 }
 private static SecurityException unmanaged(){return new SecurityException("No active Android device administrator");}
 private static void requireNoAdmin(ComponentName admin){if(admin!=null)throw unmanaged();}
 private static void requireCurrentUser(int user){if(user!=android.os.UserHandle.myUserId())throw new SecurityException("Cross-user device policy is unavailable");}
}
