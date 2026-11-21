// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Debug;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import android.text.TextUtils;
import android.util.Size;
import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** In-process service for the loaded APK, not a host-wide task/process monitor. */
public class ActivityManager {
    public static final String ACTION_REPORT_HEAP_LIMIT = "android.app.action.REPORT_HEAP_LIMIT";
    public static final String META_HOME_ALTERNATE = "android.app.home.alternate";
    public static final int LOCK_TASK_MODE_NONE = 0;
    public static final int LOCK_TASK_MODE_LOCKED = 1;
    public static final int LOCK_TASK_MODE_PINNED = 2;
    public static final int MOVE_TASK_WITH_HOME = 1;
    public static final int MOVE_TASK_NO_USER_ACTION = 2;
    public static final int RECENT_WITH_EXCLUDED = 1;
    public static final int RECENT_IGNORE_UNAVAILABLE = 2;

    /** Shiraz has no registered Android Monkey activity controller. */
    public static boolean isUserAMonkey() {
        return false;
    }

    /** Whether Android's test-harness mode is enabled for this runtime. */
    @Deprecated
    public static boolean isRunningInTestHarness() {
        return android.os.SystemProperties.getBoolean("ro.test_harness", false);
    }

    public static boolean isRunningInUserTestHarness() {
        return android.os.SystemProperties.getBoolean("persist.sys.test_harness", false);
    }

    public static boolean isLowMemoryKillReportSupported() {
        return true;
    }

    public static void setVrThread(int tid) {}

    public static class AppTask {
        AppTask() {}

        public void finishAndRemoveTask() {}

        public RecentTaskInfo getTaskInfo() {
            return new RecentTaskInfo();
        }

        public void moveToFront() {}

        public void startActivity(Context context, Intent intent, Bundle options) {
            if (context != null) {
                context.startActivity(intent, options);
            }
        }

        public void setExcludeFromRecents(boolean exclude) {}
    }

    public static class TaskDescription implements Parcelable {
        public String label;
        public Bitmap icon;
        public int color;
        private int iconRes;
        private int backgroundColor;
        private int statusBarColor;
        private int navigationBarColor;

        @Deprecated
        public TaskDescription(String label, Bitmap icon, int colorPrimary) {
            this.label = label;
            this.icon = icon;
            this.color = colorPrimary;
        }

        @Deprecated
        public TaskDescription(String label, int iconRes, int colorPrimary) {
            this.label = label;
            this.icon = null;
            this.iconRes = iconRes;
            this.color = colorPrimary;
        }

        @Deprecated
        public TaskDescription(String label, Bitmap icon) {
            this(label, icon, 0);
        }

        @Deprecated
        public TaskDescription(String label, int iconRes) {
            this(label, iconRes, 0);
        }

        @Deprecated
        public TaskDescription(String label) {
            this(label, (Bitmap) null, 0);
        }

        @Deprecated
        public TaskDescription() {
            this(null, (Bitmap) null, 0);
        }

        public TaskDescription(TaskDescription td) {
            if (td != null) {
                this.label = td.label;
                this.icon = td.icon;
                this.iconRes = td.iconRes;
                this.color = td.color;
                this.backgroundColor = td.backgroundColor;
                this.statusBarColor = td.statusBarColor;
                this.navigationBarColor = td.navigationBarColor;
            }
        }

        private TaskDescription(
                String label,
                int iconRes,
                int primaryColor,
                int backgroundColor,
                int statusBarColor,
                int navigationBarColor) {
            this.label = label;
            this.iconRes = iconRes;
            this.color = primaryColor;
            this.backgroundColor = backgroundColor;
            this.statusBarColor = statusBarColor;
            this.navigationBarColor = navigationBarColor;
        }

        private TaskDescription(Parcel source) {
            readFromParcel(source);
        }

        public String getLabel() {
            return label;
        }

        @Deprecated
        public Bitmap getIcon() {
            return icon;
        }

        public int getPrimaryColor() {
            return color;
        }

        public int getBackgroundColor() {
            return backgroundColor;
        }

        public int getStatusBarColor() {
            return statusBarColor;
        }

        public int getNavigationBarColor() {
            return navigationBarColor;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(label);
            dest.writeInt(iconRes);
            dest.writeInt(color);
            dest.writeInt(backgroundColor);
            dest.writeInt(statusBarColor);
            dest.writeInt(navigationBarColor);
        }

        public void readFromParcel(Parcel source) {
            label = source.readString();
            iconRes = source.readInt();
            color = source.readInt();
            backgroundColor = source.readInt();
            statusBarColor = source.readInt();
            navigationBarColor = source.readInt();
        }

        @Override
        public String toString() {
            return "TaskDescription Label: " + label + " PrimaryColor: " + Integer.toHexString(color);
        }

        @Override
        public int hashCode() {
            return Objects.hash(label, iconRes, color, backgroundColor, statusBarColor, navigationBarColor);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof TaskDescription)) return false;
            TaskDescription other = (TaskDescription) obj;
            return iconRes == other.iconRes
                    && color == other.color
                    && backgroundColor == other.backgroundColor
                    && statusBarColor == other.statusBarColor
                    && navigationBarColor == other.navigationBarColor
                    && Objects.equals(label, other.label)
                    && Objects.equals(icon, other.icon);
        }

        public static final Parcelable.Creator<TaskDescription> CREATOR =
                new Parcelable.Creator<TaskDescription>() {
                    @Override
                    public TaskDescription createFromParcel(Parcel source) {
                        return new TaskDescription(source);
                    }

                    @Override
                    public TaskDescription[] newArray(int size) {
                        return new TaskDescription[size];
                    }
                };

        public static final class Builder {
            private String label;
            private int iconRes;
            private int primaryColor;
            private int backgroundColor;
            private int statusBarColor;
            private int navigationBarColor;

            public Builder() {}

            public Builder setLabel(String label) {
                this.label = label;
                return this;
            }

            public Builder setIcon(int iconRes) {
                this.iconRes = iconRes;
                return this;
            }

            public Builder setPrimaryColor(int primaryColor) {
                this.primaryColor = primaryColor;
                return this;
            }

            public Builder setBackgroundColor(int backgroundColor) {
                this.backgroundColor = backgroundColor;
                return this;
            }

            public Builder setStatusBarColor(int statusBarColor) {
                this.statusBarColor = statusBarColor;
                return this;
            }

            public Builder setNavigationBarColor(int navigationBarColor) {
                this.navigationBarColor = navigationBarColor;
                return this;
            }

            public TaskDescription build() {
                return new TaskDescription(
                        label, iconRes, primaryColor, backgroundColor, statusBarColor, navigationBarColor);
            }
        }
    }

    static {
        System.loadLibrary("shiraz-host");
    }

    private static native long[] nativeMemoryInfo();

    public static class MemoryInfo implements Parcelable {
        public long availMem;
        public long totalMem;
        public long threshold;
        public boolean lowMemory;
        public long advertisedMem;

        public MemoryInfo() {}

        private MemoryInfo(Parcel source) {
            readFromParcel(source);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(availMem);
            dest.writeLong(totalMem);
            dest.writeLong(threshold);
            dest.writeInt(lowMemory ? 1 : 0);
            dest.writeLong(advertisedMem);
        }

        public void readFromParcel(Parcel source) {
            availMem = source.readLong();
            totalMem = source.readLong();
            threshold = source.readLong();
            lowMemory = source.readInt() != 0;
            advertisedMem = source.readLong();
        }

        public static final Parcelable.Creator<MemoryInfo> CREATOR =
                new Parcelable.Creator<MemoryInfo>() {
                    @Override
                    public MemoryInfo createFromParcel(Parcel source) {
                        return new MemoryInfo(source);
                    }

                    @Override
                    public MemoryInfo[] newArray(int size) {
                        return new MemoryInfo[size];
                    }
                };
    }

    public static class ProcessErrorStateInfo implements Parcelable {
        public static final int NO_ERROR = 0;
        public static final int CRASHED = 1;
        public static final int NOT_RESPONDING = 2;

        public int condition;
        public String processName;
        public int pid;
        public int uid;
        public String tag;
        public String shortMsg;
        public String longMsg;
        public String stackTrace;
        public byte[] crashData;

        public ProcessErrorStateInfo() {}

        private ProcessErrorStateInfo(Parcel source) {
            readFromParcel(source);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(condition);
            dest.writeString(processName);
            dest.writeInt(pid);
            dest.writeInt(uid);
            dest.writeString(tag);
            dest.writeString(shortMsg);
            dest.writeString(longMsg);
            dest.writeString(stackTrace);
            dest.writeByteArray(crashData);
        }

        public void readFromParcel(Parcel source) {
            condition = source.readInt();
            processName = source.readString();
            pid = source.readInt();
            uid = source.readInt();
            tag = source.readString();
            shortMsg = source.readString();
            longMsg = source.readString();
            stackTrace = source.readString();
            crashData = source.createByteArray();
        }

        public static final Parcelable.Creator<ProcessErrorStateInfo> CREATOR =
                new Parcelable.Creator<ProcessErrorStateInfo>() {
                    @Override
                    public ProcessErrorStateInfo createFromParcel(Parcel source) {
                        return new ProcessErrorStateInfo(source);
                    }

                    @Override
                    public ProcessErrorStateInfo[] newArray(int size) {
                        return new ProcessErrorStateInfo[size];
                    }
                };
    }

    public static class RecentTaskInfo extends TaskInfo implements Parcelable {
        @Deprecated public int id;
        @Deprecated public int persistentId;
        @Deprecated public CharSequence description;
        @Deprecated public int affiliatedTaskId;

        public RecentTaskInfo() {}

        private RecentTaskInfo(Parcel source) {
            readFromParcel(source);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public void readFromParcel(Parcel source) {
            id = source.readInt();
            persistentId = source.readInt();
            description = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
            affiliatedTaskId = source.readInt();
            readTaskInfoFromParcel(source);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(id);
            dest.writeInt(persistentId);
            TextUtils.writeToParcel(description, dest, flags);
            dest.writeInt(affiliatedTaskId);
            writeTaskInfoToParcel(dest, flags);
        }

        public static final Parcelable.Creator<RecentTaskInfo> CREATOR =
                new Parcelable.Creator<RecentTaskInfo>() {
                    @Override
                    public RecentTaskInfo createFromParcel(Parcel source) {
                        return new RecentTaskInfo(source);
                    }

                    @Override
                    public RecentTaskInfo[] newArray(int size) {
                        return new RecentTaskInfo[size];
                    }
                };
    }

    public static class RunningTaskInfo extends TaskInfo implements Parcelable {
        @Deprecated public int id;
        @Deprecated public CharSequence description;
        @Deprecated public Bitmap thumbnail;
        @Deprecated public int numRunning;

        public RunningTaskInfo() {}

        private RunningTaskInfo(Parcel source) {
            readFromParcel(source);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public void readFromParcel(Parcel source) {
            id = source.readInt();
            description = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
            numRunning = source.readInt();
            readTaskInfoFromParcel(source);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(id);
            TextUtils.writeToParcel(description, dest, flags);
            dest.writeInt(numRunning);
            writeTaskInfoToParcel(dest, flags);
        }

        public static final Parcelable.Creator<RunningTaskInfo> CREATOR =
                new Parcelable.Creator<RunningTaskInfo>() {
                    @Override
                    public RunningTaskInfo createFromParcel(Parcel source) {
                        return new RunningTaskInfo(source);
                    }

                    @Override
                    public RunningTaskInfo[] newArray(int size) {
                        return new RunningTaskInfo[size];
                    }
                };
    }

    public static class RunningServiceInfo implements Parcelable {
        public static final int FLAG_STARTED = 1;
        public static final int FLAG_FOREGROUND = 2;
        public static final int FLAG_SYSTEM_PROCESS = 4;
        public static final int FLAG_PERSISTENT_PROCESS = 8;

        public ComponentName service;
        public int pid;
        public int uid;
        public String process;
        public boolean foreground;
        public long activeSince;
        public boolean started;
        public int clientCount;
        public int crashCount;
        public long lastActivityTime;
        public long restarting;
        public int flags;
        public String clientPackage;
        public int clientLabel;

        public RunningServiceInfo() {}

        private RunningServiceInfo(Parcel source) {
            readFromParcel(source);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int parcelFlags) {
            if (service != null) {
                dest.writeInt(1);
                dest.writeString(service.getPackageName());
                dest.writeString(service.getClassName());
            } else {
                dest.writeInt(0);
            }
            dest.writeInt(pid);
            dest.writeInt(uid);
            dest.writeString(process);
            dest.writeInt(foreground ? 1 : 0);
            dest.writeLong(activeSince);
            dest.writeInt(started ? 1 : 0);
            dest.writeInt(clientCount);
            dest.writeInt(crashCount);
            dest.writeLong(lastActivityTime);
            dest.writeLong(restarting);
            dest.writeInt(flags);
            dest.writeString(clientPackage);
            dest.writeInt(clientLabel);
        }

        public void readFromParcel(Parcel source) {
            service = source.readInt() != 0 ? new ComponentName(source.readString(), source.readString()) : null;
            pid = source.readInt();
            uid = source.readInt();
            process = source.readString();
            foreground = source.readInt() != 0;
            activeSince = source.readLong();
            started = source.readInt() != 0;
            clientCount = source.readInt();
            crashCount = source.readInt();
            lastActivityTime = source.readLong();
            restarting = source.readLong();
            flags = source.readInt();
            clientPackage = source.readString();
            clientLabel = source.readInt();
        }

        public static final Parcelable.Creator<RunningServiceInfo> CREATOR =
                new Parcelable.Creator<RunningServiceInfo>() {
                    @Override
                    public RunningServiceInfo createFromParcel(Parcel source) {
                        return new RunningServiceInfo(source);
                    }

                    @Override
                    public RunningServiceInfo[] newArray(int size) {
                        return new RunningServiceInfo[size];
                    }
                };
    }

    public void getMemoryInfo(MemoryInfo out) {
        if (out == null) throw new NullPointerException("outInfo");
        long[] values = nativeMemoryInfo();
        out.totalMem = values[0];
        out.availMem = values[1];
        out.advertisedMem = values[0];
        // Host policy: reserve up to 128MiB (or one sixteenth on small hosts).
        out.threshold = Math.min(128L * 1024 * 1024, out.totalMem / 16);
        out.lowMemory = out.availMem < out.threshold;
    }

    // Standalone ART has one configured heap cap; no separate manifest large-heap tier.
    public int getMemoryClass() {
        return memoryClass(Runtime.getRuntime().maxMemory());
    }

    public int getLargeMemoryClass() {
        return getMemoryClass();
    }

    private static native int nativeGlesVersion();

    public android.content.pm.ConfigurationInfo getDeviceConfigurationInfo() {
        android.content.pm.ConfigurationInfo info = new android.content.pm.ConfigurationInfo();
        info.reqGlEsVersion = nativeGlesVersion();
        return info;
    }

    private static int memoryClass(long bytes) {
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, bytes / (1024L * 1024L)));
    }

    private final String processName;
    private static volatile ActivityManager current;
    private volatile java.util.function.IntSupplier serviceImportance;
    private volatile boolean foreground = true;
    private final Context context;

    private ActivityManager(String processName, Context context) {
        this.processName = processName;
        this.context = context;
    }

    private android.content.res.Resources iconResources() {
        return context == null ? android.content.res.Resources.getSystem() : context.getResources();
    }

    public int getLauncherLargeIconDensity() {
        android.content.res.Resources r = iconResources();
        int density = r.getDisplayMetrics().densityDpi;
        if (r.getConfiguration().smallestScreenWidthDp < 600) return density;
        int[] sources = {120, 160, 213, 240, 320, 480};
        int[] targets = {160, 240, 320, 320, 480, 640};
        for (int i = 0; i < sources.length; i++) {
            if (density == sources[i]) return targets[i];
        }
        return Math.round(density * 1.5f);
    }

    public int getLauncherLargeIconSize() {
        android.content.res.Resources r = iconResources();
        int resource = r.getIdentifier("app_icon_size", "dimen", "android");
        if (resource == 0) throw new android.content.res.Resources.NotFoundException("Framework app_icon_size");
        int size = r.getDimensionPixelSize(resource);
        if (r.getConfiguration().smallestScreenWidthDp < 600) return size;
        int density = r.getDisplayMetrics().densityDpi;
        int[] sources = {120, 160, 213, 240, 320, 480};
        int[] numerators = {160, 240, 320, 320, 480, 640};
        int[] denominators = {120, 160, 240, 240, 320, 480};
        for (int i = 0; i < sources.length; i++) {
            if (density == sources[i]) return (int) ((long) size * numerators[i] / denominators[i]);
        }
        return Math.round(size * 1.5f);
    }

    public static ActivityManager shirazForProcess(String processName, Context context) {
        if (processName == null || processName.isEmpty()) throw new IllegalArgumentException("process name");
        return new ActivityManager(processName, context);
    }

    public static ActivityManager shirazForProcess(String processName) {
        if (processName == null || processName.isEmpty()) throw new IllegalArgumentException("process name");
        return new ActivityManager(processName, null);
    }

    public void shirazSetForeground(boolean foreground) {
        this.foreground = foreground;
    }

    public static void shirazInstall(ActivityManager manager, java.util.function.IntSupplier services) {
        if (manager == null || services == null) throw new NullPointerException("process state");
        manager.serviceImportance = services;
        current = manager;
    }

    public static void shirazClear() {
        current = null;
    }

    private int importance() {
        if (foreground) return RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
        java.util.function.IntSupplier supplier = serviceImportance;
        return supplier == null ? RunningAppProcessInfo.IMPORTANCE_CACHED : supplier.getAsInt();
    }

    public boolean isLowRamDevice() {
        return false;
    }

    @Deprecated
    public List<RecentTaskInfo> getRecentTasks(int maxNum, int flags) throws SecurityException {
        return Collections.emptyList();
    }

    public List<AppTask> getAppTasks() {
        return Collections.emptyList();
    }

    public Size getAppTaskThumbnailSize() {
        return new Size(0, 0);
    }

    public int addAppTask(Activity activity, Intent intent, TaskDescription description, Bitmap thumbnail) {
        return -1;
    }

    @Deprecated
    public List<RunningTaskInfo> getRunningTasks(int maxNum) throws SecurityException {
        return Collections.emptyList();
    }

    public void moveTaskToFront(int taskId, int flags) {
        moveTaskToFront(taskId, flags, null);
    }

    public void moveTaskToFront(int taskId, int flags, Bundle options) {}

    public boolean isActivityStartAllowedOnDisplay(Context context, int displayId, Intent intent) {
        return displayId == 0;
    }

    @Deprecated
    public List<RunningServiceInfo> getRunningServices(int maxNum) throws SecurityException {
        return Collections.emptyList();
    }

    public PendingIntent getRunningServiceControlPanel(ComponentName service) throws SecurityException {
        return null;
    }

    public boolean clearApplicationUserData() {
        return false;
    }

    public List<ProcessErrorStateInfo> getProcessesInErrorState() {
        return null;
    }

    public boolean isBackgroundRestricted() {
        return false;
    }

    public List<ApplicationStartInfo> getHistoricalProcessStartReasons(int maxNum) {
        return Collections.emptyList();
    }

    public void addApplicationStartInfoCompletionListener(
            Executor executor, Consumer<ApplicationStartInfo> listener) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
    }

    public void removeApplicationStartInfoCompletionListener(Consumer<ApplicationStartInfo> listener) {
        Objects.requireNonNull(listener, "listener");
    }

    public void addStartInfoTimestamp(int key, long timestampNs) {}

    public List<ApplicationExitInfo> getHistoricalProcessExitReasons(String packageName, int pid, int maxNum) {
        return Collections.emptyList();
    }

    public void setProcessStateSummary(byte[] state) {}

    public Debug.MemoryInfo[] getProcessMemoryInfo(int[] pids) {
        if (pids == null) return new Debug.MemoryInfo[0];
        Debug.MemoryInfo[] out = new Debug.MemoryInfo[pids.length];
        for (int i = 0; i < pids.length; i++) {
            out[i] = new Debug.MemoryInfo();
        }
        return out;
    }

    @Deprecated
    public void restartPackage(String packageName) {}

    public void killBackgroundProcesses(String packageName) {}

    public void dumpPackageState(FileDescriptor fd, String packageName) {}

    public void setWatchHeapLimit(long pssSize) {}

    public void clearWatchHeapLimit() {}

    @Deprecated
    public boolean isInLockTaskMode() {
        return false;
    }

    public int getLockTaskModeState() {
        return LOCK_TASK_MODE_NONE;
    }

    public void appNotResponding(String reason) {
        Objects.requireNonNull(reason, "reason");
    }

    public static void getMyMemoryState(RunningAppProcessInfo out) {
        if (out == null) throw new NullPointerException("outState");
        out.pid = Process.myPid();
        out.uid = Process.myUid();
        ActivityManager manager = current;
        out.importance = manager == null ? RunningAppProcessInfo.IMPORTANCE_CACHED : manager.importance();
        out.lru = 0;
        out.importanceReasonCode = 0;
        out.lastTrimLevel = 0;
    }

    public List<RunningAppProcessInfo> getRunningAppProcesses() {
        RunningAppProcessInfo info =
                new RunningAppProcessInfo(processName, Process.myPid(), new String[] {processName});
        info.uid = Process.myUid();
        info.importance = importance();
        ArrayList<RunningAppProcessInfo> result = new ArrayList<RunningAppProcessInfo>(1);
        result.add(info);
        return result;
    }

    public static class RunningAppProcessInfo implements Parcelable {
        public static final int IMPORTANCE_FOREGROUND = 100;
        public static final int IMPORTANCE_FOREGROUND_SERVICE = 125;
        @Deprecated public static final int IMPORTANCE_PERCEPTIBLE_PRE_26 = 130;
        @Deprecated public static final int IMPORTANCE_TOP_SLEEPING_PRE_28 = 150;
        public static final int IMPORTANCE_VISIBLE = 200;
        public static final int IMPORTANCE_PERCEPTIBLE = 230;
        public static final int IMPORTANCE_SERVICE = 300;
        @Deprecated public static final int IMPORTANCE_TOP_SLEEPING = 325;
        public static final int IMPORTANCE_CANT_SAVE_STATE = 350;
        @Deprecated public static final int IMPORTANCE_BACKGROUND = 400;
        public static final int IMPORTANCE_CACHED = 400;
        @Deprecated public static final int IMPORTANCE_EMPTY = 500;
        public static final int IMPORTANCE_GONE = 1000;

        public static final int REASON_UNKNOWN = 0;
        public static final int REASON_PROVIDER_IN_USE = 1;
        public static final int REASON_SERVICE_IN_USE = 2;

        public String processName;
        public int pid;
        public int uid;
        public String[] pkgList;
        public int flags;
        public int lastTrimLevel;
        public int importance;
        public int lru;
        public int importanceReasonCode;
        public int importanceReasonPid;
        public ComponentName importanceReasonComponent;

        public RunningAppProcessInfo() {}

        public RunningAppProcessInfo(String processName, int pid, String[] pkgList) {
            this.processName = processName;
            this.pid = pid;
            this.pkgList = pkgList;
        }

        private RunningAppProcessInfo(Parcel source) {
            readFromParcel(source);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int parcelFlags) {
            dest.writeString(processName);
            dest.writeInt(pid);
            dest.writeInt(uid);
            dest.writeStringArray(pkgList);
            dest.writeInt(flags);
            dest.writeInt(lastTrimLevel);
            dest.writeInt(importance);
            dest.writeInt(lru);
            dest.writeInt(importanceReasonCode);
            dest.writeInt(importanceReasonPid);
            if (importanceReasonComponent != null) {
                dest.writeInt(1);
                dest.writeString(importanceReasonComponent.getPackageName());
                dest.writeString(importanceReasonComponent.getClassName());
            } else {
                dest.writeInt(0);
            }
        }

        public void readFromParcel(Parcel source) {
            processName = source.readString();
            pid = source.readInt();
            uid = source.readInt();
            pkgList = source.createStringArray();
            flags = source.readInt();
            lastTrimLevel = source.readInt();
            importance = source.readInt();
            lru = source.readInt();
            importanceReasonCode = source.readInt();
            importanceReasonPid = source.readInt();
            importanceReasonComponent = source.readInt() != 0
                    ? new ComponentName(source.readString(), source.readString())
                    : null;
        }

        public static final Parcelable.Creator<RunningAppProcessInfo> CREATOR =
                new Parcelable.Creator<RunningAppProcessInfo>() {
                    @Override
                    public RunningAppProcessInfo createFromParcel(Parcel source) {
                        return new RunningAppProcessInfo(source);
                    }

                    @Override
                    public RunningAppProcessInfo[] newArray(int size) {
                        return new RunningAppProcessInfo[size];
                    }
                };
    }
}
