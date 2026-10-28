// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.View;
/** Hardware-key editing with text composition supplied by the desktop input method. */
public class QwertyKeyListener extends BaseKeyListener {
 private static final QwertyKeyListener FULL=new QwertyKeyListener(TextKeyListener.Capitalize.NONE,false);
 private static final QwertyKeyListener[] INSTANCES=new QwertyKeyListener[8];
 private final int inputType;
 private final shiraz.runtime.TextKeyListener editor;
 public QwertyKeyListener(TextKeyListener.Capitalize cap,boolean autoText){
  inputType=new TextKeyListener(cap,autoText).getInputType();
  editor=new shiraz.runtime.TextKeyListener(inputType);
 }
 public static synchronized QwertyKeyListener getInstance(boolean autoText,TextKeyListener.Capitalize cap){
  int index=cap.ordinal()*2+(autoText?1:0);
  if(INSTANCES[index]==null)INSTANCES[index]=new QwertyKeyListener(cap,autoText);
  return INSTANCES[index];
 }
 public static QwertyKeyListener getInstanceForFullKeyboard(){return FULL;}
 @Override public int getInputType(){return inputType;}
 @Override public boolean onKeyDown(View view,Editable text,int code,KeyEvent event){return editor.onKeyDown(view,text,code,event);}
}
