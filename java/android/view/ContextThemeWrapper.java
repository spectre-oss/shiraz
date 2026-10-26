// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.content.Context;
import android.content.ContextWrapper;

public class ContextThemeWrapper extends ContextWrapper {
    private android.content.res.Resources.Theme theme;
    private int themeId;
    private LayoutInflater inflater;
    @Override public Object getSystemService(String name){if(LAYOUT_INFLATER_SERVICE.equals(name)){if(inflater==null)inflater=LayoutInflater.from(getBaseContext()).cloneInContext(this);return inflater;}return super.getSystemService(name);}
    public ContextThemeWrapper() { super(null); }
    public ContextThemeWrapper(Context base, int theme) {
        super(base);
        if (theme != 0) setTheme(theme);
    }
    public ContextThemeWrapper(Context base, android.content.res.Resources.Theme theme) {
        super(base);
        if (theme == null || theme.getResources() != getResources()) throw new IllegalArgumentException("Theme resource manager mismatch");
        this.theme = theme;
    }
    @Override public void setTheme(int id) {
        if (id == themeId) return;
        getTheme().applyStyle(id, true); themeId = id;
    }
    @Override public android.content.res.Resources.Theme getTheme() {
        if (theme == null) {
            android.content.res.Resources.Theme initial = getResources().newTheme();
            initial.setTo(super.getTheme()); theme = initial;
        }
        return theme;
    }
    public int getThemeResId() { return themeId; }
}
