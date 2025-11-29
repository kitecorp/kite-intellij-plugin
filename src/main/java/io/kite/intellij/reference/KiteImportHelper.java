package io.kite.intellij.reference;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.tree.IElementType;
import io.kite.intellij.psi.KiteElementTypes;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for parsing import statements and resolving cross-file references.
 * <p>
 * Supports Kite import syntax:
 * - import * from "path/to/file.kite"
 * <p>
 * Note: URL imports (http://, https://) should be handled by the Kite language
 * runtime, which downloads and caches files locally. The plugin then reads
 * from the local cache (e.g., ~/.kite/providers/).
 */
public class KiteImportHelper {

    /**
     * Get all imported files from a Kite file.
     *
     * @param file The Kite file to analyze
     * @return List of resolved PsiFiles that are imported
     */
    @NotNull
    public static List<PsiFile> getImportedFiles(@NotNull PsiFile file) {
        return getImportedFiles(file, new HashSet<>());
    }

    // Pattern to match import statements: import * from "path" or import * from 'path'
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "import\\s+\\*\\s+from\\s+[\"']([^\"']+)[\"']"
    );

    /**
     * Get all imported files, tracking visited files to prevent circular imports.
     */
    @NotNull
    private static List<PsiFile> getImportedFiles(@NotNull PsiFile file, @NotNull Set<String> visitedPaths) {
        List<PsiFile> importedFiles = new ArrayList<>();


        // Add this file to visited to prevent circular imports
        VirtualFile vFile = file.getVirtualFile();
        if (vFile != null) {
            String path = vFile.getPath();
            if (visitedPaths.contains(path)) {
                return importedFiles; // Already visited, prevent infinite loop
            }
            visitedPaths.add(path);
        }

        // Try PSI-based approach first
        findImportStatements(file, file, importedFiles);

        // If PSI approach didn't find anything, try text-based fallback
        if (importedFiles.isEmpty()) {
            findImportsFromText(file, importedFiles);
        }

        return importedFiles;
    }

    /**
     * Fallback: Parse import statements from file text using regex.
     * This is more reliable when the PSI structure doesn't match expectations.
     */
    private static void findImportsFromText(@NotNull PsiFile file, @NotNull List<PsiFile> importedFiles) {
        String text = file.getText();
        Matcher matcher = IMPORT_PATTERN.matcher(text);

        while (matcher.find()) {
            String importPath = matcher.group(1);

            PsiFile importedFile = resolveFilePath(importPath, file);
            if (importedFile != null) {
                importedFiles.add(importedFile);
            } else {
            }
        }
    }

    /**
     * Recursively find import statements in the PSI tree.
     * Looks for IMPORT_STATEMENT elements OR IMPORT keyword tokens followed by import syntax.
     */
    private static void findImportStatements(PsiElement element, PsiFile containingFile, List<PsiFile> importedFiles) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Check for IMPORT_STATEMENT composite element
        if (type == KiteElementTypes.IMPORT_STATEMENT) {
            PsiFile importedFile = resolveImport(element, containingFile);
            if (importedFile != null) {
                importedFiles.add(importedFile);
            } else {
            }
            return; // Don't recurse into children of import statement
        }

        // Alternative: Look for IMPORT keyword token and parse from there
        // This handles cases where IMPORT_STATEMENT element isn't properly created
        if (type == KiteTokenTypes.IMPORT) {
            // The import statement syntax is: import * from "path"
            // We need to find the string path that follows
            String importPath = extractImportPathFromKeyword(element);
            if (importPath != null) {
                PsiFile importedFile = resolveFilePath(importPath, containingFile);
                if (importedFile != null) {
                    importedFiles.add(importedFile);
                }
            }
            return; // Don't recurse further from import keyword
        }

        // Recurse into children
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            findImportStatements(child, containingFile, importedFiles);
        }
    }

    /**
     * Extract import path by scanning forward from the IMPORT keyword token.
     */
    @Nullable
    private static String extractImportPathFromKeyword(@NotNull PsiElement importKeyword) {
        boolean foundFrom = false;
        PsiElement sibling = importKeyword.getNextSibling();

        while (sibling != null) {
            if (sibling.getNode() == null) {
                sibling = sibling.getNextSibling();
                continue;
            }

            IElementType type = sibling.getNode().getElementType();
            String text = sibling.getText();

            // Skip whitespace
            if (type == KiteTokenTypes.WHITESPACE || type == KiteTokenTypes.NL ||
                type == com.intellij.psi.TokenType.WHITE_SPACE) {
                sibling = sibling.getNextSibling();
                continue;
            }

            // Look for "from" keyword
            if (type == KiteTokenTypes.FROM || "from".equals(text)) {
                foundFrom = true;
                sibling = sibling.getNextSibling();
                continue;
            }

            // After FROM, look for the string literal
            if (foundFrom) {

                // Check for quoted string
                if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
                    return text.substring(1, text.length() - 1);
                }
                if (text.startsWith("'") && text.endsWith("'") && text.length() >= 2) {
                    return text.substring(1, text.length() - 1);
                }

                // Check for DQUOTE element that contains the string
                if (type == KiteTokenTypes.DQUOTE || type == KiteTokenTypes.STRING ||
                    type == KiteTokenTypes.SINGLE_STRING) {
                    return extractStringContent(sibling);
                }

                // Check children for string content
                String path = extractStringFromElement(sibling);
                if (path != null) {
                    return path;
                }
            }

            // Stop at newline (end of import statement)
            if (type == KiteTokenTypes.NL) {
                break;
            }

            sibling = sibling.getNextSibling();
        }

        return null;
    }

    /**
     * Resolve an import statement to a PsiFile.
     *
     * @param importStatement The IMPORT_STATEMENT element
     * @param containingFile  The file containing the import statement
     * @return The resolved PsiFile, or null if not found
     */
    @Nullable
    public static PsiFile resolveImport(@NotNull PsiElement importStatement, @NotNull PsiFile containingFile) {
        // Parse import statement: import * from "path/to/file.kite"
        String importPath = extractImportPath(importStatement);
        if (importPath == null) {
            return null;
        }

        // Resolve the path relative to the containing file
        return resolveFilePath(importPath, containingFile);
    }

    /**
     * Extract the import path from an import statement.
     * <p>
     * Parses: import * from "path/to/file.kite"
     * Returns: "path/to/file.kite"
     */
    @Nullable
    public static String extractImportPath(@NotNull PsiElement importStatement) {

        boolean foundFrom = false;

        for (PsiElement child = importStatement.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;

            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.FROM) {
                foundFrom = true;
                continue;
            }

            // After FROM, look for the string literal
            if (foundFrom) {
                // Handle various string token types
                if (type == KiteTokenTypes.STRING ||
                    type == KiteTokenTypes.SINGLE_STRING ||
                    type == KiteTokenTypes.DQUOTE) {
                    String result = extractStringContent(child);
                    return result;
                }

                // For composite elements, recurse to find string content
                String path = extractStringFromElement(child);
                if (path != null) {
                    return path;
                }
            }
        }

        return null;
    }

    /**
     * Extract string content from various string element types.
     */
    @Nullable
    private static String extractStringFromElement(@NotNull PsiElement element) {
        String text = element.getText();

        // Direct string content
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
            return text.substring(1, text.length() - 1);
        }
        if (text.startsWith("'") && text.endsWith("'") && text.length() >= 2) {
            return text.substring(1, text.length() - 1);
        }

        // Check children for string content
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;

            IElementType type = child.getNode().getElementType();
            if (type == KiteTokenTypes.STRING_TEXT) {
                return child.getText();
            }

            // Recurse into composite elements
            String result = extractStringFromElement(child);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * Extract the content from a string literal element.
     */
    @NotNull
    private static String extractStringContent(@NotNull PsiElement stringElement) {
        String text = stringElement.getText();

        // Remove surrounding quotes if present
        if ((text.startsWith("\"") && text.endsWith("\"")) ||
            (text.startsWith("'") && text.endsWith("'"))) {
            if (text.length() >= 2) {
                return text.substring(1, text.length() - 1);
            }
        }

        // For DQUOTE tokens, we need to find the STRING_TEXT inside
        for (PsiElement child = stringElement.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null && child.getNode().getElementType() == KiteTokenTypes.STRING_TEXT) {
                return child.getText();
            }
        }

        return text;
    }

    /**
     * Resolve a file path relative to a containing file.
     * Supports multiple resolution strategies:
     * 1. Relative path from containing file (e.g., "common.kite", "../shared/utils.kite")
     * 2. Project-local providers: .kite/providers/
     * 3. User-global providers: ~/.kite/providers/
     * 4. Package-style paths: "aws.DatabaseConfig" → "aws/DatabaseConfig.kite"
     *
     * @param importPath     The import path (e.g., "common.kite" or "aws.DatabaseConfig")
     * @param containingFile The file containing the import
     * @return The resolved PsiFile, or null if not found
     */
    @Nullable
    public static PsiFile resolveFilePath(@NotNull String importPath, @NotNull PsiFile containingFile) {
        Project project = containingFile.getProject();
        VirtualFile containingVFile = containingFile.getVirtualFile();
        if (containingVFile == null) {
            return null;
        }

        VirtualFile containingDir = containingVFile.getParent();
        if (containingDir == null) {
            return null;
        }

        com.intellij.openapi.vfs.LocalFileSystem fileSystem = com.intellij.openapi.vfs.LocalFileSystem.getInstance();
        VirtualFile targetFile = null;

        // Strategy 1: Try to resolve the path relative to the containing file's directory
        targetFile = containingDir.findFileByRelativePath(importPath);

        // If not found, try without the leading "./" if present
        if (targetFile == null && importPath.startsWith("./")) {
            targetFile = containingDir.findFileByRelativePath(importPath.substring(2));
        }

        // Strategy 2: Try searching in project base path
        if (targetFile == null) {
            String basePath = project.getBasePath();
            if (basePath != null) {
                VirtualFile projectRoot = fileSystem.findFileByPath(basePath);
                if (projectRoot != null) {
                    targetFile = projectRoot.findFileByRelativePath(importPath);
                }
            }
        }

        // Strategy 3: Try project-local providers directory (.kite/providers/)
        if (targetFile == null) {
            String basePath = project.getBasePath();
            if (basePath != null) {
                VirtualFile projectRoot = fileSystem.findFileByPath(basePath);
                if (projectRoot != null) {
                    VirtualFile providersDir = projectRoot.findFileByRelativePath(".kite/providers");
                    if (providersDir != null && providersDir.isDirectory()) {
                        targetFile = resolveInProviderDir(providersDir, importPath);
                    }
                }
            }
        }

        // Strategy 4: Try user-global providers directory (~/.kite/providers/)
        if (targetFile == null) {
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                VirtualFile userProvidersDir = fileSystem.findFileByPath(userHome + "/.kite/providers");
                if (userProvidersDir != null && userProvidersDir.isDirectory()) {
                    targetFile = resolveInProviderDir(userProvidersDir, importPath);
                }
            }
        }

        if (targetFile == null || !targetFile.exists()) {
            return null;
        }

        // Convert to PsiFile
        return PsiManager.getInstance(project).findFile(targetFile);
    }

    /**
     * Resolve an import path within a provider directory.
     * Handles both direct file paths and package-style paths.
     * Package-style: "aws.DatabaseConfig" → "aws/DatabaseConfig.kite"
     *
     * @param providerDir The provider directory to search in
     * @param importPath  The import path
     * @return The resolved VirtualFile, or null if not found
     */
    @Nullable
    private static VirtualFile resolveInProviderDir(VirtualFile providerDir, String importPath) {
        // First, try direct path
        VirtualFile targetFile = providerDir.findFileByRelativePath(importPath);
        if (targetFile != null && targetFile.exists()) {
            return targetFile;
        }

        // Try package-style resolution: convert dots to path separators and add .kite extension
        // Example: "aws.DatabaseConfig" → "aws/DatabaseConfig.kite"
        if (!importPath.contains("/") && !importPath.endsWith(".kite")) {
            String packagePath = importPath.replace('.', '/') + ".kite";
            targetFile = providerDir.findFileByRelativePath(packagePath);
            if (targetFile != null && targetFile.exists()) {
                return targetFile;
            }

            // Also try just the last component as a file name
            // Example: "aws.DatabaseConfig" → look for "DatabaseConfig.kite" in "aws/" folder
            int lastDot = importPath.lastIndexOf('.');
            if (lastDot > 0) {
                String folderPath = importPath.substring(0, lastDot).replace('.', '/');
                String fileName = importPath.substring(lastDot + 1) + ".kite";
                VirtualFile folder = providerDir.findFileByRelativePath(folderPath);
                if (folder != null && folder.isDirectory()) {
                    targetFile = folder.findChild(fileName);
                    if (targetFile != null && targetFile.exists()) {
                        return targetFile;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Check if a name is imported in a file (for wildcard imports, everything is imported).
     *
     * @param name            The name to check
     * @param importStatement The import statement to check
     * @return true if the name is imported by this statement
     */
    public static boolean isNameImported(@NotNull String name, @NotNull PsiElement importStatement) {
        // For now, we only support wildcard imports (import * from "...")
        // which imports everything
        for (PsiElement child = importStatement.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null && child.getNode().getElementType() == KiteTokenTypes.MULTIPLY) {
                return true; // Wildcard import includes everything
            }
        }
        return false;
    }
}
