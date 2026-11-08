// SPDX-License-Identifier: GPL-3.0-only
import android.view.*;
import android.widget.*;
public final class ViewSwitcherSmoke {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  System.setProperty("shiraz.frameworkResources",new java.io.File("build/framework-res/framework-res.apk").getAbsolutePath());
  int spec=View.MeasureSpec.makeMeasureSpec(-32,View.MeasureSpec.EXACTLY);check(View.MeasureSpec.getMode(spec)==View.MeasureSpec.EXACTLY&&View.MeasureSpec.getSize(spec)==0x3fffffe0,"measure spec masks caller size bits");
  android.content.Context context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}};
  new TextView(context){private final Object ready=new Object();@Override public void setTextSize(int unit,float size){if(ready==null)throw new AssertionError("constructor dispatched to subclass");super.setTextSize(unit,size);}};
  EditText phone=new EditText(context);phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);phone.getText().replace(0,0,"+1 (202) abc#*");check("+1 (202) #*".contentEquals(phone.getText()),"phone filter on editable replacement");
  phone.setInputType(android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);phone.getText().replace(0,phone.length(),"text");check(phone.getInputType()==0x2000&&"text".contentEquals(phone.getText()),"flag-only type uses text listener");
  TextView padded=new TextView(context);padded.setText("line");padded.setPadding(2,3,4,5);padded.setGravity(Gravity.BOTTOM);padded.measure(View.MeasureSpec.makeMeasureSpec(120,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY));padded.layout(0,0,120,100);check(padded.getExtendedPaddingTop()==3&&padded.getExtendedPaddingBottom()==5&&padded.getTotalPaddingTop()>3&&padded.getTotalPaddingBottom()==5,"extended and total vertical padding");
  View.ROTATION.set(padded,90f);check(View.ROTATION.get(padded)==90f,"rotation property forwards to matrix state");
  ViewSwitcher switcher=new ViewSwitcher(context);switcher.setFactory(new ViewSwitcher.ViewFactory(){public View makeView(){return new View(context);}});
  View first=switcher.getCurrentView(),second=switcher.getNextView();check(first!=second&&first.getVisibility()==View.VISIBLE&&second.getVisibility()==View.GONE,"factory visibility");
  switcher.showNext();check(switcher.getCurrentView()==second&&first.getVisibility()==View.GONE&&second.getVisibility()==View.VISIBLE,"switch next");
  switcher.showNext();check(switcher.getCurrentView()==first,"wrap next");
  switcher.showPrevious();check(switcher.getCurrentView()==second,"wrap previous");
  boolean rejected=false;try{switcher.addView(new View(context));}catch(IllegalStateException expected){rejected=true;}check(rejected&&switcher.getChildCount()==2,"two child bound");
  switcher.reset();check(first.getVisibility()==View.GONE&&second.getVisibility()==View.GONE,"reset hides children");switcher.showNext();check(switcher.getCurrentView().getVisibility()==View.VISIBLE,"show after reset");
  switcher.removeView(first);check(switcher.getChildCount()==1&&switcher.getCurrentView()==second,"remove keeps selection valid");
  switcher.removeAllViews();check(switcher.getCurrentView()==null&&switcher.getDisplayedChild()==0,"empty reset");
  System.out.println("VIEW_SWITCHER_OK");
 }
}
