// SPDX-License-Identifier: GPL-3.0-only
import android.view.*;import android.graphics.*;import android.graphics.drawable.*;
public final class ChildDrawableStateSmoke {
 static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
 static boolean has(View v,int state){for(int x:v.getDrawableState())if(x==state)return true;return false;}
 static class Group extends android.widget.FrameLayout {Group(android.content.Context c){super(c);}void detach(View v){detachViewFromParent(v);}void attach(View v){attachViewToParent(v,-1,new LayoutParams(10,10));}}
 public static void main(String[] args){
  final android.content.res.Resources r=android.content.res.Resources.shirazOpen(args[0],96,160);final android.content.res.Resources.Theme theme=r.newTheme();
  android.content.Context c=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return r;}public android.content.res.Resources.Theme getTheme(){return theme;}};
  Group root=new Group(c),group=new Group(c);View child=new View(c);root.addView(group);group.addView(child);
  check(!group.addStatesFromChildren(),"default does not aggregate");child.setPressed(true);check(!has(group,16842919),"default isolation");
  group.setAddStatesFromChildren(true);root.setAddStatesFromChildren(true);check(has(root,16842919),"nested aggregate initial pressed");
  StateListDrawable bg=new StateListDrawable();bg.addState(new int[]{16842919},new ColorDrawable(Color.RED));bg.addState(new int[0],new ColorDrawable(Color.BLUE));root.setBackground(bg);
  check(((ColorDrawable)bg.getCurrent()).getColor()==Color.RED,"aggregated background");child.setPressed(false);check(!has(root,16842919)&&((ColorDrawable)bg.getCurrent()).getColor()==Color.BLUE,"child notifies ancestors");
  child.setSelected(true);check(has(root,16842913),"selected child");group.detach(child);check(!has(root,16842913),"detach refresh");group.attach(child);check(has(root,16842913),"reattach refresh");group.removeView(child);check(!has(root,16842913),"remove refresh");group.addView(child);check(has(root,16842913),"add refresh");
  group.setAddStatesFromChildren(false);check(!has(root,16842913),"disable aggregation");child.setDuplicateParentStateEnabled(true);group.setPressed(true);check(has(child,16842919),"parent state duplication still works");
  try{group.setAddStatesFromChildren(true);throw new AssertionError("drawable state cycle accepted");}catch(IllegalStateException expected){}check(!group.addStatesFromChildren(),"cycle rejected before mutation");
  child.setDuplicateParentStateEnabled(false);group.setAddStatesFromChildren(true);child.setFocusable(true);check(child.requestFocus()&&has(group,16842908),"focused text field state");child.clearFocus();check(!has(group,16842908),"focus removal");
  final java.util.ArrayList<Boolean> events=new java.util.ArrayList<Boolean>();View.OnFocusChangeListener listener=new View.OnFocusChangeListener(){public void onFocusChange(View v,boolean focused){check(v.isFocused()==focused,"Listener sees updated focus");events.add(focused);}};child.setOnFocusChangeListener(listener);check(child.getOnFocusChangeListener()==listener,"Listener identity");child.requestFocus();child.requestFocus();child.clearFocus();child.clearFocus();check(events.equals(java.util.Arrays.asList(true,false)),"Focus transitions delivered once");child.setOnFocusChangeListener(null);child.requestFocus();check(events.size()==2,"Removed focus listener");child.clearFocus();
  child.setHovered(true);check(child.isHovered()&&has(root,16843623),"Hover state propagated");child.setHovered(false);check(!has(root,16843623),"Hover cleared");
  root.removeAllViews();r.shirazClose();System.out.println("CHILD_DRAWABLE_STATE_OK");
 }
}
