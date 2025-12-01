package cloud.kitelang.intellij.inspection;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteFile;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Inspection that detects empty blocks that may indicate incomplete code.
 * Reports empty schema, component, resource, and function bodies.
 */
public class KiteEmptyBlockInspection extends KiteInspectionBase {

    @Override
    public @NotNull String getShortName() {
        return "KiteEmptyBlock";
    }

    @Override
    protected void checkKiteFile(@NotNull KiteFile file,
                                  @NotNull InspectionManager manager,
                                  boolean isOnTheFly,
                                  @NotNull List<ProblemDescriptor> problems) {
        checkBlocksRecursive(file, manager, isOnTheFly, problems);
    }

    private void checkBlocksRecursive(PsiElement element,
                                       InspectionManager manager,
                                       boolean isOnTheFly,
                                       List<ProblemDescriptor> problems) {
        if (element == null || element.getNode() == null) return;

        var type = element.getNode().getElementType();

        // Check for empty blocks in various declarations
        if (type == KiteElementTypes.SCHEMA_DECLARATION) {
            checkEmptyBlock(element, "schema", manager, isOnTheFly, problems);
        } else if (type == KiteElementTypes.COMPONENT_DECLARATION) {
            checkEmptyBlock(element, "component", manager, isOnTheFly, problems);
        } else if (type == KiteElementTypes.RESOURCE_DECLARATION) {
            checkEmptyBlock(element, "resource", manager, isOnTheFly, problems);
        } else if (type == KiteElementTypes.FUNCTION_DECLARATION) {
            checkEmptyBlock(element, "function", manager, isOnTheFly, problems);
        }

        // Recurse into children
        var child = element.getFirstChild();
        while (child != null) {
            checkBlocksRecursive(child, manager, isOnTheFly, problems);
            child = child.getNextSibling();
        }
    }

    private void checkEmptyBlock(PsiElement declaration,
                                  String blockType,
                                  InspectionManager manager,
                                  boolean isOnTheFly,
                                  List<ProblemDescriptor> problems) {
        // Find the braces and check if there's any meaningful content between them
        PsiElement openBrace = null;
        PsiElement closeBrace = null;

        var child = declaration.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                var childType = child.getNode().getElementType();
                if (childType == KiteTokenTypes.LBRACE) {
                    openBrace = child;
                } else if (childType == KiteTokenTypes.RBRACE) {
                    closeBrace = child;
                }
            }
            child = child.getNextSibling();
        }

        // If we found braces, check if the block is empty
        if (openBrace != null && closeBrace != null) {
            if (isBlockEmpty(openBrace, closeBrace)) {
                var nameElement = findDeclarationName(declaration, blockType);
                var targetElement = nameElement != null ? nameElement : declaration;
                var problem = createWeakWarning(
                        manager,
                        targetElement,
                        "Empty " + blockType + " body",
                        isOnTheFly
                );
                problems.add(problem);
            }
        }
    }

    /**
     * Check if the content between braces is empty (only whitespace/newlines).
     */
    private boolean isBlockEmpty(PsiElement openBrace, PsiElement closeBrace) {
        var current = openBrace.getNextSibling();
        while (current != null && current != closeBrace) {
            if (current.getNode() != null) {
                var type = current.getNode().getElementType();
                // Skip whitespace
                if (!KitePsiUtil.isWhitespace(type)) {
                    // Found non-whitespace content
                    return false;
                }
            }
            current = current.getNextSibling();
        }
        return true;
    }

    /**
     * Find the name element of a declaration for better error highlighting.
     */
    @Nullable
    private PsiElement findDeclarationName(PsiElement declaration, String blockType) {
        IElementType keywordType = getKeywordType(blockType);
        if (keywordType == null) return null;

        boolean foundKeyword = false;
        var child = declaration.getFirstChild();

        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                if (type == keywordType) {
                    foundKeyword = true;
                } else if (foundKeyword && type == KiteTokenTypes.IDENTIFIER) {
                    return child; // First identifier after keyword is the name
                } else if (type == KiteTokenTypes.LBRACE) {
                    break;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }

    @Nullable
    private IElementType getKeywordType(String blockType) {
        return switch (blockType) {
            case "schema" -> KiteTokenTypes.SCHEMA;
            case "component" -> KiteTokenTypes.COMPONENT;
            case "resource" -> KiteTokenTypes.RESOURCE;
            case "function" -> KiteTokenTypes.FUN;
            default -> null;
        };
    }
}
