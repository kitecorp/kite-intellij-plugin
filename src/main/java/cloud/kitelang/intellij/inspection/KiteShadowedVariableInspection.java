package cloud.kitelang.intellij.inspection;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteFile;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.util.KiteDeclarationHelper;
import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Inspection that detects shadowed variables.
 * A variable is shadowed when an inner scope declares a variable with the same
 * name as a variable in an outer scope, potentially leading to confusion.
 */
public class KiteShadowedVariableInspection extends KiteInspectionBase {

    @Override
    public @NotNull String getShortName() {
        return "KiteShadowedVariable";
    }

    @Override
    protected void checkKiteFile(@NotNull KiteFile file,
                                  @NotNull InspectionManager manager,
                                  boolean isOnTheFly,
                                  @NotNull List<ProblemDescriptor> problems) {
        // Collect top-level declarations
        var globalScope = new HashSet<String>();
        collectTopLevelDeclarations(file, globalScope);

        // Check for shadowing in nested scopes
        checkForShadowing(file, globalScope, manager, isOnTheFly, problems);
    }

    /**
     * Collect all top-level variable declarations (VARIABLE_DECLARATION elements).
     */
    private void collectTopLevelDeclarations(PsiElement file, Set<String> declarations) {
        var child = file.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                if (type == KiteElementTypes.VARIABLE_DECLARATION) {
                    var nameElement = KiteDeclarationHelper.findNameElementInDeclaration(child, type);
                    if (nameElement != null) {
                        declarations.add(nameElement.getText());
                    }
                }
            }
            child = child.getNextSibling();
        }
    }

    private void checkForShadowing(PsiElement element,
                                    Set<String> outerScope,
                                    InspectionManager manager,
                                    boolean isOnTheFly,
                                    List<ProblemDescriptor> problems) {
        if (element == null || element.getNode() == null) return;

        var type = element.getNode().getElementType();

        // Check component declarations for shadowed inputs
        if (type == KiteElementTypes.COMPONENT_DECLARATION) {
            checkComponentForShadowing(element, outerScope, manager, isOnTheFly, problems);
            return;
        }

        // Check function declarations for shadowed parameters/locals
        if (type == KiteElementTypes.FUNCTION_DECLARATION) {
            checkFunctionForShadowing(element, outerScope, manager, isOnTheFly, problems);
            return;
        }

        // Recurse into children
        var child = element.getFirstChild();
        while (child != null) {
            checkForShadowing(child, outerScope, manager, isOnTheFly, problems);
            child = child.getNextSibling();
        }
    }

    /**
     * Check component body for variables that shadow outer scope.
     */
    private void checkComponentForShadowing(PsiElement componentDecl,
                                             Set<String> outerScope,
                                             InspectionManager manager,
                                             boolean isOnTheFly,
                                             List<ProblemDescriptor> problems) {
        // Recursively check all declarations inside the component's children
        var child = componentDecl.getFirstChild();
        while (child != null) {
            checkDeclarationsRecursive(child, outerScope, manager, isOnTheFly, problems, true);
            child = child.getNextSibling();
        }
    }

    /**
     * Check function body for parameters/variables that shadow outer scope.
     */
    private void checkFunctionForShadowing(PsiElement functionDecl,
                                            Set<String> outerScope,
                                            InspectionManager manager,
                                            boolean isOnTheFly,
                                            List<ProblemDescriptor> problems) {
        // Check parameters first
        checkFunctionParameters(functionDecl, outerScope, manager, isOnTheFly, problems);

        // Then check local variables in the function's children
        var child = functionDecl.getFirstChild();
        while (child != null) {
            checkDeclarationsRecursive(child, outerScope, manager, isOnTheFly, problems, false);
            child = child.getNextSibling();
        }
    }

    /**
     * Check function parameters for shadowing.
     */
    private void checkFunctionParameters(PsiElement functionDecl,
                                          Set<String> outerScope,
                                          InspectionManager manager,
                                          boolean isOnTheFly,
                                          List<ProblemDescriptor> problems) {
        boolean insideParams = false;
        String prevIdentifier = null;
        PsiElement prevElement = null;

        var child = functionDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                if (type == KiteTokenTypes.LPAREN) {
                    insideParams = true;
                } else if (type == KiteTokenTypes.RPAREN) {
                    // Check last parameter
                    if (insideParams && prevIdentifier != null && prevElement != null) {
                        if (outerScope.contains(prevIdentifier)) {
                            var problem = createWeakWarning(
                                    manager,
                                    prevElement,
                                    "Parameter '" + prevIdentifier + "' shadows a variable in outer scope",
                                    isOnTheFly
                            );
                            problems.add(problem);
                        }
                    }
                    break;
                } else if (type == KiteTokenTypes.LBRACE) {
                    break;
                } else if (insideParams) {
                    // Skip whitespace
                    if (KitePsiUtil.isWhitespace(type)) {
                        child = child.getNextSibling();
                        continue;
                    }

                    // Comma separates parameters
                    if (type == KiteTokenTypes.COMMA) {
                        // Previous identifier was the parameter name
                        if (prevIdentifier != null && prevElement != null) {
                            if (outerScope.contains(prevIdentifier)) {
                                var problem = createWeakWarning(
                                        manager,
                                        prevElement,
                                        "Parameter '" + prevIdentifier + "' shadows a variable in outer scope",
                                        isOnTheFly
                                );
                                problems.add(problem);
                            }
                        }
                        prevIdentifier = null;
                        prevElement = null;
                    } else if (type == KiteTokenTypes.IDENTIFIER || type == KiteTokenTypes.ANY) {
                        // Track this identifier (could be type or name)
                        prevIdentifier = child.getText();
                        prevElement = child;
                    }
                }
            }
            child = child.getNextSibling();
        }
    }

    /**
     * Recursively check for shadowed declarations.
     */
    private void checkDeclarationsRecursive(PsiElement element,
                                             Set<String> outerScope,
                                             InspectionManager manager,
                                             boolean isOnTheFly,
                                             List<ProblemDescriptor> problems,
                                             boolean checkInputs) {
        if (element == null || element.getNode() == null) return;

        var type = element.getNode().getElementType();

        // Check variable declarations
        if (type == KiteElementTypes.VARIABLE_DECLARATION) {
            var nameElement = KiteDeclarationHelper.findNameElementInDeclaration(element, type);
            if (nameElement != null) {
                var varName = nameElement.getText();
                if (outerScope.contains(varName)) {
                    var problem = createWeakWarning(
                            manager,
                            nameElement,
                            "Variable '" + varName + "' shadows a variable in outer scope",
                            isOnTheFly
                    );
                    problems.add(problem);
                }
            }
        }

        // Check input declarations in components
        if (checkInputs && type == KiteElementTypes.INPUT_DECLARATION) {
            var nameElement = KiteDeclarationHelper.findNameElementInDeclaration(element, type);
            if (nameElement != null) {
                var inputName = nameElement.getText();
                if (outerScope.contains(inputName)) {
                    var problem = createWeakWarning(
                            manager,
                            nameElement,
                            "Input '" + inputName + "' shadows a variable in outer scope",
                            isOnTheFly
                    );
                    problems.add(problem);
                }
            }
        }

        // Skip nested functions and components - they have their own scope check
        if (type == KiteElementTypes.FUNCTION_DECLARATION || type == KiteElementTypes.COMPONENT_DECLARATION) {
            return;
        }

        // Recurse into children
        var child = element.getFirstChild();
        while (child != null) {
            checkDeclarationsRecursive(child, outerScope, manager, isOnTheFly, problems, checkInputs);
            child = child.getNextSibling();
        }
    }
}
