// SPDX-License-Identifier: GPL-3.0-only
import android.widget.TextView;
import android.view.View;
import android.util.TypedValue;
public final class AutoSizeTextSmoke {
 public static void main(String[] args){
  android.content.Context context=new android.content.ContextWrapper(null){public String getPackageName(){return "org.shiraz.fixture";}public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}};
  TextView view=new TextView(context);view.setSingleLine(true);view.setText("A long label to fit");
  if(view.getAutoSizeTextType()!=0||view.getAutoSizeStepGranularity()!=-1)throw new AssertionError("defaults");
  view.setAutoSizeTextTypeUniformWithPresetSizes(new int[]{32,12,20,12},TypedValue.COMPLEX_UNIT_PX);
  view.measure(View.MeasureSpec.makeMeasureSpec(110,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(50,View.MeasureSpec.EXACTLY));float small=view.getTextSize();
  view.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY));
  if(small>=view.getTextSize()||view.getTextSize()!=32||view.getAutoSizeTextAvailableSizes().length!=3)throw new AssertionError("resize fit");
  view.setAutoSizeTextTypeWithDefaults(0);if(view.getAutoSizeMinTextSize()!=-1)throw new AssertionError("disable");
  System.out.println("AUTO_SIZE_TEXT_OK");
 }
}
