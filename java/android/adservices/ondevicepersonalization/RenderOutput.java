// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import android.os.PersistableBundle;
import java.util.Objects;

/** Output returned from {@link IsolatedWorker#onRender}. */
public final class RenderOutput {
    private final String mContent;
    private final String mTemplateId;
    private final PersistableBundle mTemplateParams;

    private RenderOutput(
            String content, String templateId, PersistableBundle templateParams) {
        this.mContent = content;
        this.mTemplateId = templateId;
        this.mTemplateParams =
                templateParams == null ? PersistableBundle.EMPTY : templateParams;
    }

    public String getContent() {
        return mContent;
    }

    public String getTemplateId() {
        return mTemplateId;
    }

    public PersistableBundle getTemplateParams() {
        return mTemplateParams;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RenderOutput)) {
            return false;
        }
        RenderOutput that = (RenderOutput) o;
        return Objects.equals(mContent, that.mContent)
                && Objects.equals(mTemplateId, that.mTemplateId)
                && Objects.equals(mTemplateParams, that.mTemplateParams);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mContent, mTemplateId, mTemplateParams);
    }

    public static final class Builder {
        private String mContent;
        private String mTemplateId;
        private PersistableBundle mTemplateParams = PersistableBundle.EMPTY;

        public Builder() {
        }

        public Builder setContent(String value) {
            this.mContent = value;
            return this;
        }

        public Builder setTemplateId(String value) {
            this.mTemplateId = value;
            return this;
        }

        public Builder setTemplateParams(PersistableBundle value) {
            this.mTemplateParams =
                    Objects.requireNonNull(value, "templateParams");
            return this;
        }

        public RenderOutput build() {
            return new RenderOutput(mContent, mTemplateId, mTemplateParams);
        }
    }
}
