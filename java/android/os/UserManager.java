// SPDX-License-Identifier: GPL-3.0-only
package android.os;
/** Single-user desktop session: the user is always unlocked. */
public class UserManager {
 public long getSerialNumberForUser(UserHandle user){return UserHandle.SYSTEM.equals(user)?0:-1;}
 public boolean isUserUnlocked(){return true;}
 public boolean isSystemUser(){return true;}
 public boolean isUserRunning(UserHandle user){return true;}
}
