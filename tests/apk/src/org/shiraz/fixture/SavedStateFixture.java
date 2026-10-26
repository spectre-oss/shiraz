// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.app.Activity;
import android.content.Context;
import android.os.*;
import android.util.SparseArray;
import android.view.View;
import android.widget.FrameLayout;
public final class SavedStateFixture {
 private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 private static final class State extends View.BaseSavedState {
  final int value;State(Parcelable parent,int value){super(parent);this.value=value;}
 }
 private static class Stateful extends View {
  int value,saves,restores;
  Stateful(Context c,int id){super(c);setId(id);setFocusable(true);}
  protected Parcelable onSaveInstanceState(){saves++;return new State(super.onSaveInstanceState(),value);}
  protected void onRestoreInstanceState(Parcelable state){State own=(State)state;super.onRestoreInstanceState(own.getSuperState());value=own.value;restores++;}
 }
 private static final class Sample extends Activity {
  int value;final Stateful child;final FrameLayout root;
  Sample(Context c){attachBaseContext(c);root=new FrameLayout(this);child=new Stateful(this,101);root.addView(child);setContentView(root);}
  protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("application-value",value);}
  protected void onRestoreInstanceState(Bundle state){super.onRestoreInstanceState(state);value=state.getInt("application-value");}
  Bundle save(){Bundle b=new Bundle();onSaveInstanceState(b);return b;}
  void restore(Bundle b){onRestoreInstanceState(b);}
 }
 public static void check(Context context) {
  Sample before=new Sample(context);before.value=7;before.child.value=42;check(before.child.requestFocus(),"initial focus");
  Stateful disabled=new Stateful(context,102);disabled.setSaveEnabled(false);before.root.addView(disabled);
  Stateful excluded=new Stateful(context,103);excluded.setSaveFromParentEnabled(false);before.root.addView(excluded);
  Stateful noId=new Stateful(context,View.NO_ID);before.root.addView(noId);
  Bundle state=before.save();Sample after=new Sample(context);after.restore(state);
  check(after.value==7&&after.child.value==42&&after.child.restores==1&&after.child.hasFocus(),"activity/custom view/focus restoration");
  check(disabled.saves==0&&excluded.saves==0&&noId.saves==0,"save flags and no id");
  SparseArray<Parcelable> values=state.getBundle("android:viewHierarchyState").getSparseParcelableArray("android:views");
  check(values.size()==2&&values.get(101)!=null&&values.get(android.R.id.content)!=null,"typed sparse state container");
  View bad=new View(context){protected Parcelable onSaveInstanceState(){return View.BaseSavedState.EMPTY_STATE;}};bad.setId(104);
  try{bad.saveHierarchyState(new SparseArray<Parcelable>());throw new AssertionError("missing save super accepted");}catch(IllegalStateException expected){}
  View badRestore=new View(context){protected void onRestoreInstanceState(Parcelable ignored){}};badRestore.setId(101);
  try{badRestore.restoreHierarchyState(values);throw new AssertionError("missing restore super accepted");}catch(IllegalStateException expected){}
  View plain=new View(context);plain.setId(101);try{plain.restoreHierarchyState(values);throw new AssertionError("wrong state class accepted");}catch(IllegalArgumentException expected){}
  SparseArray<Parcelable> direct=new SparseArray<Parcelable>();excluded.saveHierarchyState(direct);check(direct.size()==1,"saveFromParent does not disable direct saves");
  check(new State(View.BaseSavedState.EMPTY_STATE,1).getSuperState()==null,"empty super state normalization");
  try{new State(null,1);throw new AssertionError("null super state accepted");}catch(IllegalArgumentException expected){}
  System.out.println("SHIRAZ_FIXTURE_SAVED_STATE_OK");
 }
}
