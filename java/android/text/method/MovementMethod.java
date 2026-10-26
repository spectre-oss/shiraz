// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;
import android.text.Spannable;import android.widget.TextView;import android.view.*;
public interface MovementMethod {
 void initialize(TextView widget,Spannable text);void onTakeFocus(TextView widget,Spannable text,int direction);
 boolean onKeyDown(TextView widget,Spannable text,int code,KeyEvent event);boolean onKeyUp(TextView widget,Spannable text,int code,KeyEvent event);boolean onKeyOther(TextView widget,Spannable text,KeyEvent event);
 boolean onTouchEvent(TextView widget,Spannable text,MotionEvent event);boolean onTrackballEvent(TextView widget,Spannable text,MotionEvent event);boolean onGenericMotionEvent(TextView widget,Spannable text,MotionEvent event);boolean canSelectArbitrarily();
}
