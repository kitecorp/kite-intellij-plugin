package cloud.kitelang.intellij.inspection;

import cloud.kitelang.intellij.psi.KiteFile;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;
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
    protected void checkKiteFile(@NotNull KiteFile file,
                                  @NotNull InspectionManager manager,
                                  boolean isOnTheFly,
                                  @NotNull List<ProblemDescriptor> problems) {
        checkDecoratorsRecursive(file, manager, isOnTheFly, problems);
    }

    private void checkDecoratorsRecursive(PsiElement element,
                                           InspectionManager manager,
                                           boolean isOnTheFly,
                                           List<ProblemDescriptor> problems) {
        if (element == null || element.getNode() == null) return;

        var type = element.getNode().getElementType();

        // Check for @ symbol followed by identifier (decorator pattern)
        if (type == KiteTokenTypes.AT) {
            var next = KitePsiUtil.skipWhitespace(element.getNextSibling());
            if (next != null && next.getNode() != null &&
                next.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {

                var decoratorName = next.getText();
                if (!VALID_DECORATORS.contains(decoratorName)) {
                    var problem = createWarning(
                            manager,
                            next,
                            "Unknown decorator '@" + decoratorName + "'",
                            isOnTheFly
                    );
                    problems.add(problem);
                }
            }
        }

        // Recurse into children
        var child = element.getFirstChild();
        while (child != null) {
            checkDecoratorsRecursive(child, manager, isOnTheFly, problems);
            child = child.getNextSibling();
        }
    }
}
