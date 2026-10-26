// SPDX-License-Identifier: GPL-3.0-only
package android.view.autofill;
import android.view.View;
import android.graphics.Rect;
/** Capability facade for a host without an autofill provider. No field data is retained. */
public final class AutofillManager {
 private AutofillManager(){}
 public static AutofillManager shirazUnavailable(){return new AutofillManager();}
 public boolean isEnabled(){return false;}
 public boolean isAutofillSupported(){return false;}
 public boolean hasEnabledAutofillServices(){return false;}
 // Android's no-feature path ignores these notifications before creating a session.
 public void requestAutofill(View view){}
 public void requestAutofill(View view,int virtualId,Rect bounds){}
 public void notifyViewEntered(View view){}
 public void notifyViewEntered(View view,int virtualId,Rect bounds){}
 public void notifyViewExited(View view){}
 public void notifyViewExited(View view,int virtualId){}
 public void notifyValueChanged(View view){}
 public void notifyValueChanged(View view,int virtualId,AutofillValue value){}
 public void notifyViewVisibilityChanged(View view,boolean visible){}
 public void notifyViewVisibilityChanged(View view,int virtualId,boolean visible){}
 public void commit(){}
 public void cancel(){}
 public void disableAutofillServices(){}
 public void registerCallback(AutofillCallback callback){}
 public void unregisterCallback(AutofillCallback callback){}
 public abstract static class AutofillCallback {
  public static final int EVENT_INPUT_SHOWN=1,EVENT_INPUT_HIDDEN=2,EVENT_INPUT_UNAVAILABLE=3;
  public void onAutofillEvent(View view,int event){}
  public void onAutofillEvent(View view,int virtualId,int event){}
 }
}
