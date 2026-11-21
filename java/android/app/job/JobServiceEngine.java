// SPDX-License-Identifier: GPL-3.0-only
package android.app.job;

import android.app.Notification;
import android.app.Service;
import android.os.Binder;
import android.os.IBinder;

public abstract class JobServiceEngine {
    private final Service mService;
    private final IBinder mBinder = new Binder();

    public JobServiceEngine(Service service) {
        mService = service;
    }

    public final IBinder getBinder() {
        return mBinder;
    }

    public abstract boolean onStartJob(JobParameters params);

    public abstract boolean onStopJob(JobParameters params);

    public void jobFinished(JobParameters params, boolean needsReschedule) {
    }

    public void onNetworkChanged(JobParameters params) {
    }

    public void updateTransferredNetworkBytes(
            JobParameters params, JobWorkItem item, long downloadBytes, long uploadBytes) {
    }

    public void updateEstimatedNetworkBytes(
            JobParameters params, JobWorkItem item, long downloadBytes, long uploadBytes) {
    }

    public void setNotification(
            JobParameters params,
            int notificationId,
            Notification notification,
            int jobEndNotificationPolicy) {
        if (notification == null) {
            throw new NullPointerException("notification");
        }
    }
}
