// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.content.*;
import android.content.res.Resources;
import android.os.Looper;
import shiraz.runtime.DialogOverlay;
public final class AlertDialogBuilderSmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static android.widget.TextView findText(android.view.View view,String text){
  if(view instanceof android.widget.TextView&&text.contentEquals(((android.widget.TextView)view).getText()))return (android.widget.TextView)view;
  if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.widget.TextView found=findText(group.getChildAt(i),text);if(found!=null)return found;}}
  return null;
 }
 public static void main(String[] args){
  Looper.prepareMainLooper();
  try {
   Context context=new ContextWrapper(null){private final Resources.Theme theme=Resources.getSystem().newTheme();public Resources getResources(){return Resources.getSystem();}public Resources.Theme getTheme(){return theme;}};
   Resources.shirazSetSystemDisplay(720,1280);
   final int[] clicks={0},cancels={0},dismisses={0};
   AlertDialog.Builder builder=new AlertDialog.Builder(context).setTitle("Graphics unavailable").setMessage("This renderer is unsupported.").setCancelable(false)
    .setPositiveButton("OK",new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog,int which){check(which==-1&&((Dialog)dialog).isShowing(),"callback identity and ordering");clicks[0]++;}})
    .setOnCancelListener(new DialogInterface.OnCancelListener(){public void onCancel(DialogInterface dialog){cancels[0]++;}})
    .setOnDismissListener(new DialogInterface.OnDismissListener(){public void onDismiss(DialogInterface dialog){dismisses[0]++;}});
   AlertDialog first=builder.create();check(!first.isShowing()&&builder.getContext()==context,"create does not show");
   builder.setPositiveButton("Changed",null).setCancelable(true);
   first.show();check(DialogOverlay.prepare(720,1280),"modal layout");
   check("OK".contentEquals(first.getButton(-1).getText()),"created dialog retains configuration");
   first.onBackPressed();check(first.isShowing(),"noncancelable builder flag");
   first.getButton(-1).performClick();Looper.getMainLooper().shirazDispatchDue(100);
   check(clicks[0]==1&&cancels[0]==0&&dismisses[0]==1&&!first.isShowing(),"button dismisses without cancel");
   AlertDialog second=builder.show();check(second!=first&&"Changed".contentEquals(second.getButton(-1).getText()),"builder creates independent dialogs");
   second.onBackPressed();Looper.getMainLooper().shirazDispatchDue(100);
   check(cancels[0]==1&&dismisses[0]==2&&!DialogOverlay.hasVisible(),"cancel and cleanup");
   AlertDialog dark=new AlertDialog.Builder(context,0x01030009).setTitle("Dark theme").setMessage("Readable error").setPositiveButton("OK",null).show();
   check(DialogOverlay.prepare(720,1280),"dark dialog layout");
   android.widget.TextView error=findText(dark.getWindow().getDecorView(),"Readable error");
   check(error!=null&&(error.getCurrentTextColor()&0xffffff)!=0,"dark theme message contrasts with black background");
   dark.dismiss();
   System.out.println("ALERT_BUILDER_MODAL_CALLBACKS_OK");
  } finally {DialogOverlay.clear();Looper.shirazShutdownMain();}
 }
}
