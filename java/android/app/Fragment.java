// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.content.*;
import android.os.Bundle;
import android.view.*;
/** Platform fragments for nonvisual lifecycle owners. View containers remain unsupported. */
public class Fragment implements android.content.ComponentCallbacks2 {
 FragmentManager manager;Activity activity;String tag;Bundle arguments,saved;int phase;boolean removing;
 public Fragment(){}
 public final Activity getActivity(){return activity;}
 public Context getContext(){return activity;}
 public final FragmentManager getFragmentManager(){return manager;}
 public final String getTag(){return tag;}
 public final boolean isAdded(){return activity!=null&&!removing;}
 public final boolean isRemoving(){return removing;}
 public final boolean isResumed(){return phase==4;}
 public final boolean isDetached(){return false;}
 public final int getId(){return 0;}
 public View getView(){return null;}
 public void setArguments(Bundle args){if(manager!=null&&manager.isStateSaved())throw new IllegalStateException("Fragment state is saved");arguments=args;}
 public final Bundle getArguments(){return arguments;}
 public final android.content.res.Resources getResources(){if(activity==null)throw new IllegalStateException("Fragment is detached");return activity.getResources();}
 public final String getString(int id){return getResources().getString(id);}
 public void setRetainInstance(boolean retain){if(retain)throw new UnsupportedOperationException("Retained platform fragments are not implemented");}
 public final boolean getRetainInstance(){return false;}
 public void onAttach(Context context){if(context instanceof Activity)onAttach((Activity)context);}
 public void onAttach(Activity activity){}
 public void onCreate(Bundle state){}
 public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle state){return null;}
 public void onActivityCreated(Bundle state){}
 public void onStart(){}
 public void onResume(){}
 public void onPause(){}
 public void onStop(){}
 public void onDestroyView(){}
 public void onDestroy(){}
 public void onDetach(){}
 public void onSaveInstanceState(Bundle out){}
 public void onConfigurationChanged(android.content.res.Configuration config){}
 public void onLowMemory(){}
 public void onTrimMemory(int level){}
}
