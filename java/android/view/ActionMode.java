// SPDX-License-Identifier: GPL-3.0-only
package android.view;

/** Contextual action-mode contract. Presentation is supplied by concrete modes. */
public abstract class ActionMode {
 public static final int TYPE_PRIMARY=0,TYPE_FLOATING=1,DEFAULT_HIDE_DURATION=-1;
 private Object tag;private boolean titleOptional;private int type=TYPE_PRIMARY;
 public void setTag(Object value){tag=value;}public Object getTag(){return tag;}
 public abstract void setTitle(CharSequence title);
 public abstract void setTitle(int resource);
 public abstract void setSubtitle(CharSequence subtitle);
 public abstract void setSubtitle(int resource);
 public void setTitleOptionalHint(boolean value){titleOptional=value;}
 public boolean getTitleOptionalHint(){return titleOptional;}
 public boolean isTitleOptional(){return false;}
 public abstract void setCustomView(View view);
 public void setType(int value){type=value;}public int getType(){return type;}
 public abstract void invalidate();
 public void invalidateContentRect(){}
 public void hide(long duration){}
 public abstract void finish();
 public abstract Menu getMenu();
 public abstract CharSequence getTitle();
 public abstract CharSequence getSubtitle();
 public abstract View getCustomView();
 public abstract MenuInflater getMenuInflater();
 public void onWindowFocusChanged(boolean focused){}
 public boolean isUiFocusable(){return true;}
 public interface Callback {
  boolean onCreateActionMode(ActionMode mode,Menu menu);
  boolean onPrepareActionMode(ActionMode mode,Menu menu);
  boolean onActionItemClicked(ActionMode mode,MenuItem item);
  void onDestroyActionMode(ActionMode mode);
 }
 public abstract static class Callback2 implements Callback {
  public void onGetContentRect(ActionMode mode,View view,android.graphics.Rect outRect){outRect.set(0,0,view==null?0:view.getWidth(),view==null?0:view.getHeight());}
 }
}
