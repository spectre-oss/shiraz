// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.Selection;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.LinearLayout;

/** Modal picker dialog allowing selection of accented or special character variants. */
public class CharacterPickerDialog extends Dialog
        implements AdapterView.OnItemClickListener, View.OnClickListener {
    private final View mView;
    private final Editable mText;
    private final String mOptions;
    private final boolean mInsert;
    private Button mCancelButton;

    public CharacterPickerDialog(
            Context context, View view, Editable text, String options, boolean insert) {
        super(context);
        mView = view;
        mText = text;
        mOptions = options != null ? options : "";
        mInsert = insert;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        Context ctx = getContext();
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);

        GridView grid = new GridView(ctx);
        grid.setNumColumns(Math.max(1, Math.min(5, mOptions.length())));
        grid.setAdapter(new OptionsAdapter(ctx));
        grid.setOnItemClickListener(this);
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mCancelButton = new Button(ctx);
        mCancelButton.setText("Cancel");
        mCancelButton.setOnClickListener(this);
        root.addView(mCancelButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    @Override
    public void onItemClick(AdapterView parent, View view, int position, long id) {
        if (position >= 0 && position < mOptions.length()) {
            String result = String.valueOf(mOptions.charAt(position));
            replaceCharacterAndClose(result);
        }
    }

    private void replaceCharacterAndClose(CharSequence replace) {
        int selEnd = Selection.getSelectionEnd(mText);
        if (selEnd < 0) {
            selEnd = mText.length();
        }
        if (mInsert || selEnd == 0) {
            mText.insert(selEnd, replace);
        } else {
            mText.replace(selEnd - 1, selEnd, replace);
        }
        dismiss();
    }

    @Override
    public void onClick(View v) {
        if (v == mCancelButton) {
            dismiss();
        } else if (v instanceof Button) {
            CharSequence result = ((Button) v).getText();
            replaceCharacterAndClose(result);
        }
    }

    private class OptionsAdapter extends BaseAdapter {
        private final Context mContext;

        OptionsAdapter(Context context) {
            mContext = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Button b = convertView instanceof Button ? (Button) convertView : new Button(mContext);
            b.setText(String.valueOf(mOptions.charAt(position)));
            b.setOnClickListener(CharacterPickerDialog.this);
            return b;
        }

        @Override
        public final int getCount() {
            return mOptions.length();
        }

        @Override
        public final Object getItem(int position) {
            return String.valueOf(mOptions.charAt(position));
        }

        @Override
        public final long getItemId(int position) {
            return position;
        }
    }
}
