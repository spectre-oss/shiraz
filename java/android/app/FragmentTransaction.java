// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.view.View;
import java.util.ArrayList;

/** Bounded add/remove transaction; back stack and visual containers are explicit gaps. */
@Deprecated
public abstract class FragmentTransaction {
    public static final int TRANSIT_ENTER_MASK = 0x1000;
    public static final int TRANSIT_EXIT_MASK = 0x2000;
    public static final int TRANSIT_UNSET = -1;
    public static final int TRANSIT_NONE = 0;
    public static final int TRANSIT_FRAGMENT_OPEN = 0x1001;
    public static final int TRANSIT_FRAGMENT_CLOSE = 0x2002;
    public static final int TRANSIT_FRAGMENT_FADE = 0x1003;

    public FragmentTransaction() {}

    public abstract FragmentTransaction add(Fragment fragment, String tag);
    public abstract FragmentTransaction add(int containerViewId, Fragment fragment);
    public abstract FragmentTransaction add(int containerViewId, Fragment fragment, String tag);
    public abstract FragmentTransaction replace(int containerViewId, Fragment fragment);
    public abstract FragmentTransaction replace(int containerViewId, Fragment fragment, String tag);
    public abstract FragmentTransaction remove(Fragment fragment);
    public abstract FragmentTransaction hide(Fragment fragment);
    public abstract FragmentTransaction show(Fragment fragment);
    public abstract FragmentTransaction detach(Fragment fragment);
    public abstract FragmentTransaction attach(Fragment fragment);
    public abstract FragmentTransaction setPrimaryNavigationFragment(Fragment fragment);
    public abstract boolean isEmpty();
    public abstract FragmentTransaction setCustomAnimations(int enter, int exit);
    public abstract FragmentTransaction setCustomAnimations(int enter, int exit, int popEnter, int popExit);
    public abstract FragmentTransaction setTransition(int transit);
    public abstract FragmentTransaction addSharedElement(View sharedElement, String name);
    public abstract FragmentTransaction setTransitionStyle(int styleRes);
    public abstract FragmentTransaction addToBackStack(String name);
    public abstract boolean isAddToBackStackAllowed();
    public abstract FragmentTransaction disallowAddToBackStack();
    public abstract FragmentTransaction setBreadCrumbTitle(int res);
    public abstract FragmentTransaction setBreadCrumbTitle(CharSequence text);
    public abstract FragmentTransaction setBreadCrumbShortTitle(int res);
    public abstract FragmentTransaction setBreadCrumbShortTitle(CharSequence text);
    public abstract FragmentTransaction setReorderingAllowed(boolean reorderingAllowed);
    public abstract FragmentTransaction runOnCommit(Runnable runnable);
    public abstract int commit();
    public abstract int commitAllowingStateLoss();
    public abstract void commitNow();
    public abstract void commitNowAllowingStateLoss();

    static final class Impl extends FragmentTransaction {
        static final int OP_ADD = 1;
        static final int OP_REMOVE = 2;
        static final int OP_HIDE = 3;
        static final int OP_SHOW = 4;
        static final int OP_DETACH = 5;
        static final int OP_ATTACH = 6;
        static final int OP_PRIMARY_NAV = 7;

        static final class Op {
            final int cmd;
            final Fragment fragment;
            final String tag;

            Op(int cmd, Fragment f, String t) {
                this.cmd = cmd;
                this.fragment = f;
                this.tag = t;
            }
        }

        final FragmentManager manager;
        final ArrayList<Op> operations = new ArrayList<Op>();
        ArrayList<Runnable> commitRunnables;
        boolean committed;
        boolean allowAddToBackStack = true;

        Impl(FragmentManager manager) {
            this.manager = manager;
        }

        private void writable() {
            manager.checkThread();
            if (committed) throw new IllegalStateException("Transaction already committed");
            if (operations.size() >= 256) throw new IllegalStateException("Fragment operation limit");
        }

        @Override
        public FragmentTransaction add(Fragment fragment, String tag) {
            writable();
            if (fragment == null) throw new NullPointerException("fragment");
            if (tag != null && tag.length() > 4096) throw new IllegalArgumentException("Fragment tag limit");
            operations.add(new Op(OP_ADD, fragment, tag));
            return this;
        }

        @Override
        public FragmentTransaction add(int container, Fragment fragment) {
            return add(container, fragment, null);
        }

        @Override
        public FragmentTransaction add(int container, Fragment fragment, String tag) {
            if (container != 0) {
                throw new UnsupportedOperationException("Platform fragment view containers are not implemented");
            }
            return add(fragment, tag);
        }

        @Override
        public FragmentTransaction replace(int containerViewId, Fragment fragment) {
            return replace(containerViewId, fragment, null);
        }

        @Override
        public FragmentTransaction replace(int container, Fragment fragment, String tag) {
            throw new UnsupportedOperationException("Platform fragment replacement is not implemented");
        }

        @Override
        public FragmentTransaction remove(Fragment fragment) {
            writable();
            if (fragment == null) throw new NullPointerException("fragment");
            operations.add(new Op(OP_REMOVE, fragment, null));
            return this;
        }

        @Override
        public FragmentTransaction hide(Fragment fragment) {
            writable();
            if (fragment == null) throw new NullPointerException("fragment");
            operations.add(new Op(OP_HIDE, fragment, null));
            return this;
        }

        @Override
        public FragmentTransaction show(Fragment fragment) {
            writable();
            if (fragment == null) throw new NullPointerException("fragment");
            operations.add(new Op(OP_SHOW, fragment, null));
            return this;
        }

        @Override
        public FragmentTransaction detach(Fragment fragment) {
            writable();
            if (fragment == null) throw new NullPointerException("fragment");
            operations.add(new Op(OP_DETACH, fragment, null));
            return this;
        }

        @Override
        public FragmentTransaction attach(Fragment fragment) {
            writable();
            if (fragment == null) throw new NullPointerException("fragment");
            operations.add(new Op(OP_ATTACH, fragment, null));
            return this;
        }

        @Override
        public FragmentTransaction setPrimaryNavigationFragment(Fragment fragment) {
            writable();
            operations.add(new Op(OP_PRIMARY_NAV, fragment, null));
            return this;
        }

        @Override
        public boolean isEmpty() {
            return operations.isEmpty();
        }

        @Override
        public FragmentTransaction setCustomAnimations(int enter, int exit) {
            writable();
            return this;
        }

        @Override
        public FragmentTransaction setCustomAnimations(int enter, int exit, int popEnter, int popExit) {
            writable();
            return this;
        }

        @Override
        public FragmentTransaction setTransition(int transit) {
            writable();
            return this;
        }

        @Override
        public FragmentTransaction addSharedElement(View sharedElement, String name) {
            writable();
            return this;
        }

        @Override
        public FragmentTransaction setTransitionStyle(int styleRes) {
            writable();
            return this;
        }

        @Override
        public FragmentTransaction addToBackStack(String name) {
            throw new UnsupportedOperationException("Platform fragment back stack is not implemented");
        }

        @Override
        public boolean isAddToBackStackAllowed() {
            return allowAddToBackStack;
        }

        @Override
        public FragmentTransaction disallowAddToBackStack() {
            allowAddToBackStack = false;
            return this;
        }

        @Override
        public FragmentTransaction setBreadCrumbTitle(int res) {
            return this;
        }

        @Override
        public FragmentTransaction setBreadCrumbTitle(CharSequence text) {
            return this;
        }

        @Override
        public FragmentTransaction setBreadCrumbShortTitle(int res) {
            return this;
        }

        @Override
        public FragmentTransaction setBreadCrumbShortTitle(CharSequence text) {
            return this;
        }

        @Override
        public FragmentTransaction setReorderingAllowed(boolean reorderingAllowed) {
            return this;
        }

        @Override
        public FragmentTransaction runOnCommit(Runnable runnable) {
            writable();
            if (runnable == null) throw new IllegalArgumentException("runnable cannot be null");
            if (!allowAddToBackStack) {
                if (commitRunnables == null) commitRunnables = new ArrayList<Runnable>();
                commitRunnables.add(runnable);
            } else {
                allowAddToBackStack = false;
                if (commitRunnables == null) commitRunnables = new ArrayList<Runnable>();
                commitRunnables.add(runnable);
            }
            return this;
        }

        @Override
        public int commit() {
            return manager.enqueue(this, false, false);
        }

        @Override
        public int commitAllowingStateLoss() {
            return manager.enqueue(this, true, false);
        }

        @Override
        public void commitNow() {
            manager.enqueue(this, false, true);
        }

        @Override
        public void commitNowAllowingStateLoss() {
            manager.enqueue(this, true, true);
        }
    }
}
