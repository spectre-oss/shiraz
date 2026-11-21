// SPDX-License-Identifier: GPL-3.0-only
import android.content.Intent;
import android.content.ComponentName;
import android.net.Uri;
import java.util.HashMap;
public final class IntentFilterIdentitySmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        Intent a=new Intent("action",Uri.parse("content://fixture/item")).setDataAndType(Uri.parse("content://fixture/item"),"text/plain").setPackage("fixture").setComponent(new ComponentName("fixture","fixture.Screen")).addCategory("one").addCategory("two");
        Intent b=new Intent(a).putExtra("payload",42).setFlags(123);
        b.removeCategory("one");b.addCategory("one");
        check(a.filterEquals(b)&&a.filterHashCode()==b.filterHashCode(),"Payload or category insertion order changed identity");
        HashMap<Intent.FilterComparison,String> map=new HashMap<Intent.FilterComparison,String>();map.put(new Intent.FilterComparison(a),"found");check("found".equals(map.get(new Intent.FilterComparison(b))),"Routing key lookup");
        for(Intent different:new Intent[]{new Intent(a).setAction("other"),new Intent(a).setData(Uri.parse("content://fixture/other")),new Intent(a).setType("image/png"),new Intent(a).setPackage("other"),new Intent(a).setComponent(null),new Intent(a).addCategory("three")})check(!a.filterEquals(different),"Routing field ignored");
        Intent empty=new Intent();check(empty.filterHashCode()==0&&!empty.filterEquals(null)&&empty.filterEquals(new Intent()),"Empty identity");
        Intent.FilterComparison wrapper=new Intent.FilterComparison(a);int hash=wrapper.hashCode();a.setAction("changed");check(wrapper.getIntent()==a&&wrapper.hashCode()==hash,"Comparison ownership/hash snapshot");
        Object absent=null;
        check(!wrapper.equals(a)&&!wrapper.equals(absent),"Comparison type safety");
        System.out.println("INTENT_FILTER_IDENTITY_OK");
    }
}
