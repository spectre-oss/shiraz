// SPDX-License-Identifier: GPL-3.0-only
import android.app.Dialog;
import android.content.*;
import android.content.res.Resources;
import android.os.Looper;
import android.view.*;
import shiraz.runtime.DialogOverlay;
public final class DialogPointerBoundsSmoke {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  Looper.prepareMainLooper();Context context=new ContextWrapper(null){public Resources getResources(){return Resources.getSystem();}public Resources.Theme getTheme(){return getResources().newTheme();}};
  Dialog dialog=new Dialog(context);dialog.setContentView(new View(context));dialog.getWindow().setLayout(120,80);dialog.getWindow().setGravity(Gravity.CENTER);
  PointerIcon hand=PointerIcon.getSystemIcon(context,PointerIcon.TYPE_HAND);dialog.getWindow().getDecorView().setPointerIcon(hand);
  try{
   dialog.show();DialogOverlay.prepare(400,300);
   check(DialogOverlay.pointerIcon(140,110)==hand&&DialogOverlay.pointerIcon(259,189)==hand,"inside dialog edges");
   check(DialogOverlay.pointerIcon(139,150)==null&&DialogOverlay.pointerIcon(200,109)==null,"outside left/top");
   check(DialogOverlay.pointerIcon(260,150)==null&&DialogOverlay.pointerIcon(200,190)==null,"outside right/bottom within viewport");
   DialogOverlay.prepare(300,400);
   check(DialogOverlay.pointerIcon(90,160)==hand&&DialogOverlay.pointerIcon(209,239)==hand,"resized viewport origin");
   check(DialogOverlay.pointerIcon(210,200)==null&&DialogOverlay.pointerIcon(150,240)==null,"resized bounds");
   dialog.hide();check(DialogOverlay.pointerIcon(150,200)==null,"hidden dialog");
  }finally{dialog.dismiss();DialogOverlay.clear();Looper.shirazShutdownMain();}
  System.out.println("DIALOG_POINTER_BOUNDS_OK");
 }
}
