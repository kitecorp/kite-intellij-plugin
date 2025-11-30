package cloud.kitelang.intellij.navigation;

import cloud.kitelang.intellij.KiteFileType;
import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.TokenType;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler for "Go to Declaration" (Cmd+Click) in Kite files.
 * Uses direct PSI traversal to resolve identifiers to their declarations.
 *
 * For STRING tokens with interpolations:
 * - This handler provides navigation targets (finding the declaration)
 * - KiteReferenceContributor provides HighlightedReference for precise highlighting
 * - Together they enable Cmd+Click navigation with only the variable name underlined
 */
public class KiteGotoDeclarationHandler implements GotoDeclarationHandler {
    private static final Logger LOG = Logger.getInstance(KiteGotoDeclarationHandler.class);

    // Patterns for string interpolation
    private static final Pattern BRACE_INTERPOLATION_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final Pattern SIMPLE_INTERPOLATION_PATTERN = Pattern.compile("\\$([a-zA-Z_][a-zA-Z0-9_]*)");

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        if (sourceElement == null) {
            return null;
        }

        // Only handle Kite files
        PsiFile file = sourceElement.getContainingFile();
        if (file == null || file.getLanguage() != KiteLanguage.INSTANCE) {
            return null;
        }

        IElementType elementType = sourceElement.getNode().getElementType();
        LOG.info("[KiteGotoDecl] sourceElement type: " + elementType + ", text: " + sourceElement.getText().substring(0, Math.min(50, sourceElement.getText().length())));

        // Handle INTERP_IDENTIFIER tokens (the identifier inside ${...})
        // With the new lexer, each interpolated identifier is its own token
        if (elementType == KiteTokenTypes.INTERP_IDENTIFIER) {
            String varName = sourceElement.getText();
            LOG.info("[KiteGotoDecl] INTERP_IDENTIFIER token: " + varName);
            PsiElement declaration = findDeclaration(file, varName, sourceElement);
            if (declaration != null) {
                LOG.info("[KiteGotoDecl] Found declaration for INTERP_IDENTIFIER: " + varName);
                return new PsiElement[]{declaration};
            }
            // Not found locally - search in imported files (cross-file navigation)
            LOG.info("[KiteGotoDecl] INTERP_IDENTIFIER not found locally, searching imports...");
            PsiElement importedDeclaration = findDeclarationInImportedFiles(file, varName, sourceElement, new HashSet<>());
            if (importedDeclaration != null) {
                LOG.info("[KiteGotoDecl] Found INTERP_IDENTIFIER in imported file: " + varName);
                return new PsiElement[]{importedDeclaration};
            }
            return null;
        }

        // Handle INTERP_SIMPLE tokens (the $identifier pattern)
        // The token includes the $ prefix, so extract just the variable name
        if (elementType == KiteTokenTypes.INTERP_SIMPLE) {
            String text = sourceElement.getText();
            if (text.startsWith("$") && text.length() > 1) {
                String varName = text.substring(1);
                LOG.info("[KiteGotoDecl] INTERP_SIMPLE token: " + varName);
                PsiElement declaration = findDeclaration(file, varName, sourceElement);
                if (declaration != null) {
                    LOG.info("[KiteGotoDecl] Found declaration for INTERP_SIMPLE: " + varName);
                    return new PsiElement[]{declaration};
                }
                // Not found locally - search in imported files (cross-file navigation)
                LOG.info("[KiteGotoDecl] INTERP_SIMPLE not found locally, searching imports...");
                PsiElement importedDeclaration = findDeclarationInImportedFiles(file, varName, sourceElement, new HashSet<>());
                if (importedDeclaration != null) {
                    LOG.info("[KiteGotoDecl] Found INTERP_SIMPLE in imported file: " + varName);
                    return new PsiElement[]{importedDeclaration};
                }
            }
            return null;
        }

        // Handle file path strings in import statements
        // e.g., import * from "common.kite" - clicking on "common.kite" navigates to the file
        if (isImportPathString(sourceElement)) {
            LOG.info("[KiteGotoDecl] Import path string detected");
            PsiFile targetFile = resolveImportPathToFile(sourceElement, file);
            if (targetFile != null) {
                LOG.info("[KiteGotoDecl] Resolved import path to file: " + targetFile.getName());
                return new PsiElement[]{targetFile};
            }
        }

        // Handle legacy STRING tokens with interpolations (for backwards compatibility)
        if (elementType == KiteTokenTypes.STRING) {
            LOG.info("[KiteGotoDecl] Legacy STRING token detected, handling string interpolation navigation");
            PsiElement[] targets = handleStringInterpolation(sourceElement, offset, file);
            if (targets != null && targets.length > 0) {
                LOG.info("[KiteGotoDecl] Found " + targets.length + " interpolation targets");
            }
            return targets;
        }

        // Only handle IDENTIFIER tokens
        if (elementType != KiteTokenTypes.IDENTIFIER) {
            return null;
        }

        String name = sourceElement.getText();

        // Check if this is a resource property - navigate to schema property definition
        ResourcePropertyInfo resourcePropertyInfo = getResourcePropertyInfo(sourceElement);
        if (resourcePropertyInfo != null) {
            LOG.info("[KiteGotoDecl] Resource property detected: " + name + " in schema " + resourcePropertyInfo.schemaName);
            PsiElement schemaProperty = findSchemaPropertyElement(file, resourcePropertyInfo.schemaName, name);
            if (schemaProperty != null) {
                LOG.info("[KiteGotoDecl] Found schema property: " + schemaProperty.getText());
                return new PsiElement[]{schemaProperty};
            }
        }

        // For function parameter declarations, show usages within the function body
        // e.g., clicking on "instances" in "fun calculateCost(number instances, ...)" shows all usages
        if (isParameterDeclaration(sourceElement)) {
            LOG.info("[KiteGotoDecl] Parameter declaration detected: " + name);
            // Find the enclosing function
            PsiElement functionDecl = sourceElement.getParent();
            while (functionDecl != null && !(functionDecl instanceof PsiFile)) {
                if (functionDecl.getNode() != null &&
                    functionDecl.getNode().getElementType() == KiteElementTypes.FUNCTION_DECLARATION) {
                    break;
                }
                functionDecl = functionDecl.getParent();
            }
            if (functionDecl != null && !(functionDecl instanceof PsiFile)) {
                List<PsiElement> usages = findParameterUsagesInFunction(functionDecl, name, sourceElement);
                LOG.info("[KiteGotoDecl] Found " + usages.size() + " parameter usages");
                if (!usages.isEmpty()) {
                    return usages.toArray(new PsiElement[0]);
                }
            }
            return null;
        }

        // For declaration names, show a dropdown of all usages
        // e.g., clicking on "server" in "resource VM.Instance server { }" shows all usages of "server"
        boolean isDeclName = isDeclarationName(sourceElement);
        LOG.info("[KiteGotoDecl] isDeclarationName(" + name + ") = " + isDeclName);
        if (isDeclName) {
            List<PsiElement> usages = findUsages(file, name, sourceElement);
            LOG.info("[KiteGotoDecl] Found " + usages.size() + " usages for declaration: " + name);
            if (!usages.isEmpty()) {
                return usages.toArray(new PsiElement[0]);
            }
            return null;
        }

        // Check if this is a property access (identifier after a DOT)
        // Use full chain resolution for nested property access like server.tag.New
        List<String> propertyChain = getPropertyAccessChain(sourceElement);
        LOG.info("[KiteGotoDecl] propertyChain: " + (propertyChain != null ? propertyChain : "null"));

        if (propertyChain != null) {
            // Property access chain: resolve step by step through nested objects
            LOG.info("[KiteGotoDecl] Resolving property chain: " + propertyChain + "." + name);
            return resolvePropertyAccessChain(file, propertyChain, name, sourceElement);
        } else {
            // Simple identifier: search declarations in file scope
            LOG.info("[KiteGotoDecl] Searching for declaration of: " + name);
            PsiElement declaration = findDeclaration(file, name, sourceElement);
            LOG.info("[KiteGotoDecl] findDeclaration returned: " + (declaration != null ? declaration.getText() + " at " + declaration.getTextRange() : "null"));
            if (declaration != null) {
                LOG.info("[KiteGotoDecl] Returning declaration target: " + declaration.getText());
                return new PsiElement[]{declaration};
            }

            // Not found locally - search in imported files (cross-file navigation)
            LOG.info("[KiteGotoDecl] Not found locally, searching in imported files...");
            PsiElement importedDeclaration = findDeclarationInImportedFiles(file, name, sourceElement, new HashSet<>());
            if (importedDeclaration != null) {
                LOG.info("[KiteGotoDecl] Found in imported file: " + importedDeclaration.getText());
                return new PsiElement[]{importedDeclaration};
            }
        }

        LOG.info("[KiteGotoDecl] No targets found, returning null");
        return null;
    }

    /**
     * Find all usages (references) of a name in the file and in files that import this file.
     * Used when clicking on a declaration name to show where it's used.
     * Returns wrapped elements with custom presentation for the popup.
     */
    private List<PsiElement> findUsages(PsiElement element, String targetName, PsiElement sourceElement) {
        List<PsiElement> rawUsages = new ArrayList<>();
        PsiFile containingFile = sourceElement.getContainingFile();

        // Search in current file
        findUsagesRecursive(element, targetName, sourceElement, rawUsages);
        LOG.info("[findUsages] Found " + rawUsages.size() + " usages in current file");

        // Also search in files that import this file (cross-file usages)
        if (containingFile != null) {
            List<PsiFile> importingFiles = findFilesThatImport(containingFile);
            LOG.info("[findUsages] Found " + importingFiles.size() + " files that import " + containingFile.getName());

            for (PsiFile importingFile : importingFiles) {
                int before = rawUsages.size();
                findUsagesRecursive(importingFile, targetName, null, rawUsages);
                LOG.info("[findUsages] Found " + (rawUsages.size() - before) + " usages in " + importingFile.getName());
            }
        }

        // Wrap each usage in a navigatable element with custom presentation
        List<PsiElement> wrappedUsages = new ArrayList<>();
        for (PsiElement usage : rawUsages) {
            wrappedUsages.add(new KiteNavigatablePsiElement(usage));
        }
        return wrappedUsages;
    }

    /**
     * Find all Kite files in the project that import the given file.
     * This is a reverse lookup - given a file, find which files import it.
     */
    private List<PsiFile> findFilesThatImport(PsiFile targetFile) {
        List<PsiFile> importingFiles = new ArrayList<>();

        if (targetFile == null || targetFile.getVirtualFile() == null) {
            return importingFiles;
        }

        Project project = targetFile.getProject();
        String targetFileName = targetFile.getVirtualFile().getName();
        String targetFilePath = targetFile.getVirtualFile().getPath();

        LOG.info("[findFilesThatImport] Looking for files that import: " + targetFileName);

        // Get all Kite files in the project
        Collection<VirtualFile> kiteFiles = FileTypeIndex.getFiles(
                KiteFileType.INSTANCE,
                GlobalSearchScope.projectScope(project)
        );

        LOG.info("[findFilesThatImport] Found " + kiteFiles.size() + " Kite files in project");

        PsiManager psiManager = PsiManager.getInstance(project);

        for (VirtualFile vFile : kiteFiles) {
            // Skip the target file itself
            if (vFile.getPath().equals(targetFilePath)) {
                continue;
            }

            PsiFile psiFile = psiManager.findFile(vFile);
            if (psiFile == null) {
                continue;
            }

            // Check if this file imports the target file
            List<PsiFile> imports = KiteImportHelper.getImportedFiles(psiFile);
            for (PsiFile importedFile : imports) {
                if (importedFile != null && importedFile.getVirtualFile() != null &&
                    importedFile.getVirtualFile().getPath().equals(targetFilePath)) {
                    LOG.info("[findFilesThatImport] " + psiFile.getName() + " imports " + targetFileName);
                    importingFiles.add(psiFile);
                    break;
                }
            }
        }

        return importingFiles;
    }

    /**
     * Recursively find all usages of a name.
     * Includes usages in:
     * - Regular IDENTIFIER tokens
     * - INTERP_SIMPLE tokens ($varName)
     * - INTERP_IDENTIFIER tokens (inside ${...})
     * - Legacy STRING tokens with interpolation patterns
     */
    private void findUsagesRecursive(PsiElement element, String targetName, PsiElement sourceElement, List<PsiElement> usages) {
        IElementType type = element.getNode().getElementType();

        // Check if this is an identifier with the target name
        if (type == KiteTokenTypes.IDENTIFIER && targetName.equals(element.getText())) {
            // Exclude the source element itself
            if (element != sourceElement) {
                // Only include if this is NOT a declaration name (i.e., it's a reference/usage)
                if (!isDeclarationName(element)) {
                    usages.add(element);
                }
            }
        }

        // Check for INTERP_SIMPLE tokens ($varName)
        if (type == KiteTokenTypes.INTERP_SIMPLE) {
            String text = element.getText();
            if (text.startsWith("$") && text.length() > 1) {
                String varName = text.substring(1);
                if (targetName.equals(varName) && element != sourceElement) {
                    usages.add(element);
                }
            }
        }

        // Check for INTERP_IDENTIFIER tokens (inside ${...})
        if (type == KiteTokenTypes.INTERP_IDENTIFIER && targetName.equals(element.getText())) {
            if (element != sourceElement) {
                usages.add(element);
            }
        }

        // Check legacy STRING tokens for interpolation patterns
        if (type == KiteTokenTypes.STRING) {
            findUsagesInString(element, targetName, sourceElement, usages);
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            findUsagesRecursive(child, targetName, sourceElement, usages);
            child = child.getNextSibling();
        }
    }

    /**
     * Find usages of a name within a STRING token (interpolations).
     * Handles both ${varName} and $varName patterns.
     */
    private void findUsagesInString(PsiElement stringElement, String targetName, PsiElement sourceElement, List<PsiElement> usages) {
        String text = stringElement.getText();

        // Check ${...} interpolations
        Matcher braceMatcher = BRACE_INTERPOLATION_PATTERN.matcher(text);
        while (braceMatcher.find()) {
            String content = braceMatcher.group(1);
            String varName = extractFirstIdentifier(content);
            if (targetName.equals(varName)) {
                usages.add(stringElement);
                return; // Only add once per string
            }
        }

        // Check $var interpolations
        Matcher simpleMatcher = SIMPLE_INTERPOLATION_PATTERN.matcher(text);
        while (simpleMatcher.find()) {
            int matchStart = simpleMatcher.start();
            // Skip if this is part of ${...}
            if (matchStart + 1 < text.length() && text.charAt(matchStart + 1) == '{') {
                continue;
            }
            String varName = simpleMatcher.group(1);
            if (targetName.equals(varName)) {
                usages.add(stringElement);
                return; // Only add once per string
            }
        }
    }

    /**
     * Check if this identifier is part of a property access expression (after a DOT).
     * Returns only the immediate object element (not the full chain).
     */
    @Nullable
    private PsiElement getPropertyAccessObject(PsiElement element) {
        PsiElement prev = skipWhitespaceBackward(element.getPrevSibling());
        if (prev != null && prev.getNode().getElementType() == KiteTokenTypes.DOT) {
            PsiElement objectElement = skipWhitespaceBackward(prev.getPrevSibling());
            if (objectElement != null && objectElement.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                return objectElement;
            }
        }
        return null;
    }

    /**
     * Get the full property access chain for an identifier.
     * For "server.tag.New", returns ["server", "tag"] when called for "New".
     * Returns null if this is not a property access.
     */
    @Nullable
    private List<String> getPropertyAccessChain(PsiElement element) {
        List<String> chain = new ArrayList<>();
        PsiElement current = element;

        // Walk backward through the chain: identifier <- DOT <- identifier <- DOT <- ...
        while (true) {
            PsiElement prev = skipWhitespaceBackward(current.getPrevSibling());

            if (prev != null && prev.getNode().getElementType() == KiteTokenTypes.DOT) {
                // Found DOT before us, get the identifier before the DOT
                PsiElement objectElement = skipWhitespaceBackward(prev.getPrevSibling());
                if (objectElement != null && objectElement.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                    chain.add(0, objectElement.getText()); // Add at beginning to maintain order
                    current = objectElement;
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        return chain.isEmpty() ? null : chain;
    }

    /**
     * Resolve property access chain: navigate through nested object literals.
     * For "server.tag.New", chain = ["server", "tag"], propertyName = "New"
     * Steps:
     *   1. Find declaration of "server" (the resource)
     *   2. Find "tag" property inside server's block → get its OBJECT_LITERAL value
     *   3. Find "New" property inside that object literal
     *
     * Special case for component instances:
     *   For "serviceA.endpoint" where serviceA is a component INSTANCE (component WebServer serviceA {}),
     *   look for "endpoint" in the component TYPE definition (component WebServer {}), not the instance body.
     */
    @Nullable
    private PsiElement[] resolvePropertyAccessChain(PsiFile file, List<String> chain, String propertyName, PsiElement sourceElement) {
        LOG.info("[KiteGotoDecl] resolvePropertyAccessChain: chain=" + chain + ", target='" + propertyName + "'");

        // Start with the first element in the chain (e.g., "server")
        String rootName = chain.get(0);
        PsiElement currentScope = findDeclarationElement(file, rootName);

        if (currentScope == null) {
            LOG.info("[KiteGotoDecl] Root declaration not found: " + rootName);
            return null;
        }

        LOG.info("[KiteGotoDecl] Found root declaration: " + currentScope.getNode().getElementType());

        // Special case: if chain has only the root (e.g., chain=["serviceA"]) and root is a COMPONENT_DECLARATION
        // that is a component INSTANCE, look for the property in the component TYPE definition
        if (chain.size() == 1 && currentScope.getNode().getElementType() == KiteElementTypes.COMPONENT_DECLARATION) {
            String componentTypeName = getComponentTypeName(currentScope);
            LOG.info("[KiteGotoDecl] Root is COMPONENT_DECLARATION, componentTypeName=" + componentTypeName);

            if (componentTypeName != null) {
                // This is a component INSTANCE - find the TYPE definition and look for outputs/inputs there
                PsiElement typeDefinition = findComponentTypeDefinition(file, componentTypeName);
                LOG.info("[KiteGotoDecl] Found component type definition: " + (typeDefinition != null));

                if (typeDefinition != null) {
                    // Look for the property in the type definition's outputs and inputs
                    PsiElement result = findOutputOrInputInComponent(typeDefinition, propertyName, sourceElement);
                    if (result != null) {
                        LOG.info("[KiteGotoDecl] FOUND output/input '" + propertyName + "' in component type " + componentTypeName);
                        return new PsiElement[]{result};
                    }
                }
            }
            // If not a component instance or property not found, fall through to normal resolution
        }

        // Navigate through the rest of the chain (e.g., ["tag"] for server.tag.New)
        for (int i = 1; i < chain.size(); i++) {
            String chainPropertyName = chain.get(i);
            LOG.info("[KiteGotoDecl] Step " + i + ": Looking for property '" + chainPropertyName + "'");

            // Find the property and get its value (should be an object literal)
            PsiElement propertyValue = findPropertyValue(currentScope, chainPropertyName);

            if (propertyValue == null) {
                LOG.info("[KiteGotoDecl] Property '" + chainPropertyName + "' not found");
                return null;
            }

            LOG.info("[KiteGotoDecl] Found property value type: " + propertyValue.getNode().getElementType());

            // The value should be an object literal to continue traversing
            if (propertyValue.getNode().getElementType() == KiteElementTypes.OBJECT_LITERAL) {
                currentScope = propertyValue;
                LOG.info("[KiteGotoDecl] Advanced to nested OBJECT_LITERAL");
            } else {
                LOG.info("[KiteGotoDecl] Property value is not OBJECT_LITERAL, can't traverse deeper");
                return null;
            }
        }

        // Now find the target property in the final scope
        LOG.info("[KiteGotoDecl] Final step: Finding target '" + propertyName + "'");
        PsiElement result = findPropertyInScope(currentScope, propertyName, sourceElement);
        if (result != null) {
            LOG.info("[KiteGotoDecl] FOUND target at " + result.getTextRange());
            return new PsiElement[]{result};
        }

        LOG.info("[KiteGotoDecl] Target property not found");
        return null;
    }

    /**
     * Get the component type name from a component INSTANCE declaration.
     * A component INSTANCE has pattern: component TypeName instanceName { ... }
     * A component TYPE definition has pattern: component TypeName { ... }
     * <p>
     * Returns the type name if this is an instance (2 identifiers before {), null if it's a type definition.
     */
    @Nullable
    private String getComponentTypeName(PsiElement componentDeclaration) {
        if (componentDeclaration.getNode().getElementType() != KiteElementTypes.COMPONENT_DECLARATION) {
            return null;
        }

        PsiElement child = componentDeclaration.getFirstChild();
        String firstIdentifier = null;
        String secondIdentifier = null;
        boolean foundComponent = false;

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.COMPONENT) {
                foundComponent = true;
            } else if (foundComponent && childType == KiteTokenTypes.IDENTIFIER) {
                if (firstIdentifier == null) {
                    firstIdentifier = child.getText();
                } else if (secondIdentifier == null) {
                    secondIdentifier = child.getText();
                }
            } else if (childType == KiteTokenTypes.LBRACE) {
                // Stop at opening brace
                break;
            }

            child = child.getNextSibling();
        }

        // If we found two identifiers before {, this is an instance
        // Return the first identifier (the type name)
        if (firstIdentifier != null && secondIdentifier != null) {
            LOG.info("[KiteGotoDecl] Component INSTANCE: type=" + firstIdentifier + ", name=" + secondIdentifier);
            return firstIdentifier;
        }

        // Only one identifier - this is a type definition, not an instance
        LOG.info("[KiteGotoDecl] Component TYPE definition: " + firstIdentifier);
        return null;
    }

    /**
     * Find a component TYPE definition by name.
     * A type definition has pattern: component TypeName { ... } (only one identifier before {)
     */
    @Nullable
    private PsiElement findComponentTypeDefinition(PsiElement scope, String typeName) {
        return findComponentTypeDefinitionRecursive(scope, typeName);
    }

    @Nullable
    private PsiElement findComponentTypeDefinitionRecursive(PsiElement element, String typeName) {
        IElementType type = element.getNode().getElementType();

        if (type == KiteElementTypes.COMPONENT_DECLARATION) {
            // Check if this is a TYPE definition (not an instance) with the matching name
            PsiElement child = element.getFirstChild();
            String firstIdentifier = null;
            String secondIdentifier = null;
            boolean foundComponent = false;

            while (child != null) {
                IElementType childType = child.getNode().getElementType();

                if (childType == KiteTokenTypes.COMPONENT) {
                    foundComponent = true;
                } else if (foundComponent && childType == KiteTokenTypes.IDENTIFIER) {
                    if (firstIdentifier == null) {
                        firstIdentifier = child.getText();
                    } else if (secondIdentifier == null) {
                        secondIdentifier = child.getText();
                    }
                } else if (childType == KiteTokenTypes.LBRACE) {
                    break;
                }

                child = child.getNextSibling();
            }

            // This is a TYPE definition if there's only one identifier before {
            // and it matches the type name we're looking for
            if (firstIdentifier != null && secondIdentifier == null && typeName.equals(firstIdentifier)) {
                LOG.info("[KiteGotoDecl] Found component TYPE definition: " + typeName);
                return element;
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            PsiElement result = findComponentTypeDefinitionRecursive(child, typeName);
            if (result != null) {
                return result;
            }
            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Find an output or input declaration in a component type definition.
     */
    @Nullable
    private PsiElement findOutputOrInputInComponent(PsiElement componentDeclaration, String propertyName, PsiElement sourceElement) {
        return findOutputOrInputRecursive(componentDeclaration, propertyName, sourceElement, false);
    }

    @Nullable
    private PsiElement findOutputOrInputRecursive(PsiElement element, String propertyName, PsiElement sourceElement, boolean insideBraces) {
        PsiElement child = element.getFirstChild();
        boolean currentInsideBraces = insideBraces;

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            // Track brace state
            if (childType == KiteTokenTypes.LBRACE) {
                currentInsideBraces = true;
            } else if (childType == KiteTokenTypes.RBRACE) {
                currentInsideBraces = false;
            }

            // Look for OUTPUT_DECLARATION or INPUT_DECLARATION inside braces
            if (currentInsideBraces &&
                (childType == KiteElementTypes.OUTPUT_DECLARATION || childType == KiteElementTypes.INPUT_DECLARATION)) {
                PsiElement nameElement = findNameInDeclaration(child, childType);
                if (nameElement != null && propertyName.equals(nameElement.getText()) && nameElement != sourceElement) {
                    LOG.info("[KiteGotoDecl] Found " + childType + " named '" + propertyName + "'");
                    return nameElement;
                }
            }

            // Recurse into composite elements, but not into nested component declarations
            if (child.getFirstChild() != null && childType != KiteElementTypes.COMPONENT_DECLARATION) {
                PsiElement result = findOutputOrInputRecursive(child, propertyName, sourceElement, currentInsideBraces);
                if (result != null) {
                    return result;
                }
            }

            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Find a property's value (the expression after = or :) within a declaration or object literal.
     */
    @Nullable
    private PsiElement findPropertyValue(PsiElement scope, String propertyName) {
        // If scope is already an OBJECT_LITERAL, we're conceptually "inside braces"
        boolean startInsideBraces = (scope.getNode().getElementType() == KiteElementTypes.OBJECT_LITERAL);
        return findPropertyValueRecursive(scope, propertyName, startInsideBraces);
    }

    @Nullable
    private PsiElement findPropertyValueRecursive(PsiElement element, String propertyName, boolean insideBraces) {
        PsiElement child = element.getFirstChild();
        boolean currentInsideBraces = insideBraces;

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            // Track brace state
            if (childType == KiteTokenTypes.LBRACE) {
                currentInsideBraces = true;
            } else if (childType == KiteTokenTypes.RBRACE) {
                currentInsideBraces = false;
            }

            // Look for property assignments
            if (currentInsideBraces && childType == KiteTokenTypes.IDENTIFIER) {
                String identText = child.getText();
                if (propertyName.equals(identText)) {
                    // Check if followed by = or :
                    PsiElement next = skipWhitespaceForward(child.getNextSibling());
                    if (next != null) {
                        IElementType nextType = next.getNode().getElementType();
                        if (nextType == KiteTokenTypes.ASSIGN || nextType == KiteTokenTypes.COLON) {
                            // Get the value after = or :
                            PsiElement value = skipWhitespaceForward(next.getNextSibling());
                            if (value != null) {
                                return value;
                            }
                        }
                    }
                }
            }

            // DON'T recurse into nested OBJECT_LITERALs - we only want direct children
            if (childType == KiteElementTypes.OBJECT_LITERAL) {
                // Skip nested objects when searching at current level
            } else if (child.getFirstChild() != null && !isDeclarationType(childType)) {
                PsiElement result = findPropertyValueRecursive(child, propertyName, currentInsideBraces);
                if (result != null) {
                    return result;
                }
            }

            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Find property in scope (object literal or declaration).
     */
    @Nullable
    private PsiElement findPropertyInScope(PsiElement scope, String propertyName, PsiElement sourceElement) {
        IElementType scopeType = scope.getNode().getElementType();

        if (scopeType == KiteElementTypes.OBJECT_LITERAL) {
            // For object literals, search directly inside
            return findPropertyInObjectLiteral(scope, propertyName, sourceElement);
        } else {
            // For declarations, use the existing recursive search
            return findPropertyInDeclaration(scope, propertyName, sourceElement);
        }
    }

    /**
     * Find property in an object literal.
     */
    @Nullable
    private PsiElement findPropertyInObjectLiteral(PsiElement objectLiteral, String propertyName, PsiElement sourceElement) {
        PsiElement child = objectLiteral.getFirstChild();

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.IDENTIFIER) {
                String identText = child.getText();
                if (propertyName.equals(identText) && child != sourceElement) {
                    // Check if followed by : (object literal property)
                    PsiElement next = skipWhitespaceForward(child.getNextSibling());
                    if (next != null && next.getNode().getElementType() == KiteTokenTypes.COLON) {
                        return child;
                    }
                }
            }

            // Don't recurse into nested object literals
            if (childType != KiteElementTypes.OBJECT_LITERAL && child.getFirstChild() != null) {
                PsiElement result = findPropertyInObjectLiteral(child, propertyName, sourceElement);
                if (result != null) {
                    return result;
                }
            }

            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Resolve property access: find the property within the object's declaration scope.
     * (Legacy method - kept for backwards compatibility)
     */
    @Nullable
    private PsiElement[] resolvePropertyAccess(PsiFile file, String objectName, String propertyName, PsiElement sourceElement) {
        // First, find the declaration of the object
        PsiElement objectDeclaration = findDeclarationElement(file, objectName);

        if (objectDeclaration != null) {
            // Search for the property within the object's declaration body
            PsiElement property = findPropertyInDeclaration(objectDeclaration, propertyName, sourceElement);
            if (property != null) {
                return new PsiElement[]{property};
            }
        }

        return null;
    }

    /**
     * Find property definitions within a declaration body.
     */
    @Nullable
    private PsiElement findPropertyInDeclaration(PsiElement declaration, String propertyName, PsiElement sourceElement) {
        return findPropertyRecursive(declaration, propertyName, sourceElement, false);
    }

    /**
     * Recursively search for property definitions within a declaration.
     */
    @Nullable
    private PsiElement findPropertyRecursive(PsiElement element, String propertyName, PsiElement sourceElement, boolean insideBraces) {
        PsiElement child = element.getFirstChild();
        boolean currentInsideBraces = insideBraces;

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            // Track when we enter/exit braces
            if (childType == KiteTokenTypes.LBRACE) {
                currentInsideBraces = true;
            } else if (childType == KiteTokenTypes.RBRACE) {
                currentInsideBraces = false;
            }

            // Check for property patterns only inside the braces
            if (currentInsideBraces && childType == KiteTokenTypes.IDENTIFIER) {
                String identText = child.getText();
                if (propertyName.equals(identText) && child != sourceElement) {
                    // Check if this identifier is followed by = or : (property assignment)
                    PsiElement next = skipWhitespaceForward(child.getNextSibling());
                    if (next != null) {
                        IElementType nextType = next.getNode().getElementType();
                        if (nextType == KiteTokenTypes.ASSIGN ||
                            nextType == KiteTokenTypes.COLON ||
                            nextType == KiteTokenTypes.PLUS_ASSIGN) {
                            return child;
                        }
                    }
                }
            }

            // Check for input/output/var declarations inside braces
            if (currentInsideBraces && isDeclarationType(childType)) {
                PsiElement declName = findNameInDeclaration(child, childType);
                if (declName != null && propertyName.equals(declName.getText()) && declName != sourceElement) {
                    return declName;
                }
            }

            // Recurse into composite elements, but not into nested declarations
            if (child.getFirstChild() != null && !isDeclarationType(childType)) {
                PsiElement result = findPropertyRecursive(child, propertyName, sourceElement, currentInsideBraces);
                if (result != null) {
                    return result;
                }
            }

            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Find a declaration with the given name in the file (for simple identifier resolution).
     */
    @Nullable
    private PsiElement findDeclaration(PsiElement element, String targetName, PsiElement sourceElement) {
        // First, check if we're inside a function and the target is a parameter
        if (sourceElement != null) {
            PsiElement parameterDecl = findParameterInEnclosingFunction(sourceElement, targetName);
            if (parameterDecl != null) {
                LOG.info("[findDeclaration] MATCH FOUND (function parameter): " + targetName);
                return parameterDecl;
            }
        }

        return findDeclarationRecursive(element, targetName, sourceElement);
    }

    /**
     * Recursively search for a declaration with the given name.
     */
    @Nullable
    private PsiElement findDeclarationRecursive(PsiElement element, String targetName, PsiElement sourceElement) {
        IElementType type = element.getNode().getElementType();
        LOG.info("[findDeclaration] Searching in element type: " + type + " for: " + targetName);

        if (isDeclarationType(type)) {
            LOG.info("[findDeclaration] Found declaration type: " + type);
            PsiElement nameElement = findNameInDeclaration(element, type);
            LOG.info("[findDeclaration] Declaration name element: " + (nameElement != null ? nameElement.getText() : "null"));
            if (nameElement != null && targetName.equals(nameElement.getText()) && nameElement != sourceElement) {
                LOG.info("[findDeclaration] MATCH FOUND: " + nameElement.getText() + " at " + nameElement.getTextRange());
                return nameElement;
            }
        }

        // Handle raw VAR tokens (local variables inside function bodies)
        if (type == KiteTokenTypes.VAR) {
            PsiElement nameElement = findVarNameElementFromToken(element);
            if (nameElement != null && targetName.equals(nameElement.getText()) && nameElement != sourceElement) {
                LOG.info("[findDeclaration] MATCH FOUND (VAR token): " + nameElement.getText());
                return nameElement;
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            PsiElement result = findDeclarationRecursive(child, targetName, sourceElement);
            if (result != null) {
                return result;
            }
            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Find a parameter declaration in the enclosing function.
     * Walks up the PSI tree to find a FUNCTION_DECLARATION and searches for the parameter name.
     */
    @Nullable
    private PsiElement findParameterInEnclosingFunction(PsiElement element, String parameterName) {
        // Walk up to find the enclosing function declaration
        PsiElement current = element;
        while (current != null && !(current instanceof PsiFile)) {
            if (current.getNode() != null &&
                current.getNode().getElementType() == KiteElementTypes.FUNCTION_DECLARATION) {
                // Found enclosing function - search for parameter
                return findParameterInFunction(current, parameterName);
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Find a parameter by name within a function declaration.
     * Function syntax: fun name(type param1, type param2) returnType { ... }
     * Parameters are identifiers between LPAREN and RPAREN, where each parameter
     * is the identifier AFTER a type identifier.
     */
    @Nullable
    private PsiElement findParameterInFunction(PsiElement functionDeclaration, String parameterName) {
        boolean inParams = false;
        PsiElement prevIdentifier = null;

        PsiElement child = functionDeclaration.getFirstChild();
        while (child != null) {
            if (child.getNode() == null) {
                child = child.getNextSibling();
                continue;
            }

            IElementType childType = child.getNode().getElementType();

            // Enter parameter list
            if (childType == KiteTokenTypes.LPAREN) {
                inParams = true;
                child = child.getNextSibling();
                continue;
            }

            // Exit parameter list
            if (childType == KiteTokenTypes.RPAREN) {
                break;
            }

            // Inside parameter list
            if (inParams) {
                // Skip whitespace
                if (isWhitespace(childType)) {
                    child = child.getNextSibling();
                    continue;
                }

                // Identifiers in params: pattern is "type name, type name"
                // So the parameter NAME is the identifier that comes AFTER another identifier
                if (childType == KiteTokenTypes.IDENTIFIER) {
                    if (prevIdentifier != null) {
                        // This is the parameter name (previous was the type)
                        if (parameterName.equals(child.getText())) {
                            LOG.info("[findParameterInFunction] Found parameter: " + parameterName);
                            return child;
                        }
                        prevIdentifier = null; // Reset after finding a param name
                    } else {
                        // This is the type
                        prevIdentifier = child;
                    }
                }

                // Comma resets the type/name pair tracking
                if (childType == KiteTokenTypes.COMMA) {
                    prevIdentifier = null;
                }
            }

            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Find the variable name element from a raw VAR token.
     * Pattern: var [type] name = value
     * Returns the identifier element that is the variable name (last identifier before =)
     */
    @Nullable
    private PsiElement findVarNameElementFromToken(PsiElement varToken) {
        PsiElement lastIdentifier = null;
        PsiElement sibling = varToken.getNextSibling();

        while (sibling != null) {
            if (sibling.getNode() == null) {
                sibling = sibling.getNextSibling();
                continue;
            }

            IElementType siblingType = sibling.getNode().getElementType();

            // Skip whitespace
            if (isWhitespace(siblingType)) {
                sibling = sibling.getNextSibling();
                continue;
            }

            // Collect identifiers
            if (siblingType == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = sibling;
            }

            // Stop at = and return the last identifier found
            if (siblingType == KiteTokenTypes.ASSIGN) {
                return lastIdentifier;
            }

            // Stop at newline (incomplete declaration)
            if (siblingType == KiteTokenTypes.NL) {
                break;
            }

            sibling = sibling.getNextSibling();
        }

        return null;
    }

    /**
     * Find the declaration element (the whole node) for a given name.
     */
    @Nullable
    private PsiElement findDeclarationElement(PsiElement element, String targetName) {
        IElementType type = element.getNode().getElementType();

        if (isDeclarationType(type)) {
            PsiElement nameElement = findNameInDeclaration(element, type);
            if (nameElement != null && targetName.equals(nameElement.getText())) {
                return element; // Return the whole declaration, not just the name
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            PsiElement result = findDeclarationElement(child, targetName);
            if (result != null) {
                return result;
            }
            child = child.getNextSibling();
        }

        return null;
    }

    private boolean isDeclarationType(IElementType type) {
        return type == KiteElementTypes.VARIABLE_DECLARATION ||
               type == KiteElementTypes.INPUT_DECLARATION ||
               type == KiteElementTypes.OUTPUT_DECLARATION ||
               type == KiteElementTypes.RESOURCE_DECLARATION ||
               type == KiteElementTypes.COMPONENT_DECLARATION ||
               type == KiteElementTypes.SCHEMA_DECLARATION ||
               type == KiteElementTypes.FUNCTION_DECLARATION ||
               type == KiteElementTypes.TYPE_DECLARATION ||
               type == KiteElementTypes.FOR_STATEMENT;
    }

    /**
     * Find the name identifier within a declaration.
     */
    @Nullable
    private PsiElement findNameInDeclaration(PsiElement declaration, IElementType declarationType) {
        if (declarationType == KiteElementTypes.FOR_STATEMENT) {
            // For loop: "for identifier in ..." - name is right after 'for'
            boolean foundFor = false;
            PsiElement child = declaration.getFirstChild();
            while (child != null) {
                IElementType childType = child.getNode().getElementType();
                if (childType == KiteTokenTypes.FOR) {
                    foundFor = true;
                } else if (foundFor && childType == KiteTokenTypes.IDENTIFIER) {
                    return child;
                }
                child = child.getNextSibling();
            }
        }

        // Special handling for function declarations
        // Pattern: fun name(params) returnType { ... }
        // The function name is the identifier after FUN and before LPAREN
        if (declarationType == KiteElementTypes.FUNCTION_DECLARATION) {
            boolean foundFun = false;
            PsiElement child = declaration.getFirstChild();
            while (child != null) {
                IElementType childType = child.getNode().getElementType();
                if (childType == KiteTokenTypes.FUN) {
                    foundFun = true;
                } else if (foundFun && childType == KiteTokenTypes.IDENTIFIER) {
                    return child; // First identifier after FUN is the function name
                } else if (childType == KiteTokenTypes.LPAREN) {
                    break; // Stop at opening paren
                }
                child = child.getNextSibling();
            }
            return null;
        }

        // For var/input/output: keyword [type] name [= value]
        // For resource/component/schema: keyword [type] name { ... }
        // Find the identifier that comes before '=' or '{'
        PsiElement lastIdentifier = null;
        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();
            if (childType == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = child;
            } else if (childType == KiteTokenTypes.ASSIGN ||
                       childType == KiteTokenTypes.LBRACE ||
                       childType == KiteTokenTypes.PLUS_ASSIGN) {
                if (lastIdentifier != null) {
                    return lastIdentifier;
                }
            }
            child = child.getNextSibling();
        }

        return lastIdentifier;
    }

    @Nullable
    private PsiElement skipWhitespaceBackward(@Nullable PsiElement element) {
        while (element != null && isWhitespace(element.getNode().getElementType())) {
            element = element.getPrevSibling();
        }
        return element;
    }

    @Nullable
    private PsiElement skipWhitespaceForward(@Nullable PsiElement element) {
        while (element != null && isWhitespace(element.getNode().getElementType())) {
            element = element.getNextSibling();
        }
        return element;
    }

    private boolean isWhitespace(IElementType type) {
        return type == TokenType.WHITE_SPACE ||
               type == KiteTokenTypes.NL ||
               type == KiteTokenTypes.WHITESPACE ||
               type == KiteTokenTypes.NEWLINE;
    }

    /**
     * Handle string interpolation navigation.
     * Finds the interpolation variable at the given offset and resolves it to its declaration.
     */
    @Nullable
    private PsiElement[] handleStringInterpolation(PsiElement stringElement, int offset, PsiFile file) {
        String text = stringElement.getText();
        int elementStart = stringElement.getTextRange().getStartOffset();
        int relativeOffset = offset - elementStart;

        LOG.info("[KiteGotoDecl] String text: " + text + ", relativeOffset: " + relativeOffset);

        // Check ${...} interpolations
        Matcher braceMatcher = BRACE_INTERPOLATION_PATTERN.matcher(text);
        while (braceMatcher.find()) {
            int contentStart = braceMatcher.start(1);
            int contentEnd = braceMatcher.end(1);
            String content = braceMatcher.group(1);

            // Check if offset is within this interpolation
            if (relativeOffset >= braceMatcher.start() && relativeOffset <= braceMatcher.end()) {
                String varName = extractFirstIdentifier(content);
                if (varName != null) {
                    LOG.info("[KiteGotoDecl] Found brace interpolation var: " + varName);
                    PsiElement declaration = findDeclaration(file, varName, stringElement);
                    if (declaration != null) {
                        return new PsiElement[]{declaration};
                    }
                }
            }
        }

        // Check $var interpolations
        Matcher simpleMatcher = SIMPLE_INTERPOLATION_PATTERN.matcher(text);
        while (simpleMatcher.find()) {
            int matchStart = simpleMatcher.start();

            // Skip if this is part of ${...}
            if (matchStart + 1 < text.length() && text.charAt(matchStart + 1) == '{') {
                continue;
            }

            // Check if offset is within this interpolation
            if (relativeOffset >= matchStart && relativeOffset <= simpleMatcher.end()) {
                String varName = simpleMatcher.group(1);
                LOG.info("[KiteGotoDecl] Found simple interpolation var: " + varName);
                PsiElement declaration = findDeclaration(file, varName, stringElement);
                if (declaration != null) {
                    return new PsiElement[]{declaration};
                }
            }
        }

        return null;
    }

    /**
     * Extract the first identifier from an expression.
     * For "obj.prop" returns "obj", for "func()" returns "func", etc.
     */
    @Nullable
    private String extractFirstIdentifier(String expression) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (i == 0) {
                if (Character.isJavaIdentifierStart(c)) {
                    sb.append(c);
                } else {
                    return null;
                }
            } else {
                if (Character.isJavaIdentifierPart(c)) {
                    sb.append(c);
                } else {
                    break;
                }
            }
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * Find declaration in imported files (cross-file navigation).
     * Uses KiteImportHelper to resolve imports and search in imported files.
     * Only searches for symbols that are explicitly imported (named or wildcard).
     *
     * @param file          The file containing the import statements
     * @param targetName    The name to find
     * @param sourceElement The element that triggered the navigation (to exclude from results)
     * @param visited       Set of visited file paths to prevent infinite loops
     * @return The declaration element if found, null otherwise
     */
    @Nullable
    private PsiElement findDeclarationInImportedFiles(PsiFile file, String targetName, PsiElement sourceElement, Set<String> visited) {
        // Check if this symbol is actually imported
        // getImportSourceFile returns the file only if the symbol is imported (named or wildcard)
        PsiFile importSourceFile = KiteImportHelper.getImportSourceFile(targetName, file);

        if (importSourceFile == null) {
            // Symbol is not imported, don't search imported files
            LOG.info("[KiteGotoDecl] Symbol '" + targetName + "' is not imported - skipping cross-file search");
            return null;
        }

        if (importSourceFile.getVirtualFile() == null) {
            return null;
        }

        String filePath = importSourceFile.getVirtualFile().getPath();
        if (visited.contains(filePath)) {
            return null; // Already visited, skip to prevent infinite loop
        }
        visited.add(filePath);

        LOG.info("[KiteGotoDecl] Symbol '" + targetName + "' is imported from: " + importSourceFile.getName());

        // Search for declaration in the specific imported file
        PsiElement declaration = findDeclaration(importSourceFile, targetName, null);
        if (declaration != null) {
            LOG.info("[KiteGotoDecl] Found declaration in imported file: " + filePath);
            return declaration;
        }

        // Also recursively check imports in the imported file (for re-exports)
        PsiElement nestedDeclaration = findDeclarationInImportedFiles(importSourceFile, targetName, sourceElement, visited);
        if (nestedDeclaration != null) {
            return nestedDeclaration;
        }

        return null;
    }

    /**
     * Check if this identifier is a parameter declaration in a function signature.
     * Function syntax: fun name(type param1, type param2) returnType { ... }
     * Parameters are the SECOND identifier in each "type name" pair between parentheses.
     */
    private boolean isParameterDeclaration(PsiElement element) {
        if (element == null || element.getNode() == null) {
            return false;
        }

        // Walk up to find if we're inside a FUNCTION_DECLARATION
        PsiElement functionDecl = element.getParent();
        while (functionDecl != null && !(functionDecl instanceof PsiFile)) {
            if (functionDecl.getNode() != null &&
                functionDecl.getNode().getElementType() == KiteElementTypes.FUNCTION_DECLARATION) {
                break;
            }
            functionDecl = functionDecl.getParent();
        }

        if (functionDecl == null || functionDecl instanceof PsiFile) {
            return false;
        }

        // Check if this element is inside the parameter list (between LPAREN and RPAREN)
        // and is the NAME part (second identifier) of a "type name" pair
        boolean foundLParen = false;
        PsiElement prevIdentifier = null;

        PsiElement child = functionDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() == null) {
                child = child.getNextSibling();
                continue;
            }

            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.LPAREN) {
                foundLParen = true;
                child = child.getNextSibling();
                continue;
            }

            if (childType == KiteTokenTypes.RPAREN) {
                break;
            }

            if (foundLParen) {
                if (isWhitespace(childType)) {
                    child = child.getNextSibling();
                    continue;
                }

                if (childType == KiteTokenTypes.IDENTIFIER) {
                    if (prevIdentifier != null) {
                        // This is the parameter NAME (previous was the type)
                        if (child == element) {
                            return true;
                        }
                        prevIdentifier = null;
                    } else {
                        // This is the type
                        prevIdentifier = child;
                    }
                }

                if (childType == KiteTokenTypes.COMMA) {
                    prevIdentifier = null;
                }
            }

            child = child.getNextSibling();
        }

        return false;
    }

    /**
     * Find all usages of a parameter within the function body.
     * Returns the list of references to the parameter inside the function.
     */
    private List<PsiElement> findParameterUsagesInFunction(PsiElement functionDecl, String parameterName, PsiElement parameterDecl) {
        List<PsiElement> usages = new ArrayList<>();

        // Find the function body (between { and })
        PsiElement functionBody = null;
        boolean foundLBrace = false;

        PsiElement child = functionDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null && child.getNode().getElementType() == KiteTokenTypes.LBRACE) {
                foundLBrace = true;
                // Start searching from after LBRACE
                child = child.getNextSibling();
                break;
            }
            child = child.getNextSibling();
        }

        if (!foundLBrace) {
            return usages;
        }

        // Search for all usages of the parameter name in the function body
        while (child != null) {
            if (child.getNode() != null) {
                IElementType childType = child.getNode().getElementType();

                // Stop at closing brace
                if (childType == KiteTokenTypes.RBRACE) {
                    break;
                }

                // Check if this is an identifier with the parameter name
                if (childType == KiteTokenTypes.IDENTIFIER && parameterName.equals(child.getText())) {
                    // Exclude the parameter declaration itself
                    if (child != parameterDecl) {
                        usages.add(new KiteNavigatablePsiElement(child));
                    }
                }

                // Recurse into composite elements
                if (child.getFirstChild() != null) {
                    findParameterUsagesRecursive(child, parameterName, parameterDecl, usages);
                }
            }

            child = child.getNextSibling();
        }

        return usages;
    }

    /**
     * Recursively find parameter usages within an element.
     */
    private void findParameterUsagesRecursive(PsiElement element, String parameterName, PsiElement parameterDecl, List<PsiElement> usages) {
        PsiElement child = element.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                IElementType childType = child.getNode().getElementType();

                if (childType == KiteTokenTypes.IDENTIFIER && parameterName.equals(child.getText())) {
                    if (child != parameterDecl) {
                        usages.add(new KiteNavigatablePsiElement(child));
                    }
                }

                if (child.getFirstChild() != null) {
                    findParameterUsagesRecursive(child, parameterName, parameterDecl, usages);
                }
            }
            child = child.getNextSibling();
        }
    }

    /**
     * Check if this identifier is a declaration name (the name being declared, not a reference).
     * Declaration names are:
     * - The last identifier before = or { in input/output/var/resource/component/schema/function/type declarations
     * - The identifier after "for" keyword in for loops
     * - Property names in object literals (identifier before : or =)
     *
     * NOTE: Identifiers that come AFTER = are VALUES (references), not declaration names.
     * Example: in "instanceType = instanceTypes", instanceType is a property name (not navigable),
     * but instanceTypes is a value/reference (should be navigable).
     */
    private boolean isDeclarationName(PsiElement element) {
        LOG.info("[isDeclarationName] Checking: '" + element.getText() + "'");

        // First, check if this identifier comes AFTER an equals sign
        // If so, it's a value (reference), not a declaration name - it SHOULD be navigable
        PsiElement prev = skipWhitespaceBackward(element.getPrevSibling());
        LOG.info("[isDeclarationName] prev element: " + (prev != null ? prev.getText() + " (" + prev.getNode().getElementType() + ")" : "null"));
        if (prev != null && prev.getNode().getElementType() == KiteTokenTypes.ASSIGN) {
            // This identifier comes after =, so it's a value, not a declaration name
            LOG.info("[isDeclarationName] '" + element.getText() + "' comes after ASSIGN -> returning false (is reference)");
            return false;
        }

        // Check if this identifier is followed by = or { or += or : (declaration/property pattern)
        PsiElement next = skipWhitespaceForward(element.getNextSibling());
        LOG.info("[isDeclarationName] next element: " + (next != null ? next.getText() + " (" + next.getNode().getElementType() + ")" : "null"));
        if (next != null) {
            IElementType nextType = next.getNode().getElementType();
            if (nextType == KiteTokenTypes.ASSIGN ||
                nextType == KiteTokenTypes.LBRACE ||
                nextType == KiteTokenTypes.PLUS_ASSIGN ||
                nextType == KiteTokenTypes.COLON) {
                // This identifier is followed by = or { or : - it's a declaration/property name
                LOG.info("[isDeclarationName] '" + element.getText() + "' followed by " + nextType + " -> returning true");
                return true;
            }
            // Special case for function declarations: identifier followed by ( is a declaration name
            // ONLY if preceded by 'fun' keyword
            if (nextType == KiteTokenTypes.LPAREN && prev != null &&
                prev.getNode().getElementType() == KiteTokenTypes.FUN) {
                LOG.info("[isDeclarationName] '" + element.getText() + "' is function name (fun ... LPAREN) -> returning true");
                return true;
            }
        }

        // Check if this is a for loop variable (identifier after "for" keyword)
        // Note: We already have 'prev' from the ASSIGN check above
        if (prev != null && prev.getNode().getElementType() == KiteTokenTypes.FOR) {
            LOG.info("[isDeclarationName] '" + element.getText() + "' follows FOR -> returning true");
            return true;
        }

        // Check if parent is a declaration and this is the declared name
        PsiElement parent = element.getParent();
        if (parent != null) {
            IElementType parentType = parent.getNode().getElementType();
            if (isDeclarationType(parentType)) {
                // Find the name element in this declaration
                PsiElement nameElement = findNameInDeclaration(parent, parentType);
                if (nameElement == element) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if the element is a string literal in an import statement.
     * Import syntax: import * from "path/to/file.kite"
     * The string element can be:
     * - STRING token (the whole string including quotes)
     * - STRING_TEXT token (the text inside the quotes)
     * - DQUOTE or SINGLE_STRING token
     */
    private boolean isImportPathString(PsiElement element) {
        if (element == null || element.getNode() == null) {
            return false;
        }

        IElementType type = element.getNode().getElementType();

        // Check if this is a string-related token
        boolean isStringToken = (type == KiteTokenTypes.STRING ||
                                 type == KiteTokenTypes.STRING_TEXT ||
                                 type == KiteTokenTypes.DQUOTE ||
                                 type == KiteTokenTypes.SINGLE_STRING);

        if (!isStringToken) {
            return false;
        }

        // Walk backward to find if there's a FROM keyword before this string
        PsiElement current = element.getPrevSibling();
        while (current != null) {
            if (current.getNode() == null) {
                current = current.getPrevSibling();
                continue;
            }

            IElementType currentType = current.getNode().getElementType();

            // Skip whitespace
            if (isWhitespace(currentType)) {
                current = current.getPrevSibling();
                continue;
            }

            // Found FROM keyword - this is an import path string
            if (currentType == KiteTokenTypes.FROM) {
                return true;
            }

            // If we hit any other non-whitespace token, this is not an import path
            break;
        }

        // Also check parent element for IMPORT_STATEMENT
        PsiElement parent = element.getParent();
        while (parent != null && !(parent instanceof PsiFile)) {
            if (parent.getNode() != null &&
                parent.getNode().getElementType() == KiteElementTypes.IMPORT_STATEMENT) {
                return true;
            }
            parent = parent.getParent();
        }

        return false;
    }

    /**
     * Resolve an import path string to a PsiFile.
     * Extracts the path from the string element and uses KiteImportHelper to resolve it.
     */
    @Nullable
    private PsiFile resolveImportPathToFile(PsiElement stringElement, PsiFile containingFile) {
        String text = stringElement.getText();

        // Extract path from string (remove quotes if present)
        String path = text;
        if ((path.startsWith("\"") && path.endsWith("\"")) ||
            (path.startsWith("'") && path.endsWith("'"))) {
            if (path.length() >= 2) {
                path = path.substring(1, path.length() - 1);
            }
        }

        // If this is just the text content (STRING_TEXT), it won't have quotes
        // Use it directly if it looks like a file path
        if (path.isEmpty()) {
            return null;
        }

        LOG.info("[KiteGotoDecl] Resolving import path: " + path);

        // Use KiteImportHelper to resolve the path
        return KiteImportHelper.resolveFilePath(path, containingFile);
    }

    // ========== Resource Property to Schema Property Navigation ==========

    /**
     * Simple class to hold resource property information.
     */
    private static class ResourcePropertyInfo {
        final String schemaName;
        final PsiElement resourceDeclaration;

        ResourcePropertyInfo(String schemaName, PsiElement resourceDeclaration) {
            this.schemaName = schemaName;
            this.resourceDeclaration = resourceDeclaration;
        }
    }

    /**
     * Check if the element is a property name inside a resource block.
     * Returns info about the containing resource if so, null otherwise.
     * <p>
     * A property is identified by: identifier followed by =
     * Inside a resource block: resource TypeName instanceName { ... }
     */
    @Nullable
    private ResourcePropertyInfo getResourcePropertyInfo(PsiElement element) {
        // First check if this identifier is followed by = (property assignment)
        PsiElement next = skipWhitespaceForward(element.getNextSibling());
        if (next == null || next.getNode() == null ||
            next.getNode().getElementType() != KiteTokenTypes.ASSIGN) {
            return null;
        }

        // Walk up to find if we're inside a RESOURCE_DECLARATION
        PsiElement current = element.getParent();
        while (current != null && !(current instanceof PsiFile)) {
            if (current.getNode() != null &&
                current.getNode().getElementType() == KiteElementTypes.RESOURCE_DECLARATION) {
                // Check if we're inside the braces
                if (isInsideBraces(element, current)) {
                    String schemaName = extractResourceTypeName(current);
                    if (schemaName != null) {
                        return new ResourcePropertyInfo(schemaName, current);
                    }
                }
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Check if position is inside the braces of a declaration.
     */
    private boolean isInsideBraces(PsiElement position, PsiElement declaration) {
        int posOffset = position.getTextOffset();

        // Find LBRACE and RBRACE positions
        int lbraceOffset = -1;
        int rbraceOffset = -1;

        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();
                if (type == KiteTokenTypes.LBRACE && lbraceOffset == -1) {
                    lbraceOffset = child.getTextOffset();
                } else if (type == KiteTokenTypes.RBRACE) {
                    rbraceOffset = child.getTextOffset();
                }
            }
            child = child.getNextSibling();
        }

        return lbraceOffset != -1 && rbraceOffset != -1 &&
               posOffset > lbraceOffset && posOffset < rbraceOffset;
    }

    /**
     * Extract the resource type name (schema name) from a resource declaration.
     * Pattern: resource TypeName instanceName { ... }
     */
    @Nullable
    private String extractResourceTypeName(PsiElement resourceDecl) {
        boolean foundResource = false;
        PsiElement child = resourceDecl.getFirstChild();

        while (child != null) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();

                if (type == KiteTokenTypes.RESOURCE) {
                    foundResource = true;
                } else if (foundResource && type == KiteTokenTypes.IDENTIFIER) {
                    return child.getText(); // First identifier is the type name
                } else if (type == KiteTokenTypes.LBRACE) {
                    break;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }

    /**
     * Find the property name element in a schema definition.
     * Searches current file and imported files.
     */
    @Nullable
    private PsiElement findSchemaPropertyElement(PsiFile file, String schemaName, String propertyName) {
        // Search in current file
        PsiElement result = findSchemaPropertyRecursive(file, schemaName, propertyName);
        if (result != null) {
            return result;
        }

        // Search in imported files
        return findSchemaPropertyInImports(file, schemaName, propertyName, new HashSet<>());
    }

    /**
     * Recursively search for a schema and find the property element.
     */
    @Nullable
    private PsiElement findSchemaPropertyRecursive(PsiElement element, String schemaName, String propertyName) {
        if (element == null || element.getNode() == null) return null;

        if (element.getNode().getElementType() == KiteElementTypes.SCHEMA_DECLARATION) {
            // Check if this is the schema we're looking for
            String name = extractSchemaName(element);
            if (schemaName.equals(name)) {
                // Found the schema - now find the property
                return findPropertyNameInSchema(element, propertyName);
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            PsiElement result = findSchemaPropertyRecursive(child, schemaName, propertyName);
            if (result != null) return result;
            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Search for schema property in imported files.
     */
    @Nullable
    private PsiElement findSchemaPropertyInImports(PsiFile file, String schemaName, String propertyName, Set<String> visited) {
        List<PsiFile> importedFiles = KiteImportHelper.getImportedFiles(file);

        for (PsiFile importedFile : importedFiles) {
            if (importedFile == null || importedFile.getVirtualFile() == null) continue;

            String path = importedFile.getVirtualFile().getPath();
            if (visited.contains(path)) continue;
            visited.add(path);

            PsiElement result = findSchemaPropertyRecursive(importedFile, schemaName, propertyName);
            if (result != null) return result;

            // Recursively check imports
            result = findSchemaPropertyInImports(importedFile, schemaName, propertyName, visited);
            if (result != null) return result;
        }

        return null;
    }

    /**
     * Get the schema name from a schema declaration.
     */
    @Nullable
    private String extractSchemaName(PsiElement schemaDecl) {
        boolean foundSchema = false;
        PsiElement child = schemaDecl.getFirstChild();

        while (child != null) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();

                if (type == KiteTokenTypes.SCHEMA) {
                    foundSchema = true;
                } else if (foundSchema && type == KiteTokenTypes.IDENTIFIER) {
                    return child.getText();
                } else if (type == KiteTokenTypes.LBRACE) {
                    break;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }

    /**
     * Find the property NAME element in a schema (the identifier, not the type).
     * Schema syntax: schema Name { type propName [= default] }
     * Returns the propName identifier element.
     */
    @Nullable
    private PsiElement findPropertyNameInSchema(PsiElement schemaDecl, String propertyName) {
        boolean insideBraces = false;
        PsiElement prevIdentifier = null; // Tracks the type identifier

        PsiElement child = schemaDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();

                if (type == KiteTokenTypes.LBRACE) {
                    insideBraces = true;
                } else if (type == KiteTokenTypes.RBRACE) {
                    break;
                } else if (insideBraces) {
                    // Skip whitespace
                    if (isWhitespace(type)) {
                        child = child.getNextSibling();
                        continue;
                    }

                    // Track type -> name pattern
                    if (type == KiteTokenTypes.IDENTIFIER) {
                        if (prevIdentifier != null) {
                            // This is the property name (previous was the type)
                            if (propertyName.equals(child.getText())) {
                                return child;
                            }
                            prevIdentifier = null;
                        } else {
                            // This is the type
                            prevIdentifier = child;
                        }
                    }

                    // Reset on newline or assignment
                    if (type == KiteTokenTypes.NL || type == KiteTokenTypes.ASSIGN) {
                        prevIdentifier = null;
                    }
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }
}
