// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.animation.Animator;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.transition.Transition;
import android.util.AndroidRuntimeException;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/** Platform fragments for nonvisual lifecycle owners. View containers remain unsupported. */
@Deprecated
public class Fragment implements ComponentCallbacks2, View.OnCreateContextMenuListener {
    @Deprecated
    public static class InstantiationException extends AndroidRuntimeException {
        public InstantiationException(String msg, Exception cause) {
            super(msg, cause);
        }
    }

    @Deprecated
    public static class SavedState implements Parcelable {
        final Bundle state;

        SavedState(Bundle state) {
            this.state = state;
        }

        SavedState(Parcel in, ClassLoader loader) {
            Bundle b = in.readBundle(loader != null ? loader : Fragment.class.getClassLoader());
            this.state = b;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeBundle(state);
        }

        public static final Parcelable.ClassLoaderCreator<SavedState> CREATOR =
                new Parcelable.ClassLoaderCreator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in, null);
                    }

                    @Override
                    public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                        return new SavedState(in, loader);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

    FragmentManager manager;
    FragmentManager childManager;
    Fragment parentFragment;
    Fragment targetFragment;
    int targetRequestCode;
    LoaderManager.Impl loaderManager;
    Activity activity;
    String tag;
    Bundle arguments;
    Bundle saved;
    int phase;
    boolean removing;
    boolean hidden;
    boolean detached;
    boolean hasOptionsMenu;
    boolean menuVisible = true;
    boolean userVisibleHint = true;
    private SharedElementCallback enterSharedElementCallback;
    private SharedElementCallback exitSharedElementCallback;
    private Transition enterTransition;
    private Transition returnTransition;
    private Transition exitTransition;
    private Transition reenterTransition;
    private Transition sharedElementEnterTransition;
    private Transition sharedElementReturnTransition;
    private boolean allowEnterTransitionOverlap = true;
    private boolean allowReturnTransitionOverlap = true;

    public Fragment() {}

    public static Fragment instantiate(Context context, String fname) {
        return instantiate(context, fname, null);
    }

    public static Fragment instantiate(Context context, String fname, Bundle args) {
        try {
            ClassLoader loader = context != null ? context.getClassLoader() : Fragment.class.getClassLoader();
            Class<? extends Fragment> clazz = Class.forName(fname, false, loader).asSubclass(Fragment.class);
            Fragment f = clazz.getConstructor().newInstance();
            if (args != null) {
                args.setClassLoader(f.getClass().getClassLoader());
                f.setArguments(args);
            }
            return f;
        } catch (Exception e) {
            throw new InstantiationException("Unable to instantiate fragment " + fname, e);
        }
    }

    @Override
    public final boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append(getClass().getSimpleName());
        sb.append('{');
        sb.append(Integer.toHexString(System.identityHashCode(this)));
        if (tag != null) {
            sb.append(' ');
            sb.append(tag);
        }
        sb.append('}');
        return sb.toString();
    }

    public final int getId() {
        return 0;
    }

    public final String getTag() {
        return tag;
    }

    public void setArguments(Bundle args) {
        if (manager != null && manager.isStateSaved()) {
            throw new IllegalStateException("Fragment state is saved");
        }
        arguments = args;
    }

    public final Bundle getArguments() {
        return arguments;
    }

    public final boolean isStateSaved() {
        return manager != null && manager.isStateSaved();
    }

    public void setInitialSavedState(SavedState state) {
        if (manager != null) {
            throw new IllegalStateException("Fragment already added");
        }
        saved = state != null && state.state != null ? state.state : null;
    }

    public void setTargetFragment(Fragment fragment, int requestCode) {
        targetFragment = fragment;
        targetRequestCode = requestCode;
    }

    public final Fragment getTargetFragment() {
        return targetFragment;
    }

    public final int getTargetRequestCode() {
        return targetRequestCode;
    }

    public Context getContext() {
        return activity;
    }

    public final Activity getActivity() {
        return activity;
    }

    public final Object getHost() {
        return activity;
    }

    public final Resources getResources() {
        if (activity == null) throw new IllegalStateException("Fragment is detached");
        return activity.getResources();
    }

    public final CharSequence getText(int resId) {
        return getResources().getText(resId);
    }

    public final String getString(int resId) {
        return getResources().getString(resId);
    }

    public final String getString(int resId, Object... formatArgs) {
        return getResources().getString(resId, formatArgs);
    }

    public final FragmentManager getFragmentManager() {
        return manager;
    }

    public final FragmentManager getChildFragmentManager() {
        if (childManager == null) {
            FragmentManager.Impl impl = new FragmentManager.Impl(activity, phase);
            impl.parent = this;
            childManager = impl;
        }
        return childManager;
    }

    public final Fragment getParentFragment() {
        return parentFragment;
    }

    public final boolean isAdded() {
        return activity != null && !removing;
    }

    public final boolean isDetached() {
        return detached;
    }

    public final boolean isRemoving() {
        return removing;
    }

    public final boolean isInLayout() {
        return false;
    }

    public final boolean isResumed() {
        return phase == 4;
    }

    public final boolean isVisible() {
        return isAdded() && !isHidden() && getView() != null && getView().getWindowToken() != null
                && getView().getVisibility() == View.VISIBLE;
    }

    public final boolean isHidden() {
        return hidden;
    }

    public void onHiddenChanged(boolean hidden) {}

    public void setRetainInstance(boolean retain) {
        if (retain) {
            throw new UnsupportedOperationException("Retained platform fragments are not implemented");
        }
    }

    public final boolean getRetainInstance() {
        return false;
    }

    public void setHasOptionsMenu(boolean hasMenu) {
        this.hasOptionsMenu = hasMenu;
    }

    public void setMenuVisibility(boolean menuVisible) {
        this.menuVisible = menuVisible;
    }

    public void setUserVisibleHint(boolean isVisibleToUser) {
        this.userVisibleHint = isVisibleToUser;
    }

    public boolean getUserVisibleHint() {
        return userVisibleHint;
    }

    @Deprecated
    public LoaderManager getLoaderManager() {
        if (loaderManager == null) {
            loaderManager = new LoaderManager.Impl();
        }
        return loaderManager;
    }

    public void startActivity(Intent intent) {
        startActivity(intent, null);
    }

    public void startActivity(Intent intent, Bundle options) {
        if (activity == null) {
            throw new IllegalStateException("Fragment " + this + " not attached to Activity");
        }
        activity.startActivityFromFragment(this, intent, -1, options);
    }

    public void startActivityForResult(Intent intent, int requestCode) {
        startActivityForResult(intent, requestCode, null);
    }

    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        if (activity == null) {
            throw new IllegalStateException("Fragment " + this + " not attached to Activity");
        }
        activity.startActivityFromFragment(this, intent, requestCode, options);
    }

    public void startIntentSenderForResult(
            IntentSender intent,
            int requestCode,
            Intent fillInIntent,
            int flagsMask,
            int flagsValues,
            int extraFlags,
            Bundle options)
            throws IntentSender.SendIntentException {
        if (activity == null) {
            throw new IllegalStateException("Fragment " + this + " not attached to Activity");
        }
        activity.startIntentSenderForResult(
                intent, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, options);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {}

    public final void requestPermissions(String[] permissions, int requestCode) {
        if (activity == null) {
            throw new IllegalStateException("Fragment " + this + " not attached to Activity");
        }
        activity.requestPermissions(permissions, requestCode);
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {}

    public boolean shouldShowRequestPermissionRationale(String permission) {
        return activity != null && activity.shouldShowRequestPermissionRationale(permission);
    }

    public LayoutInflater onGetLayoutInflater(Bundle savedInstanceState) {
        if (activity == null) {
            throw new IllegalStateException("Fragment is detached");
        }
        return activity.getLayoutInflater();
    }

    public final LayoutInflater getLayoutInflater() {
        return onGetLayoutInflater(saved);
    }

    @Deprecated
    public void onInflate(AttributeSet attrs, Bundle savedInstanceState) {}

    public void onInflate(Context context, AttributeSet attrs, Bundle savedInstanceState) {
        onInflate(attrs, savedInstanceState);
        if (context instanceof Activity) {
            onInflate((Activity) context, attrs, savedInstanceState);
        }
    }

    @Deprecated
    public void onInflate(Activity activity, AttributeSet attrs, Bundle savedInstanceState) {}

    public void onAttachFragment(Fragment childFragment) {}

    public void onAttach(Context context) {
        if (context instanceof Activity) onAttach((Activity) context);
    }

    @Deprecated
    public void onAttach(Activity activity) {}

    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        return null;
    }

    public void onCreate(Bundle state) {}

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        return null;
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {}

    public View getView() {
        return null;
    }

    public void onActivityCreated(Bundle state) {}

    public void onViewStateRestored(Bundle savedInstanceState) {}

    public void onStart() {}

    public void onResume() {}

    public void onSaveInstanceState(Bundle out) {}

    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        onMultiWindowModeChanged(isInMultiWindowMode);
    }

    @Deprecated
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {}

    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        onPictureInPictureModeChanged(isInPictureInPictureMode);
    }

    @Deprecated
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {}

    @Override
    public void onConfigurationChanged(Configuration config) {}

    public void onPause() {}

    public void onStop() {}

    @Override
    public void onLowMemory() {}

    @Override
    public void onTrimMemory(int level) {}

    public void onDestroyView() {}

    public void onDestroy() {}

    public void onDetach() {}

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {}

    public void onPrepareOptionsMenu(Menu menu) {}

    public void onDestroyOptionsMenu() {}

    public boolean onOptionsItemSelected(MenuItem item) {
        return false;
    }

    public void onOptionsMenuClosed(Menu menu) {}

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (activity != null) {
            activity.onCreateContextMenu(menu, v, menuInfo);
        }
    }

    public void registerForContextMenu(View view) {
        view.setOnCreateContextMenuListener(this);
    }

    public void unregisterForContextMenu(View view) {
        view.setOnCreateContextMenuListener(null);
    }

    public boolean onContextItemSelected(MenuItem item) {
        return false;
    }

    public void setEnterSharedElementCallback(SharedElementCallback callback) {
        enterSharedElementCallback = callback;
    }

    public void setExitSharedElementCallback(SharedElementCallback callback) {
        exitSharedElementCallback = callback;
    }

    public void setEnterTransition(Transition transition) {
        enterTransition = transition;
    }

    public Transition getEnterTransition() {
        return enterTransition;
    }

    public void setReturnTransition(Transition transition) {
        returnTransition = transition;
    }

    public Transition getReturnTransition() {
        return returnTransition != null ? returnTransition : enterTransition;
    }

    public void setExitTransition(Transition transition) {
        exitTransition = transition;
    }

    public Transition getExitTransition() {
        return exitTransition;
    }

    public void setReenterTransition(Transition transition) {
        reenterTransition = transition;
    }

    public Transition getReenterTransition() {
        return reenterTransition != null ? reenterTransition : exitTransition;
    }

    public void setSharedElementEnterTransition(Transition transition) {
        sharedElementEnterTransition = transition;
    }

    public Transition getSharedElementEnterTransition() {
        return sharedElementEnterTransition;
    }

    public void setSharedElementReturnTransition(Transition transition) {
        sharedElementReturnTransition = transition;
    }

    public Transition getSharedElementReturnTransition() {
        return sharedElementReturnTransition != null
                ? sharedElementReturnTransition
                : sharedElementEnterTransition;
    }

    public void setAllowEnterTransitionOverlap(boolean allow) {
        allowEnterTransitionOverlap = allow;
    }

    public boolean getAllowEnterTransitionOverlap() {
        return allowEnterTransitionOverlap;
    }

    public void setAllowReturnTransitionOverlap(boolean allow) {
        allowReturnTransitionOverlap = allow;
    }

    public boolean getAllowReturnTransitionOverlap() {
        return allowReturnTransitionOverlap;
    }

    public void postponeEnterTransition() {}

    public void startPostponedEnterTransition() {}

    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        if (writer != null) {
            writer.print(prefix);
            writer.println(this);
        }
    }
}
