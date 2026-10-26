// SPDX-License-Identifier: GPL-3.0-only
package android.view.accessibility;
import android.os.Bundle;
import java.util.List;
/** Extension point: the default provider exposes no virtual nodes or actions. */
public class AccessibilityNodeProvider {
 public static final int HOST_VIEW_ID=-1;
 public AccessibilityNodeInfo createAccessibilityNodeInfo(int id){return null;}
 public List<AccessibilityNodeInfo> findAccessibilityNodeInfosByText(String text,int id){return null;}
 public boolean performAction(int id,int action,Bundle arguments){return false;}
 public AccessibilityNodeInfo findFocus(int focus){return null;}
 public void addExtraDataToAccessibilityNodeInfo(int id,AccessibilityNodeInfo info,String key,Bundle arguments){}
}
