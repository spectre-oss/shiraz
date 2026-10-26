// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Parcelable;
import android.util.*;
import android.view.*;
import android.widget.*;
public final class CompoundButtonFixture {
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static class Toggle extends CheckBox {public Toggle(Context context){this(context,null);}public Toggle(Context context,AttributeSet attrs){super(context,attrs,0);}}
 private static final class Indicator extends ColorDrawable {public int getIntrinsicWidth(){return 8;}public int getIntrinsicHeight(){return 12;}public boolean isStateful(){return true;}protected boolean onStateChange(int[] state){boolean checked=false;for(int value:state)if(value==android.R.attr.state_checked)checked=true;setColor(checked?Color.GREEN:Color.RED);return true;}}
 private static Bitmap draw(View view){view.measure(View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(60,View.MeasureSpec.EXACTLY));view.layout(0,0,100,60);Bitmap image=Bitmap.createBitmap(100,60,Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));return image;}
 private static int height(TextView view,int mode,int limit){view.measure(View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(limit,mode));return view.getMeasuredHeight();}
 private static void heights(Context context){
  TextView view=new TextView(context);view.setText("one\ntwo\nthree");view.setPadding(0,7,0,9);view.setHeight(40);check(view.getMinHeight()==40&&view.getMaxHeight()==40&&view.getMinLines()==-1&&view.getMaxLines()==-1,"Pixel height mode metadata");check(height(view,View.MeasureSpec.UNSPECIFIED,0)==40,"Height includes padding");check(height(view,View.MeasureSpec.AT_MOST,25)==25&&height(view,View.MeasureSpec.EXACTLY,70)==70,"Parent size constraints override requested height");
  view.setLines(2);check(view.getMinHeight()==-1&&view.getMaxHeight()==-1&&view.getMinLines()==2&&view.getMaxLines()==2,"Line mode overrides fixed height");view.setMinHeight(80);view.setMaxHeight(30);check(height(view,View.MeasureSpec.UNSPECIFIED,0)==80,"Pixel minimum wins conflicting maximum");view.setMaxLines(1);check(view.getMinHeight()==80&&view.getMaxHeight()==-1&&view.getMaxLines()==1,"Independent max line override");view.setMinLines(0);view.setMaxHeight(20);check(height(view,View.MeasureSpec.UNSPECIFIED,0)==20,"Pixel maximum includes compound padding");
  view.setMaxHeight(10);view.setMinLines(3);check(height(view,View.MeasureSpec.UNSPECIFIED,0)==10,"Satisfied minimum lines do not undo pixel cap");view.setText("one");check(height(view,View.MeasureSpec.UNSPECIFIED,0)==10+2*view.getLineHeight(),"Missing minimum lines add after pixel cap");view.setHeight(70);view.setSingleLine();check(view.getMinHeight()==-1&&view.getMaxHeight()==-1&&view.getMinLines()==1&&view.getMaxLines()==1,"Single line resets both pixel modes");
  int id=context.getResources().getIdentifier("checkbox_height","layout","org.shiraz.fixture");CheckBox xml=(CheckBox)LayoutInflater.from(context).inflate(id,null);int expected=Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,28,context.getResources().getDisplayMetrics()));check(xml.isChecked()&&xml.getMinHeight()==expected&&xml.getMaxHeight()==expected&&height(xml,View.MeasureSpec.UNSPECIFIED,0)==expected,"Compiled CheckBox height and checked state");
 }
 public static void check(Context context){heights(context);
  CheckBox standard=new CheckBox(context);check(standard.getAccessibilityClassName().equals("android.widget.CheckBox")&&!standard.isChecked(),"CheckBox constructor and accessibility identity");check(new CheckBox(context,null,0,0).getAccessibilityClassName().equals("android.widget.CheckBox"),"Four-argument CheckBox constructor");
  Toggle toggle=new Toggle(context);toggle.setClickable(true);toggle.setFocusable(true);toggle.setPadding(3,2,4,2);toggle.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);toggle.setText("Label");Indicator indicator=new Indicator();toggle.setButtonDrawable(indicator);check(toggle.getCompoundPaddingLeft()==11&&toggle.getCompoundPaddingRight()==4,"Indicator reserves text width");
  final StringBuilder order=new StringBuilder();toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){public void onCheckedChanged(CompoundButton button,boolean value){check(button.isChecked()==value,"Listener sees updated state");order.append(value?'T':'F');}});toggle.setOnClickListener(new View.OnClickListener(){public void onClick(View view){order.append('C');}});
  Bitmap image=draw(toggle);try{check(image.getPixel(2,25)==Color.RED,"Unchecked indicator pixels");}finally{image.recycle();}
  check(toggle.requestFocus(),"Keyboard focus");toggle.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));toggle.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));check(toggle.isChecked()&&order.toString().equals("TC"),"Keyboard toggles before click");
  image=draw(toggle);try{check(image.getPixel(2,25)==Color.GREEN,"Checked state reaches drawable");}finally{image.recycle();}
  toggle.setChecked(true);check(order.toString().equals("TC"),"Identical state does not notify");
  MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,20,20,0),up=MotionEvent.obtain(0,1,MotionEvent.ACTION_UP,20,20,0);try{toggle.dispatchTouchEvent(down);toggle.dispatchTouchEvent(up);}finally{down.recycle();up.recycle();}check(!toggle.isChecked()&&order.toString().equals("TCFC"),"Pointer toggle order");
  toggle.setEnabled(false);toggle.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));toggle.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));check(!toggle.isChecked()&&order.toString().equals("TCFC"),"Disabled input cannot toggle");toggle.setEnabled(true);
  final int[] calls={0};toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){public void onCheckedChanged(CompoundButton button,boolean value){calls[0]++;button.setChecked(!value);}});toggle.setChecked(true);check(calls[0]==1&&!toggle.isChecked(),"Reentrant state change does not recurse");
  toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){public void onCheckedChanged(CompoundButton button,boolean value){calls[0]++;if(value)throw new IllegalStateException("fixture listener");}});try{toggle.setChecked(true);throw new AssertionError("Callback failure swallowed");}catch(IllegalStateException expected){}toggle.setChecked(false);check(calls[0]==3,"Callback failure releases broadcasting guard");
  toggle.setOnCheckedChangeListener(null);toggle.setChecked(true);toggle.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);check(toggle.getCompoundPaddingLeft()==3&&toggle.getCompoundPaddingRight()==12,"RTL indicator reserves right width");image=draw(toggle);try{check(image.getPixel(97,25)==Color.GREEN&&image.getPixel(2,25)==0,"RTL indicator placement");}finally{image.recycle();}
  android.view.accessibility.AccessibilityNodeInfo info=toggle.createAccessibilityNodeInfo();check(info.isCheckable()&&info.isChecked(),"Checkable accessibility metadata");info.recycle();
  toggle.setId(201);SparseArray<Parcelable> state=new SparseArray<Parcelable>();toggle.saveHierarchyState(state);Toggle restored=new Toggle(context);restored.setId(201);restored.restoreHierarchyState(state);check(restored.isChecked(),"Checked state survives hierarchy recreation");
  toggle.setButtonDrawable((Drawable)null);check(indicator.getCallback()==null&&toggle.getCompoundPaddingRight()==4,"Removed indicator releases callback and space");
  int id=context.getResources().getIdentifier("compound_button","layout","org.shiraz.fixture");Toggle xml=(Toggle)LayoutInflater.from(context).inflate(id,null);check(xml.isChecked()&&xml.isClickable()&&xml.getButtonDrawable() instanceof ColorDrawable&&xml.getText().toString().equals("Enabled"),"Compiled checked/button/text attributes");
  check(!xml.isSaveEnabled(),"Compiled saveEnabled=false is respected");
  LinearLayout rows=new LinearLayout(context);xml.setId(73);rows.addView(xml);
  rows.setLayoutAnimation(null);check(rows.getLayoutAnimation()==null,"Disabling absent layout animation is supported");
  check(rows.getChildCount()==1&&rows.getChildAt(0)==xml,"Disabling layout animation preserves children");
  Toggle other=(Toggle)LayoutInflater.from(context).inflate(id,null);other.setId(73);other.setChecked(false);rows.addView(other);
  SparseArray<Parcelable> savedRows=new SparseArray<Parcelable>();rows.saveHierarchyState(savedRows);
  check(savedRows.get(73)==null,"Repeated row IDs excluded from hierarchy state");
  rows.restoreHierarchyState(savedRows);check(xml.isChecked()&&!other.isChecked(),"App-owned row states survive hierarchy restoration");
  LinearLayout enabledRows=new LinearLayout(context);Toggle first=new Toggle(context);first.setId(81);first.setChecked(true);Toggle second=new Toggle(context);second.setId(81);second.setChecked(false);enabledRows.addView(first);enabledRows.addView(second);
  SparseArray<Parcelable> duplicate=new SparseArray<Parcelable>();enabledRows.saveHierarchyState(duplicate);check(duplicate.indexOfKey(81)>=0&&duplicate.get(81)==null,"Enabled duplicate ids are not restored as one value");
  first.setChecked(true);second.setChecked(false);enabledRows.restoreHierarchyState(duplicate);check(first.isChecked()&&!second.isChecked(),"Duplicate ids keep the states set by the app");
  Toggle ordinary=new Toggle(context);ordinary.setId(74);ordinary.setChecked(true);ordinary.saveHierarchyState(savedRows);ordinary.setChecked(false);ordinary.restoreHierarchyState(savedRows);
  check(ordinary.isSaveEnabled()&&ordinary.isChecked(),"Default hierarchy saving remains enabled");

  System.out.println("COMPOUND_BUTTON_INPUT_STATE_RENDER_OK");
 }
}
