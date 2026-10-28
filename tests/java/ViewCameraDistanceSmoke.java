// SPDX-License-Identifier: GPL-3.0-only
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.view.View;
public class ViewCameraDistanceSmoke {
 public static void main(String[] args){
  android.os.Looper.prepare();
  final Resources resources=Resources.shirazOpen(args[0],360,640,160);
  try{
   View view=new View(new ContextWrapper(null){@Override public Resources getResources(){return resources;}});
   if(view.getCameraDistance()!=1280)throw new AssertionError("default density");
   view.setCameraDistance(-2400);if(view.getCameraDistance()!=2400)throw new AssertionError("negative depth");
   android.content.res.Configuration config=resources.getConfiguration();config.densityDpi=240;resources.updateConfiguration(config,null);if(view.getCameraDistance()!=3600)throw new AssertionError("density change");
   view.setCameraDistance(0);if(view.getCameraDistance()!=0||!view.getMatrix().isIdentity())throw new AssertionError("planar identity");
   try{view.setCameraDistance(Float.NaN);throw new AssertionError("nonfinite depth");}catch(IllegalArgumentException expected){}
   try{view.setRotationX(20);throw new AssertionError("unsupported perspective");}catch(UnsupportedOperationException expected){}
   android.widget.EditText editor=new android.widget.EditText(view.getContext());
   android.view.ActionMode.Callback callback=new android.view.ActionMode.Callback(){
    public boolean onCreateActionMode(android.view.ActionMode mode,android.view.Menu menu){throw new AssertionError("registration created action mode");}
    public boolean onPrepareActionMode(android.view.ActionMode mode,android.view.Menu menu){return false;}
    public boolean onActionItemClicked(android.view.ActionMode mode,android.view.MenuItem item){return false;}
    public void onDestroyActionMode(android.view.ActionMode mode){}
   };
   editor.setCustomSelectionActionModeCallback(callback);editor.setCustomInsertionActionModeCallback(callback);
   if(editor.getCustomSelectionActionModeCallback()!=callback||editor.getCustomInsertionActionModeCallback()!=callback)throw new AssertionError("editor callback identity");
   editor.setCustomSelectionActionModeCallback(null);editor.setCustomInsertionActionModeCallback(null);
   if(editor.getCustomSelectionActionModeCallback()!=null||editor.getCustomInsertionActionModeCallback()!=null)throw new AssertionError("editor callback clearing");
   System.out.println("VIEW_CAMERA_DISTANCE_OK");
  }finally{resources.shirazClose();}
 }
}
