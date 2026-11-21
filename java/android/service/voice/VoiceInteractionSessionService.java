// SPDX-License-Identifier: GPL-3.0-only
package android.service.voice;

import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/** An active voice interaction session, hosted inside a service. */
public abstract class VoiceInteractionSessionService extends Service {
    private VoiceInteractionSession mSession;
    private final IBinder mBinder = new Binder();

    public VoiceInteractionSessionService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public abstract VoiceInteractionSession onNewSession(Bundle args);

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mSession != null) {
            mSession.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mSession != null) {
            mSession.onLowMemory();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (mSession != null) {
            mSession.onTrimMemory(level);
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (mSession != null) {
            mSession.dump("  ", fd, writer, args);
        } else if (writer != null) {
            writer.println("No active session");
        }
    }
}
