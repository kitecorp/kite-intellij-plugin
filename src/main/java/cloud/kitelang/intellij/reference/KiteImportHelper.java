package cloud.kitelang.intellij.reference;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
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

    // Pattern to match import statements: import * from "path" or import Symbol from 'path'
    // Matches both wildcard imports (import * from) and named imports (import name1, name2 from)
    // Uses negative lookbehind to exclude commented lines (// at start of line or with leading whitespace)
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s+(?:\\*|[\\w,\\s]+)\\s+from\\s+[\"']([^\"']+)[\"']",
            Pattern.MULTILINE
    );

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
                    return extractStringContent(child);
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

    /**
     * Check if a symbol name is imported from any import statement in a file.
     * For named imports: only specifically named symbols are importable
     * For wildcard imports: all symbols are importable
     *
     * @param symbolName     The symbol name to check
     * @param containingFile The file containing the import statements
     * @return true if the symbol is imported by any import statement
     */
    public static boolean isSymbolImported(@NotNull String symbolName, @NotNull PsiFile containingFile) {
        String fileText = containingFile.getText();

        // Pattern to match import statements: import <symbols> from "path"
        // Group 1: the symbols part (either * or name1, name2, ...)
        Pattern importPattern = Pattern.compile(
                "^\\s*import\\s+([\\w,\\s*]+)\\s+from\\s+[\"'][^\"']+[\"']",
                Pattern.MULTILINE
        );

        Matcher matcher = importPattern.matcher(fileText);
        while (matcher.find()) {
            String symbolsPart = matcher.group(1).trim();

            // Check for wildcard import
            if (symbolsPart.equals("*")) {
                return true; // Wildcard imports everything
            }

            // Check for named imports: split by comma and check each
            String[] importedSymbols = symbolsPart.split("\\s*,\\s*");
            for (String imported : importedSymbols) {
                if (imported.trim().equals(symbolName)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Get the file from which a symbol is imported.
     * Returns null if the symbol is not imported or is defined locally.
     *
     * @param symbolName     The symbol name to find
     * @param containingFile The file containing the import statements
     * @return The PsiFile where the symbol is imported from, or null
     */
    @Nullable
    public static PsiFile getImportSourceFile(@NotNull String symbolName, @NotNull PsiFile containingFile) {
        String fileText = containingFile.getText();

        // Pattern to match import statements: import <symbols> from "path"
        Pattern importPattern = Pattern.compile(
                "^\\s*import\\s+([\\w,\\s*]+)\\s+from\\s+[\"']([^\"']+)[\"']",
                Pattern.MULTILINE
        );

        Matcher matcher = importPattern.matcher(fileText);
        while (matcher.find()) {
            String symbolsPart = matcher.group(1).trim();
            String importPath = matcher.group(2);

            boolean symbolMatches = false;

            // Check for wildcard import
            if (symbolsPart.equals("*")) {
                symbolMatches = true;
            } else {
                // Check for named imports
                String[] importedSymbols = symbolsPart.split("\\s*,\\s*");
                for (String imported : importedSymbols) {
                    if (imported.trim().equals(symbolName)) {
                        symbolMatches = true;
                        break;
                    }
                }
            }

            if (symbolMatches) {
                PsiFile sourceFile = resolveFilePath(importPath, containingFile);
                if (sourceFile != null) {
                    return sourceFile;
                }
            }
        }

        return null;
    }

    /**
     * Find all .kite files in the project.
     *
     * @param project The project to search
     * @return List of PsiFiles for all .kite files in the project
     */
    @NotNull
    public static List<PsiFile> getAllKiteFilesInProject(@NotNull Project project) {
        List<PsiFile> result = new ArrayList<>();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        PsiManager psiManager = PsiManager.getInstance(project);

        // Use VFS to recursively find all .kite files
        String basePath = project.getBasePath();
        if (basePath != null) {
            VirtualFile projectRoot = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(basePath);
            if (projectRoot != null) {
                findKiteFilesRecursively(projectRoot, psiManager, result);
            }
        }

        return result;
    }

    /**
     * Recursively find all .kite files in a directory.
     */
    private static void findKiteFilesRecursively(@NotNull VirtualFile dir, @NotNull PsiManager psiManager, @NotNull List<PsiFile> result) {
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                // Skip common non-source directories
                String name = child.getName();
                if (!name.startsWith(".") && !name.equals("build") && !name.equals("node_modules") && !name.equals("out")) {
                    findKiteFilesRecursively(child, psiManager, result);
                }
            } else if (child.getName().endsWith(".kite")) {
                PsiFile psiFile = psiManager.findFile(child);
                if (psiFile != null) {
                    result.add(psiFile);
                }
            }
        }
    }

    /**
     * Get the relative import path from one file to another.
     * Used for generating import statements.
     *
     * @param fromFile The file containing the import statement
     * @param toFile   The file to import from
     * @return The relative path to use in the import statement
     */
    @Nullable
    public static String getRelativeImportPath(@NotNull PsiFile fromFile, @NotNull PsiFile toFile) {
        VirtualFile fromVFile = fromFile.getVirtualFile();
        VirtualFile toVFile = toFile.getVirtualFile();
        if (fromVFile == null || toVFile == null) {
            return null;
        }

        VirtualFile fromDir = fromVFile.getParent();
        if (fromDir == null) {
            return null;
        }

        // If in same directory, just use the file name
        VirtualFile toDir = toVFile.getParent();
        if (fromDir.equals(toDir)) {
            return toVFile.getName();
        }

        // Try to find relative path
        String fromPath = fromDir.getPath();
        String toPath = toVFile.getPath();

        // Simple case: if toFile is in the same project directory tree
        // Calculate relative path using path manipulation
        String[] fromParts = fromPath.split("/");
        String[] toParts = toPath.split("/");

        // Find common prefix length
        int commonLen = 0;
        int minLen = Math.min(fromParts.length, toParts.length);
        for (int i = 0; i < minLen; i++) {
            if (fromParts[i].equals(toParts[i])) {
                commonLen = i + 1;
            } else {
                break;
            }
        }

        // Build relative path
        StringBuilder relPath = new StringBuilder();
        // Add ".." for each directory we need to go up
        for (int i = commonLen; i < fromParts.length; i++) {
            if (!relPath.isEmpty()) relPath.append("/");
            relPath.append("..");
        }
        // Add the remaining path parts
        for (int i = commonLen; i < toParts.length; i++) {
            if (!relPath.isEmpty()) relPath.append("/");
            relPath.append(toParts[i]);
        }

        return !relPath.isEmpty() ? relPath.toString() : toVFile.getName();
    }

    /**
     * Generic utility for searching through imports recursively.
     * This replaces duplicate *InImports() methods throughout the codebase.
     * <p>
     * Usage example:
     * <pre>
     * String type = KiteImportHelper.searchInImports(file, importedFile ->
     *     findIdentifierType(importedFile.getNode(), identifierName)
     * );
     * </pre>
     *
     * @param file         The starting file
     * @param fileSearcher Function that searches a single file and returns result (or null if not found)
     * @param <T>          The return type
     * @return The first non-null result found, or null if not found in any imported file
     */
    @Nullable
    public static <T> T searchInImports(@NotNull PsiFile file, @NotNull Function<PsiFile, T> fileSearcher) {
        return searchInImportsRecursive(file, fileSearcher, new HashSet<>());
    }

    /**
     * Search through imports recursively, tracking visited files to prevent cycles.
     */
    @Nullable
    private static <T> T searchInImportsRecursive(@NotNull PsiFile file,
                                                  @NotNull Function<PsiFile, T> fileSearcher,
                                                  @NotNull Set<String> visited) {
        var importedFiles = getImportedFiles(file);
        for (var importedFile : importedFiles) {
            if (importedFile == null || importedFile.getVirtualFile() == null) {
                continue;
            }

            var path = importedFile.getVirtualFile().getPath();
            if (visited.contains(path)) {
                continue;
            }
            visited.add(path);

            // Search in this file
            var result = fileSearcher.apply(importedFile);
            if (result != null) {
                return result;
            }

            // Recursively search in imported files
            result = searchInImportsRecursive(importedFile, fileSearcher, visited);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * Iterate over all imported files recursively, calling the consumer for each file.
     * Use this for aggregation patterns that need to collect data from all imports.
     * <p>
     * Usage example:
     * <pre>
     * Map&lt;String, String&gt; results = new HashMap&lt;&gt;();
     * KiteImportHelper.forEachImport(file, importedFile ->
     *     collectFromFile(importedFile, results)
     * );
     * </pre>
     *
     * @param file         The starting file
     * @param fileConsumer Consumer that processes each imported file
     */
    public static void forEachImport(@NotNull PsiFile file, @NotNull Consumer<PsiFile> fileConsumer) {
        forEachImportRecursive(file, fileConsumer, new HashSet<>());
    }

    /**
     * Iterate over imports recursively, tracking visited files to prevent cycles.
     */
    private static void forEachImportRecursive(@NotNull PsiFile file,
                                               @NotNull Consumer<PsiFile> fileConsumer,
                                               @NotNull Set<String> visited) {
        var importedFiles = getImportedFiles(file);
        for (var importedFile : importedFiles) {
            if (importedFile == null || importedFile.getVirtualFile() == null) {
                continue;
            }

            var path = importedFile.getVirtualFile().getPath();
            if (visited.contains(path)) {
                continue;
            }
            visited.add(path);

            // Process this file
            fileConsumer.accept(importedFile);

            // Recursively process imported files
            forEachImportRecursive(importedFile, fileConsumer, visited);
        }
    }

    /**
     * Get all symbols currently imported into this file.
     * For wildcard imports, returns all exported symbols from the imported file.
     * For named imports, returns only the specifically named symbols.
     *
     * @param file The file to analyze
     * @return Set of all imported symbol names
     */
    @NotNull
    public static Set<String> getImportedSymbols(@NotNull PsiFile file) {
        Set<String> symbols = new HashSet<>();
        String fileText = file.getText();

        Pattern importPattern = Pattern.compile(
                "^\\s*import\\s+([\\w,\\s*]+)\\s+from\\s+[\"']([^\"']+)[\"']",
                Pattern.MULTILINE
        );

        Matcher matcher = importPattern.matcher(fileText);
        while (matcher.find()) {
            String symbolsPart = matcher.group(1).trim();
            String importPath = matcher.group(2);

            if (symbolsPart.equals("*")) {
                // Wildcard import - get all exports from the file
                PsiFile importedFile = resolveFilePath(importPath, file);
                if (importedFile != null) {
                    symbols.addAll(getExportedSymbols(importedFile));
                }
            } else {
                // Named imports
                String[] importedSymbols = symbolsPart.split("\\s*,\\s*");
                for (String symbol : importedSymbols) {
                    String trimmed = symbol.trim();
                    if (!trimmed.isEmpty()) {
                        symbols.add(trimmed);
                    }
                }
            }
        }

        return symbols;
    }

    /**
     * Get all symbols exported from this file (top-level declarations).
     *
     * @param file The file to analyze
     * @return Set of all exported symbol names
     */
    @NotNull
    public static Set<String> getExportedSymbols(@NotNull PsiFile file) {
        Set<String> exports = new HashSet<>();

        for (PsiElement child = file.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;

            IElementType type = child.getNode().getElementType();
            if (isExportableDeclaration(type)) {
                String name = findDeclarationName(child, type);
                if (name != null && !name.isEmpty()) {
                    exports.add(name);
                }
            }
        }

        return exports;
    }

    /**
     * Check if this element type represents an exportable declaration.
     */
    private static boolean isExportableDeclaration(IElementType type) {
        return type == KiteElementTypes.VARIABLE_DECLARATION ||
               type == KiteElementTypes.FUNCTION_DECLARATION ||
               type == KiteElementTypes.SCHEMA_DECLARATION ||
               type == KiteElementTypes.COMPONENT_DECLARATION ||
               type == KiteElementTypes.RESOURCE_DECLARATION ||
               type == KiteElementTypes.TYPE_DECLARATION;
    }

    /**
     * Find the name of a declaration element.
     */
    @Nullable
    private static String findDeclarationName(@NotNull PsiElement declaration, IElementType declType) {
        for (PsiElement child = declaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null && child.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                PsiElement next = skipWhitespaceForward(child.getNextSibling());
                if (next != null && next.getNode() != null) {
                    IElementType nextType = next.getNode().getElementType();
                    if (nextType == KiteTokenTypes.ASSIGN ||
                        nextType == KiteTokenTypes.LBRACE ||
                        nextType == KiteTokenTypes.COLON ||
                        nextType == KiteTokenTypes.PLUS_ASSIGN ||
                        nextType == KiteTokenTypes.LPAREN) {  // For function declarations
                        return child.getText();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Skip whitespace tokens moving forward.
     */
    @Nullable
    private static PsiElement skipWhitespaceForward(@Nullable PsiElement element) {
        while (element != null && isWhitespaceElement(element)) {
            element = element.getNextSibling();
        }
        return element;
    }

    /**
     * Check if element is whitespace.
     */
    private static boolean isWhitespaceElement(@NotNull PsiElement element) {
        if (element.getNode() == null) return false;
        IElementType type = element.getNode().getElementType();
        return type == com.intellij.psi.TokenType.WHITE_SPACE ||
               type == KiteTokenTypes.NL ||
               type == KiteTokenTypes.WHITESPACE ||
               type == KiteTokenTypes.NEWLINE;
    }

    /**
     * Get imports grouped by file path.
     * Returns a map of file path -> set of imported symbols.
     *
     * @param file The file to analyze
     * @return Map of file paths to their imported symbols
     */
    @NotNull
    public static Map<String, Set<String>> getImportsByFile(@NotNull PsiFile file) {
        Map<String, Set<String>> imports = new LinkedHashMap<>();
        String fileText = file.getText();

        Pattern importPattern = Pattern.compile(
                "^\\s*import\\s+([\\w,\\s*]+)\\s+from\\s+[\"']([^\"']+)[\"']",
                Pattern.MULTILINE
        );

        Matcher matcher = importPattern.matcher(fileText);
        while (matcher.find()) {
            String symbolsPart = matcher.group(1).trim();
            String importPath = matcher.group(2);

            Set<String> symbols = imports.computeIfAbsent(importPath, k -> new LinkedHashSet<>());

            if (!symbolsPart.equals("*")) {
                String[] importedSymbols = symbolsPart.split("\\s*,\\s*");
                for (String symbol : importedSymbols) {
                    String trimmed = symbol.trim();
                    if (!trimmed.isEmpty()) {
                        symbols.add(trimmed);
                    }
                }
            }
            // For wildcard imports, we keep empty set to indicate the file is imported
        }

        return imports;
    }

    /**
     * Get the relative path from one file to another.
     * Alias for getRelativeImportPath for cleaner API.
     *
     * @param fromFile The source file
     * @param toFile   The target file
     * @return The relative path string
     */
    @Nullable
    public static String getRelativePath(@NotNull PsiFile fromFile, @NotNull PsiFile toFile) {
        return getRelativeImportPath(fromFile, toFile);
    }
}
