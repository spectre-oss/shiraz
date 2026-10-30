// SPDX-License-Identifier: GPL-3.0-only
package android.database;

/** SQL text helpers. Statement classification follows the API-27 prefix contract. */
public class DatabaseUtils {
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
}
