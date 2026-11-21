// SPDX-License-Identifier: GPL-3.0-only
package android.database.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.CancellationSignal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parameterized SQL SELECT/UNION/INSERT/UPDATE/DELETE builder with projection maps and strict
 * column/grammar validation.
 */
public class SQLiteQueryBuilder {
    private static final Pattern LIMIT_PATTERN = Pattern.compile("\\s*\\d+\\s*(,\\s*\\d+\\s*)?");
    private static final int STRICT_PARENTHESES = 1;
    private static final int STRICT_COLUMNS = 2;
    private static final int STRICT_GRAMMAR = 4;

    private Map<String, String> mProjectionMap;
    private Collection<Pattern> mProjectionGreylist;
    private String mTables = "";
    private StringBuilder mWhereClause;
    private boolean mDistinct;
    private SQLiteDatabase.CursorFactory mFactory;
    private int mStrictFlags;

    public SQLiteQueryBuilder() {}

    public void setDistinct(boolean distinct) {
        mDistinct = distinct;
    }

    public boolean isDistinct() {
        return mDistinct;
    }

    public String getTables() {
        return mTables;
    }

    public void setTables(String inTables) {
        mTables = inTables == null ? "" : inTables;
    }

    public void appendWhere(CharSequence inWhere) {
        if (mWhereClause == null) {
            mWhereClause = new StringBuilder(inWhere.length() + 16);
        }
        mWhereClause.append(inWhere);
    }

    public void appendWhereEscapeString(String inWhere) {
        if (mWhereClause == null) {
            mWhereClause = new StringBuilder(inWhere.length() + 16);
        }
        DatabaseUtils.appendEscapedSQLString(mWhereClause, inWhere);
    }

    public void appendWhereStandalone(CharSequence inWhere) {
        if (mWhereClause == null) {
            mWhereClause = new StringBuilder(inWhere.length() + 16);
        }
        if (mWhereClause.length() > 0) {
            mWhereClause.append(" AND ");
        }
        mWhereClause.append('(').append(inWhere).append(')');
    }

    public void setProjectionMap(Map<String, String> columnMap) {
        mProjectionMap = columnMap;
    }

    public Map<String, String> getProjectionMap() {
        return mProjectionMap;
    }

    public void setProjectionGreylist(Collection<Pattern> projectionGreylist) {
        mProjectionGreylist = projectionGreylist;
    }

    public Collection<Pattern> getProjectionGreylist() {
        return mProjectionGreylist;
    }

    public void setCursorFactory(SQLiteDatabase.CursorFactory factory) {
        mFactory = factory;
    }

    public SQLiteDatabase.CursorFactory getCursorFactory() {
        return mFactory;
    }

    public void setStrict(boolean strict) {
        if (strict) {
            mStrictFlags |= STRICT_PARENTHESES;
        } else {
            mStrictFlags &= ~STRICT_PARENTHESES;
        }
    }

    public boolean isStrict() {
        return (mStrictFlags & STRICT_PARENTHESES) != 0;
    }

    public void setStrictColumns(boolean strictColumns) {
        if (strictColumns) {
            mStrictFlags |= STRICT_COLUMNS;
        } else {
            mStrictFlags &= ~STRICT_COLUMNS;
        }
    }

    public boolean isStrictColumns() {
        return (mStrictFlags & STRICT_COLUMNS) != 0;
    }

    public void setStrictGrammar(boolean strictGrammar) {
        if (strictGrammar) {
            mStrictFlags |= STRICT_GRAMMAR;
        } else {
            mStrictFlags &= ~STRICT_GRAMMAR;
        }
    }

    public boolean isStrictGrammar() {
        return (mStrictFlags & STRICT_GRAMMAR) != 0;
    }

    public static String buildQueryString(
            boolean distinct,
            String tables,
            String[] columns,
            String where,
            String groupBy,
            String having,
            String orderBy,
            String limit) {
        if (isEmpty(groupBy) && !isEmpty(having)) {
            throw new IllegalArgumentException(
                    "HAVING clauses are only permitted when using a groupBy clause");
        }
        if (!isEmpty(limit) && !LIMIT_PATTERN.matcher(limit).matches()) {
            throw new IllegalArgumentException("invalid LIMIT clauses:" + limit);
        }
        StringBuilder query = new StringBuilder(120);
        query.append("SELECT ");
        if (distinct) {
            query.append("DISTINCT ");
        }
        if (columns != null && columns.length != 0) {
            appendColumns(query, columns);
        } else {
            query.append("* ");
        }
        query.append("FROM ");
        query.append(tables);
        appendClause(query, " WHERE ", where);
        appendClause(query, " GROUP BY ", groupBy);
        appendClause(query, " HAVING ", having);
        appendClause(query, " ORDER BY ", orderBy);
        appendClause(query, " LIMIT ", limit);
        return query.toString();
    }

    private static void appendClause(StringBuilder s, String name, String clause) {
        if (!isEmpty(clause)) {
            s.append(name);
            s.append(clause);
        }
    }

    public static void appendColumns(StringBuilder s, String[] columns) {
        int n = columns.length;
        for (int i = 0; i < n; i++) {
            String column = columns[i];
            if (column != null) {
                if (i > 0) {
                    s.append(", ");
                }
                s.append(column);
            }
        }
        s.append(' ');
    }

    public Cursor query(
            SQLiteDatabase db,
            String[] projectionIn,
            String selection,
            String[] selectionArgs,
            String groupBy,
            String having,
            String sortOrder) {
        return query(db, projectionIn, selection, selectionArgs, groupBy, having, sortOrder, null, null);
    }

    public Cursor query(
            SQLiteDatabase db,
            String[] projectionIn,
            String selection,
            String[] selectionArgs,
            String groupBy,
            String having,
            String sortOrder,
            String limit) {
        return query(db, projectionIn, selection, selectionArgs, groupBy, having, sortOrder, limit, null);
    }

    public Cursor query(
            SQLiteDatabase db,
            String[] projectionIn,
            String selection,
            String[] selectionArgs,
            String groupBy,
            String having,
            String sortOrder,
            String limit,
            CancellationSignal cancellationSignal) {
        if (mTables == null || mTables.isEmpty()) {
            return null;
        }
        if (cancellationSignal != null) {
            cancellationSignal.throwIfCanceled();
        }
        String sql = buildQuery(projectionIn, selection, groupBy, having, sortOrder, limit);
        if (isStrictGrammar()) {
            validateUnbalancedSql(selection);
        }
        return db.rawQueryWithFactory(mFactory, sql, selectionArgs, findFirstTable(mTables));
    }

    public long insert(SQLiteDatabase db, ContentValues values) {
        Objects.requireNonNull(mTables, "No tables defined");
        Objects.requireNonNull(db, "No database defined");
        if (values != null && isStrictColumns() && mProjectionMap != null) {
            for (String col : values.keySet()) {
                enforceStrictColumn(col);
            }
        }
        return db.insert(findFirstTable(mTables), null, values);
    }

    public int update(
            SQLiteDatabase db, ContentValues values, String selection, String[] selectionArgs) {
        Objects.requireNonNull(mTables, "No tables defined");
        Objects.requireNonNull(db, "No database defined");
        Objects.requireNonNull(values, "No values defined");
        if (isStrictColumns() && mProjectionMap != null) {
            for (String col : values.keySet()) {
                enforceStrictColumn(col);
            }
        }
        String where = computeWhere(selection);
        return db.update(findFirstTable(mTables), values, where, selectionArgs);
    }

    public int delete(SQLiteDatabase db, String selection, String[] selectionArgs) {
        Objects.requireNonNull(mTables, "No tables defined");
        Objects.requireNonNull(db, "No database defined");
        String where = computeWhere(selection);
        return db.delete(findFirstTable(mTables), where, selectionArgs);
    }

    public String buildQuery(
            String[] projectionIn,
            String selection,
            String groupBy,
            String having,
            String sortOrder,
            String limit) {
        String[] projection = computeProjection(projectionIn);
        String where = computeWhere(selection);
        return buildQueryString(
                mDistinct, mTables, projection, where, groupBy, having, sortOrder, limit);
    }

    @Deprecated
    public String buildQuery(
            String[] projectionIn,
            String selection,
            String[] selectionArgs,
            String groupBy,
            String having,
            String sortOrder,
            String limit) {
        return buildQuery(projectionIn, selection, groupBy, having, sortOrder, limit);
    }

    public String buildUnionSubQuery(
            String typeDiscriminatorColumn,
            String[] unionColumns,
            Set<String> columnsPresentInTable,
            int computedColumnsOffset,
            String typeDiscriminatorValue,
            String selection,
            String groupBy,
            String having) {
        int unionColumnsCount = unionColumns.length;
        String[] projectionIn = new String[unionColumnsCount];
        for (int i = 0; i < unionColumnsCount; i++) {
            String unionColumn = unionColumns[i];
            if (unionColumn.equals(typeDiscriminatorColumn)) {
                projectionIn[i] = "'" + typeDiscriminatorValue + "' AS " + typeDiscriminatorColumn;
            } else if (i <= computedColumnsOffset || columnsPresentInTable.contains(unionColumn)) {
                projectionIn[i] = unionColumn;
            } else {
                projectionIn[i] = "NULL AS " + unionColumn;
            }
        }
        return buildQuery(projectionIn, selection, groupBy, having, null, null);
    }

    @Deprecated
    public String buildUnionSubQuery(
            String typeDiscriminatorColumn,
            String[] unionColumns,
            Set<String> columnsPresentInTable,
            int computedColumnsOffset,
            String typeDiscriminatorValue,
            String selection,
            String[] selectionArgs,
            String groupBy,
            String having) {
        return buildUnionSubQuery(
                typeDiscriminatorColumn,
                unionColumns,
                columnsPresentInTable,
                computedColumnsOffset,
                typeDiscriminatorValue,
                selection,
                groupBy,
                having);
    }

    public String buildUnionQuery(String[] subQueries, String sortOrder, String limit) {
        StringBuilder query = new StringBuilder(128);
        int subQueryCount = subQueries.length;
        String unionOperator = mDistinct ? " UNION " : " UNION ALL ";
        for (int i = 0; i < subQueryCount; i++) {
            if (i > 0) {
                query.append(unionOperator);
            }
            query.append(subQueries[i]);
        }
        appendClause(query, " ORDER BY ", sortOrder);
        appendClause(query, " LIMIT ", limit);
        return query.toString();
    }

    private String[] computeProjection(String[] projectionIn) {
        if (projectionIn != null && projectionIn.length > 0) {
            if (mProjectionMap != null) {
                String[] projection = new String[projectionIn.length];
                int length = projectionIn.length;
                for (int i = 0; i < length; i++) {
                    String userColumn = projectionIn[i];
                    String column = mProjectionMap.get(userColumn);
                    if (column != null) {
                        projection[i] = column;
                        continue;
                    }
                    if (!isStrictColumns()
                            && (userColumn.contains(" AS ") || userColumn.contains(" as "))) {
                        projection[i] = userColumn;
                        continue;
                    }
                    if (mProjectionGreylist != null) {
                        boolean match = false;
                        for (Pattern p : mProjectionGreylist) {
                            if (p.matcher(userColumn).matches()) {
                                match = true;
                                break;
                            }
                        }
                        if (match) {
                            projection[i] = userColumn;
                            continue;
                        }
                    }
                    throw new IllegalArgumentException("Invalid column " + projectionIn[i]);
                }
                return projection;
            }
            return projectionIn;
        } else if (mProjectionMap != null) {
            Set<Map.Entry<String, String>> entrySet = mProjectionMap.entrySet();
            String[] projection = new String[entrySet.size()];
            Iterator<Map.Entry<String, String>> entryIter = entrySet.iterator();
            int i = 0;
            while (entryIter.hasNext()) {
                Map.Entry<String, String> entry = entryIter.next();
                if ("_count".equals(entry.getKey())) {
                    continue;
                }
                projection[i++] = entry.getValue();
            }
            return Arrays.copyOf(projection, i);
        }
        return null;
    }

    private String computeWhere(String selection) {
        boolean hasInternal = mWhereClause != null && mWhereClause.length() > 0;
        boolean hasExternal = !isEmpty(selection);
        if (!hasInternal && !hasExternal) {
            return null;
        }
        StringBuilder where = new StringBuilder();
        if (hasInternal) {
            where.append('(').append(mWhereClause).append(')');
        }
        if (hasInternal && hasExternal) {
            where.append(" AND ");
        }
        if (hasExternal) {
            if (isStrict()) {
                validateUnbalancedSql(selection);
                where.append('(').append(selection).append(')');
            } else {
                where.append(selection);
            }
        }
        return where.toString();
    }

    private void enforceStrictColumn(String column) {
        if (!mProjectionMap.containsKey(column)) {
            throw new IllegalArgumentException("Invalid column " + column);
        }
    }

    private static void validateUnbalancedSql(String sql) {
        if (sql == null) {
            return;
        }
        int depth = 0;
        boolean inQuotes = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                inQuotes = !inQuotes;
            } else if (!inQuotes) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth < 0) {
                        throw new IllegalArgumentException("Unbalanced parentheses in SQL: " + sql);
                    }
                } else if (c == ';') {
                    throw new IllegalArgumentException("Unexpected semicolon in strict SQL: " + sql);
                }
            }
        }
        if (inQuotes || depth != 0) {
            throw new IllegalArgumentException("Malformed strict SQL clause: " + sql);
        }
    }

    private static String findFirstTable(String tables) {
        if (tables == null) {
            return "";
        }
        int space = tables.indexOf(' ');
        int comma = tables.indexOf(',');
        if (space >= 0 && (comma < 0 || space < comma)) {
            return tables.substring(0, space);
        }
        if (comma >= 0) {
            return tables.substring(0, comma);
        }
        return tables;
    }

    private static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }
}
