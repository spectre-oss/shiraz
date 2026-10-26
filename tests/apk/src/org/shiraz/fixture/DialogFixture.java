// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.app.Dialog;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
/** SDK-compiled dialog content/lifecycle and live SDL pointer fixture. */
public final class DialogFixture {
 public static class Lifecycle extends Dialog {
  public int creates,starts,stops,cancels;public View view;public final StringBuilder callbacks=new StringBuilder();
  public Lifecycle(Context c){super(c);setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface d){callbacks.append('S');}});setOnDismissListener(new DialogInterface.OnDismissListener(){public void onDismiss(DialogInterface d){callbacks.append('D');}});setOnCancelListener(new DialogInterface.OnCancelListener(){public void onCancel(DialogInterface d){callbacks.append('C');}});}
  protected void onCreate(Bundle state){super.onCreate(state);creates++;view=new View(getContext()){public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_CANCEL)cancels++;return super.onTouchEvent(event);}};view.setClickable(true);view.setBackgroundColor(Color.RED);setContentView(view,new ViewGroup.LayoutParams(32,24));}
  protected void onStart(){super.onStart();starts++;}protected void onStop(){super.onStop();stops++;}
 }
 public static final class Live extends Dialog {
  public int draws,clicks;private boolean injected;public View view;
  public Live(Context c){super(c);}
  protected void onCreate(Bundle state){super.onCreate(state);view=new View(getContext()){
   protected void onDraw(Canvas canvas){canvas.drawColor(Color.RED);draws++;if(!injected){injected=true;System.loadLibrary("shiraz-test-input");inject();}}
   public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&(event.getX()!=16||event.getY()!=12))throw new AssertionError("Modal pointer coordinates");return super.onTouchEvent(event);}
  };view.setOnClickListener(new View.OnClickListener(){public void onClick(View v){clicks++;dismiss();}});setContentView(view,new ViewGroup.LayoutParams(32,24));}
 }
 private static native void inject();
}
