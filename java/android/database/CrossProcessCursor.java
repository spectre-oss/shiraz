// SPDX-License-Identifier: GPL-3.0-only
package android.database;
public interface CrossProcessCursor extends Cursor {
    CursorWindow getWindow();
    void fillWindow(int position,CursorWindow window);
    boolean onMove(int oldPosition,int newPosition);
}
