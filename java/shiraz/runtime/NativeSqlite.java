// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
/** Internal typed SQLite interface; values are null, Long, Double, String, or byte[]. */
public final class NativeSqlite {
    private NativeSqlite() { }
    static { System.loadLibrary("shiraz-sqlite"); }
    public static final class Result {
        public final String[] columns;
        public final Object[][] rows;
        public Result(String[] columns, Object[][] rows) { this.columns = columns; this.rows = rows; }
    }
    public static native long open(byte[] path, int flags);
    public static native void close(long connection);
    public static native long prepare(long connection, String sql);
    public static native boolean isReadOnly(long statement);
    public static native boolean autocommit(long connection);
    public static native String scalarString(long connection, long statement, Object[] arguments);
    public static native int parameterCount(long statement);
    public static native void finalizeStatement(long statement);
    /** mode 0: execute/discard; 1: insert ID; 2: changed rows; 3: scalar long. */
    public static native long execute(long connection, long statement, Object[] arguments, int mode);
    public static native Result query(long connection, long statement, Object[] arguments);
}
