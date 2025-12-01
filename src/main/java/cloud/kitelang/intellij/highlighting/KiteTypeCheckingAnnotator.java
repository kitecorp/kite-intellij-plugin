package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.quickfix.AddImportQuickFix;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import cloud.kitelang.intellij.util.*;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static cloud.kitelang.intellij.util.KiteIdentifierContextHelper.*;
import static cloud.kitelang.intellij.util.KiteImportValidationHelper.*;
import static cloud.kitelang.intellij.util.KitePsiUtil.*;
import static cloud.kitelang.intellij.util.KiteTypeInferenceHelper.*;

/**
 * Annotator that performs type checking and reference validation.
 * <p>
 * Features:
 * - Undefined reference detection: Warns when identifiers don't resolve to any declaration
 * - Type mismatch detection: Warns when assigned values don't match declared types
 */
public class KiteTypeCheckingAnnotator implements Annotator {

    // Set of keywords that should not be treated as identifiers
    private static final Set<String> KEYWORDS = Set.of(
            "if", "else", "while", "for", "in", "return", "import", "from",
            "fun", "var", "type", "init", "this", "true", "false", "null",
            "input", "output", "resource", "component", "schema"
    );

    // Set of built-in global functions that don't need to be declared
    private static final Set<String> BUILTIN_FUNCTIONS = Set.of(
            "print", "println"
    );

    // Set of valid built-in decorator names
    private static final Set<String> VALID_DECORATORS = Set.of(
            // Validation decorators
            "minValue", "maxValue", "minLength", "maxLength",
            "nonEmpty", "validate", "allowed", "unique",
            // Resource decorators
            "existing", "sensitive", "dependsOn", "tags", "provider",
            // Metadata decorators
            "description", "count", "cloud"
    );

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // Only process at file level to avoid redundant checks
        if (!(element instanceof PsiFile)) {
            return;
        }

        PsiFile file = (PsiFile) element;
        if (file.getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Collect all declared names in the file first
        Set<String> declaredNames = new HashSet<>();
        collectAllDeclaredNames(file, declaredNames);

        // Also collect declared names from imported files
        collectDeclaredNamesFromImports(file, declaredNames, new HashSet<>());

        // Check all identifiers for undefined references
        checkUndefinedReferences(file, declaredNames, holder);

        // Check for type mismatches in variable declarations
        checkTypeMismatches(file, holder);

        // Check for unknown decorator names
        checkUnknownDecorators(file, holder);

        // Check for broken import paths
        checkBrokenImportPaths(file, file, holder);

        // Check import ordering - imports must appear at the beginning of the file
        checkImportOrdering(file, holder);
    }

    // ========== Declaration Collection ==========

    private void collectAllDeclaredNames(PsiElement element, Set<String> names) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Check if this is a declaration
        if (KiteDeclarationHelper.isDeclarationType(type)) {
            String name = KitePsiUtil.findDeclarationName(element, type);
            if (name != null) {
                names.add(name);
            }

            // For function declarations, also collect parameter names
            if (type == KiteElementTypes.FUNCTION_DECLARATION) {
                collectFunctionParameters(element, names);
            }
        }

        // Check for loop variable declarations (for x in ...)
        if (type == KiteElementTypes.FOR_STATEMENT) {
            String loopVar = findForLoopVariable(element);
            if (loopVar != null) {
                names.add(loopVar);
            }
        }

        // Handle raw VAR tokens that might not be wrapped in VARIABLE_DECLARATION
        if (type == KiteTokenTypes.VAR) {
            String varName = findVarNameFromToken(element);
            if (varName != null) {
                names.add(varName);
            }
        }

        // Collect symbol names from named import statements
        if (type == KiteElementTypes.IMPORT_STATEMENT) {
            collectNamedImportSymbols(element, names);
        }

        // Also check for raw IMPORT tokens at file level (fallback)
        if (type == KiteTokenTypes.IMPORT) {
            collectNamedImportSymbolsFromToken(element, names);
        }

        // Recurse into children
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            collectAllDeclaredNames(child, names);
        }
    }

    private void collectNamedImportSymbols(PsiElement importStatement, Set<String> names) {
        boolean foundImport = false;
        boolean foundWildcard = false;

        for (PsiElement child = importStatement.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.IMPORT) {
                foundImport = true;
                continue;
            }

            if (foundImport && childType == KiteTokenTypes.MULTIPLY) {
                foundWildcard = true;
                continue;
            }

            if (foundImport && !foundWildcard && childType == KiteTokenTypes.IDENTIFIER) {
                names.add(child.getText());
            }

            if (childType == KiteTokenTypes.FROM) {
                break;
            }
        }

        // For wildcard imports, resolve the file and collect all its declared names
        if (foundWildcard) {
            String importPath = KiteImportHelper.extractImportPath(importStatement);
            if (importPath != null && !importPath.isEmpty()) {
                PsiFile containingFile = importStatement.getContainingFile();
                PsiFile importedFile = KiteImportHelper.resolveFilePath(importPath, containingFile);
                if (importedFile != null) {
                    collectAllDeclaredNames(importedFile, names);
                }
            }
        }
    }

    private void collectNamedImportSymbolsFromToken(PsiElement importToken, Set<String> names) {
        boolean foundWildcard = false;
        PsiElement sibling = importToken.getNextSibling();

        while (sibling != null) {
            if (sibling.getNode() == null) {
                sibling = sibling.getNextSibling();
                continue;
            }

            IElementType siblingType = sibling.getNode().getElementType();

            if (isWhitespace(siblingType)) {
                sibling = sibling.getNextSibling();
                continue;
            }

            if (siblingType == KiteTokenTypes.MULTIPLY) {
                foundWildcard = true;
                sibling = sibling.getNextSibling();
                continue;
            }

            if (!foundWildcard && siblingType == KiteTokenTypes.IDENTIFIER) {
                names.add(sibling.getText());
            }

            if (siblingType == KiteTokenTypes.FROM ||
                siblingType == KiteTokenTypes.NL ||
                siblingType == KiteTokenTypes.NEWLINE) {
                break;
            }

            sibling = sibling.getNextSibling();
        }

        if (foundWildcard) {
            String importPath = extractImportPathFromToken(importToken);
            if (importPath != null && !importPath.isEmpty()) {
                PsiFile containingFile = importToken.getContainingFile();
                PsiFile importedFile = KiteImportHelper.resolveFilePath(importPath, containingFile);
                if (importedFile != null) {
                    collectAllDeclaredNames(importedFile, names);
                }
            }
        }
    }

    @Nullable
    private String findVarNameFromToken(PsiElement varToken) {
        PsiElement lastIdentifier = null;
        PsiElement sibling = varToken.getNextSibling();

        while (sibling != null) {
            if (sibling.getNode() == null) {
                sibling = sibling.getNextSibling();
                continue;
            }

            IElementType siblingType = sibling.getNode().getElementType();

            if (isWhitespace(siblingType)) {
                sibling = sibling.getNextSibling();
                continue;
            }

            if (siblingType == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = sibling;
            }

            if (siblingType == KiteTokenTypes.ASSIGN) {
                return lastIdentifier != null ? lastIdentifier.getText() : null;
            }

            if (siblingType == KiteTokenTypes.NL) {
                break;
            }

            sibling = sibling.getNextSibling();
        }

        return null;
    }

    private void collectFunctionParameters(PsiElement functionDecl, Set<String> names) {
        boolean insideParens = false;
        List<String> identifiersInParens = new ArrayList<>();

        for (PsiElement child = functionDecl.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.LPAREN) {
                insideParens = true;
                identifiersInParens.clear();
                continue;
            }

            if (childType == KiteTokenTypes.RPAREN) {
                for (int i = 1; i < identifiersInParens.size(); i += 2) {
                    names.add(identifiersInParens.get(i));
                }
                break;
            }

            if (insideParens && childType == KiteTokenTypes.IDENTIFIER) {
                identifiersInParens.add(child.getText());
            }
        }
    }

    private void collectDeclaredNamesFromImports(PsiFile file, Set<String> names, Set<String> visitedPaths) {
        Map<String, PsiFile> wildcardImportedFiles = new HashMap<>();
        collectWildcardImportedFiles(file, file, wildcardImportedFiles);

        for (Map.Entry<String, PsiFile> entry : wildcardImportedFiles.entrySet()) {
            PsiFile importedFile = entry.getValue();
            if (importedFile == null || importedFile.getVirtualFile() == null) {
                continue;
            }

            String filePath = importedFile.getVirtualFile().getPath();
            if (visitedPaths.contains(filePath)) {
                continue;
            }
            visitedPaths.add(filePath);

            collectAllDeclaredNames(importedFile, names);
            collectDeclaredNamesFromImports(importedFile, names, visitedPaths);
        }
    }

    private void collectWildcardImportedFiles(PsiElement element, PsiFile containingFile,
                                               Map<String, PsiFile> wildcardFiles) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        if (type == KiteElementTypes.IMPORT_STATEMENT) {
            if (isWildcardImport(element)) {
                String importPath = KiteImportHelper.extractImportPath(element);
                if (importPath != null && !importPath.isEmpty()) {
                    PsiFile importedFile = KiteImportHelper.resolveFilePath(importPath, containingFile);
                    if (importedFile != null) {
                        wildcardFiles.put(importPath, importedFile);
                    }
                }
            }
        }

        if (type == KiteTokenTypes.IMPORT) {
            PsiElement parent = element.getParent();
            if (parent != null && parent.getNode() != null &&
                parent.getNode().getElementType() != KiteElementTypes.IMPORT_STATEMENT) {
                if (isWildcardImportFromToken(element)) {
                    String importPath = extractImportPathFromToken(element);
                    if (importPath != null && !importPath.isEmpty()) {
                        PsiFile importedFile = KiteImportHelper.resolveFilePath(importPath, containingFile);
                        if (importedFile != null) {
                            wildcardFiles.put(importPath, importedFile);
                        }
                    }
                }
            }
        }

        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            collectWildcardImportedFiles(child, containingFile, wildcardFiles);
        }
    }

    // ========== Undefined Reference Checking ==========

    private void checkUndefinedReferences(PsiElement element, Set<String> declaredNames, AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        if (type == KiteTokenTypes.IDENTIFIER) {
            String name = element.getText();

            if (KEYWORDS.contains(name) || BUILTIN_TYPES.contains(name) || BUILTIN_FUNCTIONS.contains(name)) {
                return;
            }

            if (isDeclarationName(element) || isPropertyAccess(element) ||
                isTypeAnnotation(element) || isPropertyDefinition(element) ||
                isDecoratorName(element) || isInsideImportStatement(element)) {
                return;
            }

            if (!declaredNames.contains(name)) {
                PsiFile containingFile = element.getContainingFile();
                List<AddImportQuickFix.ImportCandidate> candidates =
                        AddImportQuickFix.findImportCandidates(name, containingFile);

                if (!candidates.isEmpty()) {
                    String message = "Cannot resolve symbol '" + name + "' - import available";
                    var builder = holder.newAnnotation(HighlightSeverity.ERROR, message)
                            .range(element)
                            .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);

                    for (AddImportQuickFix.ImportCandidate candidate : candidates) {
                        builder = builder.withFix(
                                new AddImportQuickFix(candidate.symbolName, candidate.importPath));
                    }

                    builder.create();
                } else {
                    holder.newAnnotation(HighlightSeverity.WARNING,
                                    "Cannot resolve symbol '" + name + "'")
                            .range(element)
                            .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                            .create();
                }
            }
        }

        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            checkUndefinedReferences(child, declaredNames, holder);
        }
    }

    // ========== Type Mismatch Checking ==========

    private void checkTypeMismatches(PsiElement element, AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        if (type == KiteElementTypes.VARIABLE_DECLARATION ||
            type == KiteElementTypes.INPUT_DECLARATION ||
            type == KiteElementTypes.OUTPUT_DECLARATION) {
            checkDeclarationTypeMismatch(element, holder);
        }

        if (type == KiteElementTypes.RESOURCE_DECLARATION) {
            checkResourcePropertyTypeMismatches(element, holder);
        }

        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            checkTypeMismatches(child, holder);
        }
    }

    private void checkDeclarationTypeMismatch(PsiElement declaration, AnnotationHolder holder) {
        String declaredType = null;
        PsiElement valueElement = null;

        boolean foundKeyword = false;
        PsiElement firstIdentifier = null;
        PsiElement secondIdentifier = null;

        for (PsiElement child = declaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType childType = child.getNode().getElementType();

            if (isWhitespace(childType)) continue;

            if (childType == KiteTokenTypes.VAR ||
                childType == KiteTokenTypes.INPUT ||
                childType == KiteTokenTypes.OUTPUT) {
                foundKeyword = true;
                continue;
            }

            if (foundKeyword && childType == KiteTokenTypes.IDENTIFIER) {
                if (firstIdentifier == null) {
                    firstIdentifier = child;
                } else if (secondIdentifier == null) {
                    secondIdentifier = child;
                }
            }

            if (childType == KiteTokenTypes.ASSIGN) {
                valueElement = skipWhitespace(child.getNextSibling());
                break;
            }
        }

        if (secondIdentifier != null) {
            declaredType = firstIdentifier.getText();
        }

        if (declaredType == null || valueElement == null) {
            return;
        }

        String valueType = inferType(valueElement);
        if (valueType == null) {
            return;
        }

        if (!isTypeCompatible(declaredType, valueType)) {
            holder.newAnnotation(HighlightSeverity.ERROR,
                            "Type mismatch: expected '" + declaredType + "' but got '" + valueType + "'")
                    .range(valueElement)
                    .create();
        }
    }

    private void checkResourcePropertyTypeMismatches(PsiElement resourceDeclaration, AnnotationHolder holder) {
        String schemaName = KiteSchemaHelper.extractResourceTypeName(resourceDeclaration);
        if (schemaName == null) return;

        PsiFile file = resourceDeclaration.getContainingFile();
        if (file == null) return;

        Map<String, KiteSchemaHelper.SchemaPropertyInfo> schemaProperties =
                KiteSchemaHelper.findSchemaProperties(file, schemaName);
        if (schemaProperties.isEmpty()) return;

        boolean insideBraces = false;
        for (PsiElement child = resourceDeclaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.LBRACE) {
                insideBraces = true;
                continue;
            }
            if (childType == KiteTokenTypes.RBRACE) {
                break;
            }

            if (insideBraces && childType == KiteTokenTypes.IDENTIFIER) {
                String propertyName = child.getText();

                PsiElement next = skipWhitespace(child.getNextSibling());
                if (next != null && next.getNode() != null &&
                    next.getNode().getElementType() == KiteTokenTypes.ASSIGN) {

                    PsiElement valueElement = skipWhitespace(next.getNextSibling());
                    if (valueElement == null) continue;

                    KiteSchemaHelper.SchemaPropertyInfo propInfo = schemaProperties.get(propertyName);
                    if (propInfo == null) continue;

                    String actualType = inferType(valueElement);
                    if (actualType == null) continue;

                    if (!isTypeCompatible(propInfo.type, actualType)) {
                        holder.newAnnotation(HighlightSeverity.ERROR,
                                        "Type mismatch: property '" + propertyName + "' expects '" + propInfo.type +
                                        "' but got '" + actualType + "'")
                                .range(valueElement)
                                .create();
                    }
                }
            }
        }
    }

    // ========== Decorator Checking ==========

    private void checkUnknownDecorators(PsiElement element, AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        if (type == KiteTokenTypes.AT) {
            PsiElement next = skipWhitespace(element.getNextSibling());
            if (next != null && next.getNode() != null &&
                next.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {

                String decoratorName = next.getText();

                if (!VALID_DECORATORS.contains(decoratorName)) {
                    holder.newAnnotation(HighlightSeverity.WARNING,
                                    "Unknown decorator '@" + decoratorName + "'")
                            .range(next)
                            .create();
                }
            }
        }

        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            checkUnknownDecorators(child, holder);
        }
    }

    // ========== Import Path Validation ==========

    private void checkBrokenImportPaths(PsiElement element, PsiFile containingFile, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        if (type == KiteElementTypes.IMPORT_STATEMENT) {
            PsiElement stringToken = findImportPathString(element);
            if (stringToken != null) {
                String importPath = extractImportPathFromElement(stringToken);
                if (importPath != null && importPath.isEmpty()) {
                    holder.newAnnotation(HighlightSeverity.ERROR, "Empty import path")
                            .range(stringToken)
                            .highlightType(ProblemHighlightType.ERROR)
                            .create();
                } else if (importPath != null) {
                    PsiFile resolvedFile = KiteImportHelper.resolveFilePath(importPath, containingFile);
                    if (resolvedFile == null) {
                        holder.newAnnotation(HighlightSeverity.ERROR, "Cannot resolve import path '" + importPath + "'")
                                .range(stringToken)
                                .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                                .create();
                    }
                }
            }
        }

        if (type == KiteTokenTypes.IMPORT) {
            PsiElement parent = element.getParent();
            if (parent != null && parent.getNode() != null &&
                parent.getNode().getElementType() != KiteElementTypes.IMPORT_STATEMENT) {
                PsiElement stringToken = findImportPathStringFromToken(element);
                if (stringToken != null) {
                    String importPath = extractImportPathFromElement(stringToken);
                    if (importPath != null && importPath.isEmpty()) {
                        holder.newAnnotation(HighlightSeverity.ERROR, "Empty import path")
                                .range(stringToken)
                                .highlightType(ProblemHighlightType.ERROR)
                                .create();
                    } else if (importPath != null) {
                        PsiFile resolvedFile = KiteImportHelper.resolveFilePath(importPath, containingFile);
                        if (resolvedFile == null) {
                            holder.newAnnotation(HighlightSeverity.ERROR, "Cannot resolve import path '" + importPath + "'")
                                    .range(stringToken)
                                    .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                                    .create();
                        }
                    }
                }
            }
        }

        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            checkBrokenImportPaths(child, containingFile, holder);
        }
    }

    // ========== Import Ordering ==========

    private void checkImportOrdering(PsiFile file, @NotNull AnnotationHolder holder) {
        boolean seenNonImportStatement = false;

        for (PsiElement child = file.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;

            IElementType childType = child.getNode().getElementType();

            if (isWhitespace(childType)) continue;

            if (childType == KiteElementTypes.IMPORT_STATEMENT) {
                if (seenNonImportStatement) {
                    holder.newAnnotation(HighlightSeverity.ERROR,
                                    "Import statements must appear at the beginning of the file")
                            .range(child)
                            .highlightType(ProblemHighlightType.ERROR)
                            .create();
                }
                continue;
            }

            if (childType == KiteTokenTypes.IMPORT) {
                if (seenNonImportStatement) {
                    PsiElement importEnd = findImportStatementEnd(child);
                    holder.newAnnotation(HighlightSeverity.ERROR,
                                    "Import statements must appear at the beginning of the file")
                            .range(importEnd != null ? importEnd : child)
                            .highlightType(ProblemHighlightType.ERROR)
                            .create();
                } else {
                    PsiElement importEnd = findImportStatementEnd(child);
                    if (importEnd != null) {
                        child = importEnd;
                    }
                }
                continue;
            }

            if (isNonImportStatement(childType)) {
                seenNonImportStatement = true;
            }
        }
    }

    // ========== Declaration Name Finding ==========

    @Nullable
    private String findComponentName(PsiElement componentDecl) {
        List<String> identifiers = new ArrayList<>();

        for (PsiElement child = componentDecl.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.IDENTIFIER) {
                identifiers.add(child.getText());
            } else if (type == KiteTokenTypes.LBRACE) {
                break;
            }
        }

        return identifiers.isEmpty() ? null : identifiers.get(identifiers.size() - 1);
    }

    @Nullable
    private String findForLoopVariable(PsiElement forStatement) {
        boolean foundFor = false;
        for (PsiElement child = forStatement.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.FOR) {
                foundFor = true;
            } else if (foundFor && type == KiteTokenTypes.IDENTIFIER) {
                return child.getText();
            } else if (type == KiteTokenTypes.IN) {
                break;
            }
        }
        return null;
    }
}
