// SPDX-License-Identifier: GPL-3.0-only
package android.os.strictmode;

public final class UntaggedSocketViolation extends Violation {
    public UntaggedSocketViolation() {
        super("Untagged socket detected; use TrafficStats.setThreadStatsTag() to track all network usage");
    }
}
