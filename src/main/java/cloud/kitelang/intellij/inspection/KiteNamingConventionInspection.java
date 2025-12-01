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

import java.util.List;
import java.util.regex.Pattern;

/**
 * Inspection that checks naming conventions.
 * - Variables, functions, inputs, outputs: camelCase (first letter lowercase)
 * - Schemas, Components, Types: PascalCase (first letter uppercase)
 * - Resources: camelCase for instance name
 */
public class KiteNamingConventionInspection extends KiteInspectionBase {

    // Pattern for camelCase: starts with lowercase, then any word characters
    private static final Pattern CAMEL_CASE = Pattern.compile("^[a-z][a-zA-Z0-9]*$");

    // Pattern for PascalCase: starts with uppercase, then any word characters
    private static final Pattern PASCAL_CASE = Pattern.compile("^[A-Z][a-zA-Z0-9]*$");

    @Override
    public @NotNull String getShortName() {
        return "KiteNamingConvention";
    }

    @Override
    protected void checkKiteFile(@NotNull KiteFile file,
                                  @NotNull InspectionManager manager,
                                  boolean isOnTheFly,
                                  @NotNull List<ProblemDescriptor> problems) {
        checkNamingRecursive(file, manager, isOnTheFly, problems);
    }

    private void checkNamingRecursive(PsiElement element,
                                       InspectionManager manager,
                                       boolean isOnTheFly,
                                       List<ProblemDescriptor> problems) {
        if (element == null || element.getNode() == null) return;

        var type = element.getNode().getElementType();

        // Check variable declarations (should be camelCase)
        if (type == KiteElementTypes.VARIABLE_DECLARATION) {
            checkCamelCase(element, type, "Variable", manager, isOnTheFly, problems);
        }

        // Check function declarations (should be camelCase)
        if (type == KiteElementTypes.FUNCTION_DECLARATION) {
            checkFunctionName(element, manager, isOnTheFly, problems);
        }

        // Check schema declarations (should be PascalCase)
        if (type == KiteElementTypes.SCHEMA_DECLARATION) {
            checkPascalCase(element, type, "Schema", manager, isOnTheFly, problems);
        }

        // Check component declarations
        if (type == KiteElementTypes.COMPONENT_DECLARATION) {
            checkComponentName(element, manager, isOnTheFly, problems);
        }

        // Check resource declarations (instance name should be camelCase)
        if (type == KiteElementTypes.RESOURCE_DECLARATION) {
            checkResourceName(element, manager, isOnTheFly, problems);
        }

        // Check input/output declarations (should be camelCase)
        if (type == KiteElementTypes.INPUT_DECLARATION) {
            checkCamelCase(element, type, "Input", manager, isOnTheFly, problems);
        }
        if (type == KiteElementTypes.OUTPUT_DECLARATION) {
            checkCamelCase(element, type, "Output", manager, isOnTheFly, problems);
        }

        // Check type declarations (should be PascalCase)
        if (type == KiteElementTypes.TYPE_DECLARATION) {
            checkPascalCase(element, type, "Type", manager, isOnTheFly, problems);
        }

        // Recurse into children
        var child = element.getFirstChild();
        while (child != null) {
            checkNamingRecursive(child, manager, isOnTheFly, problems);
            child = child.getNextSibling();
        }
    }

    private void checkCamelCase(PsiElement declaration,
                                 IElementType type,
                                 String kind,
                                 InspectionManager manager,
                                 boolean isOnTheFly,
                                 List<ProblemDescriptor> problems) {
        var nameElement = KiteDeclarationHelper.findNameElementInDeclaration(declaration, type);
        if (nameElement == null) return;

        var name = nameElement.getText();
        if (!CAMEL_CASE.matcher(name).matches()) {
            var problem = createWeakWarning(
                    manager,
                    nameElement,
                    kind + " '" + name + "' should use camelCase (start with lowercase)",
                    isOnTheFly
            );
            problems.add(problem);
        }
    }

    private void checkPascalCase(PsiElement declaration,
                                  IElementType type,
                                  String kind,
                                  InspectionManager manager,
                                  boolean isOnTheFly,
                                  List<ProblemDescriptor> problems) {
        var nameElement = KiteDeclarationHelper.findNameElementInDeclaration(declaration, type);
        if (nameElement == null) return;

        var name = nameElement.getText();
        if (!PASCAL_CASE.matcher(name).matches()) {
            var problem = createWeakWarning(
                    manager,
                    nameElement,
                    kind + " '" + name + "' should use PascalCase (start with uppercase)",
                    isOnTheFly
            );
            problems.add(problem);
        }
    }

    private void checkFunctionName(PsiElement functionDecl,
                                    InspectionManager manager,
                                    boolean isOnTheFly,
                                    List<ProblemDescriptor> problems) {
        // Function name is the first identifier after 'fun' keyword
        boolean foundFun = false;
        var child = functionDecl.getFirstChild();

        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                if (type == KiteTokenTypes.FUN) {
                    foundFun = true;
                } else if (foundFun && type == KiteTokenTypes.IDENTIFIER) {
                    var name = child.getText();
                    if (!CAMEL_CASE.matcher(name).matches()) {
                        var problem = createWeakWarning(
                                manager,
                                child,
                                "Function '" + name + "' should use camelCase (start with lowercase)",
                                isOnTheFly
                        );
                        problems.add(problem);
                    }
                    return;
                } else if (type == KiteTokenTypes.LPAREN) {
                    break;
                }
            }
            child = child.getNextSibling();
        }
    }

    private void checkComponentName(PsiElement componentDecl,
                                     InspectionManager manager,
                                     boolean isOnTheFly,
                                     List<ProblemDescriptor> problems) {
        // Component can be definition (PascalCase) or instantiation (camelCase for instance name)
        // Pattern: component TypeName instanceName { ... } OR component TypeName { ... }
        boolean foundComponent = false;
        PsiElement firstIdent = null;
        PsiElement secondIdent = null;

        var child = componentDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                if (type == KiteTokenTypes.COMPONENT) {
                    foundComponent = true;
                } else if (foundComponent && type == KiteTokenTypes.IDENTIFIER) {
                    if (firstIdent == null) {
                        firstIdent = child;
                    } else {
                        secondIdent = child;
                    }
                } else if (type == KiteTokenTypes.LBRACE) {
                    break;
                }
            }
            child = child.getNextSibling();
        }

        if (firstIdent != null && secondIdent == null) {
            // Component definition: component TypeName { ... }
            var name = firstIdent.getText();
            if (!PASCAL_CASE.matcher(name).matches()) {
                var problem = createWeakWarning(
                        manager,
                        firstIdent,
                        "Component '" + name + "' should use PascalCase (start with uppercase)",
                        isOnTheFly
                );
                problems.add(problem);
            }
        } else if (firstIdent != null && secondIdent != null) {
            // Component instantiation: component TypeName instanceName { ... }
            // TypeName should be PascalCase, instanceName should be camelCase
            var typeName = firstIdent.getText();
            var instanceName = secondIdent.getText();

            if (!PASCAL_CASE.matcher(typeName).matches()) {
                var problem = createWeakWarning(
                        manager,
                        firstIdent,
                        "Component type '" + typeName + "' should use PascalCase",
                        isOnTheFly
                );
                problems.add(problem);
            }

            if (!CAMEL_CASE.matcher(instanceName).matches()) {
                var problem = createWeakWarning(
                        manager,
                        secondIdent,
                        "Component instance '" + instanceName + "' should use camelCase",
                        isOnTheFly
                );
                problems.add(problem);
            }
        }
    }

    private void checkResourceName(PsiElement resourceDecl,
                                    InspectionManager manager,
                                    boolean isOnTheFly,
                                    List<ProblemDescriptor> problems) {
        // Pattern: resource TypeName instanceName { ... }
        boolean foundResource = false;
        PsiElement firstIdent = null;
        PsiElement secondIdent = null;

        var child = resourceDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                if (type == KiteTokenTypes.RESOURCE) {
                    foundResource = true;
                } else if (foundResource && type == KiteTokenTypes.IDENTIFIER) {
                    if (firstIdent == null) {
                        firstIdent = child;
                    } else {
                        secondIdent = child;
                    }
                } else if (type == KiteTokenTypes.LBRACE) {
                    break;
                }
            }
            child = child.getNextSibling();
        }

        if (firstIdent != null && secondIdent != null) {
            var instanceName = secondIdent.getText();

            if (!CAMEL_CASE.matcher(instanceName).matches()) {
                var problem = createWeakWarning(
                        manager,
                        secondIdent,
                        "Resource instance '" + instanceName + "' should use camelCase",
                        isOnTheFly
                );
                problems.add(problem);
            }
        }
    }
}
