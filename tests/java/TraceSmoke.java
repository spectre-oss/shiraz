// SPDX-License-Identifier: GPL-3.0-only
public final class TraceSmoke {
    public static void main(String[] args) throws Exception {
        org.shiraz.fixture.TraceFixture.check();
        Class<?> trace=Class.forName("android.os.Trace");long tag=trace.getField("TRACE_TAG_APP").getLong(null);
        if((Boolean)trace.getMethod("isTagEnabled",long.class).invoke(null,tag))throw new AssertionError("Tracing enabled");
        trace.getMethod("asyncTraceBegin",long.class,String.class,int.class).invoke(null,tag,"androidx",1);
        trace.getMethod("asyncTraceEnd",long.class,String.class,int.class).invoke(null,tag,"androidx",1);
        trace.getMethod("traceCounter",long.class,String.class,int.class).invoke(null,tag,"androidx",1);
    }
}
