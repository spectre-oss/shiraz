// SPDX-License-Identifier: GPL-3.0-only
import android.view.*;
import android.view.accessibility.*;
public final class AccessibilitySmoke {
 public static void main(String[] args){
  android.os.Looper.prepareMainLooper();
  android.content.Context context=new android.content.ContextWrapper(null){public String getPackageName(){return "org.shiraz.fixture";}public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}};
  android.widget.FrameLayout root=org.shiraz.fixture.AccessibilityFixture.prepare(context);root.shirazAttachTree(ViewTreeObserver.shirazCreateWindowObserver());org.shiraz.fixture.AccessibilityFixture.checkAttached(root);root.shirazDetachTree();
  AccessibilityNodeInfo node=root.createAccessibilityNodeInfo();if(node.isVisibleToUser())throw new AssertionError("Detached node visible");node.recycle();try{node.getText();throw new AssertionError("Recycled node readable");}catch(IllegalStateException expected){}
  AccessibilityEvent event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);event.setContentChangeTypes(3);event.getText().add("source");AccessibilityRecord record=AccessibilityRecord.obtain();if(record.getScrollX()!=-1||record.getScrollY()!=-1||record.getItemCount()!=-1)throw new AssertionError("Undefined record fields");record.setItemCount(7);event.appendRecord(record);AccessibilityEvent copy=AccessibilityEvent.obtain(event);event.recycle();if(copy.getRecord(0).getItemCount()!=7||copy.getContentChangeTypes()!=3||!copy.getText().contains("source"))throw new AssertionError("Event copy");copy.recycle();
  AccessibilityNodeProvider provider=new AccessibilityNodeProvider();if(provider.createAccessibilityNodeInfo(-1)!=null||provider.performAction(-1,16,null))throw new AssertionError("Empty provider fabricated nodes/actions");
  View liveView=new View(context);if(liveView.getAccessibilityLiveRegion()!=View.ACCESSIBILITY_LIVE_REGION_NONE)throw new AssertionError("Default live region");liveView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);AccessibilityNodeInfo live=liveView.createAccessibilityNodeInfo(),liveCopy=AccessibilityNodeInfo.obtain(live);liveView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE);if(live.getLiveRegion()!=1||liveCopy.getLiveRegion()!=1||liveView.getAccessibilityLiveRegion()!=2)throw new AssertionError("Live region snapshot isolation");try{liveView.setAccessibilityLiveRegion(3);throw new AssertionError("Invalid live region accepted");}catch(IllegalArgumentException expected){}live.recycle();try{live.getLiveRegion();throw new AssertionError("Recycled live region readable");}catch(IllegalStateException expected){}liveCopy.recycle();
  System.out.println("ACCESSIBILITY_LOCAL_SNAPSHOT_OK");
 }
}
