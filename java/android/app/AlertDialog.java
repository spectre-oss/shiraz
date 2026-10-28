// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
/** Platform title/message/button dialogs backed by the session's modal view tree. */
public class AlertDialog extends Dialog {
 public static class Builder {
  private final Context context;
  private CharSequence title,message;private boolean cancelable=true;
  private OnCancelListener cancelListener;private OnDismissListener dismissListener;private OnKeyListener keyListener;
  private final CharSequence[] labels=new CharSequence[3];
  private final OnClickListener[] listeners=new OnClickListener[3];
  public Builder(Context context){this(context,0);}
  public Builder(Context context,int theme){if(context==null)throw new NullPointerException("context");this.context=theme==0?context:new ContextThemeWrapper(context,theme);}
  public Context getContext(){return context;}
  public Builder setTitle(CharSequence value){title=value;return this;}
  public Builder setTitle(int resource){return setTitle(context.getText(resource));}
  public Builder setMessage(CharSequence value){message=value;return this;}
  public Builder setMessage(int resource){return setMessage(context.getText(resource));}
  private Builder button(int which,CharSequence text,OnClickListener listener){int i=index(which);labels[i]=text;listeners[i]=listener;return this;}
  public Builder setPositiveButton(CharSequence text,OnClickListener listener){return button(BUTTON_POSITIVE,text,listener);}
  public Builder setPositiveButton(int resource,OnClickListener listener){return setPositiveButton(context.getText(resource),listener);}
  public Builder setNegativeButton(CharSequence text,OnClickListener listener){return button(BUTTON_NEGATIVE,text,listener);}
  public Builder setNegativeButton(int resource,OnClickListener listener){return setNegativeButton(context.getText(resource),listener);}
  public Builder setNeutralButton(CharSequence text,OnClickListener listener){return button(BUTTON_NEUTRAL,text,listener);}
  public Builder setNeutralButton(int resource,OnClickListener listener){return setNeutralButton(context.getText(resource),listener);}
  public Builder setCancelable(boolean value){cancelable=value;return this;}
  public Builder setOnCancelListener(OnCancelListener value){cancelListener=value;return this;}
  public Builder setOnDismissListener(OnDismissListener value){dismissListener=value;return this;}
  public Builder setOnKeyListener(OnKeyListener value){keyListener=value;return this;}
  public AlertDialog create(){
   AlertDialog dialog=new AlertDialog(context);dialog.setTitle(title);dialog.setMessage(message);
   dialog.setCancelable(cancelable);dialog.setCanceledOnTouchOutside(cancelable);
   dialog.setOnCancelListener(cancelListener);dialog.setOnDismissListener(dismissListener);dialog.setOnKeyListener(keyListener);
   for(int i=0;i<3;++i)dialog.setButton(-i-1,labels[i],listeners[i]);
   return dialog;
  }
  public AlertDialog show(){AlertDialog dialog=create();dialog.show();return dialog;}
 }
 private CharSequence title,message;private TextView titleView,messageView;
 private final CharSequence[] labels=new CharSequence[3];private final DialogInterface.OnClickListener[] listeners=new DialogInterface.OnClickListener[3];private final Message[] messages=new Message[3];private final Button[] buttons=new Button[3];
 public AlertDialog(Context context){super(context);}public AlertDialog(Context context,int theme){super(context,theme);}protected AlertDialog(Context context,boolean cancelable,OnCancelListener listener){super(context,cancelable,listener);}
 protected final int dp(int value){return Math.max(1,Math.round(value*getContext().getResources().getDisplayMetrics().density));}
 private static int index(int which){if(which>BUTTON_POSITIVE||which<BUTTON_NEUTRAL)throw new IllegalArgumentException("Dialog button");return -which-1;}
 @Override public void setTitle(CharSequence value){super.setTitle(value);title=value;if(titleView!=null){titleView.setText(value);titleView.setVisibility(value==null||value.length()==0?View.GONE:View.VISIBLE);}}
 public void setMessage(CharSequence value){message=value;if(messageView!=null){messageView.setText(value);messageView.setVisibility(value==null||value.length()==0?View.GONE:View.VISIBLE);}}
 public void setButton(int which,CharSequence text,OnClickListener listener){int i=index(which);labels[i]=text;listeners[i]=listener;messages[i]=null;updateButton(i);}
 public void setButton(int which,CharSequence text,Message message){int i=index(which);labels[i]=text;messages[i]=message;listeners[i]=null;updateButton(i);}
 public Button getButton(int which){return buttons[index(which)];}
 private void updateButton(int i){if(buttons[i]!=null){buttons[i].setText(labels[i]);buttons[i].setVisibility(labels[i]==null?View.GONE:View.VISIBLE);}}
 protected View createBody(){return null;}
 private static android.content.res.ColorStateList textColor(Context c,int attribute,int fallback){
  android.util.TypedValue value=new android.util.TypedValue();
  if(c.getTheme().resolveAttribute(attribute,value,true)){
   if(value.type>=28&&value.type<=31)return android.content.res.ColorStateList.valueOf(value.data);
   if(value.resourceId!=0)try{return c.getResources().getColorStateList(value.resourceId,c.getTheme());}catch(android.content.res.Resources.NotFoundException absent){}
  }
  return android.content.res.ColorStateList.valueOf(fallback);
 }
 @Override protected void onCreate(Bundle state){
  super.onCreate(state);Context c=getContext();LinearLayout root=new LinearLayout(c);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(16),dp(20),dp(12));
  android.util.TypedValue color=new android.util.TypedValue();int background=0xfffafafa;if(c.getTheme().resolveAttribute(0x01010031,color,true)&&color.type>=28&&color.type<=31)background=color.data;root.setBackgroundColor(background);
  int brightness=(((background>>16)&255)*299+((background>>8)&255)*587+(background&255)*114)/1000;
  int foreground=brightness<128?0xffffffff:0xff000000;
  titleView=new TextView(c);titleView.setTextSize(20);root.addView(titleView,new LinearLayout.LayoutParams(-1,-2));setTitle(title);
  messageView=new TextView(c);messageView.setPadding(0,dp(8),0,dp(8));root.addView(messageView,new LinearLayout.LayoutParams(-1,-2));setMessage(message);
  titleView.setTextColor(textColor(c,0x01010036,foreground));messageView.setTextColor(textColor(c,0x01010038,foreground));
  View body=createBody();if(body!=null)root.addView(body,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.END);root.addView(row,new LinearLayout.LayoutParams(-1,-2));
  for(final int i:new int[]{2,1,0}){Button b=new Button(c);buttons[i]=b;row.addView(b,new LinearLayout.LayoutParams(-2,-2));updateButton(i);b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){try{if(listeners[i]!=null)listeners[i].onClick(AlertDialog.this,-i-1);else if(messages[i]!=null)Message.obtain(messages[i]).sendToTarget();}finally{dismiss();}}});}
  int width=Math.min(dp(360),Math.max(dp(120),c.getResources().getDisplayMetrics().widthPixels-dp(32)));setContentView(root,new ViewGroup.LayoutParams(width,-2));
 }
}
