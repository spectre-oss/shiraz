// SPDX-License-Identifier: GPL-3.0-only
package android.os;

/** Trace API for a host runtime with no active trace collector.
 * Disabled calls deliberately retain no names, events, stacks or counters.
 */
public final class Trace {
    private Trace() { }
    public static final long TRACE_TAG_NEVER=0, TRACE_TAG_ALWAYS=1, TRACE_TAG_APP=1L<<12;
    public static boolean isEnabled() { return false; }
    public static boolean isTagEnabled(long tag) { return false; }
    public static void beginSection(String name) { }
    public static void endSection() { }
    public static void beginAsyncSection(String name,int cookie) { }
    public static void endAsyncSection(String name,int cookie) { }
    public static void setCounter(String name,long value) { }
    public static void traceBegin(long tag,String name) { }
    public static void traceEnd(long tag) { }
    public static void asyncTraceBegin(long tag,String name,int cookie) { }
    public static void asyncTraceEnd(long tag,String name,int cookie) { }
    public static void traceCounter(long tag,String name,int value) { }
}
