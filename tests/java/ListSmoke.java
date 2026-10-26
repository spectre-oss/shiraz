// SPDX-License-Identifier: GPL-3.0-only
public final class ListSmoke {
 public static void main(String[] args){
  android.content.Context context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}};
  org.shiraz.fixture.ListFixture.check(context);
 }
}
