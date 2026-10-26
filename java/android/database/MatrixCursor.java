// SPDX-License-Identifier: GPL-3.0-only
package android.database;
import android.os.Bundle;
import java.util.*;
/** Detached typed rows. SQLite query snapshots use the same cursor value contract. */
public class MatrixCursor implements Cursor {
    private final String[] columns;
    protected List<Object[]> rows;
    protected int position = -1;
    private boolean closed;
    private Bundle extras = Bundle.EMPTY;
    private final Set<DataSetObserver> observers = new LinkedHashSet<DataSetObserver>();
    public MatrixCursor(String[] columns) { this(columns, 16); }
    public MatrixCursor(String[] columns, int capacity) {
        if (columns == null) throw new NullPointerException("columns");
        this.columns = columns.clone(); rows = new ArrayList<Object[]>(Math.max(1, capacity));
    }
    protected final void live() { if (closed) throw new IllegalStateException("Cursor is closed"); }
    public void addRow(Object[] values) { live(); if (values.length != columns.length) throw new IllegalArgumentException("Row width"); rows.add(values.clone()); }
    public void addRow(Iterable<?> values) { live(); Object[] row = new Object[columns.length]; int at = 0; for (Object v : values) { if (at == row.length) throw new IllegalArgumentException("Row width"); row[at++] = v; } if (at != row.length) throw new IllegalArgumentException("Row width"); rows.add(row); }
    public RowBuilder newRow() { live(); Object[] row = new Object[columns.length]; rows.add(row); return new RowBuilder(row); }
    public final class RowBuilder {
        private final Object[] row; private int next;
        RowBuilder(Object[] row) { this.row = row; }
        public RowBuilder add(Object value) { live(); if (next == row.length) throw new CursorIndexOutOfBoundsException(next, row.length); row[next++] = value; return this; }
        public RowBuilder add(String column, Object value) { live(); for (int i = 0; i < columns.length; ++i) if (columns[i].equals(column)) row[i] = value; return this; }
    }
    public int getCount() { live(); return rows.size(); }
    public int getPosition() { live(); return position; }
    public boolean moveToPosition(int target) { live(); if (target < 0) { position = -1; return false; } if (target >= rows.size()) { position = rows.size(); return false; } position = target; return true; }
    public boolean move(int offset) { long target = (long)position + offset; return moveToPosition((int)Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, target))); }
    public boolean moveToFirst() { return moveToPosition(0); }
    public boolean moveToLast() { return moveToPosition(getCount() - 1); }
    public boolean moveToNext() { return move(1); }
    public boolean moveToPrevious() { return move(-1); }
    public boolean isFirst() { return getCount() != 0 && position == 0; }
    public boolean isLast() { return getCount() != 0 && position == rows.size() - 1; }
    public boolean isBeforeFirst() { return getCount() != 0 && position < 0; }
    public boolean isAfterLast() { return getCount() != 0 && position >= rows.size(); }
    public int getColumnIndex(String name) { live(); if (name == null) return -1; int dot = name.lastIndexOf('.'); if (dot >= 0) name = name.substring(dot + 1); for (int i = 0; i < columns.length; ++i) if (name.equalsIgnoreCase(columns[i])) return i; return -1; }
    public int getColumnIndexOrThrow(String name) { int found = getColumnIndex(name); if (found < 0) throw new IllegalArgumentException("No column: " + name); return found; }
    private void column(int index) { live(); if (index < 0 || index >= columns.length) throw new CursorIndexOutOfBoundsException(index, columns.length); }
    public String getColumnName(int index) { column(index); return columns[index]; }
    public String[] getColumnNames() { live(); return columns.clone(); }
    public int getColumnCount() { live(); return columns.length; }
    private Object value(int index) { column(index); if (position < 0 || position >= rows.size()) throw new CursorIndexOutOfBoundsException(position, rows.size()); return rows.get(position)[index]; }
    public String getString(int index) { Object v = value(index); if (v instanceof byte[]) throw new SQLException("Cannot convert blob to string"); return v == null ? null : v.toString(); }
    public byte[] getBlob(int index) { Object v = value(index); if (v != null && !(v instanceof byte[])) throw new SQLException("Not a blob"); return v == null ? null : ((byte[])v).clone(); }
    public long getLong(int index) { Object v = value(index); if (v == null) return 0; if (v instanceof Number) return ((Number)v).longValue(); return Long.parseLong(v.toString()); }
    public int getInt(int index) { return (int)getLong(index); }
    public short getShort(int index) { return (short)getLong(index); }
    public double getDouble(int index) { Object v = value(index); if (v == null) return 0; if (v instanceof Number) return ((Number)v).doubleValue(); return Double.parseDouble(v.toString()); }
    public float getFloat(int index) { return (float)getDouble(index); }
    public int getType(int index) { Object v = value(index); return v == null ? FIELD_TYPE_NULL : v instanceof byte[] ? FIELD_TYPE_BLOB : v instanceof Float || v instanceof Double ? FIELD_TYPE_FLOAT : v instanceof Number ? FIELD_TYPE_INTEGER : FIELD_TYPE_STRING; }
    public boolean isNull(int index) { return value(index) == null; }
    public void copyStringToBuffer(int index, CharArrayBuffer buffer) { String text = getString(index); if (buffer == null) throw new NullPointerException("buffer"); buffer.sizeCopied = text == null ? 0 : text.length(); if (buffer.data == null || buffer.data.length < buffer.sizeCopied) buffer.data = new char[buffer.sizeCopied]; if (text != null) text.getChars(0, text.length(), buffer.data, 0); }
    protected final void changed() { for (DataSetObserver observer : new ArrayList<DataSetObserver>(observers)) observer.onChanged(); }
    public void deactivate() { live(); for (DataSetObserver observer : new ArrayList<DataSetObserver>(observers)) observer.onInvalidated(); }
    public boolean requery() { live(); changed(); return true; }
    public void close() { if (!closed) { closed = true; rows.clear(); try { for (DataSetObserver observer : new ArrayList<DataSetObserver>(observers)) observer.onInvalidated(); } finally { observers.clear(); } } }
    public boolean isClosed() { return closed; }
    public void registerDataSetObserver(DataSetObserver observer) { live(); if (!observers.add(Objects.requireNonNull(observer))) throw new IllegalStateException("Observer already registered"); }
    public void unregisterDataSetObserver(DataSetObserver observer) { if (!observers.remove(Objects.requireNonNull(observer))) throw new IllegalStateException("Observer is not registered"); }
    public Bundle getExtras() { live(); return extras; }
    public void setExtras(Bundle value) { live(); extras = value == null ? Bundle.EMPTY : new Bundle(value); }
    public Bundle respond(Bundle value) { live(); return Bundle.EMPTY; }
}
