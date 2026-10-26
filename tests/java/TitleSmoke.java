// SPDX-License-Identifier: GPL-3.0-only
import android.app.Activity;
import shiraz.runtime.NativeSurface;
import org.shiraz.fixture.TitleFixture;
public final class TitleSmoke {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  Activity activity=new Activity();activity.shirazBindTitle("Initial");check(activity.getTitle().equals("Initial")&&activity.getWindow().shirazGetTitle().equals("Initial"),"initial title");
  try{activity.shirazBindTitle("again");throw new AssertionError("duplicate initialization");}catch(IllegalStateException expected){}
  activity.setTitleColor(0xff123456);check(activity.getWindow().shirazGetTitleColor()==0xff123456,"requested color");activity.setTitle((CharSequence)null);check(activity.getTitle()==null&&activity.getWindow().shirazGetTitle().equals(""),"null normalization");
  try{activity.setTitle(new String(new char[4097]));throw new AssertionError("title bound");}catch(IllegalArgumentException expected){}
  final NativeSurface primary=new NativeSurface(32,32),other=new NativeSurface(32,32);
  try{
   try{primary.requestWindowTitle("outside");throw new AssertionError("inactive surface");}catch(IllegalStateException expected){}
   primary.presentDispatched(2,new NativeSurface.FrameCallback(){int frame;
    public boolean onFrame(){String title=frame++==0?"Map café 🗺":"";primary.requestWindowTitle(title);TitleFixture.nativeTitle(title);
     try{other.requestWindowTitle("wrong surface");throw new AssertionError("foreign surface");}catch(IllegalStateException expected){}
     try{primary.requestWindowTitle("bad\0title");throw new AssertionError("NUL title");}catch(IllegalArgumentException expected){}
     TitleFixture.nativeTitle(title);return true;
    }
    public void onPointer(int a,float x,float y,int s,long t){}
    public void onKey(int a,int k,int s,int r,int m,int f,long d,long t){}
    public void onText(String s,long t){}
   });
  }finally{primary.close();other.close();}
  try{primary.requestWindowTitle("closed");throw new AssertionError("closed surface");}catch(IllegalStateException expected){}
  System.out.println("TITLE_BRIDGE_OK");
 }
}
