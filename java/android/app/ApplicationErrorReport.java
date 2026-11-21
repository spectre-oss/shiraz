// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.ComponentName;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Printer;
import java.io.PrintWriter;
import java.io.StringWriter;

public class ApplicationErrorReport implements Parcelable {
    public static final int TYPE_NONE = 0;
    public static final int TYPE_CRASH = 1;
    public static final int TYPE_ANR = 2;
    public static final int TYPE_BATTERY = 3;
    public static final int TYPE_RUNNING_SERVICE = 5;

    public int type;
    public String packageName;
    public String installerPackageName;
    public String processName;
    public long time;
    public boolean systemApp;
    public CrashInfo crashInfo;
    public AnrInfo anrInfo;
    public BatteryInfo batteryInfo;
    public RunningServiceInfo runningServiceInfo;

    public ApplicationErrorReport() {
    }

    ApplicationErrorReport(Parcel in) {
        readFromParcel(in);
    }

    public static ComponentName getErrorReportReceiver(Context context, String packageName, int appFlags) {
        return null;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(type);
        dest.writeString(packageName);
        dest.writeString(installerPackageName);
        dest.writeString(processName);
        dest.writeLong(time);
        dest.writeInt(systemApp ? 1 : 0);
        boolean hasSubInfo = (type == TYPE_CRASH && crashInfo != null)
                || (type == TYPE_ANR && anrInfo != null)
                || (type == TYPE_BATTERY && batteryInfo != null)
                || (type == TYPE_RUNNING_SERVICE && runningServiceInfo != null);
        dest.writeInt(hasSubInfo ? 1 : 0);
        if (!hasSubInfo) {
            return;
        }
        switch (type) {
            case TYPE_CRASH:
                crashInfo.writeToParcel(dest, flags);
                break;
            case TYPE_ANR:
                anrInfo.writeToParcel(dest, flags);
                break;
            case TYPE_BATTERY:
                batteryInfo.writeToParcel(dest, flags);
                break;
            case TYPE_RUNNING_SERVICE:
                runningServiceInfo.writeToParcel(dest, flags);
                break;
            default:
                break;
        }
    }

    public void readFromParcel(Parcel in) {
        type = in.readInt();
        packageName = in.readString();
        installerPackageName = in.readString();
        processName = in.readString();
        time = in.readLong();
        systemApp = in.readInt() == 1;
        boolean hasSubInfo = in.readInt() == 1;
        crashInfo = null;
        anrInfo = null;
        batteryInfo = null;
        runningServiceInfo = null;
        if (!hasSubInfo) {
            return;
        }
        switch (type) {
            case TYPE_CRASH:
                crashInfo = new CrashInfo(in);
                break;
            case TYPE_ANR:
                anrInfo = new AnrInfo(in);
                break;
            case TYPE_BATTERY:
                batteryInfo = new BatteryInfo(in);
                break;
            case TYPE_RUNNING_SERVICE:
                runningServiceInfo = new RunningServiceInfo(in);
                break;
            default:
                break;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void dump(Printer pw, String prefix) {
        if (pw == null) {
            return;
        }
        String p = prefix != null ? prefix : "";
        pw.println(p + "type: " + type);
        pw.println(p + "packageName: " + packageName);
        pw.println(p + "installerPackageName: " + installerPackageName);
        pw.println(p + "processName: " + processName);
        pw.println(p + "time: " + time);
        pw.println(p + "systemApp: " + systemApp);
        if (type == TYPE_CRASH && crashInfo != null) {
            crashInfo.dump(pw, p);
        } else if (type == TYPE_ANR && anrInfo != null) {
            anrInfo.dump(pw, p);
        } else if (type == TYPE_BATTERY && batteryInfo != null) {
            batteryInfo.dump(pw, p);
        } else if (type == TYPE_RUNNING_SERVICE && runningServiceInfo != null) {
            runningServiceInfo.dump(pw, p);
        }
    }

    public static final Parcelable.Creator<ApplicationErrorReport> CREATOR =
            new Parcelable.Creator<ApplicationErrorReport>() {
                @Override
                public ApplicationErrorReport createFromParcel(Parcel source) {
                    return new ApplicationErrorReport(source);
                }

                @Override
                public ApplicationErrorReport[] newArray(int size) {
                    return new ApplicationErrorReport[size];
                }
            };

    public static class CrashInfo {
        public String exceptionClassName;
        public String exceptionMessage;
        public String throwFileName;
        public String throwClassName;
        public String throwMethodName;
        public int throwLineNumber;
        public String stackTrace;

        public CrashInfo() {
        }

        public CrashInfo(Throwable tr) {
            if (tr == null) {
                return;
            }
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            tr.printStackTrace(pw);
            pw.flush();
            stackTrace = sw.toString();

            String msg = tr.getMessage();
            Throwable root = tr;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
                if (root.getMessage() != null && !root.getMessage().isEmpty()) {
                    msg = root.getMessage();
                }
            }
            exceptionClassName = root.getClass().getName();
            exceptionMessage = msg;
            StackTraceElement[] trace = root.getStackTrace();
            if (trace != null && trace.length > 0) {
                StackTraceElement top = trace[0];
                throwFileName = top.getFileName();
                throwClassName = top.getClassName();
                throwMethodName = top.getMethodName();
                throwLineNumber = top.getLineNumber();
            } else {
                throwFileName = "unknown";
                throwClassName = "unknown";
                throwMethodName = "unknown";
                throwLineNumber = 0;
            }
        }

        public CrashInfo(Parcel in) {
            exceptionClassName = in.readString();
            exceptionMessage = in.readString();
            throwFileName = in.readString();
            throwClassName = in.readString();
            throwMethodName = in.readString();
            throwLineNumber = in.readInt();
            stackTrace = in.readString();
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(exceptionClassName);
            dest.writeString(exceptionMessage);
            dest.writeString(throwFileName);
            dest.writeString(throwClassName);
            dest.writeString(throwMethodName);
            dest.writeInt(throwLineNumber);
            dest.writeString(stackTrace);
        }

        public void dump(Printer pw, String prefix) {
            if (pw == null) {
                return;
            }
            String p = prefix != null ? prefix : "";
            pw.println(p + "exceptionClassName: " + exceptionClassName);
            pw.println(p + "exceptionMessage: " + exceptionMessage);
            pw.println(p + "throwFileName: " + throwFileName);
            pw.println(p + "throwClassName: " + throwClassName);
            pw.println(p + "throwMethodName: " + throwMethodName);
            pw.println(p + "throwLineNumber: " + throwLineNumber);
            pw.println(p + "stackTrace: " + stackTrace);
        }
    }

    public static class AnrInfo {
        public String activity;
        public String cause;
        public String info;

        public AnrInfo() {
        }

        public AnrInfo(Parcel in) {
            activity = in.readString();
            cause = in.readString();
            info = in.readString();
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(activity);
            dest.writeString(cause);
            dest.writeString(info);
        }

        public void dump(Printer pw, String prefix) {
            if (pw == null) {
                return;
            }
            String p = prefix != null ? prefix : "";
            pw.println(p + "activity: " + activity);
            pw.println(p + "cause: " + cause);
            pw.println(p + "info: " + info);
        }
    }

    public static class BatteryInfo {
        public int usagePercent;
        public long durationMicros;
        public String usageDetails;
        public String checkinDetails;

        public BatteryInfo() {
        }

        public BatteryInfo(Parcel in) {
            usagePercent = in.readInt();
            durationMicros = in.readLong();
            usageDetails = in.readString();
            checkinDetails = in.readString();
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(usagePercent);
            dest.writeLong(durationMicros);
            dest.writeString(usageDetails);
            dest.writeString(checkinDetails);
        }

        public void dump(Printer pw, String prefix) {
            if (pw == null) {
                return;
            }
            String p = prefix != null ? prefix : "";
            pw.println(p + "usagePercent: " + usagePercent);
            pw.println(p + "durationMicros: " + durationMicros);
            pw.println(p + "usageDetails: " + usageDetails);
            pw.println(p + "checkinDetails: " + checkinDetails);
        }
    }

    public static class RunningServiceInfo {
        public long durationMillis;
        public String serviceDetails;

        public RunningServiceInfo() {
        }

        public RunningServiceInfo(Parcel in) {
            durationMillis = in.readLong();
            serviceDetails = in.readString();
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(durationMillis);
            dest.writeString(serviceDetails);
        }

        public void dump(Printer pw, String prefix) {
            if (pw == null) {
                return;
            }
            String p = prefix != null ? prefix : "";
            pw.println(p + "durationMillis: " + durationMillis);
            pw.println(p + "serviceDetails: " + serviceDetails);
        }
    }
}
