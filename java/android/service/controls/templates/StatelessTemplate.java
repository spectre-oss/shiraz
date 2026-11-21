// SPDX-License-Identifier: GPL-3.0-only
package android.service.controls.templates;

public final class StatelessTemplate extends ControlTemplate {
    public StatelessTemplate(String templateId) {
        super(templateId);
    }

    @Override
    public int getTemplateType() {
        return TYPE_STATELESS;
    }
}
