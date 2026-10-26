// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.res.Resources;
public final class InflaterSmoke {
 public static void main(String[] args){final Resources r=Resources.shirazOpen(args[0],96,160);Context c=new ContextWrapper(null){private android.view.LayoutInflater inflater;public Resources getResources(){return r;}public Resources.Theme getTheme(){return r.newTheme();}public Object getSystemService(String name){if(LAYOUT_INFLATER_SERVICE.equals(name)){if(inflater==null)inflater=new shiraz.runtime.HostLayoutInflater(this);return inflater;}throw new UnsupportedOperationException(name);}};try{org.shiraz.fixture.InflaterFixture.check(c);}finally{r.shirazClose();}System.out.println("INFLATER_XML_RENDERING_AND_FACTORIES_OK");}
}
