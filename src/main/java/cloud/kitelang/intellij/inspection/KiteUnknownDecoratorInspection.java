package cloud.kitelang.intellij.inspection;

import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Inspection that detects unknown decorator names in Kite files.
 * Reports decorators that are not in the known set of valid decorator names.
 * <p>
 * Valid decorators include:
 * <ul>
 *   <li>Validation: minValue, maxValue, minLength, maxLength, nonEmpty, validate, allowed, unique</li>
 *   <li>Resource: existing, sensitive, dependsOn, tags, provider</li>
 *   <li>Metadata: description, count, cloud</li>
 * </ul>
 */
public class KiteUnknownDecoratorInspection extends KiteInspectionBase {

    /**
     * Set of valid built-in decorator names.
     */
    private static final Set<String> VALID_DECORATORS = Set.of(
            // Validation decorators
            "minValue", "maxValue", "minLength", "maxLength",
            "nonEmpty", "validate", "allowed", "unique",
            // Resource decorators
            "existing", "sensitive", "dependsOn", "tags", "provider",
            // Metadata decorators
            "description", "count", "cloud"
    );

    @Override
    public @NotNull String getShortName() {
        return "KiteUnknownDecorator";
    }

    @Override
    protected void checkElement(@NotNull PsiElement element, @NotNull ProblemsHolder holder) {
        if (element.getNode() == null) return;

        var type = element.getNode().getElementType();

        // Check for @ symbol followed by identifier (decorator pattern)
        if (type == KiteTokenTypes.AT) {
            var next = KitePsiUtil.skipWhitespace(element.getNextSibling());
            if (next != null && next.getNode() != null &&
                next.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {

                var decoratorName = next.getText();
                if (!VALID_DECORATORS.contains(decoratorName)) {
                    registerWarning(holder, next, "Unknown decorator '@" + decoratorName + "'");
                }
            }
        }
    }
}
