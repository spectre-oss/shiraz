// SPDX-License-Identifier: GPL-3.0-only
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.DatePicker;
import java.util.Calendar;

public final class DatePickerSmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static long date(int year,int month,int day){Calendar c=Calendar.getInstance();c.clear();c.set(year,month,day);return c.getTimeInMillis();}
 public static void main(String[] args){
  Looper.prepareMainLooper();
  Context context=new ContextWrapper(null){private final Resources.Theme theme=Resources.getSystem().newTheme();public Resources getResources(){return Resources.getSystem();}public Resources.Theme getTheme(){return theme;}};
  final int[] calls={0};
  DatePickerDialog dialog=new DatePickerDialog(context,new DatePickerDialog.OnDateSetListener(){public void onDateSet(DatePicker picker,int year,int month,int day){check(year==2024&&month==1&&day==29,"selected date callback");calls[0]++;}},2024,1,28);
  DatePicker picker=dialog.getDatePicker();
  picker.setMinDate(date(2024,1,1));picker.setMaxDate(date(2024,2,1));
  ViewGroup fields=(ViewGroup)picker.getChildAt(0),day=(ViewGroup)fields.getChildAt(2);
  check(day.getChildAt(3).performClick(),"normal button event");
  check(picker.getDayOfMonth()==29,"leap day input");
  Bundle saved=dialog.onSaveInstanceState();dialog.updateDate(2025,0,1);
  check(picker.getMonth()==2&&picker.getDayOfMonth()==1,"maximum clamp");
  dialog.onRestoreInstanceState(saved);
  check(picker.getDayOfMonth()==29,"date restore");
  check(calls[0]==0,"edits do not confirm");dialog.onClick(dialog,DialogInterface.BUTTON_POSITIVE);
  check(calls[0]==1,"positive confirms");dialog.onClick(dialog,DialogInterface.BUTTON_NEGATIVE);
  check(calls[0]==1,"cancel does not confirm");
  picker.updateDate(1900,0,1);check(picker.getDayOfMonth()==1&&picker.getMonth()==1,"minimum clamp");
  try{picker.setMinDate(date(2025,0,1));throw new AssertionError("inverted bounds");}catch(IllegalArgumentException expected){}
  check(picker.getMinDate()==date(2024,1,1),"invalid bounds preserve state");
  picker.setEnabled(false);check(!day.getChildAt(3).isEnabled(),"disabled child control");
  System.out.println("DATE_PICKER_OK");
 }
}
