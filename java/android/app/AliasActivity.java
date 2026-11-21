// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Intent;
import android.os.Bundle;

@Deprecated
public class AliasActivity extends Activity {
    public AliasActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent != null && intent.getParcelableExtra(Intent.EXTRA_INTENT) instanceof Intent) {
            startActivity((Intent) intent.getParcelableExtra(Intent.EXTRA_INTENT));
        }
        finish();
    }
}
