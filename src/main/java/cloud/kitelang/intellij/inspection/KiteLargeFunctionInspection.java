package cloud.kitelang.intellij.inspection;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteFile;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Inspection that detects functions that are too large.
 * Large functions are harder to understand, test, and maintain.
 * Default threshold is 30 lines.
 */
public class KiteLargeFunctionInspection extends KiteInspectionBase {

    private static final int DEFAULT_MAX_LINES = 30;

    @Override
    public @NotNull String getShortName() {
        return "KiteLargeFunction";
    }

    @Override
    protected void checkKiteFile(@NotNull KiteFile file,
                                  @NotNull InspectionManager manager,
                                  boolean isOnTheFly,
                                  @NotNull List<ProblemDescriptor> problems) {
        checkFunctionsRecursive(file, manager, isOnTheFly, problems);
    }

    private void checkFunctionsRecursive(PsiElement element,
                                          InspectionManager manager,
                                          boolean isOnTheFly,
                                          List<ProblemDescriptor> problems) {
        if (element == null || element.getNode() == null) return;

        var type = element.getNode().getElementType();

        if (type == KiteElementTypes.FUNCTION_DECLARATION) {
            checkFunctionSize(element, manager, isOnTheFly, problems);
        }

        // Recurse into children
        var child = element.getFirstChild();
        while (child != null) {
            checkFunctionsRecursive(child, manager, isOnTheFly, problems);
            child = child.getNextSibling();
        }
    }

    private void checkFunctionSize(PsiElement functionDecl,
                                    InspectionManager manager,
                                    boolean isOnTheFly,
                                    List<ProblemDescriptor> problems) {
        // Find function name for reporting
        var functionName = findFunctionName(functionDecl);
        var nameElement = findFunctionNameElement(functionDecl);

        // Count lines in function body
        int lineCount = countBodyLines(functionDecl);

        if (lineCount > DEFAULT_MAX_LINES) {
            var targetElement = nameElement != null ? nameElement : functionDecl;
            var name = functionName != null ? functionName : "Function";
            var problem = createWeakWarning(
                    manager,
                    targetElement,
                    name + " has " + lineCount + " lines (exceeds " + DEFAULT_MAX_LINES + " line limit)",
                    isOnTheFly
            );
            problems.add(problem);
        }
    }

    /**
     * Count the number of lines in the function body.
     */
    private int countBodyLines(PsiElement functionDecl) {
        PsiElement openBrace = null;
        PsiElement closeBrace = null;

        var child = functionDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();
                if (type == KiteTokenTypes.LBRACE) {
                    openBrace = child;
                } else if (type == KiteTokenTypes.RBRACE) {
                    closeBrace = child;
                }
            }
            child = child.getNextSibling();
        }

        if (openBrace == null || closeBrace == null) return 0;

        // Get the text between braces and count newlines
        var startOffset = openBrace.getTextRange().getEndOffset();
        var endOffset = closeBrace.getTextRange().getStartOffset();

        var file = functionDecl.getContainingFile();
        if (file == null) return 0;

        var document = file.getViewProvider().getDocument();
        if (document == null) return 0;

        var startLine = document.getLineNumber(startOffset);
        var endLine = document.getLineNumber(endOffset);

        // The number of lines is endLine - startLine
        // But we count actual content lines, not including the brace lines
        return Math.max(0, endLine - startLine - 1);
    }

    private String findFunctionName(PsiElement functionDecl) {
        boolean foundFun = false;
        var child = functionDecl.getFirstChild();

        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                if (type == KiteTokenTypes.FUN) {
                    foundFun = true;
                } else if (foundFun && type == KiteTokenTypes.IDENTIFIER) {
                    return child.getText();
                } else if (type == KiteTokenTypes.LPAREN) {
                    break;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private PsiElement findFunctionNameElement(PsiElement functionDecl) {
        boolean foundFun = false;
        var child = functionDecl.getFirstChild();

        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                if (type == KiteTokenTypes.FUN) {
                    foundFun = true;
                } else if (foundFun && type == KiteTokenTypes.IDENTIFIER) {
                    return child;
                } else if (type == KiteTokenTypes.LPAREN) {
                    break;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }
}
