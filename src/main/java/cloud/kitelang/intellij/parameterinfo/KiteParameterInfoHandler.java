package cloud.kitelang.intellij.parameterinfo;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContext;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parameter info handler for Kite language.
 * Shows function parameter hints when pressing Ctrl+P inside function calls.
 * <p>
 * Example: When cursor is inside greet("Alice"|, 30), pressing Ctrl+P shows:
 * "string name, number age" with "name" highlighted.
 */
public class KiteParameterInfoHandler implements ParameterInfoHandler<PsiElement, KiteParameterInfoHandler.KiteFunctionInfo> {

    /**
     * Represents function parameter information to display.
     */
    public static class KiteFunctionInfo {
        private final String functionName;
        private final List<KiteParameter> parameters;
        private final String returnType;

        public KiteFunctionInfo(String functionName, List<KiteParameter> parameters, @Nullable String returnType) {
            this.functionName = functionName;
            this.parameters = parameters;
            this.returnType = returnType;
        }

        public String getFunctionName() {
            return functionName;
        }

        public List<KiteParameter> getParameters() {
            return parameters;
        }

        @Nullable
        public String getReturnType() {
            return returnType;
        }

        /**
         * Get the presentation text for parameters.
         */
        public String getParametersText() {
            if (parameters.isEmpty()) {
                return "<no parameters>";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                KiteParameter param = parameters.get(i);
                sb.append(param.type).append(" ").append(param.name);
            }
            return sb.toString();
        }
    }

    /**
     * Represents a single parameter with type and name.
     */
    public static class KiteParameter {
        public final String type;
        public final String name;
        public final int startOffset;
        public final int endOffset;

        public KiteParameter(String type, String name, int startOffset, int endOffset) {
            this.type = type;
            this.name = name;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }
    }

    @Override
    public @Nullable PsiElement findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
        PsiFile file = context.getFile();
        int offset = context.getOffset();

        // Find function call at the current position
        PsiElement element = file.findElementAt(offset);
        if (element == null) {
            return null;
        }

        // Walk backwards to find the function call identifier
        PsiElement functionCall = findFunctionCallElement(element, offset);
        if (functionCall == null) {
            return null;
        }

        // Get the function name
        String functionName = getFunctionName(functionCall);
        if (functionName == null) {
            return null;
        }

        // Find the function declaration and extract parameters
        KiteFunctionInfo functionInfo = findFunctionInfo(file, functionName);
        if (functionInfo == null) {
            return null;
        }

        // Set the items to show in the popup
        context.setItemsToShow(new Object[]{functionInfo});

        return functionCall;
    }

    @Override
    public void showParameterInfo(@NotNull PsiElement element, @NotNull CreateParameterInfoContext context) {
        // Show the hint at the opening parenthesis
        ASTNode node = element.getNode();
        if (node == null) return;

        // Find LPAREN after the identifier
        ASTNode sibling = node.getTreeNext();
        while (sibling != null && isWhitespace(sibling.getElementType())) {
            sibling = sibling.getTreeNext();
        }

        if (sibling != null && sibling.getElementType() == KiteTokenTypes.LPAREN) {
            context.showHint(element, sibling.getStartOffset() + 1, this);
        } else {
            context.showHint(element, element.getTextRange().getEndOffset(), this);
        }
    }

    @Override
    public @Nullable PsiElement findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
        PsiFile file = context.getFile();
        int offset = context.getOffset();

        PsiElement element = file.findElementAt(offset);
        if (element == null) {
            return null;
        }

        // Find function call at the current position
        return findFunctionCallElement(element, offset);
    }

    @Override
    public void updateParameterInfo(@NotNull PsiElement parameterOwner, @NotNull UpdateParameterInfoContext context) {
        if (context.getParameterOwner() == null || parameterOwner.equals(context.getParameterOwner())) {
            // Calculate current parameter index based on comma count before cursor
            int currentParamIndex = getCurrentParameterIndex(parameterOwner, context.getOffset());
            context.setCurrentParameter(currentParamIndex);
        } else {
            // Different function call, hide the popup
            context.removeHint();
        }
    }

    @Override
    public void updateUI(KiteFunctionInfo info, @NotNull ParameterInfoUIContext context) {
        if (info == null) {
            context.setUIComponentEnabled(false);
            return;
        }

        String text = info.getParametersText();
        int currentParameterIndex = context.getCurrentParameterIndex();

        // Calculate highlight range for the current parameter
        int highlightStart = -1;
        int highlightEnd = -1;

        if (currentParameterIndex >= 0 && currentParameterIndex < info.getParameters().size()) {
            List<KiteParameter> params = info.getParameters();

            // Calculate the position in the display text
            int pos = 0;
            for (int i = 0; i < currentParameterIndex; i++) {
                KiteParameter p = params.get(i);
                pos += p.type.length() + 1 + p.name.length(); // "type name"
                pos += 2; // ", "
            }

            KiteParameter currentParam = params.get(currentParameterIndex);
            highlightStart = pos;
            highlightEnd = pos + currentParam.type.length() + 1 + currentParam.name.length();
        }

        context.setupUIComponentPresentation(
                text,
                highlightStart,
                highlightEnd,
                !context.isUIComponentEnabled(),
                false,
                false,
                context.getDefaultParameterColor()
        );
    }

    /**
     * Find the function call element (the identifier) at or before the given offset.
     */
    @Nullable
    private PsiElement findFunctionCallElement(PsiElement element, int offset) {
        // Check if we're inside parentheses of a function call
        PsiElement current = element;
        int parenDepth = 0;

        while (current != null) {
            ASTNode node = current.getNode();
            if (node == null) {
                current = current.getParent();
                continue;
            }

            IElementType type = node.getElementType();

            if (type == KiteTokenTypes.RPAREN) {
                parenDepth++;
            } else if (type == KiteTokenTypes.LPAREN) {
                if (parenDepth == 0) {
                    // Found the opening paren, look for identifier before it
                    PsiElement prev = current.getPrevSibling();
                    while (prev != null && isWhitespaceElement(prev)) {
                        prev = prev.getPrevSibling();
                    }
                    if (prev != null && prev.getNode() != null &&
                        prev.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                        // Check if this is indeed a function call (not a declaration)
                        if (isFunctionCall(prev)) {
                            return prev;
                        }
                    }
                    return null;
                }
                parenDepth--;
            }

            // Move to previous sibling or parent
            PsiElement prev = current.getPrevSibling();
            if (prev == null) {
                current = current.getParent();
            } else {
                current = prev;
            }
        }

        return null;
    }

    /**
     * Check if the given identifier element is a function call (not a declaration).
     */
    private boolean isFunctionCall(PsiElement identifier) {
        // A function call is an identifier followed by LPAREN
        // NOT preceded by 'fun' keyword
        ASTNode node = identifier.getNode();
        if (node == null) return false;

        // Check if followed by LPAREN
        ASTNode next = node.getTreeNext();
        while (next != null && isWhitespace(next.getElementType())) {
            next = next.getTreeNext();
        }
        if (next == null || next.getElementType() != KiteTokenTypes.LPAREN) {
            return false;
        }

        // Check that it's not a function declaration (preceded by 'fun')
        ASTNode prev = node.getTreePrev();
        while (prev != null && isWhitespace(prev.getElementType())) {
            prev = prev.getTreePrev();
        }
        if (prev != null && prev.getElementType() == KiteTokenTypes.FUN) {
            return false; // This is a function declaration, not a call
        }

        return true;
    }

    /**
     * Get the function name from a function call element.
     */
    @Nullable
    private String getFunctionName(PsiElement functionCall) {
        if (functionCall.getNode() == null) return null;
        if (functionCall.getNode().getElementType() != KiteTokenTypes.IDENTIFIER) {
            return null;
        }
        return functionCall.getText();
    }

    /**
     * Find function info by searching for the function declaration.
     * First searches in the current file, then in imported files.
     */
    @Nullable
    private KiteFunctionInfo findFunctionInfo(PsiFile file, String functionName) {
        // First, search in the current file
        KiteFunctionInfo info = findFunctionInfoRecursive(file.getNode(), functionName);
        if (info != null) {
            return info;
        }

        // If not found, search in imported files
        return findFunctionInfoInImports(file, functionName, new HashSet<>());
    }

    /**
     * Search for function info in imported files.
     */
    @Nullable
    private KiteFunctionInfo findFunctionInfoInImports(PsiFile file, String functionName, Set<String> visitedPaths) {
        List<PsiFile> importedFiles = KiteImportHelper.getImportedFiles(file);

        for (PsiFile importedFile : importedFiles) {
            if (importedFile == null || importedFile.getVirtualFile() == null) {
                continue;
            }

            String filePath = importedFile.getVirtualFile().getPath();
            if (visitedPaths.contains(filePath)) {
                continue; // Already visited
            }
            visitedPaths.add(filePath);

            // Search in this imported file
            KiteFunctionInfo info = findFunctionInfoRecursive(importedFile.getNode(), functionName);
            if (info != null) {
                return info;
            }

            // Recursively search in files imported by this file
            info = findFunctionInfoInImports(importedFile, functionName, visitedPaths);
            if (info != null) {
                return info;
            }
        }

        return null;
    }

    @Nullable
    private KiteFunctionInfo findFunctionInfoRecursive(ASTNode node, String functionName) {
        if (node == null) return null;

        if (node.getElementType() == KiteElementTypes.FUNCTION_DECLARATION) {
            // Check if this is the function we're looking for
            KiteFunctionInfo info = extractFunctionInfo(node, functionName);
            if (info != null) {
                return info;
            }
        }

        // Recurse into children
        for (ASTNode child : node.getChildren(null)) {
            KiteFunctionInfo result = findFunctionInfoRecursive(child, functionName);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * Extract function info from a function declaration node.
     */
    @Nullable
    private KiteFunctionInfo extractFunctionInfo(ASTNode funcDecl, String targetName) {
        ASTNode[] children = funcDecl.getChildren(null);
        boolean foundFun = false;
        boolean foundName = false;
        boolean inParams = false;
        String functionName = null;
        String returnType = null;
        List<KiteParameter> parameters = new ArrayList<>();

        // For tracking parameter parsing
        String currentParamType = null;
        int paramStartOffset = 0;

        for (ASTNode child : children) {
            IElementType childType = child.getElementType();

            if (childType == KiteTokenTypes.FUN) {
                foundFun = true;
                continue;
            }

            if (foundFun && !foundName && childType == KiteTokenTypes.IDENTIFIER) {
                functionName = child.getText();
                if (!functionName.equals(targetName)) {
                    return null; // Not the function we're looking for
                }
                foundName = true;
                continue;
            }

            if (foundName && childType == KiteTokenTypes.LPAREN) {
                inParams = true;
                continue;
            }

            if (inParams && childType == KiteTokenTypes.RPAREN) {
                inParams = false;
                continue;
            }

            if (inParams && childType == KiteTokenTypes.IDENTIFIER) {
                // In Kite: fun greet(type name, type name)
                // Parameters are: type name pairs
                if (currentParamType == null) {
                    // This is the type
                    currentParamType = child.getText();
                    paramStartOffset = child.getStartOffset();
                } else {
                    // This is the name - create parameter
                    String paramName = child.getText();
                    int endOffset = child.getStartOffset() + child.getTextLength();
                    parameters.add(new KiteParameter(currentParamType, paramName, paramStartOffset, endOffset));
                    currentParamType = null;
                }
            }

            if (inParams && childType == KiteTokenTypes.COMMA) {
                // Reset for next parameter
                currentParamType = null;
            }

            // After params, look for return type
            if (!inParams && foundName && childType == KiteTokenTypes.IDENTIFIER && returnType == null) {
                // Check if this is before the LBRACE (it's the return type)
                ASTNode next = child.getTreeNext();
                while (next != null && isWhitespace(next.getElementType())) {
                    next = next.getTreeNext();
                }
                if (next != null && next.getElementType() == KiteTokenTypes.LBRACE) {
                    returnType = child.getText();
                }
            }

            if (childType == KiteTokenTypes.LBRACE) {
                break; // Done parsing signature
            }
        }

        if (functionName != null) {
            return new KiteFunctionInfo(functionName, parameters, returnType);
        }

        return null;
    }

    /**
     * Calculate the current parameter index based on comma count before cursor.
     */
    private int getCurrentParameterIndex(PsiElement functionCall, int offset) {
        ASTNode node = functionCall.getNode();
        if (node == null) return 0;

        // Find LPAREN
        ASTNode lparen = node.getTreeNext();
        while (lparen != null && isWhitespace(lparen.getElementType())) {
            lparen = lparen.getTreeNext();
        }

        if (lparen == null || lparen.getElementType() != KiteTokenTypes.LPAREN) {
            return 0;
        }

        // Count commas before the cursor position
        int commaCount = 0;
        int parenDepth = 0;
        ASTNode current = lparen.getTreeNext();

        while (current != null && current.getStartOffset() < offset) {
            IElementType type = current.getElementType();

            if (type == KiteTokenTypes.LPAREN || type == KiteTokenTypes.LBRACK || type == KiteTokenTypes.LBRACE) {
                parenDepth++;
            } else if (type == KiteTokenTypes.RPAREN || type == KiteTokenTypes.RBRACK || type == KiteTokenTypes.RBRACE) {
                if (parenDepth > 0) {
                    parenDepth--;
                } else {
                    // We've exited the function call
                    break;
                }
            } else if (type == KiteTokenTypes.COMMA && parenDepth == 0) {
                commaCount++;
            }

            current = current.getTreeNext();
        }

        return commaCount;
    }

    /**
     * Check if the element type is whitespace.
     */
    private boolean isWhitespace(IElementType type) {
        return KitePsiUtil.isWhitespace(type);
    }

    /**
     * Check if an element is whitespace.
     */
    private boolean isWhitespaceElement(PsiElement element) {
        return KitePsiUtil.isWhitespaceElement(element);
    }

}
