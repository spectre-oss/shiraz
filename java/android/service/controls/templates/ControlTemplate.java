// SPDX-License-Identifier: GPL-3.0-only
package android.service.controls.templates;

import java.util.Objects;

public abstract class ControlTemplate {
    public static final int TYPE_ERROR = -1;
    public static final int TYPE_NO_TEMPLATE = 0;
    public static final int TYPE_TOGGLE = 1;
    public static final int TYPE_RANGE = 2;
    public static final int TYPE_THUMBNAIL = 3;
    public static final int TYPE_TOGGLE_RANGE = 6;
    public static final int TYPE_TEMPERATURE = 7;
    public static final int TYPE_STATELESS = 8;

    private static final ControlTemplate NO_TEMPLATE = new ControlTemplate("") {
        @Override
        public int getTemplateType() {
            return TYPE_NO_TEMPLATE;
        }
    };

    private static final ControlTemplate ERROR_TEMPLATE = new ControlTemplate("") {
        @Override
        public int getTemplateType() {
            return TYPE_ERROR;
        }
    };

    private final String mTemplateId;

    ControlTemplate(String templateId) {
        mTemplateId = Objects.requireNonNull(templateId, "templateId must not be null");
    }

    public String getTemplateId() {
        return mTemplateId;
    }

    public abstract int getTemplateType();

    public static ControlTemplate getErrorTemplate() {
        return ERROR_TEMPLATE;
    }

    public static ControlTemplate getNoTemplateObject() {
        return NO_TEMPLATE;
    }
}
