// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
/** Platform title/message/button dialogs backed by the session's modal view tree. */
public class AlertDialog extends Dialog {
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
 @Override protected void onCreate(Bundle state){
  super.onCreate(state);Context c=getContext();LinearLayout root=new LinearLayout(c);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(16),dp(20),dp(12));
  android.util.TypedValue color=new android.util.TypedValue();int background=0xfffafafa;if(c.getTheme().resolveAttribute(0x01010031,color,true)&&color.type>=28&&color.type<=31)background=color.data;root.setBackgroundColor(background);
  titleView=new TextView(c);titleView.setTextSize(20);root.addView(titleView,new LinearLayout.LayoutParams(-1,-2));setTitle(title);
  messageView=new TextView(c);messageView.setPadding(0,dp(8),0,dp(8));root.addView(messageView,new LinearLayout.LayoutParams(-1,-2));setMessage(message);
  View body=createBody();if(body!=null)root.addView(body,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.END);root.addView(row,new LinearLayout.LayoutParams(-1,-2));
  for(final int i:new int[]{2,1,0}){Button b=new Button(c);buttons[i]=b;row.addView(b,new LinearLayout.LayoutParams(-2,-2));updateButton(i);b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){try{if(listeners[i]!=null)listeners[i].onClick(AlertDialog.this,-i-1);else if(messages[i]!=null)Message.obtain(messages[i]).sendToTarget();}finally{dismiss();}}});}
  int width=Math.min(dp(360),Math.max(dp(120),c.getResources().getDisplayMetrics().widthPixels-dp(32)));setContentView(root,new ViewGroup.LayoutParams(width,-2));
 }
}
