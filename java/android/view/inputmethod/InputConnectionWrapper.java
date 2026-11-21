// SPDX-License-Identifier: GPL-3.0-only

package android.view.inputmethod;

import android.graphics.RectF;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.view.KeyEvent;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Delegates a desktop input connection; an empty target remains unavailable. */
public class InputConnectionWrapper implements InputConnection {
    private InputConnection target;
    private final boolean mutable;

    public InputConnectionWrapper(InputConnection target, boolean mutable) {
        this.target = target;
        this.mutable = mutable;
    }

    public void setTarget(InputConnection replacement) {
        if (!mutable && target != null) {
            throw new SecurityException("Immutable input connection");
        }
        target = replacement;
    }

    public InputConnection getTarget() {
        return target;
    }

    @Override
    public CharSequence getTextBeforeCursor(int length, int flags) {
        return target == null ? null : target.getTextBeforeCursor(length, flags);
    }

    @Override
    public CharSequence getTextAfterCursor(int length, int flags) {
        return target == null ? null : target.getTextAfterCursor(length, flags);
    }

    @Override
    public CharSequence getSelectedText(int flags) {
        return target == null ? null : target.getSelectedText(flags);
    }

    @Override
    public SurroundingText getSurroundingText(int beforeLength, int afterLength, int flags) {
        return target == null ? null : target.getSurroundingText(beforeLength, afterLength, flags);
    }

    @Override
    public int getCursorCapsMode(int modes) {
        return target == null ? 0 : target.getCursorCapsMode(modes);
    }

    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
        return target == null ? null : target.getExtractedText(request, flags);
    }

    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        return target != null && target.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
    }

    @Override
    public boolean deleteSurroundingText(int before, int after) {
        return target != null && target.deleteSurroundingText(before, after);
    }

    @Override
    public boolean setComposingText(CharSequence text, int position) {
        return target != null && target.setComposingText(text, position);
    }

    @Override
    public boolean setComposingText(
            CharSequence text, int newCursorPosition, TextAttribute textAttribute) {
        return target != null && target.setComposingText(text, newCursorPosition, textAttribute);
    }

    @Override
    public boolean setComposingRegion(int start, int end) {
        return target != null && target.setComposingRegion(start, end);
    }

    @Override
    public boolean setComposingRegion(int start, int end, TextAttribute textAttribute) {
        return target != null && target.setComposingRegion(start, end, textAttribute);
    }

    @Override
    public boolean finishComposingText() {
        return target != null && target.finishComposingText();
    }

    @Override
    public boolean commitText(CharSequence text, int position) {
        return target != null && target.commitText(text, position);
    }

    @Override
    public boolean commitText(
            CharSequence text, int newCursorPosition, TextAttribute textAttribute) {
        return target != null && target.commitText(text, newCursorPosition, textAttribute);
    }

    @Override
    public boolean commitCompletion(CompletionInfo text) {
        return target != null && target.commitCompletion(text);
    }

    @Override
    public boolean commitCorrection(CorrectionInfo correctionInfo) {
        return target != null && target.commitCorrection(correctionInfo);
    }

    @Override
    public boolean setSelection(int start, int end) {
        return target != null && target.setSelection(start, end);
    }

    @Override
    public boolean performEditorAction(int action) {
        return target != null && target.performEditorAction(action);
    }

    @Override
    public boolean performContextMenuAction(int id) {
        return target != null && target.performContextMenuAction(id);
    }

    @Override
    public boolean beginBatchEdit() {
        return target != null && target.beginBatchEdit();
    }

    @Override
    public boolean endBatchEdit() {
        return target != null && target.endBatchEdit();
    }

    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        return target != null && target.sendKeyEvent(event);
    }

    @Override
    public boolean clearMetaKeyStates(int states) {
        return target != null && target.clearMetaKeyStates(states);
    }

    @Override
    public boolean reportFullscreenMode(boolean enabled) {
        return target != null && target.reportFullscreenMode(enabled);
    }

    @Override
    public boolean performSpellCheck() {
        return target != null && target.performSpellCheck();
    }

    @Override
    public boolean performPrivateCommand(String action, Bundle data) {
        return target != null && target.performPrivateCommand(action, data);
    }

    @Override
    public void performHandwritingGesture(
            HandwritingGesture gesture, Executor executor, IntConsumer consumer) {
        if (target != null) {
            target.performHandwritingGesture(gesture, executor, consumer);
        }
    }

    @Override
    public boolean previewHandwritingGesture(
            PreviewableHandwritingGesture gesture, CancellationSignal cancellationSignal) {
        return target != null && target.previewHandwritingGesture(gesture, cancellationSignal);
    }

    @Override
    public boolean requestCursorUpdates(int cursorUpdateMode) {
        return target != null && target.requestCursorUpdates(cursorUpdateMode);
    }

    @Override
    public boolean requestCursorUpdates(int cursorUpdateMode, int cursorUpdateFilter) {
        return target != null && target.requestCursorUpdates(cursorUpdateMode, cursorUpdateFilter);
    }

    @Override
    public void requestTextBoundsInfo(
            RectF bounds, Executor executor, Consumer<TextBoundsInfoResult> consumer) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(consumer, "consumer");
        if (target != null) {
            target.requestTextBoundsInfo(bounds, executor, consumer);
        }
    }

    @Override
    public Handler getHandler() {
        return target == null ? null : target.getHandler();
    }

    @Override
    public void closeConnection() {
        if (target != null) {
            target.closeConnection();
        }
    }

    @Override
    public boolean commitContent(
            InputContentInfo inputContentInfo, int flags, Bundle opts) {
        return target != null && target.commitContent(inputContentInfo, flags, opts);
    }

    @Override
    public boolean setImeConsumesInput(boolean imeConsumesInput) {
        return target != null && target.setImeConsumesInput(imeConsumesInput);
    }

    @Override
    public TextSnapshot takeSnapshot() {
        return target == null ? null : target.takeSnapshot();
    }

    @Override
    public boolean replaceText(
            int start,
            int end,
            CharSequence text,
            int newCursorPosition,
            TextAttribute textAttribute) {
        return target != null
                && target.replaceText(start, end, text, newCursorPosition, textAttribute);
    }
}
