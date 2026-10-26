// SPDX-License-Identifier: GPL-3.0-only
package android.view.inputmethod;
public interface InputConnection {
 int GET_TEXT_WITH_STYLES=1,GET_EXTRACTED_TEXT_MONITOR=1,CURSOR_UPDATE_IMMEDIATE=1,CURSOR_UPDATE_MONITOR=2;
 boolean commitText(CharSequence text,int newCursorPosition);
 boolean setComposingText(CharSequence text,int newCursorPosition);
 boolean finishComposingText();
 boolean beginBatchEdit();
 boolean endBatchEdit();
 boolean deleteSurroundingText(int beforeLength,int afterLength);
 boolean setSelection(int start,int end);
 boolean sendKeyEvent(android.view.KeyEvent event);
 boolean performEditorAction(int action);
 CharSequence getTextBeforeCursor(int length,int flags);
 CharSequence getTextAfterCursor(int length,int flags);
 CharSequence getSelectedText(int flags);
 int getCursorCapsMode(int modes);
 ExtractedText getExtractedText(ExtractedTextRequest request,int flags);
 void closeConnection();
}
