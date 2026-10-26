// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.content.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.text.NumberFormat;
/** Spinner and horizontal progress using real ProgressBar state and dialog lifecycle. */
public class ProgressDialog extends AlertDialog {
 public static final int STYLE_SPINNER=0,STYLE_HORIZONTAL=1;
 private int style,max=100,progress,secondary;private boolean indeterminate;private ProgressBar bar;private TextView numbers;
 private Drawable progressDrawable,indeterminateDrawable;private String numberFormat="%1d/%2d";private NumberFormat percentFormat=NumberFormat.getPercentInstance();
 public ProgressDialog(Context c){super(c);}public ProgressDialog(Context c,int theme){super(c,theme);}
 public static ProgressDialog show(Context c,CharSequence title,CharSequence message){return show(c,title,message,false);}
 public static ProgressDialog show(Context c,CharSequence title,CharSequence message,boolean indeterminate){return show(c,title,message,indeterminate,false);}
 public static ProgressDialog show(Context c,CharSequence title,CharSequence message,boolean indeterminate,boolean cancelable){return show(c,title,message,indeterminate,cancelable,null);}
 public static ProgressDialog show(Context c,CharSequence title,CharSequence message,boolean indeterminate,boolean cancelable,OnCancelListener listener){ProgressDialog d=new ProgressDialog(c);d.setTitle(title);d.setMessage(message);d.setIndeterminate(indeterminate);d.setCancelable(cancelable);d.setOnCancelListener(listener);d.show();return d;}
 public void setProgressStyle(int value){if(value<0||value>1)throw new IllegalArgumentException("Progress style");if(bar!=null&&style!=value)throw new IllegalStateException("Progress style already created");style=value;}
 @Override protected View createBody(){
  LinearLayout body=new LinearLayout(getContext());body.setOrientation(LinearLayout.VERTICAL);
  bar=new ProgressBar(getContext(),null,style==STYLE_HORIZONTAL?0x01010078:0x01010077);bar.setId(0x0102000d);bar.setMax(max);bar.setIndeterminate(style==STYLE_SPINNER||indeterminate);bar.setProgress(progress);bar.setSecondaryProgress(secondary);
  if(progressDrawable!=null)bar.setProgressDrawable(progressDrawable);if(indeterminateDrawable!=null)bar.setIndeterminateDrawable(indeterminateDrawable);
  LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(style==STYLE_HORIZONTAL?-1:dp(48),style==STYLE_HORIZONTAL?dp(12):dp(48));layout.gravity=Gravity.CENTER_HORIZONTAL;body.addView(bar,layout);
  numbers=new TextView(getContext());numbers.setGravity(Gravity.END);body.addView(numbers,new LinearLayout.LayoutParams(-1,-2));updateNumbers();return body;
 }
 private void updateNumbers(){if(numbers==null)return;numbers.setVisibility(style==STYLE_HORIZONTAL&&!isIndeterminate()?View.VISIBLE:View.GONE);String n=numberFormat==null?"":String.format(java.util.Locale.getDefault(),numberFormat,getProgress(),getMax());String pct=percentFormat==null?"":percentFormat.format(getMax()==0?0:(double)getProgress()/getMax());numbers.setText(n+(n.length()>0&&pct.length()>0?"  ":"")+pct);}
 public void setProgress(int value){progress=Math.max(0,Math.min(value,max));if(bar!=null)bar.setProgress(progress);updateNumbers();}public int getProgress(){return bar==null?progress:bar.getProgress();}
 public void setSecondaryProgress(int value){secondary=Math.max(0,Math.min(value,max));if(bar!=null)bar.setSecondaryProgress(secondary);}public int getSecondaryProgress(){return bar==null?secondary:bar.getSecondaryProgress();}
 public void setMax(int value){max=Math.max(0,value);progress=Math.min(progress,max);secondary=Math.min(secondary,max);if(bar!=null)bar.setMax(max);updateNumbers();}public int getMax(){return bar==null?max:bar.getMax();}
 public void incrementProgressBy(int amount){setProgress((int)Math.max(0,Math.min((long)max,(long)getProgress()+amount)));}public void incrementSecondaryProgressBy(int amount){setSecondaryProgress((int)Math.max(0,Math.min((long)max,(long)getSecondaryProgress()+amount)));}
 public void setIndeterminate(boolean value){indeterminate=value;if(bar!=null)bar.setIndeterminate(value);updateNumbers();}public boolean isIndeterminate(){return bar==null?indeterminate:bar.isIndeterminate();}
 public void setProgressDrawable(Drawable value){progressDrawable=value;if(bar!=null)bar.setProgressDrawable(value);}public void setIndeterminateDrawable(Drawable value){indeterminateDrawable=value;if(bar!=null)bar.setIndeterminateDrawable(value);}
 public void setProgressNumberFormat(String format){numberFormat=format;updateNumbers();}public void setProgressPercentFormat(NumberFormat format){percentFormat=format;updateNumbers();}
}
