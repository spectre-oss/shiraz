// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.app.*;
import android.content.Context;
import android.os.Bundle;
/** Real platform fragment callbacks, queued transactions, and recreation state. */
public final class FragmentFixture {
 public static class Sample extends Fragment {
  String events="";int value=96;
  public void onAttach(Context context){super.onAttach(context);events+="a";if(context!=getActivity())throw new AssertionError("fragment context");}
  public void onCreate(Bundle state){super.onCreate(state);events+="c";if(getArguments().getInt("token")!=123)throw new AssertionError("fragment arguments");if(state!=null){value=state.getInt("value");if(value<97)throw new AssertionError("fragment restored state");System.out.println("SHIRAZ_FRAGMENT_RESTORED_OK");}}
  public void onActivityCreated(Bundle state){super.onActivityCreated(state);events+="v";}
  public void onStart(){super.onStart();events+="s";}
  public void onResume(){super.onResume();events+="r";value++;if(!isResumed()||!isAdded()||getFragmentManager().findFragmentByTag(getTag())!=this)throw new AssertionError("fragment live state");}
  public void onPause(){super.onPause();events+="p";}
  public void onStop(){super.onStop();events+="t";}
  public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);System.out.println("SHIRAZ_FRAGMENT_CONFIGURATION_OK");}
  public void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("value",value);try{getFragmentManager().beginTransaction().add(new Sample(),"after-save").commit();throw new AssertionError("commit after save accepted");}catch(IllegalStateException expected){}}
  public void onDestroyView(){super.onDestroyView();events+="w";}
  public void onDestroy(){super.onDestroy();events+="d";if("1".equals(System.getenv("SHIRAZ_FIXTURE_FRAGMENT_FAIL")))throw new IllegalStateException("SHIRAZ_FRAGMENT_CLEANUP_FAILURE");}
  public void onDetach(){super.onDetach();events+="x";if("1".equals(System.getenv("SHIRAZ_FIXTURE_FRAGMENT_FAIL")))return;String expected="probe".equals(getTag())?"acdx":"acvsrptwdx";if(!events.equals(expected))throw new AssertionError("fragment order "+events);System.out.println("SHIRAZ_FRAGMENT_LIFECYCLE_OK");}
 }
 public static void install(Activity activity) {
  FragmentManager manager=activity.getFragmentManager();Sample sample=(Sample)manager.findFragmentByTag("report");
  if(sample==null){sample=new Sample();Bundle args=new Bundle();args.putInt("token",123);sample.setArguments(args);FragmentTransaction transaction=manager.beginTransaction().add(sample,"report");transaction.commit();if(manager.findFragmentByTag("report")!=null)throw new AssertionError("commit ran inline");if(!manager.executePendingTransactions()||manager.executePendingTransactions())throw new AssertionError("pending transaction result");try{transaction.commit();throw new AssertionError("double commit");}catch(IllegalStateException expected){}}
  if("1".equals(System.getenv("SHIRAZ_FIXTURE_FRAGMENT_FAIL")))throw new IllegalStateException("SHIRAZ_FRAGMENT_PRIMARY_FAILURE");
  if(!sample.events.equals("ac")||sample.getActivity()!=activity)throw new AssertionError("fragment creation stage");
  Sample probe=new Sample();Bundle args=new Bundle();args.putInt("token",123);probe.setArguments(args);manager.beginTransaction().add(probe,"probe").commitNow();manager.beginTransaction().remove(probe).commitNow();
  if(probe.getActivity()!=null||probe.getFragmentManager()!=null||manager.findFragmentByTag("probe")!=null)throw new AssertionError("removed fragment ownership");
  try{manager.beginTransaction().add(42,new Sample()).commitNow();throw new AssertionError("view container accepted");}catch(UnsupportedOperationException expected){}
  System.out.println("SHIRAZ_FRAGMENT_TRANSACTIONS_OK");
 }
}
