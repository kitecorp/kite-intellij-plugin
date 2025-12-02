package cloud.kitelang.intellij.editor;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;

/**
 * Tests for auto-import on paste functionality.
 * When code is pasted containing undefined symbols that exist in other project files,
 * the plugin should automatically add the necessary import statements.
 */
public class KiteAutoImportOnPasteTest extends KiteTestBase {

    // ==================== Basic Auto-Import ====================

    public void testAutoImportUndefinedVariable() {
        // Setup: create a file with an exported variable
        addFile("common.kite", "var defaultRegion = \"us-east-1\"");

        // Main file with code referencing undefined symbol
        configureByText("var myRegion = defaultRegion");

        // Trigger auto-import
        triggerAutoImport(myFixture.getFile().getTextLength());

        // Should auto-import the variable
        String result = myFixture.getFile().getText();
        assertTrue("Should contain import statement", result.contains("import defaultRegion from \"common.kite\""));
        assertTrue("Should contain original code", result.contains("var myRegion = defaultRegion"));
    }

    public void testAutoImportUndefinedFunction() {
        addFile("utils.kite", "fun formatName(string name) string { return name }");

        configureByText("var result = formatName(\"test\")");

        triggerAutoImport(myFixture.getFile().getTextLength());

        String result = myFixture.getFile().getText();
        assertTrue("Should contain import statement", result.contains("import formatName from \"utils.kite\""));
        assertTrue("Should contain original code", result.contains("var result = formatName(\"test\")"));
    }

    public void testAutoImportUndefinedSchema() {
        addFile("schemas.kite", "schema DatabaseConfig { string host }");

        configureByText("resource DatabaseConfig myDb { host = \"localhost\" }");

        triggerAutoImport(myFixture.getFile().getTextLength());

        String result = myFixture.getFile().getText();
        assertTrue("Should contain import statement", result.contains("import DatabaseConfig from \"schemas.kite\""));
        assertTrue("Should contain original code", result.contains("resource DatabaseConfig myDb"));
    }

    public void testAutoImportUndefinedComponent() {
        addFile("components.kite", "component WebServer { input string port = \"8080\" }");

        configureByText("component WebServer myServer { port = \"3000\" }");

        triggerAutoImport(myFixture.getFile().getTextLength());

        String result = myFixture.getFile().getText();
        assertTrue("Should contain import statement", result.contains("import WebServer from \"components.kite\""));
    }

    // ==================== Multiple Symbols ====================

    public void testAutoImportMultipleSymbolsFromSameFile() {
        addFile("common.kite", """
                var region = "us-east-1"
                var env = "prod"
                """);

        configureByText("""
                var myRegion = region
                var myEnv = env
                """);

        triggerAutoImport(myFixture.getFile().getTextLength());

        String result = myFixture.getFile().getText();
        // Should import both symbols (possibly in single import or separate)
        assertTrue("Should contain region import", result.contains("region"));
        assertTrue("Should contain env import", result.contains("env"));
        assertTrue("Should have import from common.kite", result.contains("from \"common.kite\""));
    }

    public void testAutoImportSymbolsFromDifferentFiles() {
        addFile("regions.kite", "var defaultRegion = \"us-east-1\"");
        addFile("config.kite", "var defaultPort = 8080");

        configureByText("""
                var region = defaultRegion
                var port = defaultPort
                """);

        triggerAutoImport(myFixture.getFile().getTextLength());

        String result = myFixture.getFile().getText();
        assertTrue("Should import from regions.kite", result.contains("import defaultRegion from \"regions.kite\""));
        assertTrue("Should import from config.kite", result.contains("import defaultPort from \"config.kite\""));
    }

    // ==================== No Import Needed Cases ====================

    public void testNoImportForDefinedSymbol() {
        configureByText("""
                var existingVar = "hello"
                var result = existingVar
                """);

        triggerAutoImport(myFixture.getFile().getTextLength());

        String result = myFixture.getFile().getText();
        assertFalse("Should NOT add import for locally defined symbol", result.contains("import"));
    }

    public void testNoImportForAlreadyImportedSymbol() {
        addFile("common.kite", "var sharedVar = \"test\"");

        configureByText("""
                import sharedVar from "common.kite"
                var result = sharedVar
                """);

        triggerAutoImport(myFixture.getFile().getTextLength());

        String result = myFixture.getFile().getText();
        // Should have exactly one import line
        int importCount = countOccurrences(result);
        assertEquals("Should have exactly one import", 1, importCount);
    }

    public void testNoImportForLiteralAssignment() {
        configureByText("var x = 42");

        triggerAutoImport(myFixture.getFile().getTextLength());

        String result = myFixture.getFile().getText();
        assertFalse("Should NOT add import for literal assignment", result.contains("import"));
        assertEquals("var x = 42", result.trim());
    }

    public void testNoImportForKeywords() {
        configureByText("var x = true\nvar y = false\nvar z = null");

        triggerAutoImport(myFixture.getFile().getTextLength());

        String result = myFixture.getFile().getText();
        assertFalse("Should NOT import keywords", result.contains("import"));
    }

    public void testNoImportForBuiltinTypes() {
        configureByText("var string name = \"test\"");

        triggerAutoImport(myFixture.getFile().getTextLength());

        String result = myFixture.getFile().getText();
        assertFalse("Should NOT import builtin types", result.contains("import"));
    }

    // ==================== Existing Imports Merging ====================

    public void testMergesWithExistingImportFromSameFile() {
        addFile("common.kite", """
                var alpha = 1
                var beta = 2
                """);

        configureByText("""
                import alpha from "common.kite"
                var result = beta
                """);

        triggerAutoImport(myFixture.getFile().getTextLength());

        String result = myFixture.getFile().getText();
        // Should merge into single import
        assertTrue("Should have merged import",
                result.contains("import alpha, beta from \"common.kite\"") ||
                result.contains("import beta, alpha from \"common.kite\""));
    }

    // ==================== Edge Cases ====================

    public void testAutoImportIntoFileWithExistingCode() {
        addFile("common.kite", "var importedVar = \"value\"");

        configureByText("""
                var existingVar = "hello"
                var x = importedVar
                """);

        triggerAutoImport(myFixture.getFile().getTextLength());

        String result = myFixture.getFile().getText();
        assertTrue("Should add import at top", result.startsWith("import importedVar"));
        assertTrue("Should preserve existing code", result.contains("var existingVar"));
        assertTrue("Should contain the reference", result.contains("var x = importedVar"));
    }

    public void testNoImportForSymbolNotInProject() {
        configureByText("var x = nonExistentSymbol");

        triggerAutoImport(myFixture.getFile().getTextLength());

        String result = myFixture.getFile().getText();
        assertFalse("Should NOT add import for symbol not in project", result.contains("import"));
        assertTrue("Should keep the code unchanged", result.contains("var x = nonExistentSymbol"));
    }

    // ==================== Helper Methods ====================

    private void triggerAutoImport(int endOffset) {
        // Commit any pending changes
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

        // Call the auto-import service
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            KiteAutoImportService.processAutoImports(myFixture.getFile(), 0, endOffset);
        });

        // Refresh the fixture
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }

    private int countOccurrences(String text) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf("import sharedVar", index)) != -1) {
            count++;
            index += "import sharedVar".length();
        }
        return count;
    }
}
