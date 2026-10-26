// SPDX-License-Identifier: GPL-3.0-only
public final class AlphaSmoke {
 public static void main(String[] args){final android.content.res.Resources r=android.content.res.Resources.shirazOpen(args[0],100,100);android.content.Context context=new android.content.ContextWrapper(null){public String getPackageName(){return "org.shiraz.fixture";}public android.content.res.Resources getResources(){return r;}public android.content.res.Resources.Theme getTheme(){return r.newTheme();}public Object getSystemService(String name){if(LAYOUT_INFLATER_SERVICE.equals(name))return new shiraz.runtime.HostLayoutInflater(this);throw new UnsupportedOperationException(name);}};try{org.shiraz.fixture.AlphaFixture.check(context);}finally{r.shirazClose();}}
}
