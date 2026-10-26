// SPDX-License-Identifier: GPL-3.0-only
package android.view.inputmethod;
/** Delegates a desktop input connection; an empty target remains unavailable. */
public class InputConnectionWrapper implements InputConnection {
 private InputConnection target;private final boolean mutable;
 public InputConnectionWrapper(InputConnection target,boolean mutable){this.target=target;this.mutable=mutable;}
 public void setTarget(InputConnection replacement){if(!mutable&&target!=null)throw new SecurityException("Immutable input connection");target=replacement;}
 public InputConnection getTarget(){return target;}
 public boolean commitText(CharSequence text,int position){return target==null?false:target.commitText(text,position);}
 public boolean setComposingText(CharSequence text,int position){return target==null?false:target.setComposingText(text,position);}
 public boolean finishComposingText(){return target==null?false:target.finishComposingText();}
 public boolean beginBatchEdit(){return target==null?false:target.beginBatchEdit();}
 public boolean endBatchEdit(){return target==null?false:target.endBatchEdit();}
 public boolean deleteSurroundingText(int before,int after){return target==null?false:target.deleteSurroundingText(before,after);}
 public boolean setSelection(int start,int end){return target==null?false:target.setSelection(start,end);}
 public boolean sendKeyEvent(android.view.KeyEvent event){return target==null?false:target.sendKeyEvent(event);}
 public boolean performEditorAction(int action){return target==null?false:target.performEditorAction(action);}
 public CharSequence getTextBeforeCursor(int length,int flags){return target==null?null:target.getTextBeforeCursor(length,flags);}
 public CharSequence getTextAfterCursor(int length,int flags){return target==null?null:target.getTextAfterCursor(length,flags);}
 public CharSequence getSelectedText(int flags){return target==null?null:target.getSelectedText(flags);}
 public int getCursorCapsMode(int modes){return target==null?0:target.getCursorCapsMode(modes);}
 public ExtractedText getExtractedText(ExtractedTextRequest request,int flags){return target==null?null:target.getExtractedText(request,flags);}
 public void closeConnection(){if(target!=null)target.closeConnection();}
}
