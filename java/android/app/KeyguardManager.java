// SPDX-License-Identifier: GPL-3.0-only
package android.app;
/** Keyguard stubs for desktop hosts without a lock screen. */
public class KeyguardManager {
 public class KeyguardLock {
  public void disableKeyguard(){}
  public void reenableKeyguard(){}
 }
 public KeyguardLock newKeyguardLock(String tag){return new KeyguardLock();}
 public boolean isKeyguardLocked(){return false;}
 public boolean isKeyguardSecure(){return false;}
 public boolean inKeyguardRestrictedInputMode(){return false;}
 public boolean isDeviceLocked(){return false;}
 public boolean isDeviceSecure(){return false;}
 public boolean isKeyguardEngineered(){return false;}
 public android.content.Intent createConfirmDeviceCredentialIntent(CharSequence title,CharSequence description){return null;}
 public void requestDismissKeyguard(Activity activity,KeyguardDismissCallback callback){if(callback!=null)callback.onDismissSucceeded();}
 public static abstract class KeyguardDismissCallback {
  public void onDismissError(){}
  public void onDismissSucceeded(){}
  public void onDismissCancelled(){}
 }
}
