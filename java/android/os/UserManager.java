// SPDX-License-Identifier: GPL-3.0-only
package android.os;
/** Single-user desktop session: the user is always unlocked. */
public class UserManager {
 private final String packageName;
 public UserManager(){packageName=null;}
 public UserManager(android.content.Context context){packageName=java.util.Objects.requireNonNull(context,"context").getPackageName();}
 /** This unmanaged session has no saved application policies; callers cannot query another app. */
 public Bundle getApplicationRestrictions(String target){
  java.util.Objects.requireNonNull(target,"packageName");
  if(packageName==null||!packageName.equals(target))throw new SecurityException("Application restrictions belong to the calling package");
  return new Bundle();
 }
 public Bundle getApplicationRestrictions(String target,UserHandle user){requireSessionUser(user);return getApplicationRestrictions(target);}

 public int getUserCount(){return 1;}
 public static UserManager get(android.content.Context context){return (UserManager)context.getSystemService(android.content.Context.USER_SERVICE);}
 /** The single desktop user has no managed profile or parent relationship. */
 public android.content.pm.UserInfo getProfileParent(int userId){return null;}
 public long getSerialNumberForUser(UserHandle user){return UserHandle.SYSTEM.equals(user)?0:-1;}
 public UserHandle getUserForSerialNumber(long serial){return serial==0?UserHandle.SYSTEM:null;}
 public boolean isUserUnlocked(){return true;}
 public boolean isSystemUser(){return true;}
 public boolean isDemoUser(){return false;}
 public boolean isUserRunning(UserHandle user){return UserHandle.SYSTEM.equals(user);}
 /** No device/profile policy controller supplies restrictions in this session. */
 public Bundle getUserRestrictions(){return new Bundle();}
 public Bundle getUserRestrictions(UserHandle user){requireSessionUser(user);return getUserRestrictions();}
 public static final int USER_OPERATION_SUCCESS = 0;
 public static final int USER_OPERATION_ERROR_UNKNOWN = 1;
 public static final int USER_OPERATION_ERROR_MANAGED_PROFILE = 2;
 public static final int USER_OPERATION_ERROR_MAX_RUNNING_USERS = 3;
 public static final int USER_OPERATION_ERROR_CURRENT_USER = 4;
 public static final int USER_OPERATION_ERROR_LOW_STORAGE = 5;
 public static final int USER_OPERATION_ERROR_MAX_USERS = 6;

 public static class UserOperationException extends RuntimeException {
  private final int userOperationResult;
  public UserOperationException(String message, int userOperationResult) {
   super(message);
   this.userOperationResult = userOperationResult;
  }
  public int getUserOperationResult() {
   return userOperationResult;
  }
 }

 public boolean hasUserRestriction(String key){java.util.Objects.requireNonNull(key,"key");return false;}
 public boolean hasUserRestriction(String key,UserHandle user){requireSessionUser(user);return hasUserRestriction(key);}
 private static void requireSessionUser(UserHandle user){java.util.Objects.requireNonNull(user,"user");if(!UserHandle.SYSTEM.equals(user))throw new SecurityException("User is outside the current session");}
}
