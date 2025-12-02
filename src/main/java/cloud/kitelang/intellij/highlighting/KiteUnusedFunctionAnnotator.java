package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Annotator that warns about unused functions (declared but never called).
 * <p>
 * Example:
 * <pre>
 * fun helper() number {       // Warning: Function 'helper' is declared but never called
 *     return 42
 * }
 *
 * fun calculate() number {    // OK - called below
 *     return 1
 * }
 *
 * var result = calculate()
 * </pre>
 */
public class KiteUnusedFunctionAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Only process FUNCTION_DECLARATION elements
        if (type != KiteElementTypes.FUNCTION_DECLARATION) {
            return;
        }

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Get function name from the declaration
        String funcName = extractFunctionName(element);
        if (funcName == null || funcName.isEmpty()) {
            return;
        }

        // Skip init functions (special case)
        if ("init".equals(funcName)) {
            return;
        }

        // Check if this function is called anywhere in the file (excluding self-calls)
        if (!isFunctionCalledInFile(element.getContainingFile(), funcName, element)) {
            PsiElement nameElement = findFunctionNameElement(element);
            PsiElement rangeElement = nameElement != null ? nameElement : element;

            holder.newAnnotation(HighlightSeverity.WARNING,
                            "Function '" + funcName + "' is declared but never called")
                    .range(rangeElement)
                    .create();
        }
    }

    /**
     * Extract the function name from a FUNCTION_DECLARATION element.
     */
    @Nullable
    private String extractFunctionName(PsiElement funcDecl) {
        // Pattern: FUN identifier LPAREN ... -> find first IDENTIFIER after FUN
        boolean foundFun = false;
        for (PsiElement child = funcDecl.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;

            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.FUN) {
                foundFun = true;
            } else if (foundFun && childType == KiteTokenTypes.IDENTIFIER) {
                return child.getText();
            } else if (childType == KiteTokenTypes.LPAREN) {
                break; // Stop before parameters
            }
        }
        return null;
    }

    /**
     * Find the function name element for annotation range.
     */
    @Nullable
    private PsiElement findFunctionNameElement(PsiElement funcDecl) {
        boolean foundFun = false;
        for (PsiElement child = funcDecl.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;

            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.FUN) {
                foundFun = true;
            } else if (foundFun && childType == KiteTokenTypes.IDENTIFIER) {
                return child;
            } else if (childType == KiteTokenTypes.LPAREN) {
                break;
            }
        }
        return null;
    }

    /**
     * Check if a function is called anywhere in the file, excluding calls within itself.
     */
    private boolean isFunctionCalledInFile(PsiFile file, String funcName, PsiElement functionDecl) {
        // Get the range of the function declaration to exclude self-calls
        int funcStart = functionDecl.getTextRange().getStartOffset();
        int funcEnd = functionDecl.getTextRange().getEndOffset();

        // Search the entire file for function calls
        return searchForFunctionCall(file.getFirstChild(), funcName, funcStart, funcEnd);
    }

    /**
     * Search for function calls in the file, excluding the function's own body.
     */
    private boolean searchForFunctionCall(PsiElement element, String funcName,
                                          int funcStart, int funcEnd) {
        if (element == null) return false;

        // Check all siblings and their children
        for (PsiElement current = element; current != null; current = current.getNextSibling()) {
            int currentOffset = current.getTextOffset();

            // Skip elements inside the function's own declaration
            if (currentOffset >= funcStart && currentOffset < funcEnd) {
                continue;
            }

            if (current.getNode() != null) {
                IElementType type = current.getNode().getElementType();

                // Check if this is an identifier that could be a function call
                if (type == KiteTokenTypes.IDENTIFIER && funcName.equals(current.getText())) {
                    // Check if followed by LPAREN (function call)
                    PsiElement next = current.getNextSibling();
                    while (next != null && isWhitespace(next)) {
                        next = next.getNextSibling();
                    }
                    if (next != null && next.getNode() != null &&
                        next.getNode().getElementType() == KiteTokenTypes.LPAREN) {
                        return true;
                    }
                }
            }

            // Recursively search children
            if (searchForFunctionCall(current.getFirstChild(), funcName, funcStart, funcEnd)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if an element is whitespace.
     */
    private boolean isWhitespace(PsiElement element) {
        if (element == null || element.getNode() == null) return true;
        IElementType type = element.getNode().getElementType();
        return type == KiteTokenTypes.WHITESPACE ||
               type == KiteTokenTypes.NL ||
               type == KiteTokenTypes.NEWLINE ||
               type == com.intellij.psi.TokenType.WHITE_SPACE;
    }
}
