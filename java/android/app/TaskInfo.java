// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Parcel;

public class TaskInfo {
    public int taskId = -1;
    public boolean isRunning;
    public Intent baseIntent;
    public ComponentName baseActivity;
    public ComponentName topActivity;
    public ComponentName origActivity;
    public int numActivities;
    public ActivityManager.TaskDescription taskDescription;
    boolean mIsVisible;

    TaskInfo() {
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    void readTaskInfoFromParcel(Parcel source) {
        taskId = source.readInt();
        isRunning = source.readInt() != 0;
        baseIntent = source.readInt() != 0 ? Intent.CREATOR.createFromParcel(source) : null;
        baseActivity = readComponent(source);
        topActivity = readComponent(source);
        origActivity = readComponent(source);
        numActivities = source.readInt();
        taskDescription = source.readInt() != 0
                ? ActivityManager.TaskDescription.CREATOR.createFromParcel(source)
                : null;
        mIsVisible = source.readInt() != 0;
    }

    void writeTaskInfoToParcel(Parcel dest, int flags) {
        dest.writeInt(taskId);
        dest.writeInt(isRunning ? 1 : 0);
        if (baseIntent != null) {
            dest.writeInt(1);
            baseIntent.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        writeComponent(baseActivity, dest);
        writeComponent(topActivity, dest);
        writeComponent(origActivity, dest);
        dest.writeInt(numActivities);
        if (taskDescription != null) {
            dest.writeInt(1);
            taskDescription.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        dest.writeInt(mIsVisible ? 1 : 0);
    }

    private static ComponentName readComponent(Parcel source) {
        return source.readInt() != 0 ? new ComponentName(source.readString(), source.readString()) : null;
    }

    private static void writeComponent(ComponentName cn, Parcel dest) {
        if (cn != null) {
            dest.writeInt(1);
            dest.writeString(cn.getPackageName());
            dest.writeString(cn.getClassName());
        } else {
            dest.writeInt(0);
        }
    }

    @Override
    public String toString() {
        return "TaskInfo{taskId=" + taskId
                + " isRunning=" + isRunning
                + " baseIntent=" + baseIntent
                + " baseActivity=" + baseActivity
                + " topActivity=" + topActivity
                + " numActivities=" + numActivities
                + " isVisible=" + mIsVisible + "}";
    }
}
