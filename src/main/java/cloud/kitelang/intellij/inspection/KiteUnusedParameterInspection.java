package cloud.kitelang.intellij.inspection;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteFile;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Inspection that detects unused function parameters.
 * A parameter is unused if it's declared but never referenced in the function body.
 */
public class KiteUnusedParameterInspection extends KiteInspectionBase {

    @Override
    public @NotNull String getShortName() {
        return "KiteUnusedParameter";
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
            checkFunctionParameters(element, manager, isOnTheFly, problems);
        }

        // Recurse into children
        var child = element.getFirstChild();
        while (child != null) {
            checkFunctionsRecursive(child, manager, isOnTheFly, problems);
            child = child.getNextSibling();
        }
    }

    private void checkFunctionParameters(PsiElement functionDecl,
                                          InspectionManager manager,
                                          boolean isOnTheFly,
                                          List<ProblemDescriptor> problems) {
        // Collect all parameters with their elements
        var parameters = new LinkedHashMap<String, PsiElement>();
        collectParameters(functionDecl, parameters);

        if (parameters.isEmpty()) return;

        // Find all usages in the function body
        var usedParams = new HashSet<String>();
        collectUsages(functionDecl, parameters.keySet(), usedParams);

        // Report unused parameters
        for (var entry : parameters.entrySet()) {
            if (!usedParams.contains(entry.getKey())) {
                var problem = createWeakWarning(
                        manager,
                        entry.getValue(),
                        "Unused parameter '" + entry.getKey() + "'",
                        isOnTheFly
                );
                problems.add(problem);
            }
        }
    }

    /**
     * Collect function parameters: pattern is type name, type name, ...
     */
    private void collectParameters(PsiElement functionDecl, Map<String, PsiElement> parameters) {
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
                    // Save last parameter
                    if (insideParams && prevIdentifier != null && prevElement != null) {
                        parameters.put(prevIdentifier, prevElement);
                    }
                    break;
                } else if (type == KiteTokenTypes.LBRACE) {
                    break;
                } else if (insideParams) {
                    if (KitePsiUtil.isWhitespace(type)) {
                        child = child.getNextSibling();
                        continue;
                    }

                    if (type == KiteTokenTypes.COMMA) {
                        // Previous identifier was the parameter name
                        if (prevIdentifier != null && prevElement != null) {
                            parameters.put(prevIdentifier, prevElement);
                        }
                        prevIdentifier = null;
                        prevElement = null;
                    } else if (type == KiteTokenTypes.IDENTIFIER || type == KiteTokenTypes.ANY) {
                        // Track identifiers - last one before comma/rparen is param name
                        prevIdentifier = child.getText();
                        prevElement = child;
                    }
                }
            }
            child = child.getNextSibling();
        }
    }

    /**
     * Collect usages of parameter names in the function body.
     */
    private void collectUsages(PsiElement functionDecl, Set<String> paramNames, Set<String> usedParams) {
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
                    collectUsagesRecursive(child, paramNames, usedParams);
                }
            }
            child = child.getNextSibling();
        }
    }

    private void collectUsagesRecursive(PsiElement element, Set<String> paramNames, Set<String> usedParams) {
        if (element == null || element.getNode() == null) return;

        var type = element.getNode().getElementType();

        // Check identifier usages
        if (type == KiteTokenTypes.IDENTIFIER) {
            var text = element.getText();
            if (paramNames.contains(text)) {
                // Check if this is a usage (not a declaration)
                if (isUsage(element)) {
                    usedParams.add(text);
                }
            }
        }

        // Check string interpolations
        if (type == KiteTokenTypes.INTERP_SIMPLE || type == KiteTokenTypes.INTERP_IDENTIFIER) {
            var text = element.getText();
            // Remove $ prefix for INTERP_SIMPLE
            if (type == KiteTokenTypes.INTERP_SIMPLE && text.startsWith("$")) {
                text = text.substring(1);
            }
            if (paramNames.contains(text)) {
                usedParams.add(text);
            }
        }

        // Recurse
        var child = element.getFirstChild();
        while (child != null) {
            collectUsagesRecursive(child, paramNames, usedParams);
            child = child.getNextSibling();
        }
    }

    /**
     * Check if an identifier is a usage (not a declaration like var x = ...).
     */
    private boolean isUsage(PsiElement identifier) {
        var prev = KitePsiUtil.skipWhitespaceBackward(identifier.getPrevSibling());
        if (prev != null && prev.getNode() != null) {
            var prevType = prev.getNode().getElementType();
            // If preceded by var/input/output, this might be a declaration
            if (prevType == KiteTokenTypes.VAR ||
                prevType == KiteTokenTypes.INPUT ||
                prevType == KiteTokenTypes.OUTPUT) {
                return false;
            }
            // If preceded by a type identifier (for typed var), also skip
            if (prevType == KiteTokenTypes.IDENTIFIER || prevType == KiteTokenTypes.ANY) {
                var prevPrev = KitePsiUtil.skipWhitespaceBackward(prev.getPrevSibling());
                if (prevPrev != null && prevPrev.getNode() != null) {
                    var prevPrevType = prevPrev.getNode().getElementType();
                    return prevPrevType != KiteTokenTypes.VAR &&
                           prevPrevType != KiteTokenTypes.INPUT &&
                           prevPrevType != KiteTokenTypes.OUTPUT;
                }
            }
        }
        return true;
    }
}
