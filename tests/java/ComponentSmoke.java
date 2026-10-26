// SPDX-License-Identifier: GPL-3.0-only
import android.app.Application;
import android.content.res.Resources;
public final class ComponentSmoke {
 public static void main(String[] args){Application app=new Application(){public Resources getResources(){return Resources.getSystem();}};org.shiraz.fixture.ComponentFixture.check(app);org.shiraz.fixture.ComponentFixture.live(app);app.onTrimMemory(20);System.out.println("COMPONENT_CALLBACK_DISPATCH_OK");}
}
