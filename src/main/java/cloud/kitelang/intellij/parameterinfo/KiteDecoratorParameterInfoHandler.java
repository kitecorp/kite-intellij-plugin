package cloud.kitelang.intellij.parameterinfo;

import cloud.kitelang.intellij.psi.KiteTokenTypes;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parameter info handler for Kite decorator arguments.
 * Shows decorator parameter hints when pressing Ctrl+P inside decorator parentheses.
 * <p>
 * Example: When cursor is inside @minValue(|), pressing Ctrl+P shows:
 * "n: number (0 to 999999)" with the parameter highlighted.
 */
public class KiteDecoratorParameterInfoHandler implements ParameterInfoHandler<PsiElement, KiteDecoratorParameterInfoHandler.KiteDecoratorInfo> {

    /**
     * Represents decorator parameter information to display.
     */
    public static class KiteDecoratorInfo {
        private final String decoratorName;
        private final List<DecoratorParam> parameters;
        private final boolean hasNamedArgs;

        public KiteDecoratorInfo(String decoratorName, List<DecoratorParam> parameters, boolean hasNamedArgs) {
            this.decoratorName = decoratorName;
            this.parameters = parameters;
            this.hasNamedArgs = hasNamedArgs;
        }

        public String getDecoratorName() {
            return decoratorName;
        }

        public List<DecoratorParam> getParameters() {
            return parameters;
        }

        public boolean hasNamedArgs() {
            return hasNamedArgs;
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
                    sb.append(hasNamedArgs ? " or " : ", ");
                }
                DecoratorParam param = parameters.get(i);
                if (param.isNamed) {
                    sb.append(param.name).append(": ").append(param.type);
                } else {
                    sb.append(param.name).append(": ").append(param.type);
                }
            }
            return sb.toString();
        }
    }

    /**
     * Represents a single decorator parameter.
     */
    public static class DecoratorParam {
        public final String name;
        public final String type;
        public final boolean isNamed;
        public final int startOffset;
        public final int endOffset;

        public DecoratorParam(String name, String type, boolean isNamed) {
            this.name = name;
            this.type = type;
            this.isNamed = isNamed;
            this.startOffset = 0;
            this.endOffset = 0;
        }

        public DecoratorParam(String name, String type, boolean isNamed, int startOffset, int endOffset) {
            this.name = name;
            this.type = type;
            this.isNamed = isNamed;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        public String getDisplayText() {
            if (isNamed) {
                return name + ": " + type;
            }
            return name + ": " + type;
        }
    }

    // Map of decorator names to their parameter info
    private static final Map<String, KiteDecoratorInfo> DECORATOR_PARAMS = new HashMap<>();

    static {
        // Validation decorators
        DECORATOR_PARAMS.put("minValue", new KiteDecoratorInfo("minValue",
                List.of(new DecoratorParam("n", "number (0 to 999999)", false)), false));
        DECORATOR_PARAMS.put("maxValue", new KiteDecoratorInfo("maxValue",
                List.of(new DecoratorParam("n", "number (0 to 999999)", false)), false));
        DECORATOR_PARAMS.put("minLength", new KiteDecoratorInfo("minLength",
                List.of(new DecoratorParam("n", "number (0 to 999999)", false)), false));
        DECORATOR_PARAMS.put("maxLength", new KiteDecoratorInfo("maxLength",
                List.of(new DecoratorParam("n", "number (0 to 999999)", false)), false));

        // No-argument decorators - still register them but with empty params
        DECORATOR_PARAMS.put("nonEmpty", new KiteDecoratorInfo("nonEmpty", List.of(), false));
        DECORATOR_PARAMS.put("unique", new KiteDecoratorInfo("unique", List.of(), false));
        DECORATOR_PARAMS.put("sensitive", new KiteDecoratorInfo("sensitive", List.of(), false));
        DECORATOR_PARAMS.put("cloud", new KiteDecoratorInfo("cloud", List.of(), false));

        // Named argument decorators
        DECORATOR_PARAMS.put("validate", new KiteDecoratorInfo("validate",
                List.of(
                        new DecoratorParam("regex", "string", true),
                        new DecoratorParam("preset", "string", true)
                ), true));

        // Array/single value decorators
        DECORATOR_PARAMS.put("allowed", new KiteDecoratorInfo("allowed",
                List.of(new DecoratorParam("values", "array of literals (1 to 256 elements)", false)), false));
        DECORATOR_PARAMS.put("existing", new KiteDecoratorInfo("existing",
                List.of(new DecoratorParam("reference", "string (ARN, URL, ID, alias, tags)", false)), false));
        DECORATOR_PARAMS.put("dependsOn", new KiteDecoratorInfo("dependsOn",
                List.of(new DecoratorParam("resources", "resource/component reference or array", false)), false));
        DECORATOR_PARAMS.put("tags", new KiteDecoratorInfo("tags",
                List.of(new DecoratorParam("tags", "object, array of strings, or string", false)), false));
        DECORATOR_PARAMS.put("provider", new KiteDecoratorInfo("provider",
                List.of(new DecoratorParam("providers", "string or array of strings", false)), false));
        DECORATOR_PARAMS.put("provisionOn", new KiteDecoratorInfo("provisionOn",
                List.of(new DecoratorParam("providers", "array of strings", false)), false));
        DECORATOR_PARAMS.put("description", new KiteDecoratorInfo("description",
                List.of(new DecoratorParam("text", "string", false)), false));
        DECORATOR_PARAMS.put("count", new KiteDecoratorInfo("count",
                List.of(new DecoratorParam("n", "number", false)), false));
    }

    @Override
    public @Nullable PsiElement findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
        PsiFile file = context.getFile();
        int offset = context.getOffset();

        // Find element at the current position
        PsiElement element = file.findElementAt(offset);
        if (element == null) {
            return null;
        }

        // Walk backwards to find the decorator call
        PsiElement decoratorElement = findDecoratorElement(element, offset);
        if (decoratorElement == null) {
            return null;
        }

        // Get the decorator name
        String decoratorName = getDecoratorName(decoratorElement);
        if (decoratorName == null) {
            return null;
        }

        // Find the decorator info
        KiteDecoratorInfo decoratorInfo = DECORATOR_PARAMS.get(decoratorName);
        if (decoratorInfo == null || decoratorInfo.getParameters().isEmpty()) {
            return null; // Unknown decorator or no parameters
        }

        // Set the items to show in the popup
        context.setItemsToShow(new Object[]{decoratorInfo});

        return decoratorElement;
    }

    @Override
    public void showParameterInfo(@NotNull PsiElement element, @NotNull CreateParameterInfoContext context) {
        // Show the hint at the opening parenthesis
        ASTNode node = element.getNode();
        if (node == null) return;

        // The element is the decorator name identifier. Find LPAREN after it.
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

        // Find decorator element at the current position
        return findDecoratorElement(element, offset);
    }

    @Override
    public void updateParameterInfo(@NotNull PsiElement parameterOwner, @NotNull UpdateParameterInfoContext context) {
        if (context.getParameterOwner() == null || parameterOwner.equals(context.getParameterOwner())) {
            // Calculate current parameter index based on cursor position
            int currentParamIndex = getCurrentParameterIndex(parameterOwner, context.getOffset());
            context.setCurrentParameter(currentParamIndex);
        } else {
            // Different decorator call, hide the popup
            context.removeHint();
        }
    }

    @Override
    public void updateUI(KiteDecoratorInfo info, @NotNull ParameterInfoUIContext context) {
        if (info == null) {
            context.setUIComponentEnabled(false);
            return;
        }

        String text = info.getParametersText();
        int currentParameterIndex = context.getCurrentParameterIndex();

        // Calculate highlight range for the current parameter
        int highlightStart = -1;
        int highlightEnd = -1;

        if (!info.getParameters().isEmpty()) {
            List<DecoratorParam> params = info.getParameters();

            if (info.hasNamedArgs()) {
                // For named args (like @validate), highlight based on what's being typed
                // For now, highlight the first option
                if (currentParameterIndex >= 0 && currentParameterIndex < params.size()) {
                    int pos = 0;
                    for (int i = 0; i < currentParameterIndex; i++) {
                        pos += params.get(i).getDisplayText().length();
                        pos += 4; // " or "
                    }
                    DecoratorParam currentParam = params.get(currentParameterIndex);
                    highlightStart = pos;
                    highlightEnd = pos + currentParam.getDisplayText().length();
                }
            } else {
                // For positional args, just highlight the current position
                if (currentParameterIndex >= 0 && currentParameterIndex < params.size()) {
                    int pos = 0;
                    for (int i = 0; i < currentParameterIndex; i++) {
                        pos += params.get(i).getDisplayText().length();
                        pos += 2; // ", "
                    }
                    DecoratorParam currentParam = params.get(currentParameterIndex);
                    highlightStart = pos;
                    highlightEnd = pos + currentParam.getDisplayText().length();
                } else if (params.size() == 1) {
                    // Single parameter - always highlight it
                    highlightStart = 0;
                    highlightEnd = params.get(0).getDisplayText().length();
                }
            }
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
     * Find the decorator element (the identifier after @) at or before the given offset.
     */
    @Nullable
    private PsiElement findDecoratorElement(PsiElement element, int offset) {
        // Check if we're inside parentheses of a decorator call
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
                        // Check if this is preceded by @ (it's a decorator)
                        if (isDecoratorName(prev)) {
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
     * Check if the given identifier element is a decorator name (preceded by @).
     */
    private boolean isDecoratorName(PsiElement identifier) {
        if (identifier == null || identifier.getNode() == null) {
            return false;
        }
        if (identifier.getNode().getElementType() != KiteTokenTypes.IDENTIFIER) {
            return false;
        }
        // Check if preceded by @
        PsiElement prev = identifier.getPrevSibling();
        while (prev != null && isWhitespaceElement(prev)) {
            prev = prev.getPrevSibling();
        }
        return prev != null && prev.getNode() != null &&
               prev.getNode().getElementType() == KiteTokenTypes.AT;
    }

    /**
     * Get the decorator name from a decorator element.
     */
    @Nullable
    private String getDecoratorName(PsiElement decoratorElement) {
        if (decoratorElement.getNode() == null) return null;
        if (decoratorElement.getNode().getElementType() != KiteTokenTypes.IDENTIFIER) {
            return null;
        }
        return decoratorElement.getText();
    }

    /**
     * Calculate the current parameter index based on cursor position.
     * For decorators with named arguments, we detect which named arg is being typed.
     */
    private int getCurrentParameterIndex(PsiElement decoratorElement, int offset) {
        ASTNode node = decoratorElement.getNode();
        if (node == null) return 0;

        // Get the decorator info
        String decoratorName = getDecoratorName(decoratorElement);
        if (decoratorName == null) return 0;

        KiteDecoratorInfo info = DECORATOR_PARAMS.get(decoratorName);
        if (info == null) return 0;

        // Find LPAREN
        ASTNode lparen = node.getTreeNext();
        while (lparen != null && isWhitespace(lparen.getElementType())) {
            lparen = lparen.getTreeNext();
        }

        if (lparen == null || lparen.getElementType() != KiteTokenTypes.LPAREN) {
            return 0;
        }

        // For named argument decorators like @validate, try to detect which named arg
        if (info.hasNamedArgs()) {
            // Look for identifier: pattern before cursor
            String currentNamedArg = findCurrentNamedArg(lparen, offset);
            if (currentNamedArg != null) {
                List<DecoratorParam> params = info.getParameters();
                for (int i = 0; i < params.size(); i++) {
                    if (params.get(i).name.equals(currentNamedArg)) {
                        return i;
                    }
                }
            }
            return 0; // Default to first option
        }

        // For positional arguments, count commas before cursor
        int commaCount = 0;
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;
        ASTNode current = lparen.getTreeNext();

        while (current != null && current.getStartOffset() < offset) {
            IElementType type = current.getElementType();

            if (type == KiteTokenTypes.LPAREN) {
                parenDepth++;
            } else if (type == KiteTokenTypes.RPAREN) {
                if (parenDepth > 0) {
                    parenDepth--;
                } else {
                    // We've exited the decorator call
                    break;
                }
            } else if (type == KiteTokenTypes.LBRACK) {
                bracketDepth++;
            } else if (type == KiteTokenTypes.RBRACK) {
                bracketDepth--;
            } else if (type == KiteTokenTypes.LBRACE) {
                braceDepth++;
            } else if (type == KiteTokenTypes.RBRACE) {
                braceDepth--;
            } else if (type == KiteTokenTypes.COMMA && parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                commaCount++;
            }

            current = current.getTreeNext();
        }

        return commaCount;
    }

    /**
     * Find the named argument being typed (for decorators with named args like @validate).
     */
    @Nullable
    private String findCurrentNamedArg(ASTNode lparen, int offset) {
        ASTNode current = lparen.getTreeNext();
        String lastIdentifier = null;
        boolean foundColon = false;

        while (current != null && current.getStartOffset() < offset) {
            IElementType type = current.getElementType();

            if (type == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = current.getText();
                foundColon = false;
            } else if (type == KiteTokenTypes.COLON) {
                foundColon = true;
            } else if (type == KiteTokenTypes.COMMA) {
                // Reset for next arg
                lastIdentifier = null;
                foundColon = false;
            } else if (type == KiteTokenTypes.RPAREN) {
                break;
            }

            current = current.getTreeNext();
        }

        // If we found "identifier:" pattern, return that identifier
        if (lastIdentifier != null && foundColon) {
            return lastIdentifier;
        }

        return null;
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
