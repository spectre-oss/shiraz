// SPDX-License-Identifier: GPL-3.0-only
public final class LruSmoke {
    public static void main(String[] args) throws Exception {
        org.shiraz.fixture.LruFixture.check();
        System.out.println("LRU_WEIGHTED_EVICTION_AND_CREATION_RACES_OK");
    }
}
