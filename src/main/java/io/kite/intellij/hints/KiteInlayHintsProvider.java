package io.kite.intellij.hints;

import com.intellij.codeInsight.hints.*;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import io.kite.intellij.KiteLanguage;
import io.kite.intellij.psi.KiteElementTypes;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides inlay hints for the Kite language.
 * <p>
 * Features:
 * - Type hints for variables that have inferred types (e.g., var x = "hello" shows ": string")
 * - Parameter name hints in function calls (e.g., greet("Alice", 30) shows "name:" and "age:")
 */
public class KiteInlayHintsProvider implements InlayHintsProvider<KiteInlayHintsProvider.Settings> {

    private static final SettingsKey<Settings> KEY = new SettingsKey<>("kite.inlay.hints");

    @Override
    public boolean isVisibleInSettings() {
        return true;
    }

    @NotNull
    @Override
    public SettingsKey<Settings> getKey() {
        return KEY;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
        return "Kite";
    }

    @Nullable
    @Override
    public String getPreviewText() {
        return """
                // Type hints show inferred types for variables
                var message = "Hello, Kite!"
                var count = 42
                var enabled = true
                
                // Parameter hints show names in function calls
                fun greet(name string, age number) {
                    console.log(name, age)
                }
                
                greet("Alice", 30)
                """;
    }

    @NotNull
    @Override
    public ImmediateConfigurable createConfigurable(@NotNull Settings settings) {
        return new ImmediateConfigurable() {
            @NotNull
            @Override
            public JComponent createComponent(@NotNull ChangeListener listener) {
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

                JCheckBox typeHintsCheckBox = new JCheckBox("Show type hints for variables", settings.showTypeHints);
                typeHintsCheckBox.addActionListener(e -> {
                    settings.showTypeHints = typeHintsCheckBox.isSelected();
                    listener.settingsChanged();
                });
                panel.add(typeHintsCheckBox);

                JCheckBox parameterHintsCheckBox = new JCheckBox("Show parameter name hints", settings.showParameterHints);
                parameterHintsCheckBox.addActionListener(e -> {
                    settings.showParameterHints = parameterHintsCheckBox.isSelected();
                    listener.settingsChanged();
                });
                panel.add(parameterHintsCheckBox);

                return panel;
            }
        };
    }

    @NotNull
    @Override
    public Settings createSettings() {
        return new Settings();
    }

    @Override
    public boolean isLanguageSupported(@NotNull Language language) {
        return language.is(KiteLanguage.INSTANCE);
    }

    @Nullable
    @Override
    public InlayHintsCollector getCollectorFor(@NotNull PsiFile file,
                                               @NotNull Editor editor,
                                               @NotNull Settings settings,
                                               @NotNull InlayHintsSink sink) {
        return new KiteInlayHintsCollector(editor, settings);
    }

    /**
     * Settings for Kite inlay hints.
     */
    public static class Settings {
        public boolean showTypeHints = true;
        public boolean showParameterHints = true;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Settings settings = (Settings) o;
            return showTypeHints == settings.showTypeHints &&
                   showParameterHints == settings.showParameterHints;
        }

        @Override
        public int hashCode() {
            return 31 * Boolean.hashCode(showTypeHints) + Boolean.hashCode(showParameterHints);
        }
    }

    /**
     * Collector that traverses PSI and creates inlay hints.
     */
    private static class KiteInlayHintsCollector implements InlayHintsCollector {
        private final Editor editor;
        private final Settings settings;

        KiteInlayHintsCollector(Editor editor, Settings settings) {
            this.editor = editor;
            this.settings = settings;
        }

        @Override
        public boolean collect(@NotNull PsiElement element, @NotNull Editor editor, @NotNull InlayHintsSink sink) {
            ASTNode node = element.getNode();
            if (node == null) return true;

            IElementType elementType = node.getElementType();

            // Show type hints for VARIABLE declarations without explicit type
            if (settings.showTypeHints && elementType == KiteElementTypes.VARIABLE_DECLARATION) {
                collectTypeHints(element, sink);
            }

            // Show parameter hints for function calls
            if (settings.showParameterHints) {
                // Function calls are identifiers followed by parentheses with arguments
                if (elementType == KiteTokenTypes.IDENTIFIER) {
                    collectParameterHints(element, sink);
                }
            }

            return true;
        }

        /**
         * Collect type hints for VAR declarations.
         * Shows inferred type when the type is not explicitly specified.
         * <p>
         * Example: var x = "hello"  ->  var x: string = "hello"
         */
        private void collectTypeHints(PsiElement varDeclaration, InlayHintsSink sink) {
            ASTNode node = varDeclaration.getNode();
            if (node == null) return;

            System.err.println("[InlayHints] collectTypeHints called for: " + node.getText().replace("\n", "\\n"));

            // Check if type is already explicitly specified
            // VAR_DECLARATION structure: VAR <type>? <name> = <value>
            ASTNode[] children = node.getChildren(null);
            System.err.println("[InlayHints] Found " + children.length + " children");

            boolean hasExplicitType = false;
            ASTNode nameNode = null;
            ASTNode valueNode = null;
            ASTNode assignNode = null;  // Track the = sign position
            boolean foundEquals = false;

            for (ASTNode child : children) {
                IElementType childType = child.getElementType();
                System.err.println("[InlayHints]   Child: " + childType + " = '" + child.getText().replace("\n", "\\n") + "'");

                // Skip whitespace and newlines (both Kite tokens and IntelliJ's built-in whitespace)
                if (childType == KiteTokenTypes.WHITESPACE ||
                    childType == KiteTokenTypes.NL ||
                    childType == KiteTokenTypes.NEWLINE ||
                    childType == TokenType.WHITE_SPACE) {
                    continue;
                }

                // Skip the VAR keyword
                if (childType == KiteTokenTypes.VAR) {
                    continue;
                }

                // Skip decorators (identified by @ symbol)
                if (childType == KiteTokenTypes.AT) {
                    // Skip the decorator and its content
                    continue;
                }

                if (childType == KiteTokenTypes.ASSIGN) {
                    assignNode = child;  // Remember the = position
                    foundEquals = true;
                    continue;
                }

                if (!foundEquals) {
                    // Before equals: could be type or name
                    if (childType == KiteTokenTypes.IDENTIFIER) {
                        if (nameNode == null) {
                            // First identifier - could be type or name
                            nameNode = child;
                        } else {
                            // Second identifier - first was type, this is name
                            hasExplicitType = true;
                            nameNode = child;
                        }
                    }
                    // Check for built-in type keywords (object, any) or type identifiers
                    else if (isTypeKeyword(childType) ||
                             (childType == KiteTokenTypes.IDENTIFIER && isBuiltInTypeName(child.getText()))) {
                        hasExplicitType = true;
                    }
                } else {
                    // After equals: this is the value
                    valueNode = child;
                    break;
                }
            }

            // Only show hint if no explicit type and we have a name and value
            if (!hasExplicitType && nameNode != null && valueNode != null) {
                String inferredType = inferType(valueNode);
                if (inferredType != null) {
                    // Place hint right after the variable name
                    int offset = nameNode.getTextRange().getEndOffset();
                    PresentationFactory factory = new PresentationFactory(editor);
                    // Use roundWithBackground for proper vertical baseline alignment
                    InlayPresentation text = factory.smallText(":" + inferredType);
                    InlayPresentation presentation = factory.roundWithBackground(text);
                    sink.addInlineElement(offset, true, presentation, false);
                }
            }
        }

        /**
         * Check if the element type is a type keyword (object, any) or an identifier that
         * represents a type. In Kite, built-in types like string, number, boolean are
         * just identifiers in type position.
         */
        private boolean isTypeKeyword(IElementType type) {
            // Only OBJECT and ANY are actual type keywords in KiteTokenTypes
            // string, number, boolean are regular identifiers when used as types
            return type == KiteTokenTypes.OBJECT ||
                   type == KiteTokenTypes.ANY;
        }

        /**
         * Check if an identifier text represents a built-in type name.
         */
        private boolean isBuiltInTypeName(String text) {
            return "string".equals(text) ||
                   "number".equals(text) ||
                   "boolean".equals(text) ||
                   "object".equals(text) ||
                   "any".equals(text) ||
                   "void".equals(text);
        }

        /**
         * Infer the type from a value node.
         */
        private String inferType(ASTNode valueNode) {
            if (valueNode == null) return null;

            IElementType valueType = valueNode.getElementType();

            // String literal - can be STRING token or DQUOTE (opening quote of interpolated string)
            // Also check for SINGLE_STRING which is single-quoted strings
            if (valueType == KiteTokenTypes.STRING ||
                valueType == KiteTokenTypes.DQUOTE ||
                valueType == KiteTokenTypes.SINGLE_STRING) {
                return "string";
            }

            // Number literal
            if (valueType == KiteTokenTypes.NUMBER) {
                return "number";
            }

            // Boolean literals
            if (valueType == KiteTokenTypes.TRUE || valueType == KiteTokenTypes.FALSE) {
                return "boolean";
            }

            // Null literal
            if (valueType == KiteTokenTypes.NULL) {
                return "null";
            }

            // Object literal
            if (valueType == KiteElementTypes.OBJECT_LITERAL) {
                return "object";
            }

            // Array literal
            if (valueType == KiteElementTypes.ARRAY_LITERAL) {
                return "array";
            }

            // For other expressions (identifiers, function calls, etc.),
            // we can't easily infer the type without more sophisticated analysis
            return null;
        }

        /**
         * Collect parameter name hints for function calls.
         * Shows parameter names before arguments in function calls.
         * <p>
         * Example: greet("Alice", 30) shows "name:" before "Alice" and "age:" before 30
         */
        private void collectParameterHints(PsiElement identifier, InlayHintsSink sink) {
            // Check if this identifier is followed by parentheses (function call)
            ASTNode identifierNode = identifier.getNode();
            if (identifierNode == null) return;

            ASTNode sibling = identifierNode.getTreeNext();

            // Skip whitespace
            while (sibling != null &&
                   (sibling.getElementType() == KiteTokenTypes.WHITESPACE ||
                    sibling.getElementType() == KiteTokenTypes.NL)) {
                sibling = sibling.getTreeNext();
            }

            // Check if followed by LPAREN
            if (sibling == null || sibling.getElementType() != KiteTokenTypes.LPAREN) {
                return;
            }

            // This is a function call - find the function declaration to get parameter names
            String functionName = identifierNode.getText();
            PsiFile file = identifier.getContainingFile();
            if (file == null) return;

            // Find function declaration with this name
            List<String> parameterNames = findFunctionParameters(file, functionName);
            if (parameterNames.isEmpty()) return;

            // Collect arguments
            List<ASTNode> arguments = collectArguments(sibling);

            // Add hints for each argument
            PresentationFactory factory = new PresentationFactory(editor);
            for (int i = 0; i < Math.min(arguments.size(), parameterNames.size()); i++) {
                ASTNode arg = arguments.get(i);
                String paramName = parameterNames.get(i);

                // Only show hint if the argument is not already a named argument
                if (!isNamedArgument(arg)) {
                    int offset = arg.getTextRange().getStartOffset();
                    InlayPresentation presentation = factory.smallText(paramName + ": ");
                    sink.addInlineElement(offset, true, presentation, false);
                }
            }
        }

        /**
         * Find parameter names for a function by searching for its declaration.
         */
        private List<String> findFunctionParameters(PsiFile file, String functionName) {
            List<String> params = new ArrayList<>();

            // Search the file for function declarations
            searchForFunctionDeclaration(file.getNode(), functionName, params);

            return params;
        }

        /**
         * Recursively search for a function declaration and extract its parameter names.
         */
        private void searchForFunctionDeclaration(ASTNode node, String functionName, List<String> params) {
            if (node == null) return;

            if (node.getElementType() == KiteElementTypes.FUNCTION_DECLARATION) {
                // Check if this is the function we're looking for
                ASTNode[] children = node.getChildren(null);
                boolean foundFun = false;
                boolean foundName = false;
                boolean inParams = false;

                for (ASTNode child : children) {
                    IElementType childType = child.getElementType();

                    if (childType == KiteTokenTypes.FUN) {
                        foundFun = true;
                        continue;
                    }

                    if (foundFun && !foundName && childType == KiteTokenTypes.IDENTIFIER) {
                        if (child.getText().equals(functionName)) {
                            foundName = true;
                        } else {
                            return; // Not the function we're looking for
                        }
                        continue;
                    }

                    if (foundName && childType == KiteTokenTypes.LPAREN) {
                        inParams = true;
                        continue;
                    }

                    if (inParams && childType == KiteTokenTypes.RPAREN) {
                        return; // Done collecting parameters
                    }

                    if (inParams && childType == KiteTokenTypes.IDENTIFIER) {
                        // This could be a parameter name or type
                        // In Kite: fun greet(name string, age number)
                        // Parameter names come before type identifiers
                        ASTNode next = child.getTreeNext();
                        while (next != null &&
                               (next.getElementType() == KiteTokenTypes.WHITESPACE ||
                                next.getElementType() == KiteTokenTypes.NL)) {
                            next = next.getTreeNext();
                        }

                        // If next is an identifier or type keyword, current is the param name
                        if (next != null &&
                            (next.getElementType() == KiteTokenTypes.IDENTIFIER ||
                             isTypeKeyword(next.getElementType()))) {
                            params.add(child.getText());
                        }
                    }
                }
                return;
            }

            // Recurse into children
            for (ASTNode child : node.getChildren(null)) {
                searchForFunctionDeclaration(child, functionName, params);
                if (!params.isEmpty()) return; // Found it
            }
        }

        /**
         * Collect argument nodes from a function call.
         */
        private List<ASTNode> collectArguments(ASTNode lparenNode) {
            List<ASTNode> arguments = new ArrayList<>();

            ASTNode current = lparenNode.getTreeNext();
            while (current != null && current.getElementType() != KiteTokenTypes.RPAREN) {
                IElementType type = current.getElementType();

                // Skip whitespace, newlines, and commas
                if (type != KiteTokenTypes.WHITESPACE &&
                    type != KiteTokenTypes.NL &&
                    type != KiteTokenTypes.NEWLINE &&
                    type != KiteTokenTypes.COMMA) {
                    arguments.add(current);
                }

                current = current.getTreeNext();
            }

            return arguments;
        }

        /**
         * Check if an argument is already a named argument (name: value or name = value).
         */
        private boolean isNamedArgument(ASTNode arg) {
            // Look for colon or equals after an identifier
            if (arg.getElementType() != KiteTokenTypes.IDENTIFIER) {
                return false;
            }

            ASTNode next = arg.getTreeNext();
            while (next != null &&
                   (next.getElementType() == KiteTokenTypes.WHITESPACE ||
                    next.getElementType() == KiteTokenTypes.NL)) {
                next = next.getTreeNext();
            }

            return next != null &&
                   (next.getElementType() == KiteTokenTypes.COLON ||
                    next.getElementType() == KiteTokenTypes.ASSIGN);
        }
    }
}
