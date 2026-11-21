// SPDX-License-Identifier: GPL-3.0-only
package android.service.quickaccesswallet;

public interface GetWalletCardsCallback {
    void onSuccess(GetWalletCardsResponse response);
    void onFailure(GetWalletCardsError error);
}
