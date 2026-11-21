// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Owner-thread platform fragment lifecycle with queued transactions and local saved state. */
@Deprecated
public abstract class FragmentManager {
    public static final int POP_BACK_STACK_INCLUSIVE = 1;

    @Deprecated
    public interface BackStackEntry {
        int getId();
        String getName();
        int getBreadCrumbTitleRes();
        int getBreadCrumbShortTitleRes();
        CharSequence getBreadCrumbTitle();
        CharSequence getBreadCrumbShortTitle();
    }

    @Deprecated
    public interface OnBackStackChangedListener {
        void onBackStackChanged();
    }

    @Deprecated
    public static abstract class FragmentLifecycleCallbacks {
        public FragmentLifecycleCallbacks() {}
        public void onFragmentPreAttached(FragmentManager fm, Fragment f, Context context) {}
        public void onFragmentAttached(FragmentManager fm, Fragment f, Context context) {}
        public void onFragmentPreCreated(FragmentManager fm, Fragment f, Bundle savedInstanceState) {}
        public void onFragmentCreated(FragmentManager fm, Fragment f, Bundle savedInstanceState) {}
        public void onFragmentActivityCreated(FragmentManager fm, Fragment f, Bundle savedInstanceState) {}
        public void onFragmentViewCreated(FragmentManager fm, Fragment f, View v, Bundle savedInstanceState) {}
        public void onFragmentStarted(FragmentManager fm, Fragment f) {}
        public void onFragmentResumed(FragmentManager fm, Fragment f) {}
        public void onFragmentPaused(FragmentManager fm, Fragment f) {}
        public void onFragmentStopped(FragmentManager fm, Fragment f) {}
        public void onFragmentSaveInstanceState(FragmentManager fm, Fragment f, Bundle outState) {}
        public void onFragmentViewDestroyed(FragmentManager fm, Fragment f) {}
        public void onFragmentDestroyed(FragmentManager fm, Fragment f) {}
        public void onFragmentDetached(FragmentManager fm, Fragment f) {}
    }

    public FragmentManager() {}

    public abstract FragmentTransaction beginTransaction();
    public abstract boolean executePendingTransactions();
    public abstract Fragment findFragmentById(int id);
    public abstract Fragment findFragmentByTag(String tag);
    public abstract void popBackStack();
    public abstract boolean popBackStackImmediate();
    public abstract void popBackStack(String name, int flags);
    public abstract boolean popBackStackImmediate(String name, int flags);
    public abstract void popBackStack(int id, int flags);
    public abstract boolean popBackStackImmediate(int id, int flags);
    public abstract int getBackStackEntryCount();
    public abstract BackStackEntry getBackStackEntryAt(int index);
    public abstract void addOnBackStackChangedListener(OnBackStackChangedListener listener);
    public abstract void removeOnBackStackChangedListener(OnBackStackChangedListener listener);
    public abstract void putFragment(Bundle bundle, String key, Fragment fragment);
    public abstract Fragment getFragment(Bundle bundle, String key);
    public abstract List<Fragment> getFragments();
    public abstract Fragment.SavedState saveFragmentInstanceState(Fragment f);
    public abstract boolean isDestroyed();
    public abstract void registerFragmentLifecycleCallbacks(FragmentLifecycleCallbacks cb, boolean recursive);
    public abstract void unregisterFragmentLifecycleCallbacks(FragmentLifecycleCallbacks cb);
    public abstract Fragment getPrimaryNavigationFragment();
    public abstract void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args);
    public static void enableDebugLogging(boolean enabled) {}
    public void invalidateOptionsMenu() {}
    public abstract boolean isStateSaved();

    void checkThread() {}
    int enqueue(FragmentTransaction.Impl transaction, boolean loss, boolean now) {
        throw new UnsupportedOperationException();
    }
    void moveTo(int target) {}
    void configuration(Configuration config) {}
    void lowMemory() {}
    void trimMemory(int level) {}
    Bundle save() { return new Bundle(); }
    void restore(Bundle in) {}
    void destroy() {}

    static final class Impl extends FragmentManager {
        private final Activity host;
        Fragment parent;
        Fragment primaryNav;
        private final Thread owner = Thread.currentThread();
        private final ArrayList<Fragment> fragments = new ArrayList<Fragment>();
        private final ArrayDeque<FragmentTransaction.Impl> pending = new ArrayDeque<FragmentTransaction.Impl>();
        private final ArrayList<OnBackStackChangedListener> backStackListeners =
                new ArrayList<OnBackStackChangedListener>();
        private final ArrayList<FragmentLifecycleCallbacks> lifecycleCallbacks =
                new ArrayList<FragmentLifecycleCallbacks>();
        private Handler handler;
        private int phase;
        private boolean executing;
        private boolean saved;
        private boolean destroyed;
        private final Runnable drain = new Runnable() {
            public void run() {
                executePendingTransactions();
            }
        };

        Impl(Activity host, int phase) {
            this.host = host;
            this.phase = phase;
        }

        @Override
        void checkThread() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Fragment manager owner thread required");
            }
        }

        @Override
        public FragmentTransaction beginTransaction() {
            checkThread();
            if (destroyed) throw new IllegalStateException("Fragment manager destroyed");
            return new FragmentTransaction.Impl(this);
        }

        @Override
        public Fragment findFragmentById(int id) {
            checkThread();
            return null;
        }

        @Override
        public Fragment findFragmentByTag(String tag) {
            checkThread();
            if (tag == null) return null;
            for (int i = fragments.size() - 1; i >= 0; i--) {
                if (tag.equals(fragments.get(i).tag)) return fragments.get(i);
            }
            return null;
        }

        @Override
        public void popBackStack() {
            checkThread();
        }

        @Override
        public boolean popBackStackImmediate() {
            checkThread();
            return false;
        }

        @Override
        public void popBackStack(String name, int flags) {
            checkThread();
        }

        @Override
        public boolean popBackStackImmediate(String name, int flags) {
            checkThread();
            return false;
        }

        @Override
        public void popBackStack(int id, int flags) {
            checkThread();
        }

        @Override
        public boolean popBackStackImmediate(int id, int flags) {
            checkThread();
            return false;
        }

        @Override
        public int getBackStackEntryCount() {
            checkThread();
            return 0;
        }

        @Override
        public BackStackEntry getBackStackEntryAt(int index) {
            checkThread();
            throw new IndexOutOfBoundsException("Index: " + index);
        }

        @Override
        public void addOnBackStackChangedListener(OnBackStackChangedListener listener) {
            if (listener != null && !backStackListeners.contains(listener)) {
                backStackListeners.add(listener);
            }
        }

        @Override
        public void removeOnBackStackChangedListener(OnBackStackChangedListener listener) {
            backStackListeners.remove(listener);
        }

        @Override
        public void putFragment(Bundle bundle, String key, Fragment fragment) {
            checkThread();
            if (fragment == null || fragment.manager != this) {
                throw new IllegalStateException("Fragment is not currently in the FragmentManager");
            }
            int index = fragments.indexOf(fragment);
            bundle.putInt(key, index);
        }

        @Override
        public Fragment getFragment(Bundle bundle, String key) {
            checkThread();
            int index = bundle.getInt(key, -1);
            if (index < 0 || index >= fragments.size()) {
                return null;
            }
            return fragments.get(index);
        }

        @Override
        public List<Fragment> getFragments() {
            checkThread();
            return new ArrayList<Fragment>(fragments);
        }

        @Override
        public Fragment.SavedState saveFragmentInstanceState(Fragment f) {
            checkThread();
            if (f == null || f.manager != this) {
                throw new IllegalStateException("Fragment is not currently in the FragmentManager");
            }
            if (f.phase <= 0) {
                return null;
            }
            Bundle out = new Bundle();
            f.onSaveInstanceState(out);
            return out.isEmpty() ? null : new Fragment.SavedState(out);
        }

        @Override
        public boolean isDestroyed() {
            return destroyed;
        }

        @Override
        public void registerFragmentLifecycleCallbacks(FragmentLifecycleCallbacks cb, boolean recursive) {
            if (cb != null) {
                lifecycleCallbacks.add(cb);
            }
        }

        @Override
        public void unregisterFragmentLifecycleCallbacks(FragmentLifecycleCallbacks cb) {
            lifecycleCallbacks.remove(cb);
        }

        @Override
        public Fragment getPrimaryNavigationFragment() {
            return primaryNav;
        }

        @Override
        public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
            if (writer != null) {
                writer.print(prefix);
                writer.print("Fragments count=");
                writer.println(fragments.size());
            }
        }

        @Override
        public void invalidateOptionsMenu() {
            if (host != null) {
                host.invalidateOptionsMenu();
            }
        }

        @Override
        public boolean isStateSaved() {
            return saved;
        }

        @Override
        int enqueue(FragmentTransaction.Impl transaction, boolean loss, boolean now) {
            checkThread();
            if (transaction.committed) throw new IllegalStateException("Transaction already committed");
            if (destroyed || (saved && !loss)) {
                throw new IllegalStateException("Fragment state is saved or destroyed");
            }
            if (now && executing) throw new IllegalStateException("Recursive fragment execution");
            if (!now && pending.size() >= 256) {
                throw new IllegalStateException("Pending fragment transaction limit");
            }
            if (!now && handler == null) handler = new Handler(Looper.getMainLooper());
            transaction.committed = true;
            if (now) {
                executing = true;
                try {
                    apply(transaction);
                } finally {
                    executing = false;
                }
            } else {
                pending.add(transaction);
                if (pending.size() == 1 && !handler.post(drain)) {
                    pending.remove(transaction);
                    throw new IllegalStateException("Fragment queue is closed");
                }
            }
            return -1;
        }

        private void apply(FragmentTransaction.Impl transaction) {
            ArrayList<Fragment> projected = new ArrayList<Fragment>(fragments);
            for (FragmentTransaction.Impl.Op op : transaction.operations) {
                Fragment f = op.fragment;
                if (op.cmd == FragmentTransaction.Impl.OP_ADD) {
                    if (projected.contains(f) || (f.manager != null && f.manager != this)) {
                        throw new IllegalStateException("Fragment already owned");
                    }
                    projected.add(f);
                    if (projected.size() > 64) {
                        throw new IllegalStateException("Fragment instance limit");
                    }
                } else if (op.cmd == FragmentTransaction.Impl.OP_REMOVE) {
                    if (!projected.remove(f)) {
                        throw new IllegalStateException("Fragment is not added");
                    }
                }
            }
            for (FragmentTransaction.Impl.Op op : transaction.operations) {
                Fragment f = op.fragment;
                switch (op.cmd) {
                    case FragmentTransaction.Impl.OP_ADD:
                        f.manager = this;
                        f.parentFragment = parent;
                        f.tag = op.tag;
                        f.removing = false;
                        f.detached = false;
                        fragments.add(f);
                        move(f, phase);
                        break;
                    case FragmentTransaction.Impl.OP_REMOVE:
                        f.removing = true;
                        try {
                            move(f, 0);
                        } finally {
                            fragments.remove(f);
                            f.manager = null;
                            f.parentFragment = null;
                        }
                        break;
                    case FragmentTransaction.Impl.OP_HIDE:
                        if (!f.hidden) {
                            f.hidden = true;
                            f.onHiddenChanged(true);
                        }
                        break;
                    case FragmentTransaction.Impl.OP_SHOW:
                        if (f.hidden) {
                            f.hidden = false;
                            f.onHiddenChanged(false);
                        }
                        break;
                    case FragmentTransaction.Impl.OP_DETACH:
                        if (!f.detached) {
                            f.detached = true;
                            if (f.phase > 1) {
                                move(f, 1);
                            }
                        }
                        break;
                    case FragmentTransaction.Impl.OP_ATTACH:
                        if (f.detached) {
                            f.detached = false;
                            move(f, phase);
                        }
                        break;
                    case FragmentTransaction.Impl.OP_PRIMARY_NAV:
                        primaryNav = f;
                        break;
                    default:
                        break;
                }
            }
            if (transaction.commitRunnables != null) {
                for (Runnable r : transaction.commitRunnables) {
                    r.run();
                }
            }
        }

        @Override
        public boolean executePendingTransactions() {
            checkThread();
            if (executing) throw new IllegalStateException("Recursive fragment execution");
            if (destroyed) return false;
            if (handler != null) handler.removeCallbacks(drain);
            boolean changed = false;
            executing = true;
            try {
                int count = 0;
                while (!pending.isEmpty()) {
                    if (++count > 256) {
                        throw new IllegalStateException("Reentrant fragment transaction limit");
                    }
                    apply(pending.remove());
                    changed = true;
                }
            } finally {
                executing = false;
            }
            return changed;
        }

        private FragmentLifecycleCallbacks[] snapshotLifecycleCallbacks() {
            if (lifecycleCallbacks.isEmpty()) return null;
            return lifecycleCallbacks.toArray(new FragmentLifecycleCallbacks[lifecycleCallbacks.size()]);
        }

        private void move(Fragment f, int target) {
            while (f.phase < target) {
                int next = f.phase + 1;
                f.phase = next;
                FragmentLifecycleCallbacks[] cbs = snapshotLifecycleCallbacks();
                if (next == 1) {
                    if (cbs != null) {
                        for (FragmentLifecycleCallbacks cb : cbs) cb.onFragmentPreAttached(this, f, host);
                    }
                    f.activity = host;
                    f.onAttach((Context) host);
                    if (parent != null) {
                        parent.onAttachFragment(f);
                    } else if (host != null) {
                        host.onAttachFragment(f);
                    }
                    if (cbs != null) {
                        for (FragmentLifecycleCallbacks cb : cbs) cb.onFragmentAttached(this, f, host);
                        for (FragmentLifecycleCallbacks cb : cbs) cb.onFragmentPreCreated(this, f, f.saved);
                    }
                    f.onCreate(f.saved);
                    if (cbs != null) {
                        for (FragmentLifecycleCallbacks cb : cbs) cb.onFragmentCreated(this, f, f.saved);
                    }
                } else if (next == 2) {
                    LayoutInflater inflater = host != null ? host.getLayoutInflater() : null;
                    if (f.onCreateView(inflater, null, f.saved) != null) {
                        throw new UnsupportedOperationException("Platform fragment views are not implemented");
                    }
                    f.onActivityCreated(f.saved);
                    if (cbs != null) {
                        for (FragmentLifecycleCallbacks cb : cbs) cb.onFragmentActivityCreated(this, f, f.saved);
                    }
                    f.saved = null;
                } else if (next == 3) {
                    f.onStart();
                    if (cbs != null) {
                        for (FragmentLifecycleCallbacks cb : cbs) cb.onFragmentStarted(this, f);
                    }
                } else {
                    f.onResume();
                    if (cbs != null) {
                        for (FragmentLifecycleCallbacks cb : cbs) cb.onFragmentResumed(this, f);
                    }
                }
            }
            while (f.phase > target) {
                int old = f.phase--;
                FragmentLifecycleCallbacks[] cbs = snapshotLifecycleCallbacks();
                if (old == 4) {
                    f.onPause();
                    if (cbs != null) {
                        for (FragmentLifecycleCallbacks cb : cbs) cb.onFragmentPaused(this, f);
                    }
                } else if (old == 3) {
                    f.onStop();
                    if (cbs != null) {
                        for (FragmentLifecycleCallbacks cb : cbs) cb.onFragmentStopped(this, f);
                    }
                } else if (old == 2) {
                    f.onDestroyView();
                    if (cbs != null) {
                        for (FragmentLifecycleCallbacks cb : cbs) cb.onFragmentViewDestroyed(this, f);
                    }
                } else {
                    try {
                        f.onDestroy();
                        if (cbs != null) {
                            for (FragmentLifecycleCallbacks cb : cbs) cb.onFragmentDestroyed(this, f);
                        }
                    } finally {
                        try {
                            f.onDetach();
                            if (cbs != null) {
                                for (FragmentLifecycleCallbacks cb : cbs) cb.onFragmentDetached(this, f);
                            }
                        } finally {
                            f.activity = null;
                        }
                    }
                }
            }
        }

        @Override
        void moveTo(int target) {
            checkThread();
            if (destroyed) throw new IllegalStateException("Fragment manager destroyed");
            if (executing) throw new IllegalStateException("Recursive fragment lifecycle");
            executePendingTransactions();
            phase = target;
            if (target >= 3) saved = false;
            executing = true;
            try {
                for (Fragment f : new ArrayList<Fragment>(fragments)) move(f, target);
            } finally {
                executing = false;
            }
        }

        @Override
        void configuration(Configuration config) {
            checkThread();
            for (Fragment f : new ArrayList<Fragment>(fragments)) f.onConfigurationChanged(config);
        }

        @Override
        void lowMemory() {
            checkThread();
            for (Fragment f : new ArrayList<Fragment>(fragments)) f.onLowMemory();
        }

        @Override
        void trimMemory(int level) {
            checkThread();
            for (Fragment f : new ArrayList<Fragment>(fragments)) f.onTrimMemory(level);
        }

        @Override
        Bundle save() {
            checkThread();
            executePendingTransactions();
            saved = true;
            Bundle out = new Bundle();
            out.putInt("count", fragments.size());
            int i = 0;
            executing = true;
            try {
                FragmentLifecycleCallbacks[] cbs = snapshotLifecycleCallbacks();
                for (Fragment f : fragments) {
                    Bundle record = new Bundle();
                    Bundle state = new Bundle();
                    f.onSaveInstanceState(state);
                    if (cbs != null) {
                        for (FragmentLifecycleCallbacks cb : cbs) cb.onFragmentSaveInstanceState(this, f, state);
                    }
                    record.putString("class", f.getClass().getName());
                    record.putString("tag", f.tag);
                    record.putBundle("args", f.arguments);
                    record.putBundle("state", state);
                    out.putBundle("fragment." + i++, record);
                }
                return out;
            } finally {
                executing = false;
            }
        }

        @Override
        void restore(Bundle in) {
            checkThread();
            if (in == null) return;
            if (!fragments.isEmpty()) throw new IllegalStateException("Fragments already initialized");
            int count = in.getInt("count", 0);
            if (count < 0 || count > 64) throw new IllegalArgumentException("Saved fragment limit");
            executing = true;
            try {
                ClassLoader loader = host != null ? host.getClassLoader() : Fragment.class.getClassLoader();
                for (int i = 0; i < count; i++) {
                    Bundle record = in.getBundle("fragment." + i);
                    if (record == null) throw new IllegalArgumentException("Missing saved fragment");
                    try {
                        Fragment f = Class.forName(record.getString("class"), true, loader)
                                .asSubclass(Fragment.class)
                                .newInstance();
                        f.arguments = record.getBundle("args");
                        f.saved = record.getBundle("state");
                        f.manager = this;
                        f.parentFragment = parent;
                        f.tag = record.getString("tag");
                        fragments.add(f);
                        move(f, phase);
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException("Cannot restore fragment", error);
                    }
                }
            } finally {
                executing = false;
            }
        }

        @Override
        void destroy() {
            checkThread();
            if (destroyed) return;
            destroyed = true;
            if (handler != null) handler.removeCallbacks(drain);
            pending.clear();
            phase = 0;
            Throwable failure = null;
            for (Fragment f : new ArrayList<Fragment>(fragments)) {
                try {
                    while (f.phase > 0) {
                        try {
                            move(f, 0);
                        } catch (RuntimeException | Error error) {
                            if (failure == null) failure = error;
                            else if (failure != error) failure.addSuppressed(error);
                        }
                    }
                } finally {
                    f.manager = null;
                    f.parentFragment = null;
                    f.activity = null;
                }
            }
            fragments.clear();
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            if (failure instanceof Error) throw (Error) failure;
        }
    }
}
