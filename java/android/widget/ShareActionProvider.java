// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.content.Intent;
import android.view.ActionProvider;
import android.view.SubMenu;
import android.view.View;

/** ActionProvider for displaying a share action view and submenu for a share Intent. */
public class ShareActionProvider extends ActionProvider {
    public static final String DEFAULT_SHARE_HISTORY_FILE_NAME = "share_history.xml";

    public interface OnShareTargetSelectedListener {
        boolean onShareTargetSelected(ShareActionProvider source, Intent intent);
    }

    private final Context mContext;
    private String mShareHistoryFileName = DEFAULT_SHARE_HISTORY_FILE_NAME;
    private OnShareTargetSelectedListener mOnShareTargetSelectedListener;
    private Intent mShareIntent;

    public ShareActionProvider(Context context) {
        super(context);
        mContext = context;
    }

    public void setOnShareTargetSelectedListener(OnShareTargetSelectedListener listener) {
        mOnShareTargetSelectedListener = listener;
    }

    @Override
    public View onCreateActionView() {
        Button button = new Button(mContext);
        button.setText("Share");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mShareIntent != null) {
                    if (mOnShareTargetSelectedListener != null) {
                        mOnShareTargetSelectedListener.onShareTargetSelected(
                                ShareActionProvider.this, mShareIntent);
                    }
                }
            }
        });
        return button;
    }

    @Override
    public boolean hasSubMenu() {
        return true;
    }

    @Override
    public void onPrepareSubMenu(SubMenu subMenu) {
        subMenu.clear();
    }

    public void setShareHistoryFileName(String shareHistoryFile) {
        mShareHistoryFileName = shareHistoryFile;
    }

    public void setShareIntent(Intent shareIntent) {
        if (shareIntent != null) {
            final String action = shareIntent.getAction();
            if (Intent.ACTION_SEND.equals(action)
                    || "android.intent.action.SEND_MULTIPLE".equals(action)) {
                shareIntent.addFlags(0x00080000 | 0x08000000);
            }
        }
        mShareIntent = shareIntent;
    }
}
