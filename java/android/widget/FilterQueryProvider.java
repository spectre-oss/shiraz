// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.database.Cursor;

/** Callback interface that runs filtered cursor queries on demand. */
public interface FilterQueryProvider {
    Cursor runQuery(CharSequence constraint);
}
