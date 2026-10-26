// SPDX-License-Identifier: GPL-3.0-only
public final class NinePatchSmoke {public static void main(String[] args)throws Exception{android.content.res.Resources r=android.content.res.Resources.shirazOpen(args[0],100,100);try{org.shiraz.fixture.NinePatchFixture.check(r);}finally{r.shirazClose();}}}
