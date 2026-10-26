// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.text.*;
import android.util.AttributeSet;
import android.view.*;
/** Plain editable text using committed SDL text and navigation keys. IME preedit is separate. */
public class EditText extends TextView {
 @Override public boolean getFreezesText(){return true;}
 public EditText(Context context){this(context,null);}
 public EditText(Context context,AttributeSet attrs){this(context,attrs,0);}
 public EditText(Context context,AttributeSet attrs,int style){this(context,attrs,style,0);}
 public EditText(Context context,AttributeSet attrs,int style,int resource){super(context,attrs,style,resource);setText(super.getText(),BufferType.EDITABLE);setFocusableInTouchMode(true);setClickable(true);setSelection(0);}
 @Override public void setText(CharSequence value,BufferType type){
  super.setText(value,BufferType.EDITABLE);
  Editable editable=getText();
  if(editable!=null&&(Selection.getSelectionStart(editable)<0||Selection.getSelectionEnd(editable)<0)){
   Selection.setSelection(editable,editable.length());
  }
 }
 @Override public Editable getText(){return getEditableText();}
 public void setSelection(int start,int end){
  Editable editable=getText();
  int len=editable==null?0:editable.length();
  Selection.setSelection(editable,Math.max(0,Math.min(start,len)),Math.max(0,Math.min(end,len)));
 }
 public void setSelection(int index){setSelection(index,index);}
 public void selectAll(){Selection.selectAll(getText());}
 public void extendSelection(int index){
  Editable editable=getText();
  int len=editable==null?0:editable.length();
  Selection.extendSelection(editable,Math.max(0,Math.min(index,len)));
 }
 private boolean selecting,dragging;
 private float downX,downY;
 @Override public boolean onTouchEvent(MotionEvent event){if(!isEnabled())return false;switch(event.getActionMasked()){
  case MotionEvent.ACTION_DOWN:requestFocus();downX=event.getX();downY=event.getY();setSelection(getOffsetForPosition(downX,downY));selecting=true;dragging=false;return true;
  case MotionEvent.ACTION_MOVE:if(selecting){if(!dragging&&Math.hypot(event.getX()-downX,event.getY()-downY)>=ViewConfiguration.get(getContext()).getScaledTouchSlop())dragging=true;if(dragging)extendSelection(getOffsetForPosition(event.getX(),event.getY()));return true;}break;
  case MotionEvent.ACTION_UP:if(selecting){if(dragging)extendSelection(getOffsetForPosition(event.getX(),event.getY()));selecting=false;dragging=false;performClick();if(getShowSoftInputOnFocus()){Object service=getContext().getSystemService(Context.INPUT_METHOD_SERVICE);if(service instanceof android.view.inputmethod.InputMethodManager)((android.view.inputmethod.InputMethodManager)service).showSoftInput(this,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);}return true;}break;
  case MotionEvent.ACTION_CANCEL:selecting=false;dragging=false;return true;
 }return super.onTouchEvent(event);}
 @Override protected void onFocusChanged(boolean gain,int direction,android.graphics.Rect previous){super.onFocusChanged(gain,direction,previous);if(!gain){selecting=false;dragging=false;}}
 @Override protected void shirazInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.shirazInitializeAccessibilityNodeInfo(info);info.setEditable(shirazAcceptsTextInput());info.setTextSelection(getSelectionStart(),getSelectionEnd());}
}
