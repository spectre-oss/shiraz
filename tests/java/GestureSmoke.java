// SPDX-License-Identifier: GPL-3.0-only
public final class GestureSmoke {
 public static void main(String[] args){android.os.Looper.prepareMainLooper();try{android.content.Context context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}};org.shiraz.fixture.GestureFixture.immediate(context);org.shiraz.fixture.GestureFixture.delayed(context,new org.shiraz.fixture.GestureFixture.Pump(){public void run(){android.os.Looper.myLooper().shirazDispatchDue(100);}});}finally{android.os.Looper.shirazShutdownMain();}}
}
