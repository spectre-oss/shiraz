// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Deprecated
public class FragmentController {
    private final FragmentHostCallback<?> mHost;
    private FragmentManager mFragmentManager;
    private LoaderManager.Impl mLoaderManager;

    private FragmentController(FragmentHostCallback<?> callbacks) {
        mHost = Objects.requireNonNull(callbacks, "callbacks");
        Object hostObj = callbacks.onGetHost();
        Activity activity = hostObj instanceof Activity ? (Activity) hostObj
                : (callbacks.getContext() instanceof Activity ? (Activity) callbacks.getContext() : null);
        mFragmentManager = new FragmentManager.Impl(activity, 0);
    }

    public static final FragmentController createController(FragmentHostCallback<?> callbacks) {
        return new FragmentController(callbacks);
    }

    public FragmentManager getFragmentManager() {
        return mFragmentManager;
    }

    public LoaderManager getLoaderManager() {
        if (mLoaderManager == null) {
            mLoaderManager = new LoaderManager.Impl();
        }
        return mLoaderManager;
    }

    public Fragment findFragmentByWho(String who) {
        return mFragmentManager.findFragmentByTag(who);
    }

    public void attachHost(Fragment parent) {
    }

    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        return null;
    }

    public void noteStateNotSaved() {
    }

    public Parcelable saveAllState() {
        return mFragmentManager.save();
    }

    @Deprecated
    public void restoreAllState(Parcelable state, List<Fragment> nonConfigList) {
        if (state instanceof Bundle) {
            mFragmentManager.restore((Bundle) state);
        }
    }

    public void restoreAllState(Parcelable state, FragmentManagerNonConfig nonConfig) {
        if (state instanceof Bundle) {
            mFragmentManager.restore((Bundle) state);
        }
    }

    @Deprecated
    public List<Fragment> retainNonConfig() {
        return Collections.emptyList();
    }

    public FragmentManagerNonConfig retainNestedNonConfig() {
        return null;
    }

    public void dispatchCreate() {
        mFragmentManager.moveTo(1);
    }

    public void dispatchActivityCreated() {
        mFragmentManager.moveTo(2);
    }

    public void dispatchStart() {
        mFragmentManager.moveTo(3);
    }

    public void dispatchResume() {
        mFragmentManager.moveTo(4);
    }

    public void dispatchPause() {
        mFragmentManager.moveTo(3);
    }

    public void dispatchStop() {
        mFragmentManager.moveTo(2);
    }

    public void dispatchDestroyView() {
        mFragmentManager.moveTo(1);
    }

    public void dispatchDestroy() {
        mFragmentManager.destroy();
    }

    @Deprecated
    public void dispatchMultiWindowModeChanged(boolean isInMultiWindowMode) {
        for (Fragment f : mFragmentManager.getFragments()) {
            f.onMultiWindowModeChanged(isInMultiWindowMode);
        }
    }

    public void dispatchMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        for (Fragment f : mFragmentManager.getFragments()) {
            f.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        }
    }

    @Deprecated
    public void dispatchPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        for (Fragment f : mFragmentManager.getFragments()) {
            f.onPictureInPictureModeChanged(isInPictureInPictureMode);
        }
    }

    public void dispatchPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        for (Fragment f : mFragmentManager.getFragments()) {
            f.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        }
    }

    public void dispatchConfigurationChanged(Configuration newConfig) {
        mFragmentManager.configuration(newConfig);
    }

    public void dispatchLowMemory() {
        mFragmentManager.lowMemory();
    }

    public void dispatchTrimMemory(int level) {
        mFragmentManager.trimMemory(level);
    }

    public boolean dispatchCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        for (Fragment f : mFragmentManager.getFragments()) {
            f.onCreateOptionsMenu(menu, inflater);
        }
        return false;
    }

    public boolean dispatchPrepareOptionsMenu(Menu menu) {
        for (Fragment f : mFragmentManager.getFragments()) {
            f.onPrepareOptionsMenu(menu);
        }
        return false;
    }

    public boolean dispatchOptionsItemSelected(MenuItem item) {
        for (Fragment f : mFragmentManager.getFragments()) {
            if (f.onOptionsItemSelected(item)) {
                return true;
            }
        }
        return false;
    }

    public boolean dispatchContextItemSelected(MenuItem item) {
        for (Fragment f : mFragmentManager.getFragments()) {
            if (f.onContextItemSelected(item)) {
                return true;
            }
        }
        return false;
    }

    public void dispatchOptionsMenuClosed(Menu menu) {
        for (Fragment f : mFragmentManager.getFragments()) {
            f.onOptionsMenuClosed(menu);
        }
    }

    public boolean execPendingActions() {
        return mFragmentManager.executePendingTransactions();
    }

    public void doLoaderStart() {
        if (mLoaderManager != null) {
            mLoaderManager.doStart();
        }
    }

    public void doLoaderStop(boolean retain) {
        if (mLoaderManager != null) {
            mLoaderManager.doStop(retain);
        }
    }

    public void doLoaderDestroy() {
        if (mLoaderManager != null) {
            mLoaderManager.doDestroy();
        }
    }

    public void reportLoaderStart() {
    }

    public ArrayMap<String, LoaderManager> retainLoaderNonConfig() {
        return null;
    }

    public void restoreLoaderNonConfig(ArrayMap<String, LoaderManager> loaderManagers) {
    }

    public void dumpLoaders(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        if (mLoaderManager != null) {
            mLoaderManager.dump(prefix, fd, writer, args);
        }
    }
}
