// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;
import android.text.*;
import android.view.*;
/** Phone punctuation filter shared by committed input, paste and input connections. */
public class DialerKeyListener extends NumberKeyListener {
 public static final char[] CHARACTERS="0123456789#*+-(),/N.; ".toCharArray();
 private static final DialerKeyListener INSTANCE=new DialerKeyListener();
 private final shiraz.runtime.TextKeyListener editor=new shiraz.runtime.TextKeyListener(InputType.TYPE_CLASS_PHONE);
 public static DialerKeyListener getInstance(){return INSTANCE;}
 @Override public int getInputType(){return InputType.TYPE_CLASS_PHONE;}
 @Override protected char[] getAcceptedChars(){return CHARACTERS.clone();}
 @Override public boolean onKeyDown(View view,Editable text,int code,KeyEvent event){return editor.onKeyDown(view,text,code,event);}
 @Override public boolean onKeyUp(View view,Editable text,int code,KeyEvent event){return editor.onKeyUp(view,text,code,event);}
 @Override public boolean onKeyOther(View view,Editable text,KeyEvent event){return editor.onKeyOther(view,text,event);}
 @Override public void clearMetaKeyState(View view,Editable text,int states){editor.clearMetaKeyState(view,text,states);}
}
