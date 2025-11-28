package io.kite.intellij.templates;

import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import io.kite.intellij.KiteLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * Defines the context in which Kite live templates are available.
 * Templates will be active when editing .kite files.
 */
public class KiteLiveTemplateContext extends TemplateContextType {

    protected KiteLiveTemplateContext() {
        super("Kite");
    }

    @Override
    public boolean isInContext(@NotNull TemplateActionContext templateActionContext) {
        return templateActionContext.getFile().getLanguage().isKindOf(KiteLanguage.INSTANCE);
    }
}
