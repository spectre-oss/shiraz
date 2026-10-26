// SPDX-License-Identifier: GPL-3.0-only
public final class SelectorSmoke {public static void main(String[] args){android.content.res.Resources r=android.content.res.Resources.shirazOpen(args[0],100,100);try{org.shiraz.fixture.SelectorFixture.check(r);}finally{r.shirazClose();}}}
