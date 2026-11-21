// SPDX-License-Identifier: GPL-3.0-only
package android.service.voice;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Set;

/** Top-level service of the current global voice interactor. */
public class VoiceInteractionService extends Service {
    public static final String SERVICE_INTERFACE = "android.service.voice.VoiceInteractionService";
    public static final String SERVICE_META_DATA = "android.voice_interaction";

    private final IBinder mBinder = new Binder();
    private int mDisabledShowContext;
    private Bundle mUiHints;

    public VoiceInteractionService() {
    }

    public void onLaunchVoiceAssistFromKeyguard() {
    }

    public void onPrepareToShowSession(Bundle args, int flags) {
    }

    public void onShowSessionFailed(Bundle args) {
    }

    public static boolean isActiveService(Context context, ComponentName service) {
        if (context == null || service == null) {
            throw new NullPointerException();
        }
        return false;
    }

    public void setDisabledShowContext(int flags) {
        mDisabledShowContext = flags;
    }

    public int getDisabledShowContext() {
        return mDisabledShowContext;
    }

    public void showSession(Bundle args, int flags) {
    }

    public Set<String> onGetSupportedVoiceActions(Set<String> voiceActions) {
        return Collections.emptySet();
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null && SERVICE_INTERFACE.equals(intent.getAction())) {
            return mBinder;
        }
        return null;
    }

    public void onReady() {
    }

    public void onShutdown() {
    }

    public final void setUiHints(Bundle hints) {
        if (hints == null) {
            throw new IllegalArgumentException("Hints must be non-null");
        }
        mUiHints = new Bundle(hints);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (pw != null) {
            pw.println("VOICE INTERACTION: disabledShowContext=" + mDisabledShowContext
                    + " uiHints=" + mUiHints);
        }
    }
}
