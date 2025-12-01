package cloud.kitelang.intellij.inspection;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteFile;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Inspection that detects deeply nested code.
 * Deeply nested code is harder to read and understand.
 * Default threshold is 4 levels of nesting.
 */
public class KiteDeepNestingInspection extends KiteInspectionBase {

    private static final int DEFAULT_MAX_DEPTH = 4;

    @Override
    public @NotNull String getShortName() {
        return "KiteDeepNesting";
    }

    @Override
    protected void checkKiteFile(@NotNull KiteFile file,
                                  @NotNull InspectionManager manager,
                                  boolean isOnTheFly,
                                  @NotNull List<ProblemDescriptor> problems) {
        // Check nesting in all function bodies
        checkNestingRecursive(file, manager, isOnTheFly, problems);
    }

    private void checkNestingRecursive(PsiElement element,
                                        InspectionManager manager,
                                        boolean isOnTheFly,
                                        List<ProblemDescriptor> problems) {
        if (element == null || element.getNode() == null) return;

        var type = element.getNode().getElementType();

        // Check function declarations for nesting
        if (type == KiteElementTypes.FUNCTION_DECLARATION) {
            checkFunctionNesting(element, manager, isOnTheFly, problems);
        }

        // Recurse into children
        var child = element.getFirstChild();
        while (child != null) {
            checkNestingRecursive(child, manager, isOnTheFly, problems);
            child = child.getNextSibling();
        }
    }

    private void checkFunctionNesting(PsiElement functionDecl,
                                       InspectionManager manager,
                                       boolean isOnTheFly,
                                       List<ProblemDescriptor> problems) {
        // Find function body (between braces)
        boolean insideBody = false;

        var child = functionDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                if (type == KiteTokenTypes.LBRACE) {
                    insideBody = true;
                } else if (type == KiteTokenTypes.RBRACE) {
                    break;
                } else if (insideBody) {
                    // Check nesting depth inside the body
                    checkNestingDepth(child, 0, manager, isOnTheFly, problems);
                }
            }
            child = child.getNextSibling();
        }
    }

    private void checkNestingDepth(PsiElement element,
                                    int currentDepth,
                                    InspectionManager manager,
                                    boolean isOnTheFly,
                                    List<ProblemDescriptor> problems) {
        if (element == null || element.getNode() == null) return;

        var type = element.getNode().getElementType();
        int newDepth = currentDepth;

        // Check if this is a nesting construct
        if (isNestingConstruct(type)) {
            newDepth = currentDepth + 1;

            // Report if exceeds max depth
            if (newDepth > DEFAULT_MAX_DEPTH) {
                var keyword = findKeyword(element);
                var targetElement = keyword != null ? keyword : element;
                var problem = createWeakWarning(
                        manager,
                        targetElement,
                        "Deeply nested code (depth " + newDepth + " exceeds " + DEFAULT_MAX_DEPTH + ")",
                        isOnTheFly
                );
                problems.add(problem);
            }
        }

        // Recurse into children
        var child = element.getFirstChild();
        while (child != null) {
            checkNestingDepth(child, newDepth, manager, isOnTheFly, problems);
            child = child.getNextSibling();
        }
    }

    /**
     * Check if the element type represents a nesting construct.
     */
    private boolean isNestingConstruct(IElementType type) {
        // Common nesting constructs: if, for, while (represented by tokens)
        return type == KiteTokenTypes.IF ||
               type == KiteTokenTypes.FOR ||
               type == KiteTokenTypes.WHILE;
    }

    /**
     * Find the keyword element for better highlighting.
     */
    private PsiElement findKeyword(PsiElement element) {
        var child = element.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();
                if (type == KiteTokenTypes.IF ||
                    type == KiteTokenTypes.FOR ||
                    type == KiteTokenTypes.WHILE) {
                    return child;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }
}
