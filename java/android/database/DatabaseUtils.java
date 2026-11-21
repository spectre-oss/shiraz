// SPDX-License-Identifier: GPL-3.0-only
package android.database;

/** SQL text and scalar query helpers. Statement classification follows the API-27 prefix contract. */
public class DatabaseUtils {
    /** The first column of the first row as a long; SQLiteDoneException when the query has no rows. */
    public static long longForQuery(android.database.sqlite.SQLiteDatabase db,String query,String[] selectionArgs){
        try(android.database.sqlite.SQLiteStatement statement=db.compileStatement(query)){return longForQuery(statement,selectionArgs);}
    }
    public static long longForQuery(android.database.sqlite.SQLiteStatement statement,String[] selectionArgs){
        statement.bindAllArgsAsStrings(selectionArgs);return statement.simpleQueryForLong();
    }
    public static String stringForQuery(android.database.sqlite.SQLiteDatabase db,String query,String[] selectionArgs){
        try(android.database.sqlite.SQLiteStatement statement=db.compileStatement(query)){return stringForQuery(statement,selectionArgs);}
    }
    public static String stringForQuery(android.database.sqlite.SQLiteStatement statement,String[] selectionArgs){
        statement.bindAllArgsAsStrings(selectionArgs);return statement.simpleQueryForString();
    }
    public static long queryNumEntries(android.database.sqlite.SQLiteDatabase db,String table){return queryNumEntries(db,table,null,null);}
    public static long queryNumEntries(android.database.sqlite.SQLiteDatabase db,String table,String selection){return queryNumEntries(db,table,selection,null);}
    /** Row count of {@code table}; like AOSP, table and selection are SQL text, not bound values. */
    public static long queryNumEntries(android.database.sqlite.SQLiteDatabase db,String table,String selection,String[] selectionArgs){
        String where=selection!=null&&!selection.isEmpty()?" where "+selection:"";
        return longForQuery(db,"select count(*) from "+table+where,selectionArgs);
    }
    public static void cursorFillWindow(Cursor cursor,int position,CursorWindow window){
        if(position<0||position>=cursor.getCount())return;
        if(cursor instanceof CrossProcessCursor&&((CrossProcessCursor)cursor).getWindow()==window)throw new IllegalArgumentException("Cannot fill the cursor's source window");
        int previous=cursor.getPosition();window.clear();window.setStartPosition(position);if(!window.setNumColumns(cursor.getColumnCount()))throw new IllegalArgumentException("Cursor column limit");
        try{if(!cursor.moveToPosition(position))return;do{
            if(!window.allocRow())break;boolean complete=true;int row=cursor.getPosition();
            try{for(int column=0;column<cursor.getColumnCount()&&complete;column++)switch(cursor.getType(column)){
                case Cursor.FIELD_TYPE_NULL:complete=window.putNull(row,column);break;
                case Cursor.FIELD_TYPE_INTEGER:complete=window.putLong(cursor.getLong(column),row,column);break;
                case Cursor.FIELD_TYPE_FLOAT:complete=window.putDouble(cursor.getDouble(column),row,column);break;
                case Cursor.FIELD_TYPE_BLOB:complete=window.putBlob(cursor.getBlob(column),row,column);break;
                default:complete=window.putString(cursor.getString(column),row,column);break;
            }}catch(RuntimeException|Error failure){window.freeLastRow();throw failure;}
            if(!complete){window.freeLastRow();break;}
        }while(cursor.moveToNext());}finally{cursor.moveToPosition(previous);}
    }
    public static final int STATEMENT_SELECT=1,STATEMENT_UPDATE=2,STATEMENT_ATTACH=3,
        STATEMENT_BEGIN=4,STATEMENT_COMMIT=5,STATEMENT_ABORT=6,STATEMENT_PRAGMA=7,
        STATEMENT_DDL=8,STATEMENT_UNPREPARED=9,STATEMENT_OTHER=99;

    public static void appendEscapedSQLString(StringBuilder sql,String value){
        if(sql==null||value==null)throw new NullPointerException("SQL string");
        sql.append('\'');
        for(int i=0;i<value.length();i++){char c=value.charAt(i);sql.append(c);if(c=='\'')sql.append(c);}
        sql.append('\'');
    }
    public static String sqlEscapeString(String value){StringBuilder sql=new StringBuilder();appendEscapedSQLString(sql,value);return sql.toString();}
    public static int getSqlStatementType(String sql){
        String text=sql.trim();if(text.length()<3)return STATEMENT_OTHER;
        switch(text.substring(0,3).toUpperCase(java.util.Locale.ROOT)){
            case "SEL":return STATEMENT_SELECT;
            case "INS":case "UPD":case "REP":case "DEL":return STATEMENT_UPDATE;
            case "ATT":return STATEMENT_ATTACH;
            case "BEG":return STATEMENT_BEGIN;
            case "COM":case "END":return STATEMENT_COMMIT;
            case "ROL":return STATEMENT_ABORT;
            case "PRA":return STATEMENT_PRAGMA;
            case "CRE":case "DRO":case "ALT":return STATEMENT_DDL;
            case "ANA":case "DET":return STATEMENT_UNPREPARED;
            default:return STATEMENT_OTHER;
        }
    }

    /** Prepared bulk row insertion helper backed by {@link android.database.sqlite.SQLiteStatement}. */
    @Deprecated
    public static class InsertHelper {
        private final android.database.sqlite.SQLiteDatabase mDb;
        private final String mTableName;
        private java.util.Map<String, Integer> mColumns;
        private String mInsertSQL;
        private android.database.sqlite.SQLiteStatement mInsertStatement;
        private android.database.sqlite.SQLiteStatement mReplaceStatement;
        private android.database.sqlite.SQLiteStatement mPreparedStatement;

        public InsertHelper(android.database.sqlite.SQLiteDatabase db, String tableName) {
            if (db == null || tableName == null) {
                throw new NullPointerException("db and tableName must not be null");
            }
            mDb = db;
            mTableName = tableName;
        }

        private void buildSQL() {
            if (mInsertSQL != null) {
                return;
            }
            StringBuilder sb = new StringBuilder(128);
            sb.append("INSERT INTO ").append(mTableName).append(" (");
            StringBuilder sbv = new StringBuilder(128);
            sbv.append("VALUES (");
            mColumns = new java.util.HashMap<String, Integer>();
            int index = 1;
            try (Cursor cur = mDb.rawQuery("PRAGMA table_info(" + mTableName + ")", null)) {
                while (cur.moveToNext()) {
                    String columnName = cur.getString(1);
                    String defaultValue = cur.getString(4);
                    mColumns.put(columnName, Integer.valueOf(index));
                    if (index > 1) {
                        sb.append(", ");
                        sbv.append(", ");
                    }
                    sb.append('\'').append(columnName).append('\'');
                    if (defaultValue == null) {
                        sbv.append("?");
                    } else {
                        sbv.append("COALESCE(?, ").append(defaultValue).append(")");
                    }
                    index++;
                }
            }
            if (mColumns.isEmpty()) {
                throw new android.database.sqlite.SQLiteException("Table " + mTableName + " has no columns");
            }
            sb.append(") ").append(sbv).append(");");
            mInsertSQL = sb.toString();
        }

        private android.database.sqlite.SQLiteStatement getStatement(boolean allowReplace) {
            buildSQL();
            if (allowReplace) {
                if (mReplaceStatement == null) {
                    String replaceSQL = "INSERT OR REPLACE" + mInsertSQL.substring(6);
                    mReplaceStatement = mDb.compileStatement(replaceSQL);
                }
                return mReplaceStatement;
            }
            if (mInsertStatement == null) {
                mInsertStatement = mDb.compileStatement(mInsertSQL);
            }
            return mInsertStatement;
        }

        public int getColumnIndex(String key) {
            buildSQL();
            Integer index = mColumns.get(key);
            if (index == null) {
                throw new IllegalArgumentException("column '" + key + "' is invalid");
            }
            return index.intValue();
        }

        public void bind(int index, double value) { mPreparedStatement.bindDouble(index, value); }
        public void bind(int index, float value) { mPreparedStatement.bindDouble(index, value); }
        public void bind(int index, long value) { mPreparedStatement.bindLong(index, value); }
        public void bind(int index, int value) { mPreparedStatement.bindLong(index, value); }
        public void bind(int index, boolean value) { mPreparedStatement.bindLong(index, value ? 1L : 0L); }
        public void bindNull(int index) { mPreparedStatement.bindNull(index); }
        public void bind(int index, byte[] value) {
            if (value == null) mPreparedStatement.bindNull(index);
            else mPreparedStatement.bindBlob(index, value);
        }
        public void bind(int index, String value) {
            if (value == null) mPreparedStatement.bindNull(index);
            else mPreparedStatement.bindString(index, value);
        }

        private long insertInternal(android.content.ContentValues values, boolean allowReplace) {
            mDb.beginTransactionNonExclusive();
            try {
                android.database.sqlite.SQLiteStatement stmt = getStatement(allowReplace);
                stmt.clearBindings();
                for (java.util.Map.Entry<String, Object> entry : values.valueSet()) {
                    int idx = getColumnIndex(entry.getKey());
                    Object val = entry.getValue();
                    if (val == null) stmt.bindNull(idx);
                    else if (val instanceof byte[]) stmt.bindBlob(idx, (byte[]) val);
                    else if (val instanceof Float || val instanceof Double) stmt.bindDouble(idx, ((Number) val).doubleValue());
                    else if (val instanceof Boolean) stmt.bindLong(idx, ((Boolean) val).booleanValue() ? 1L : 0L);
                    else if (val instanceof Number) stmt.bindLong(idx, ((Number) val).longValue());
                    else stmt.bindString(idx, val.toString());
                }
                long result = stmt.executeInsert();
                mDb.setTransactionSuccessful();
                return result;
            } catch (android.database.sqlite.SQLiteException e) {
                return -1L;
            } finally {
                mDb.endTransaction();
            }
        }

        public long insert(android.content.ContentValues values) {
            return insertInternal(values, false);
        }

        public long execute() {
            if (mPreparedStatement == null) {
                throw new IllegalStateException("you must prepare this inserter before calling execute");
            }
            try {
                return mPreparedStatement.executeInsert();
            } catch (android.database.sqlite.SQLiteException e) {
                return -1L;
            } finally {
                mPreparedStatement = null;
            }
        }

        public void prepareForInsert() {
            mPreparedStatement = getStatement(false);
            mPreparedStatement.clearBindings();
        }

        public void prepareForReplace() {
            mPreparedStatement = getStatement(true);
            mPreparedStatement.clearBindings();
        }

        public long replace(android.content.ContentValues values) {
            return insertInternal(values, true);
        }

        public void close() {
            if (mInsertStatement != null) {
                mInsertStatement.close();
                mInsertStatement = null;
            }
            if (mReplaceStatement != null) {
                mReplaceStatement.close();
                mReplaceStatement = null;
            }
            mPreparedStatement = null;
            mInsertSQL = null;
            mColumns = null;
        }
    }
}
