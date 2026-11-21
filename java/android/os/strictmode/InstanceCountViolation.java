// SPDX-License-Identifier: GPL-3.0-only
package android.os.strictmode;

public class InstanceCountViolation extends Violation {
    private final long instances;

    public InstanceCountViolation(Class<?> klass, long instances, int limit) {
        super(klass.toString() + "; instances=" + instances + "; limit=" + limit);
        this.instances = instances;
    }

    public long getNumberOfInstances() {
        return instances;
    }
}
