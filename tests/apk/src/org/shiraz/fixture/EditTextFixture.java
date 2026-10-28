// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.text.*;
import android.view.*;
import android.widget.*;
import android.graphics.*;
public final class EditTextFixture {
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 private static void layout(View v,int w,int h){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);}
 private static int[] pixels(View view,int w,int h){layout(view,w,h);Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);try{view.draw(new Canvas(b));int[] p=new int[w*h];b.getPixels(p,0,w,0,0,w,h);return p;}finally{b.recycle();}}
 public static void xml(Context context){
  int passwordId=context.getResources().getIdentifier("password_editor","layout",context.getPackageName());EditText password=(EditText)LayoutInflater.from(context).inflate(passwordId,null,false);check(password.getText().toString().equals("sample")&&password.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod&&password.getTransformationMethod().getTransformation(password.getText(),password).toString().equals("••••••"),"XML appearance cannot remove password input masking");
  int id=context.getResources().getIdentifier("search_editor","layout",context.getPackageName());EditText field=(EditText)LayoutInflater.from(context).inflate(id,null,false);
  check(field.getInputType()==InputType.TYPE_CLASS_TEXT&&field.getMaxLines()==1&&field.getText().toString().equals("initial"),"compiled search input type and text");
  check(field.getImeOptions()==(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH|android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN)&&field.getImeActionId()==77&&"Find".contentEquals(field.getImeActionLabel()),"compiled editor action metadata");
 }
 private static final class TaggedEditable extends SpannableStringBuilder {TaggedEditable(CharSequence value){super(value);}}
 private static void factories(Context context){
  EditText edit=new EditText(context);edit.setText("before");final int[] calls={0};edit.setEditableFactory(new Editable.Factory(){public Editable newEditable(CharSequence value){calls[0]++;return new TaggedEditable(value);}});check(calls[0]==1&&edit.getText() instanceof TaggedEditable&&edit.getText().toString().equals("before"),"factory replaces current editable buffer");
  edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});edit.setText("after");check(calls[0]==2&&edit.getText() instanceof TaggedEditable&&edit.getText().toString().equals("aft"),"factory receives filtered replacement");edit.getText().append("more");check(edit.getText().toString().equals("aft"),"factory buffer keeps edit filters");
  TextView styled=new TextView(context);styled.setText("one",TextView.BufferType.SPANNABLE);final int[] spans={0};styled.setSpannableFactory(new Spannable.Factory(){public Spannable newSpannable(CharSequence value){spans[0]++;return new SpannableString(value);}});styled.setText("two");check(spans[0]==2&&styled.getText() instanceof Spannable,"spannable factory and buffer mode survive replacements");
 }
 private static void numericListeners(Context context){
  android.text.method.NumberKeyListener digits=new android.text.method.NumberKeyListener(){protected char[] getAcceptedChars(){return "0123456789".toCharArray();}public int getInputType(){return InputType.TYPE_CLASS_NUMBER;}};
  EditText field=new EditText(context);field.setKeyListener(digits);field.setText("1x2");check(field.getText().toString().equals("12"),"numeric listener filters programmatic text");field.requestFocus();field.setSelection(1);field.dispatchKeyEvent(new KeyEvent(1,"a3😀4",0,0));check(field.getText().toString().equals("1342"),"numeric committed text filters Unicode and inserts at cursor");field.setSelection(1,3);field.dispatchKeyEvent(new KeyEvent(2,"9",0,0));check(field.getText().toString().equals("192"),"numeric selection replacement");field.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL));check(field.getText().toString().equals("12"),"numeric deletion");field.getText().append("x5");check(field.getText().toString().equals("125"),"direct edits retain listener filter");field.setKeyListener(null);field.getText().append("x");check(field.getText().toString().equals("125x"),"removed listener removes its filter");
  SpannableString styled=new SpannableString("x1y2");Object marker=new Object();styled.setSpan(marker,1,4,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);CharSequence clean=digits.filter(styled,0,4,new SpannedString(""),0,0);check(clean.toString().equals("12")&&clean instanceof Spanned&&((Spanned)clean).getSpanStart(marker)==0&&((Spanned)clean).getSpanEnd(marker)==2,"numeric filter preserves spans");check(digits.filter("12",0,2,new SpannedString(""),0,0)==null,"accepted numeric text unchanged");
  Editable state=new SpannableStringBuilder();KeyEvent down=new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_SHIFT_LEFT),up=new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_SHIFT_LEFT);digits.onKeyDown(field,state,down.getKeyCode(),down);check(android.text.method.MetaKeyKeyListener.getMetaState(state,1)==1,"modifier press tracked");digits.onKeyUp(field,state,up.getKeyCode(),up);check(android.text.method.MetaKeyKeyListener.getMetaState(state)==0,"host chord release clears state");
 }
 private static void keyListeners(Context context){
  numericListeners(context);
  EditText edit=new EditText(context);edit.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);edit.setText("a");edit.setSelection(1);
  final android.text.method.KeyListener delegate=edit.getKeyListener();final int[] calls=new int[4];
  android.text.method.KeyListener wrapper=new android.text.method.KeyListener(){
   public int getInputType(){return delegate.getInputType();}
   public boolean onKeyDown(View view,Editable text,int code,KeyEvent event){calls[0]++;return delegate.onKeyDown(view,text,code,event);}
   public boolean onKeyUp(View view,Editable text,int code,KeyEvent event){calls[1]++;return delegate.onKeyUp(view,text,code,event);}
   public boolean onKeyOther(View view,Editable text,KeyEvent event){calls[2]++;if("blocked".equals(event.getCharacters()))return true;return delegate.onKeyOther(view,text,event);}
   public void clearMetaKeyState(View view,Editable text,int states){calls[3]++;delegate.clearMetaKeyState(view,text,states);}
  };
  edit.setKeyListener(wrapper);check(edit.getKeyListener()==wrapper&&edit.getMaxLines()==1&&edit.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod,"wrapping preserves transformation and line mode");check(edit.requestFocus(),"wrapped editor focus");
  edit.dispatchKeyEvent(new KeyEvent(1,"é😀",0,0));check(edit.getText().toString().equals("aé😀")&&calls[2]==1,"wrapped committed Unicode delegates once");edit.dispatchKeyEvent(new KeyEvent(2,"blocked",0,0));check(edit.getText().toString().equals("aé😀")&&calls[2]==2,"listener intercept prevents default insertion");
  edit.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL));edit.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DEL));check(edit.getText().toString().equals("aé")&&calls[0]==1&&calls[1]==1,"wrapped deletion and release");wrapper.clearMetaKeyState(edit,edit.getText(),KeyEvent.META_SHIFT_ON);check(calls[3]==1,"meta-state callback");
  edit.setKeyListener(null);check(edit.getKeyListener()==null&&edit.getInputType()==InputType.TYPE_NULL&&!edit.onCheckIsTextEditor(),"removing listener disables editor");edit.dispatchKeyEvent(new KeyEvent(3,"x",0,0));check(edit.getText().toString().equals("aé"),"removed listener cannot edit");
  final EditText reentrant=new EditText(context);reentrant.setText("stay");reentrant.requestFocus();reentrant.setOnEditorActionListener(new TextView.OnEditorActionListener(){public boolean onEditorAction(TextView view,int action,KeyEvent event){view.setKeyListener(null);return false;}});reentrant.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));check(reentrant.getText().toString().equals("stay")&&reentrant.getKeyListener()==null,"editor callback can remove key listener");
  TextView plain=new TextView(context);plain.setText("plain");plain.setKeyListener(wrapper);check(plain.requestFocus()&&plain.getEditableText()!=null,"custom listener makes plain TextView editable");Selection.setSelection(plain.getEditableText(),plain.getText().length());plain.dispatchKeyEvent(new KeyEvent(4,"!",0,0));check(plain.getText().toString().equals("plain!"),"plain TextView uses installed listener");plain.setText("again");check(plain.getEditableText()!=null,"listener retains editable buffer on setText");
 }
 private static void editorTypes(Context context){
  keyListeners(context);
  factories(context);
  EditText field=new EditText(context);field.requestFocus();field.setText("a😀");field.setSelection(field.getText().length());field.setInputType(InputType.TYPE_CLASS_TEXT);check(field.getInputType()==1&&field.getMaxLines()==1&&field.getSelectionEnd()==3,"single-line type preserves text and selection");
  final int[] actions={0};field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);field.setOnEditorActionListener(new TextView.OnEditorActionListener(){public boolean onEditorAction(TextView view,int action,KeyEvent event){if(event==null){check(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH,"explicit search action");actions[0]+=10;}else{check(action==android.view.inputmethod.EditorInfo.IME_NULL,"API27 hardware enter action");actions[0]++;}return true;}});
  field.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);check(actions[0]==10,"explicit action delivered once");
  field.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));field.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));check(actions[0]==12&&field.getText().toString().equals("a😀"),"physical enter callbacks do not insert text when handled");
  field.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));field.dispatchKeyEvent(KeyEvent.changeFlags(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER),KeyEvent.FLAG_CANCELED));check(actions[0]==13,"canceled release does not invoke editor action");field.setOnEditorActionListener(null);
  field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);check(field.getMaxLines()>1,"multiline type restores wrapping");field.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));check(field.getText().toString().equals("a😀\n"),"multiline enter inserts newline");
  field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);check(field.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod&&field.getTransformationMethod().getTransformation(field.getText(),field).toString().equals("••••"),"password masks display without changing text");field.setInputType(InputType.TYPE_CLASS_TEXT);check(field.getTransformationMethod()==null,"normal type clears password mask");
  int type=field.getInputType();try{field.setInputType(InputType.TYPE_CLASS_NUMBER);throw new AssertionError("unsupported numeric listener accepted");}catch(UnsupportedOperationException expected){}check(field.getInputType()==type,"failed type change preserves state");
  field.setInputType(InputType.TYPE_NULL);String before=field.getText().toString();check(!field.onCheckIsTextEditor()&&!field.dispatchKeyEvent(new KeyEvent(1,"x",0,0))&&field.getText().toString().equals(before),"null type disables text entry");
  field.setInputType(InputType.TYPE_CLASS_TEXT);field.setRawInputType(InputType.TYPE_NULL);check(!field.onCheckIsTextEditor()&&field.dispatchKeyEvent(new KeyEvent(2,"x",0,0)),"raw input type changes metadata without removing key handling");
 }
 public static void check(Context context){
  editorTypes(context);
  EditText selectable=new EditText(context);selectable.setText("select");selectable.setSelection(1,4);android.text.method.KeyListener listener=selectable.getKeyListener();selectable.setTextIsSelectable(true);check(selectable.isTextSelectable()&&selectable.isFocusable()&&selectable.getKeyListener()==listener&&selectable.getSelectionStart()==1&&selectable.getSelectionEnd()==4,"editable selection policy preserves editing");selectable.setTextIsSelectable(false);check(!selectable.isTextSelectable()&&selectable.getKeyListener()==listener,"clearing selection policy retains editor");
  EditText edit=new EditText(context);FrameLayout root=new FrameLayout(context);root.addView(edit,new FrameLayout.LayoutParams(140,80));layout(root,160,100);check(edit.requestFocus()&&edit.onCheckIsTextEditor(),"Editable focus");
  final StringBuilder trace=new StringBuilder();edit.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){trace.append('B');}public void onTextChanged(CharSequence s,int a,int b,int c){trace.append('O');}public void afterTextChanged(Editable s){trace.append('A');}});
  edit.setText("ab");check(trace.toString().equals("BOA"),"Set text callback order");trace.setLength(0);edit.setSelection(1);check(root.dispatchKeyEvent(new KeyEvent(1,"é😀",0,0)),"Focused committed text dispatch");check(edit.getText().toString().equals("aé😀b")&&edit.getSelectionEnd()==4&&trace.toString().equals("BOA"),"Unicode insertion and cursor");
  root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL));check(edit.getText().toString().equals("aéb")&&edit.getSelectionEnd()==2,"Backspace whole supplementary character");
  edit.setSelection(1,2);root.dispatchKeyEvent(new KeyEvent(2,"Q",0,0));check(edit.getText().toString().equals("aQb")&&edit.getSelectionEnd()==2,"Replace selected text");
  root.dispatchKeyEvent(new KeyEvent(0,3,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,0,KeyEvent.META_SHIFT_ON));check(edit.getSelectionStart()==2&&edit.getSelectionEnd()==1,"Shift selection");
  root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_FORWARD_DEL));check(edit.getText().toString().equals("ab")&&edit.getSelectionEnd()==1,"Delete selection");
  Editable old=edit.getText();edit.setText("new");trace.setLength(0);old.append(" stale");check(trace.length()==0&&edit.getText().toString().equals("new"),"Detached old buffer cannot notify widget");
  edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});edit.setSelection(3);root.dispatchKeyEvent(new KeyEvent(3,"XY",0,0));check(edit.getText().toString().equals("newX")&&edit.getSelectionEnd()==4,"Filtered native text");
  edit.getText().replace(0,1,"\u05d0");check(edit.getText().toString().equals("\u05d0ewX"),"Bidi edit preserves logical text");
  edit.setFilters(new InputFilter[0]);edit.setText("אבג");edit.setSelection(0);edit.setCursorVisible(false);int[] rtlPlain=pixels(edit,140,80);float rtlWidth=edit.getPaint().measureText("אבג");check(edit.getOffsetForPosition(edit.getCompoundPaddingLeft(),edit.getBaseline())==3&&edit.getOffsetForPosition(edit.getCompoundPaddingLeft()+rtlWidth,edit.getBaseline())==0,"RTL visual pointer offsets");
  edit.setCursorVisible(true);check(!java.util.Arrays.equals(rtlPlain,pixels(edit,140,80)),"RTL primary caret rendered");edit.setSelection(1,2);check(!java.util.Arrays.equals(rtlPlain,pixels(edit,140,80)),"RTL logical selection rendered");
  root.dispatchKeyEvent(new KeyEvent(4,"ح",0,0));check(edit.getText().toString().equals("אحג")&&edit.getSelectionEnd()==2,"RTL selection replacement through input");
  edit.setFilters(new InputFilter[0]);edit.setText("abcd");edit.setSelection(2);edit.setCursorVisible(false);TextView expected=new TextView(context);expected.setText("abcd");check(java.util.Arrays.equals(pixels(edit,140,80),pixels(expected,140,80)),"Editable text pixels match plain text");
  edit.setCursorVisible(true);check(!java.util.Arrays.equals(pixels(edit,140,80),pixels(expected,140,80)),"Focused cursor is rendered");
  edit.selectAll();check(!java.util.Arrays.equals(pixels(edit,140,80),pixels(expected,140,80)),"Selection is rendered");
  int flags=edit.getPaintFlags();edit.setPaintFlags(flags|Paint.UNDERLINE_TEXT_FLAG);check((edit.getPaint().getFlags()&Paint.UNDERLINE_TEXT_FLAG)!=0,"widget paint flags reach renderer");edit.setPaintFlags(flags);
  edit.setHighlightColor(0xffff0000);int[] selectedPixels=pixels(edit,140,80);boolean red=false;for(int color:selectedPixels)red|=color==0xffff0000;check(red&&edit.getHighlightColor()==0xffff0000,"custom selection color rendered");
  edit.setHighlightColor(0);check(java.util.Arrays.equals(pixels(edit,140,80),pixels(expected,140,80)),"transparent selection preserves text pixels");
  layout(edit,140,80);float x=edit.getPaint().measureText("ab");MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,x,edit.getBaseline(),0);edit.dispatchTouchEvent(down);down.recycle();check(edit.getSelectionEnd()==2,"Pointer hit test");MotionEvent up=MotionEvent.obtain(0,1,MotionEvent.ACTION_UP,x,edit.getBaseline(),0);edit.dispatchTouchEvent(up);up.recycle();
  edit.setText("one two three four five six");pixels(edit,140,80);int wide=edit.getLineCount();pixels(edit,40,140);check(edit.getLineCount()>wide,"Editable portrait reflow");
  edit.setText("a");edit.setSelection(1);edit.setEnabled(false);check(!edit.dispatchKeyEvent(new KeyEvent(4,"bad",0,0))&&edit.getText().toString().equals("a"),"Disabled input rejected");
  TextView filtered=new TextView(context);filtered.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});filtered.setText("four");check(filtered.getText().toString().equals("fo")&&filtered.getEditableText()==null,"Normal filtered text keeps noneditable buffer");
  System.out.println("EDITTEXT_INPUT_SELECTION_RENDER_OK");
 }
}
