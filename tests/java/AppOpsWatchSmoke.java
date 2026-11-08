// SPDX-License-Identifier: GPL-3.0-only
import android.app.AppOpsManager;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.os.Process;
public final class AppOpsWatchSmoke {
    public static void main(String[] args){
        Looper.prepareMainLooper();final boolean[] allowed={false};final int[] calls={0};
        ContextWrapper context=new ContextWrapper(null){public String getPackageName(){return "org.shiraz.appops";}public int checkPermission(String permission,int pid,int uid){return allowed[0]?PackageManager.PERMISSION_GRANTED:PackageManager.PERMISSION_DENIED;}};
        AppOpsManager manager=new AppOpsManager(context);final Thread main=Thread.currentThread();
        AppOpsManager.OnOpChangedListener listener=new AppOpsManager.OnOpChangedListener(){public void onOpChanged(String op,String pkg){if(Thread.currentThread()!=main||!AppOpsManager.OPSTR_CAMERA.equals(op)||!"org.shiraz.appops".equals(pkg))throw new AssertionError("callback scope/thread");calls[0]++;}};
        manager.checkPackage(Process.myUid(),context.getPackageName());
        try{manager.checkPackage(Process.myUid(),"other.package");throw new AssertionError("foreign package accepted");}catch(SecurityException expected){}
        manager.startWatchingMode(AppOpsManager.OPSTR_CAMERA,context.getPackageName(),listener);
        manager.startWatchingMode(AppOpsManager.OPSTR_CAMERA,context.getPackageName(),listener);
        AppOpsManager.shirazPermissionChanged("android.permission.CAMERA");Looper.getMainLooper().shirazDispatchDue(100);
        if(calls[0]!=0)throw new AssertionError("unchanged mode notified");
        allowed[0]=true;AppOpsManager.shirazPermissionChanged("android.permission.CAMERA");Looper.getMainLooper().shirazDispatchDue(100);
        if(calls[0]!=1||manager.checkOpNoThrow(AppOpsManager.OPSTR_CAMERA,Process.myUid(),context.getPackageName())!=AppOpsManager.MODE_ALLOWED)throw new AssertionError("real mode change");
        manager.stopWatchingMode(listener);allowed[0]=false;AppOpsManager.shirazPermissionChanged(null);Looper.getMainLooper().shirazDispatchDue(100);
        if(calls[0]!=1)throw new AssertionError("listener remained after stop");
        Looper.shirazShutdownMain();System.out.println("APP_OPS_WATCH_OK");
    }
}
