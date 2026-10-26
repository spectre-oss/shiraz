// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
public final class TraceFixture {
    public static void check() {
        if(android.os.Trace.isEnabled())throw new AssertionError("No trace collector is active");
        // Disabled tracing must not validate or retain names, even unmatched sections.
        android.os.Trace.beginSection(null);android.os.Trace.endSection();android.os.Trace.endSection();
        android.os.Trace.beginSection(new String(new char[1024]));android.os.Trace.endSection();
        android.os.Trace.beginAsyncSection("background",17);android.os.Trace.setCounter("work",Long.MAX_VALUE);
        android.os.Trace.endAsyncSection("background",17);
        System.out.println("TRACE_DISABLED_OK");
    }
}
