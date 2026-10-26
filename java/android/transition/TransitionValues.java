// SPDX-License-Identifier: GPL-3.0-only
package android.transition;
import android.view.View;
import java.util.HashMap;
import java.util.Map;
/** Captured view property bag for Transition stubs. */
public class TransitionValues {
 public View view;
 public final Map<String,Object> values=new HashMap<String,Object>();
 @Override public String toString(){return "TransitionValues: view="+view+" values="+values;}
}
