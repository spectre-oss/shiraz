// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
/** Main-thread permission ownership with manifest-bound capture consent. */
public final class PermissionRequests {
 private Activity owner;private final Handler handler;private String[] pending;private int code;private android.app.AlertDialog prompt;private String prompted;private final java.util.Set<String> resolved=new java.util.HashSet<String>();
 public PermissionRequests(Activity owner){checkThread();this.owner=owner;handler=new Handler(Looper.getMainLooper());}
 private static void checkThread(){if(Looper.getMainLooper()==null||Thread.currentThread()!=Looper.getMainLooper().getThread())throw new IllegalStateException("Permission requests require the main thread");}
 public void bind(Activity value){checkThread();handler.removeCallbacks(deliver);if(prompt!=null){prompt.dismiss();prompt=null;resolved.remove(prompted);prompted=null;}owner=value;if(value!=null&&pending!=null&&!handler.post(deliver)){pending=null;throw new IllegalStateException("Permission result Looper stopped");}}
 public void close(){checkThread();handler.removeCallbacks(deliver);if(prompt!=null){prompt.dismiss();prompt=null;}pending=null;resolved.clear();owner=null;}
 public void request(String[] permissions,int requestCode){
  checkThread();if(owner==null||owner.isDestroyed())throw new IllegalStateException("Permission request has no live Activity");
  if(requestCode<0)throw new IllegalArgumentException("requestCode should be >= 0");
  if(permissions==null)throw new NullPointerException("permissions");
  if(permissions.length>1024)throw new IllegalArgumentException("Permission request limit");
  for(String p:permissions)if(p==null||p.isEmpty()||p.length()>4096)throw new IllegalArgumentException("Invalid permission name");
  // Android API 27 cancels an overlapping request immediately with empty arrays.
  if(pending!=null){owner.onRequestPermissionsResult(requestCode,new String[0],new int[0]);return;}
  pending=permissions.clone();resolved.clear();code=requestCode;
  if(!handler.post(deliver)){pending=null;throw new IllegalStateException("Permission result Looper stopped");}
 }
 private final Runnable deliver=new Runnable(){public void run(){
  Activity target=owner;String[] permissions=pending;
  if(target==null||target.isDestroyed()||permissions==null)return;
  CapturePermissions policy=CapturePermissions.current();
  if(policy!=null)for(final String permission:permissions)if(CapturePermissions.supports(permission)&&!policy.granted(permission)&&policy.canRequest(permission)&&!resolved.contains(permission)){
   resolved.add(permission);prompted=permission;prompt=policy.prompt(target,permission,new Runnable(){public void run(){prompt=null;prompted=null;if(!handler.post(deliver))throw new IllegalStateException("Permission result Looper stopped");}});return;
  }
  int requestCode=code;pending=null;resolved.clear();
  int[] results=new int[permissions.length];for(int i=0;i<results.length;i++)results[i]=target.checkSelfPermission(permissions[i]);
  target.onRequestPermissionsResult(requestCode,permissions,results);
 }};
}
