// SPDX-License-Identifier: GPL-3.0-only
package android.database;

/** SQL text helpers. Statement classification follows the API-27 prefix contract. */
public class DatabaseUtils {
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
