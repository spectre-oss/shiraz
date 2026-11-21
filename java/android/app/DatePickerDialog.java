// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.DatePicker;
import java.util.Calendar;

public class DatePickerDialog extends AlertDialog implements DialogInterface.OnClickListener,DatePicker.OnDateChangedListener {
 public interface OnDateSetListener {void onDateSet(DatePicker view,int year,int month,int dayOfMonth);}
 private final DatePicker picker;
 private OnDateSetListener listener;
 public DatePickerDialog(Context context){this(context,0);}
 public DatePickerDialog(Context context,int theme){this(context,theme,null,Calendar.getInstance());}
 private DatePickerDialog(Context context,int theme,OnDateSetListener listener,Calendar now){this(context,theme,listener,now.get(Calendar.YEAR),now.get(Calendar.MONTH),now.get(Calendar.DAY_OF_MONTH));}
 public DatePickerDialog(Context context,OnDateSetListener listener,int year,int month,int day){this(context,0,listener,year,month,day);}
 public DatePickerDialog(Context context,int theme,OnDateSetListener listener,int year,int month,int day){
  super(context,theme);this.listener=listener;picker=new DatePicker(getContext());picker.init(year,month,day,this);
  setButton(BUTTON_POSITIVE,getContext().getText(0x0104000a),this);
  setButton(BUTTON_NEGATIVE,getContext().getText(0x01040000),this);
 }
 @Override protected View createBody(){return picker;}
 public DatePicker getDatePicker(){return picker;}
 public void setOnDateSetListener(OnDateSetListener value){listener=value;}
 public void updateDate(int year,int month,int day){picker.updateDate(year,month,day);}
 public void onDateChanged(DatePicker view,int year,int month,int day){}
 public void onClick(DialogInterface dialog,int which){if(which==BUTTON_POSITIVE){picker.clearFocus();if(listener!=null)listener.onDateSet(picker,picker.getYear(),picker.getMonth(),picker.getDayOfMonth());}else if(which==BUTTON_NEGATIVE)cancel();}
 @Override public Bundle onSaveInstanceState(){Bundle state=super.onSaveInstanceState();state.putInt("year",picker.getYear());state.putInt("month",picker.getMonth());state.putInt("day",picker.getDayOfMonth());return state;}
 @Override public void onRestoreInstanceState(Bundle state){super.onRestoreInstanceState(state);picker.init(state.getInt("year"),state.getInt("month"),state.getInt("day"),this);}
}
