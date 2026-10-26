// SPDX-License-Identifier: GPL-3.0-only
package android.os;
import android.util.Log;
import dalvik.system.BlockGuard;
import dalvik.system.CloseGuard;

/** Thread I/O checks through host ART; VM checks currently cover CloseGuard resources. */
public final class StrictMode {
    private static final int WRITE = 1, READ = 2, NETWORK = 4, SLOW = 8, MISMATCH = 16, UNBUFFERED = 32;
    private static final int LOG = 1 << 16, DEATH = 1 << 18, NETWORK_DEATH = 1 << 24;
    private static final ThreadLocal<ThreadPolicy> THREAD = new ThreadLocal<ThreadPolicy>() {
        @Override protected ThreadPolicy initialValue() { return ThreadPolicy.LAX; }
    };
    private static final ThreadLocal<Boolean> REPORTING = new ThreadLocal<Boolean>() {
        @Override protected Boolean initialValue() { return false; }
    };
    private static volatile VmPolicy vm = VmPolicy.LAX;
    private StrictMode() { }
    public static final class ThreadPolicy {
        public static final ThreadPolicy LAX = new ThreadPolicy(0);
        private final int mask;
        private ThreadPolicy(int mask) { this.mask = mask; }
        public static final class Builder {
            private int mask;
            public Builder() { }
            public Builder(ThreadPolicy policy) { if (policy == null) throw new NullPointerException("policy"); mask = policy.mask; }
            public Builder detectAll() { mask |= WRITE | READ | NETWORK | SLOW | MISMATCH | UNBUFFERED; return this; }
            public Builder permitAll() { mask &= ~(WRITE | READ | NETWORK | SLOW | MISMATCH | UNBUFFERED); return this; }
            public Builder detectDiskReads() { mask |= READ; return this; }
            public Builder detectDiskWrites() { mask |= WRITE; return this; }
            public Builder detectNetwork() { mask |= NETWORK; return this; }
            public Builder detectCustomSlowCalls() { mask |= SLOW; return this; }
            public Builder detectResourceMismatches() { mask |= MISMATCH; return this; }
            public Builder detectUnbufferedIo() { mask |= UNBUFFERED; return this; }
            public Builder permitDiskReads() { mask &= ~READ; return this; }
            public Builder permitDiskWrites() { mask &= ~WRITE; return this; }
            public Builder permitNetwork() { mask &= ~NETWORK; return this; }
            public Builder permitCustomSlowCalls() { mask &= ~SLOW; return this; }
            public Builder permitResourceMismatches() { mask &= ~MISMATCH; return this; }
            public Builder permitUnbufferedIo() { mask &= ~UNBUFFERED; return this; }
            public Builder penaltyLog() { mask |= LOG; return this; }
            public Builder penaltyDeath() { mask |= DEATH; return this; }
            public Builder penaltyDeathOnNetwork() { mask |= NETWORK_DEATH; return this; }
            public ThreadPolicy build() {
                int value = mask;
                if ((value & 63) != 0 && (value & (LOG | DEATH | NETWORK_DEATH)) == 0) value |= LOG;
                return new ThreadPolicy(value);
            }
        }
    }
    private static void report(int mask, int detector, String operation, Throwable origin) {
        if ((mask & detector) == 0 || REPORTING.get()) return;
        REPORTING.set(true);
        try {
            if (detector == NETWORK && (mask & NETWORK_DEATH) != 0) throw new NetworkOnMainThreadException();
            if ((mask & LOG) != 0) Log.d("StrictMode", "policy violation: " + operation, origin != null ? origin : new Throwable("StrictMode operation"));
            if ((mask & DEATH) != 0) throw new RuntimeException("StrictMode policy violation: " + operation, origin);
        } finally { REPORTING.set(false); }
    }
    private static final class Guard implements BlockGuard.Policy {
        private final ThreadPolicy policy;
        Guard(ThreadPolicy policy) { this.policy = policy; }
        private void check(int detector, String name) { report(policy.mask, detector, name, null); }
        public void onWriteToDisk() { check(WRITE, "disk write"); }
        public void onReadFromDisk() { check(READ, "disk read"); }
        public void onNetwork() { check(NETWORK, "network"); }
        public void onUnbufferedIO() { check(UNBUFFERED, "unbuffered I/O"); }
        public int getPolicyMask() { return policy.mask; }
    }
    public static ThreadPolicy getThreadPolicy() { return THREAD.get(); }
    public static void setThreadPolicy(ThreadPolicy policy) {
        if (policy == null) throw new NullPointerException("policy");
        BlockGuard.setThreadPolicy(policy.mask == 0 ? BlockGuard.LAX_POLICY : new Guard(policy));
        THREAD.set(policy);
    }
    public static ThreadPolicy allowThreadDiskReads() {
        ThreadPolicy old = getThreadPolicy();
        setThreadPolicy(new ThreadPolicy(old.mask & ~READ)); return old;
    }
    public static ThreadPolicy allowThreadDiskWrites() {
        ThreadPolicy old = getThreadPolicy();
        setThreadPolicy(new ThreadPolicy(old.mask & ~(READ | WRITE))); return old;
    }
    public static void noteSlowCall(String name) { report(getThreadPolicy().mask, SLOW, "slow call: " + name, null); }
    public static void noteResourceMismatch(Object tag) { report(getThreadPolicy().mask, MISMATCH, "resource mismatch", null); }

    public static final class VmPolicy {
        public static final VmPolicy LAX = new VmPolicy(0);
        private final int mask;
        private VmPolicy(int mask) { this.mask = mask; }
        public static final class Builder {
            private int mask;
            public Builder() { }
            public Builder(VmPolicy policy) { if (policy == null) throw new NullPointerException("policy"); mask = policy.mask; }
            public Builder detectLeakedClosableObjects() { mask |= 1; return this; }
            public Builder detectAll() { throw new UnsupportedOperationException("VM detectAll needs Android instance, SQLite, registration, URI and cleartext-network hooks; only CloseGuard leak detection is implemented"); }
            public Builder penaltyLog() { mask |= LOG; return this; }
            public Builder penaltyDeath() { mask |= DEATH; return this; }
            public VmPolicy build() { return new VmPolicy(mask != 0 && (mask & (LOG | DEATH)) == 0 ? mask | LOG : mask); }
        }
    }
    public static VmPolicy getVmPolicy() { return vm; }
    public static synchronized void setVmPolicy(VmPolicy policy) {
        if (policy == null) throw new NullPointerException("policy");
        vm = policy;
        CloseGuard.setReporter(new CloseGuard.Reporter() {
            public void report(String message, Throwable allocation) { StrictMode.report(vm.mask, 1, "unclosed resource", allocation); }
        });
        CloseGuard.setEnabled((policy.mask & 1) != 0);
    }
}
