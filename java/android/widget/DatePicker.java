// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import java.util.Calendar;

/** Date-only selection through ordinary focusable buttons in the host view tree. */
public class DatePicker extends FrameLayout {
 public interface OnDateChangedListener {void onDateChanged(DatePicker view,int year,int monthOfYear,int dayOfMonth);}
 private final Calendar date=Calendar.getInstance(),minimum=Calendar.getInstance(),maximum=Calendar.getInstance();
 private final TextView[] values=new TextView[3];
 private final Button[] controls=new Button[6];
 private final LinearLayout fields;
 private OnDateChangedListener listener;
 public DatePicker(Context context){this(context,null);}
 public DatePicker(Context context,AttributeSet attrs){this(context,attrs,0);}
 public DatePicker(Context context,AttributeSet attrs,int style){this(context,attrs,style,0);}
 public DatePicker(Context context,AttributeSet attrs,int style,int resource){
  super(context,attrs,style,resource);
  minimum.clear();minimum.set(1900,0,1);maximum.clear();maximum.set(2100,11,31);
  normalize(date);fields=new LinearLayout(context);fields.setOrientation(LinearLayout.HORIZONTAL);
  addView(fields,new FrameLayout.LayoutParams(-1,-2));
  final int[] calendarFields={Calendar.YEAR,Calendar.MONTH,Calendar.DAY_OF_MONTH};
  String[] names={"Year","Month","Day"};
  for(int i=0;i<3;i++){
   LinearLayout column=new LinearLayout(context);column.setOrientation(LinearLayout.VERTICAL);
   fields.addView(column,new LinearLayout.LayoutParams(0,-2,1));
   TextView label=new TextView(context);label.setText(names[i]);column.addView(label);
   values[i]=new TextView(context);column.addView(values[i]);
   for(int j=0;j<2;j++){
    final int field=calendarFields[i],delta=j==0?-1:1;
    Button button=new Button(context);controls[i*2+j]=button;button.setText(j==0?"−":"+");
    button.setContentDescription((j==0?"Previous ":"Next ")+names[i]);column.addView(button);
    button.setOnClickListener(new View.OnClickListener(){public void onClick(View view){
     Calendar next=(Calendar)date.clone();next.add(field,delta);
     updateDate(next.get(Calendar.YEAR),next.get(Calendar.MONTH),next.get(Calendar.DAY_OF_MONTH));
    }});
   }
  }
  refresh();
 }
 private static void normalize(Calendar value){value.set(Calendar.HOUR_OF_DAY,0);value.set(Calendar.MINUTE,0);value.set(Calendar.SECOND,0);value.set(Calendar.MILLISECOND,0);}
 private void refresh(){
  values[0].setText(Integer.toString(getYear()));values[1].setText(Integer.toString(getMonth()+1));values[2].setText(Integer.toString(getDayOfMonth()));
  for(int i=0;i<controls.length;i++)controls[i].setEnabled(isEnabled()&&(i%2==0?date.after(minimum):date.before(maximum)));
 }
 public void init(int year,int monthOfYear,int dayOfMonth,OnDateChangedListener value){listener=null;updateDate(year,monthOfYear,dayOfMonth);listener=value;}
 public void setOnDateChangedListener(OnDateChangedListener value){listener=value;}
 public void updateDate(int year,int month,int day){
  long old=date.getTimeInMillis();date.clear();date.set(year,month,day);
  long selected=date.getTimeInMillis();date.setTimeInMillis(Math.max(minimum.getTimeInMillis(),Math.min(maximum.getTimeInMillis(),selected)));
  refresh();if(old!=date.getTimeInMillis()&&listener!=null)listener.onDateChanged(this,getYear(),getMonth(),getDayOfMonth());
 }
 public int getYear(){return date.get(Calendar.YEAR);}public int getMonth(){return date.get(Calendar.MONTH);}public int getDayOfMonth(){return date.get(Calendar.DAY_OF_MONTH);}
 public long getMinDate(){return minimum.getTimeInMillis();}public long getMaxDate(){return maximum.getTimeInMillis();}
 private Calendar boundary(long millis){Calendar result=Calendar.getInstance();result.setTimeInMillis(millis);normalize(result);return result;}
 public void setMinDate(long millis){Calendar next=boundary(millis);if(next.after(maximum))throw new IllegalArgumentException("Minimum date after maximum");minimum.setTimeInMillis(next.getTimeInMillis());updateDate(getYear(),getMonth(),getDayOfMonth());}
 public void setMaxDate(long millis){Calendar next=boundary(millis);if(next.before(minimum))throw new IllegalArgumentException("Maximum date before minimum");maximum.setTimeInMillis(next.getTimeInMillis());updateDate(getYear(),getMonth(),getDayOfMonth());}
 @Override public void setEnabled(boolean value){super.setEnabled(value);if(controls!=null&&controls[0]!=null)refresh();}
 public boolean getCalendarViewShown(){return false;}
 public void setCalendarViewShown(boolean shown){if(shown)throw new UnsupportedOperationException("Calendar grid presentation is unavailable");}
 public boolean getSpinnersShown(){return fields.getVisibility()==View.VISIBLE;}
 public void setSpinnersShown(boolean shown){fields.setVisibility(shown?View.VISIBLE:View.GONE);}
}
