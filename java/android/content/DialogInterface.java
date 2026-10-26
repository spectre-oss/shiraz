// SPDX-License-Identifier: GPL-3.0-only
package android.content;
public interface DialogInterface {
 int BUTTON_POSITIVE=-1,BUTTON_NEGATIVE=-2,BUTTON_NEUTRAL=-3,BUTTON1=-1,BUTTON2=-2,BUTTON3=-3;
 void cancel();void dismiss();
 interface OnCancelListener {void onCancel(DialogInterface dialog);}
 interface OnDismissListener {void onDismiss(DialogInterface dialog);}
 interface OnShowListener {void onShow(DialogInterface dialog);}
 interface OnClickListener {void onClick(DialogInterface dialog,int which);}
 interface OnMultiChoiceClickListener {void onClick(DialogInterface dialog,int which,boolean checked);}
 interface OnKeyListener {boolean onKey(DialogInterface dialog,int keyCode,android.view.KeyEvent event);}
}
