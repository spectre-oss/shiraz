// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.os.Process;
import java.util.ArrayList;
import java.util.List;

/** In-process service for the loaded APK, not a host-wide task/process monitor. */
public class ActivityManager {
    /** Whether Android's test-harness mode is enabled for this runtime. */
    public static boolean isRunningInTestHarness() {
        return android.os.SystemProperties.getBoolean("ro.test_harness", false);
    }
 public static final class TaskDescription {
  public final String label;public final android.graphics.Bitmap icon;public final int color;
  public TaskDescription(String label,android.graphics.Bitmap icon,int color){this.label=label;this.icon=icon;this.color=color;}
  public TaskDescription(String label,int iconRes,int color){this(label,(android.graphics.Bitmap)null,color);}
 }
    static {System.loadLibrary("shiraz-host");}
    private static native long[] nativeMemoryInfo();
    public static class MemoryInfo {public long availMem,totalMem,threshold;public boolean lowMemory;}
    public void getMemoryInfo(MemoryInfo out){if(out==null)throw new NullPointerException("outInfo");long[] values=nativeMemoryInfo();out.totalMem=values[0];out.availMem=values[1];
        // Host policy: reserve up to128MiB (or one sixteenth on small hosts).
        out.threshold=Math.min(128L*1024*1024,out.totalMem/16);out.lowMemory=out.availMem<out.threshold;
    }
    // Standalone ART has one configured heap cap; no separate manifest large-heap tier.
    public int getMemoryClass(){return memoryClass(Runtime.getRuntime().maxMemory());}
    public int getLargeMemoryClass(){return getMemoryClass();}
    private static native int nativeGlesVersion();
    public android.content.pm.ConfigurationInfo getDeviceConfigurationInfo() {
        android.content.pm.ConfigurationInfo info = new android.content.pm.ConfigurationInfo();
        info.reqGlEsVersion = nativeGlesVersion();
        return info;
    }
    private static int memoryClass(long bytes){return (int)Math.max(1L,Math.min(Integer.MAX_VALUE,bytes/(1024L*1024L)));}
    private final String processName;
    private volatile boolean foreground = true;
    private ActivityManager(String processName) { this.processName = processName; }
    public static ActivityManager shirazForProcess(String processName) {
        if (processName == null || processName.isEmpty()) throw new IllegalArgumentException("process name");
        return new ActivityManager(processName);
    }
    public void shirazSetForeground(boolean foreground) { this.foreground = foreground; }
    public boolean isLowRamDevice() { return false; }
    public static void getMyMemoryState(RunningAppProcessInfo out) {
        if (out == null) throw new NullPointerException("outState");
        out.pid = Process.myPid();
        out.importance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
    }
    public List<RunningAppProcessInfo> getRunningAppProcesses() {
        RunningAppProcessInfo info = new RunningAppProcessInfo(processName, Process.myPid(), new String[]{processName});
        info.uid = Process.myUid();
        info.importance = foreground ? RunningAppProcessInfo.IMPORTANCE_FOREGROUND : RunningAppProcessInfo.IMPORTANCE_CACHED;
        ArrayList<RunningAppProcessInfo> result = new ArrayList<RunningAppProcessInfo>(1); result.add(info);
        return result;
    }
    public static class RunningAppProcessInfo {
        public static final int IMPORTANCE_FOREGROUND = 100, IMPORTANCE_VISIBLE = 200, IMPORTANCE_SERVICE = 300;
        public static final int IMPORTANCE_BACKGROUND = 400, IMPORTANCE_CACHED = 400, IMPORTANCE_EMPTY = 500;
        public String processName;
        public int pid, uid, importance, lru, flags;
        public String[] pkgList;
        public RunningAppProcessInfo() { }
        public RunningAppProcessInfo(String processName, int pid, String[] pkgList) {
            this.processName = processName; this.pid = pid; this.pkgList = pkgList;
        }
    }
}
