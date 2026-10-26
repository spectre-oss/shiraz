// SPDX-License-Identifier: GPL-3.0-only
package android.database.sqlite;
public interface SQLiteTransactionListener { void onBegin(); void onCommit(); void onRollback(); }
