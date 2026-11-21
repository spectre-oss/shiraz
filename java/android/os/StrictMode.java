// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import android.os.strictmode.*;
import android.util.Log;
import dalvik.system.BlockGuard;
import dalvik.system.CloseGuard;
import java.util.concurrent.Executor;

/** Thread I/O checks through host ART; VM checks currently cover CloseGuard resources. */
public final class StrictMode {
    public interface OnThreadViolationListener {
        void onThreadViolation(Violation v);
    }

    public interface OnVmViolationListener {
        void onVmViolation(Violation v);
    }

    private static final int WRITE = 1, READ = 2, NETWORK = 4, SLOW = 8, MISMATCH = 16, UNBUFFERED = 32;
    private static final int LOG = 1 << 16, DEATH = 1 << 18, NETWORK_DEATH = 1 << 24;
    private static final ThreadLocal<ThreadPolicy> THREAD = new ThreadLocal<ThreadPolicy>() {
        @Override
        protected ThreadPolicy initialValue() {
            return ThreadPolicy.LAX;
        }
    };
    private static final ThreadLocal<Boolean> REPORTING = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };
    private static volatile VmPolicy vm = VmPolicy.LAX;

    private StrictMode() {
    }

    public static final class ThreadPolicy {
        public static final ThreadPolicy LAX = new ThreadPolicy(0, null, null);
        private final int mask;
        final OnThreadViolationListener listener;
        final Executor callbackExecutor;

        private ThreadPolicy(int mask, OnThreadViolationListener listener, Executor executor) {
            this.mask = mask;
            this.listener = listener;
            this.callbackExecutor = executor;
        }

        public static final class Builder {
            private int mask;
            private OnThreadViolationListener listener;
            private Executor executor;

            public Builder() {
            }

            public Builder(ThreadPolicy policy) {
                if (policy == null) {
                    throw new NullPointerException("policy");
                }
                mask = policy.mask;
                listener = policy.listener;
                executor = policy.callbackExecutor;
            }

            public Builder detectAll() {
                mask |= WRITE | READ | NETWORK | SLOW | MISMATCH | UNBUFFERED;
                return this;
            }

            public Builder permitAll() {
                mask &= ~(WRITE | READ | NETWORK | SLOW | MISMATCH | UNBUFFERED);
                return this;
            }

            public Builder detectDiskReads() {
                mask |= READ;
                return this;
            }

            public Builder detectDiskWrites() {
                mask |= WRITE;
                return this;
            }

            public Builder detectNetwork() {
                mask |= NETWORK;
                return this;
            }

            public Builder detectCustomSlowCalls() {
                mask |= SLOW;
                return this;
            }

            public Builder detectResourceMismatches() {
                mask |= MISMATCH;
                return this;
            }

            public Builder detectUnbufferedIo() {
                mask |= UNBUFFERED;
                return this;
            }

            public Builder permitDiskReads() {
                mask &= ~READ;
                return this;
            }

            public Builder permitDiskWrites() {
                mask &= ~WRITE;
                return this;
            }

            public Builder permitNetwork() {
                mask &= ~NETWORK;
                return this;
            }

            public Builder permitCustomSlowCalls() {
                mask &= ~SLOW;
                return this;
            }

            public Builder permitResourceMismatches() {
                mask &= ~MISMATCH;
                return this;
            }

            public Builder permitUnbufferedIo() {
                mask &= ~UNBUFFERED;
                return this;
            }

            public Builder penaltyLog() {
                mask |= LOG;
                return this;
            }

            public Builder penaltyDeath() {
                mask |= DEATH;
                return this;
            }

            public Builder penaltyDeathOnNetwork() {
                mask |= NETWORK_DEATH;
                return this;
            }

            public Builder penaltyListener(Executor executor, OnThreadViolationListener listener) {
                if (executor == null) {
                    throw new NullPointerException("executor must not be null");
                }
                this.listener = listener;
                this.executor = executor;
                return this;
            }

            public ThreadPolicy build() {
                int value = mask;
                if ((value & 63) != 0 && (value & (LOG | DEATH | NETWORK_DEATH)) == 0 && listener == null) {
                    value |= LOG;
                }
                return new ThreadPolicy(value, listener, executor);
            }
        }
    }

    private static Violation threadViolationFor(int detector, String operation, Object tag) {
        switch (detector) {
            case WRITE:
                return new DiskWriteViolation();
            case READ:
                return new DiskReadViolation();
            case NETWORK:
                return new NetworkViolation();
            case UNBUFFERED:
                return new UnbufferedIoViolation();
            case MISMATCH:
                return new ResourceMismatchViolation(tag);
            default:
                return new CustomViolation(operation);
        }
    }

    private static void reportThread(ThreadPolicy policy, int detector, String operation, Object tag, Throwable origin) {
        int mask = policy.mask;
        if ((mask & detector) == 0 || REPORTING.get().booleanValue()) {
            return;
        }
        REPORTING.set(Boolean.TRUE);
        try {
            if (policy.listener != null && policy.callbackExecutor != null) {
                final OnThreadViolationListener cb = policy.listener;
                final Violation violation = threadViolationFor(detector, operation, tag);
                policy.callbackExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        cb.onThreadViolation(violation);
                    }
                });
            }
            if (detector == NETWORK && (mask & NETWORK_DEATH) != 0) {
                throw new NetworkOnMainThreadException();
            }
            if ((mask & LOG) != 0) {
                Log.d("StrictMode", "policy violation: " + operation,
                        origin != null ? origin : new Throwable("StrictMode operation"));
            }
            if ((mask & DEATH) != 0) {
                throw new RuntimeException("StrictMode policy violation: " + operation, origin);
            }
        } finally {
            REPORTING.set(Boolean.FALSE);
        }
    }

    private static void reportVm(VmPolicy policy, int detector, String operation, Throwable origin) {
        int mask = policy.mask;
        if ((mask & detector) == 0 || REPORTING.get().booleanValue()) {
            return;
        }
        REPORTING.set(Boolean.TRUE);
        try {
            if (policy.listener != null && policy.callbackExecutor != null) {
                final OnVmViolationListener cb = policy.listener;
                final Violation violation = new LeakedClosableViolation(operation, origin);
                policy.callbackExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        cb.onVmViolation(violation);
                    }
                });
            }
            if ((mask & LOG) != 0) {
                Log.d("StrictMode", "policy violation: " + operation,
                        origin != null ? origin : new Throwable("StrictMode operation"));
            }
            if ((mask & DEATH) != 0) {
                throw new RuntimeException("StrictMode policy violation: " + operation, origin);
            }
        } finally {
            REPORTING.set(Boolean.FALSE);
        }
    }

    private static final class Guard implements BlockGuard.Policy {
        private final ThreadPolicy policy;

        Guard(ThreadPolicy policy) {
            this.policy = policy;
        }

        private void check(int detector, String name) {
            reportThread(policy, detector, name, null, null);
        }

        @Override
        public void onWriteToDisk() {
            check(WRITE, "disk write");
        }

        @Override
        public void onReadFromDisk() {
            check(READ, "disk read");
        }

        @Override
        public void onNetwork() {
            check(NETWORK, "network");
        }

        @Override
        public void onUnbufferedIO() {
            check(UNBUFFERED, "unbuffered I/O");
        }

        @Override
        public int getPolicyMask() {
            return policy.mask;
        }
    }

    public static ThreadPolicy getThreadPolicy() {
        return THREAD.get();
    }

    public static void setThreadPolicy(ThreadPolicy policy) {
        if (policy == null) {
            throw new NullPointerException("policy");
        }
        BlockGuard.setThreadPolicy(policy.mask == 0 ? BlockGuard.LAX_POLICY : new Guard(policy));
        THREAD.set(policy);
    }

    public static ThreadPolicy allowThreadDiskReads() {
        ThreadPolicy old = getThreadPolicy();
        setThreadPolicy(new ThreadPolicy(old.mask & ~READ, old.listener, old.callbackExecutor));
        return old;
    }

    public static ThreadPolicy allowThreadDiskWrites() {
        ThreadPolicy old = getThreadPolicy();
        setThreadPolicy(new ThreadPolicy(old.mask & ~(READ | WRITE), old.listener, old.callbackExecutor));
        return old;
    }

    public static void noteSlowCall(String name) {
        reportThread(getThreadPolicy(), SLOW, "slow call: " + name, name, null);
    }

    public static void noteResourceMismatch(Object tag) {
        reportThread(getThreadPolicy(), MISMATCH, "resource mismatch", tag, null);
    }

    public static final class VmPolicy {
        public static final VmPolicy LAX = new VmPolicy(0, null, null);
        private final int mask;
        final OnVmViolationListener listener;
        final Executor callbackExecutor;

        private VmPolicy(int mask, OnVmViolationListener listener, Executor executor) {
            this.mask = mask;
            this.listener = listener;
            this.callbackExecutor = executor;
        }

        public static final class Builder {
            private int mask;
            private OnVmViolationListener listener;
            private Executor executor;

            public Builder() {
            }

            public Builder(VmPolicy policy) {
                if (policy == null) {
                    throw new NullPointerException("policy");
                }
                mask = policy.mask;
                listener = policy.listener;
                executor = policy.callbackExecutor;
            }

            public Builder detectLeakedClosableObjects() {
                mask |= 1;
                return this;
            }

            public Builder detectAll() {
                throw new UnsupportedOperationException(
                        "VM detectAll needs Android instance, SQLite, registration, URI and cleartext-network hooks; only CloseGuard leak detection is implemented");
            }

            public Builder penaltyLog() {
                mask |= LOG;
                return this;
            }

            public Builder penaltyDeath() {
                mask |= DEATH;
                return this;
            }

            public Builder penaltyListener(Executor executor, OnVmViolationListener listener) {
                if (executor == null) {
                    throw new NullPointerException("executor must not be null");
                }
                this.listener = listener;
                this.executor = executor;
                return this;
            }

            public VmPolicy build() {
                int value = (mask != 0 && (mask & (LOG | DEATH)) == 0 && listener == null)
                        ? (mask | LOG)
                        : mask;
                return new VmPolicy(value, listener, executor);
            }
        }
    }

    public static VmPolicy getVmPolicy() {
        return vm;
    }

    public static synchronized void setVmPolicy(VmPolicy policy) {
        if (policy == null) {
            throw new NullPointerException("policy");
        }
        vm = policy;
        CloseGuard.setReporter(new CloseGuard.Reporter() {
            @Override
            public void report(String message, Throwable allocation) {
                StrictMode.reportVm(vm, 1, "unclosed resource", allocation);
            }
        });
        CloseGuard.setEnabled((policy.mask & 1) != 0);
    }
}
