// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import java.io.FileDescriptor;
import java.io.PrintWriter;

@Deprecated
public abstract class FragmentHostCallback<E> extends FragmentContainer {
    private final Activity mActivity;
    private final Context mContext;
    private final Handler mHandler;
    private final int mWindowAnimations;

    public FragmentHostCallback(Context context, Handler handler, int windowAnimations) {
        mActivity = context instanceof Activity ? (Activity) context : null;
        mContext = context;
        mHandler = handler;
        mWindowAnimations = windowAnimations;
    }

    Context getContext() {
        return mContext;
    }

    Handler getHandler() {
        return mHandler;
    }

    public void onDump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
    }

    public boolean onShouldSaveFragmentState(Fragment fragment) {
        return true;
    }

    public LayoutInflater onGetLayoutInflater() {
        return LayoutInflater.from(mContext);
    }

    public boolean onUseFragmentManagerInflaterFactory() {
        return false;
    }

    public abstract E onGetHost();

    public void onInvalidateOptionsMenu() {
    }

    public void onStartActivityFromFragment(Fragment fragment, Intent intent, int requestCode, Bundle options) {
        if (requestCode != -1) {
            throw new IllegalStateException("Starting activity with a requestCode requires a FragmentActivity host");
        }
        if (mContext != null) {
            mContext.startActivity(intent, options);
        }
    }

    public void onStartIntentSenderFromFragment(Fragment fragment, IntentSender intent, int requestCode,
            Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, Bundle options)
            throws IntentSender.SendIntentException {
        if (requestCode != -1) {
            throw new IllegalStateException("Starting intent sender with a requestCode requires a FragmentActivity host");
        }
        if (mActivity != null) {
            mActivity.startIntentSender(intent, fillInIntent, flagsMask, flagsValues, extraFlags, options);
        } else if (mContext != null && intent != null) {
            intent.sendIntent(mContext, 0, fillInIntent, null, null);
        }
    }

    public void onRequestPermissionsFromFragment(Fragment fragment, String[] permissions, int requestCode) {
    }

    public boolean onHasWindowAnimations() {
        return true;
    }

    public int onGetWindowAnimations() {
        return mWindowAnimations;
    }

    public void onAttachFragment(Fragment fragment) {
    }

    @Override
    public <T extends View> T onFindViewById(int id) {
        return mActivity != null ? mActivity.<T>findViewById(id) : null;
    }

    @Override
    public boolean onHasView() {
        return true;
    }
}
