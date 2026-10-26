// SPDX-License-Identifier: GPL-3.0-only
public final class EditTextSmoke {
 public static void main(String[] args){android.content.Context context=new android.content.ContextWrapper(null){public String getPackageName(){return "org.shiraz.fixture";}public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}};org.shiraz.fixture.EditTextFixture.check(context);}
}
