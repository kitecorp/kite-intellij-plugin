package cloud.kitelang.intellij.util;

import cloud.kitelang.intellij.KiteTestBase;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;

/**
 * Tests for KiteImportValidationHelper utility class.
 */
public class KiteImportValidationHelperTest extends KiteTestBase {

    // ========== isWildcardImport tests ==========

    public void testIsWildcardImportTrue() {
        configureByText("""
                import * from "common.kite"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement importStatement = findFirstElementOfType(file, KiteElementTypes.IMPORT_STATEMENT);

        if (importStatement != null) {
            assertTrue("import * should be a wildcard import",
                       KiteImportValidationHelper.isWildcardImport(importStatement));
        } else {
            // If no IMPORT_STATEMENT element, use token-based check
            PsiElement importToken = findFirstElementOfType(file, KiteTokenTypes.IMPORT);
            assertNotNull("Should find IMPORT token", importToken);
            assertTrue("import * should be a wildcard import",
                       KiteImportValidationHelper.isWildcardImportFromToken(importToken));
        }
    }

    public void testIsWildcardImportFalse() {
        configureByText("""
                import Config from "common.kite"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement importStatement = findFirstElementOfType(file, KiteElementTypes.IMPORT_STATEMENT);

        if (importStatement != null) {
            assertFalse("import Config should not be a wildcard import",
                        KiteImportValidationHelper.isWildcardImport(importStatement));
        } else {
            PsiElement importToken = findFirstElementOfType(file, KiteTokenTypes.IMPORT);
            assertNotNull("Should find IMPORT token", importToken);
            assertFalse("import Config should not be a wildcard import",
                        KiteImportValidationHelper.isWildcardImportFromToken(importToken));
        }
    }

    // ========== extractImportPathFromToken tests ==========

    public void testExtractImportPathFromToken() {
        configureByText("""
                import * from "common.kite"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement importToken = findFirstElementOfType(file, KiteTokenTypes.IMPORT);
        assertNotNull("Should find IMPORT token", importToken);

        String path = KiteImportValidationHelper.extractImportPathFromToken(importToken);
        assertEquals("common.kite", path);
    }

    public void testExtractImportPathFromTokenWithSingleQuotes() {
        configureByText("""
                import * from 'other.kite'
                """);

        PsiFile file = myFixture.getFile();
        PsiElement importToken = findFirstElementOfType(file, KiteTokenTypes.IMPORT);
        assertNotNull("Should find IMPORT token", importToken);

        String path = KiteImportValidationHelper.extractImportPathFromToken(importToken);
        assertEquals("other.kite", path);
    }

    public void testExtractImportPathFromTokenNoFrom() {
        configureByText("""
                import Config
                """);

        PsiFile file = myFixture.getFile();
        PsiElement importToken = findFirstElementOfType(file, KiteTokenTypes.IMPORT);
        assertNotNull("Should find IMPORT token", importToken);

        String path = KiteImportValidationHelper.extractImportPathFromToken(importToken);
        assertNull("Should return null when no 'from' keyword", path);
    }

    // ========== extractStringContent tests ==========

    public void testExtractStringContentDoubleQuotes() {
        String content = KiteImportValidationHelper.extractStringContent("\"hello\"");
        assertEquals("hello", content);
    }

    public void testExtractStringContentSingleQuotes() {
        String content = KiteImportValidationHelper.extractStringContent("'hello'");
        assertEquals("hello", content);
    }

    public void testExtractStringContentEmptyString() {
        String content = KiteImportValidationHelper.extractStringContent("\"\"");
        assertEquals("", content);
    }

    public void testExtractStringContentNull() {
        String content = KiteImportValidationHelper.extractStringContent(null);
        assertNull(content);
    }

    public void testExtractStringContentTooShort() {
        String content = KiteImportValidationHelper.extractStringContent("x");
        assertNull(content);
    }

    public void testExtractStringContentNoQuotes() {
        String content = KiteImportValidationHelper.extractStringContent("hello");
        assertEquals("hello", content); // Returns as-is if no quotes
    }

    // ========== isNonImportStatement tests ==========

    public void testIsNonImportStatementVariableDeclaration() {
        assertTrue(KiteImportValidationHelper.isNonImportStatement(KiteElementTypes.VARIABLE_DECLARATION));
    }

    public void testIsNonImportStatementInputDeclaration() {
        assertTrue(KiteImportValidationHelper.isNonImportStatement(KiteElementTypes.INPUT_DECLARATION));
    }

    public void testIsNonImportStatementOutputDeclaration() {
        assertTrue(KiteImportValidationHelper.isNonImportStatement(KiteElementTypes.OUTPUT_DECLARATION));
    }

    public void testIsNonImportStatementResourceDeclaration() {
        assertTrue(KiteImportValidationHelper.isNonImportStatement(KiteElementTypes.RESOURCE_DECLARATION));
    }

    public void testIsNonImportStatementSchemaDeclaration() {
        assertTrue(KiteImportValidationHelper.isNonImportStatement(KiteElementTypes.SCHEMA_DECLARATION));
    }

    public void testIsNonImportStatementFunctionDeclaration() {
        assertTrue(KiteImportValidationHelper.isNonImportStatement(KiteElementTypes.FUNCTION_DECLARATION));
    }

    public void testIsNonImportStatementRawKeywords() {
        assertTrue(KiteImportValidationHelper.isNonImportStatement(KiteTokenTypes.VAR));
        assertTrue(KiteImportValidationHelper.isNonImportStatement(KiteTokenTypes.INPUT));
        assertTrue(KiteImportValidationHelper.isNonImportStatement(KiteTokenTypes.OUTPUT));
        assertTrue(KiteImportValidationHelper.isNonImportStatement(KiteTokenTypes.RESOURCE));
        assertTrue(KiteImportValidationHelper.isNonImportStatement(KiteTokenTypes.SCHEMA));
        assertTrue(KiteImportValidationHelper.isNonImportStatement(KiteTokenTypes.FUN));
    }

    public void testIsNonImportStatementImportIsNotNonImport() {
        // IMPORT is not a "non-import" statement
        assertFalse(KiteImportValidationHelper.isNonImportStatement(KiteTokenTypes.IMPORT));
    }

    // ========== findImportPathString tests ==========

    public void testFindImportPathString() {
        configureByText("""
                import * from "common.kite"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement importStatement = findFirstElementOfType(file, KiteElementTypes.IMPORT_STATEMENT);

        if (importStatement != null) {
            PsiElement pathElement = KiteImportValidationHelper.findImportPathString(importStatement);
            assertNotNull("Should find path string element", pathElement);
        }
    }

    // ========== findImportStatementEnd tests ==========

    public void testFindImportStatementEnd() {
        configureByText("""
                import * from "common.kite"
                var x = 1
                """);

        PsiFile file = myFixture.getFile();
        PsiElement importToken = findFirstElementOfType(file, KiteTokenTypes.IMPORT);
        assertNotNull("Should find IMPORT token", importToken);

        PsiElement endElement = KiteImportValidationHelper.findImportStatementEnd(importToken);
        assertNotNull("Should find end of import statement", endElement);
    }

    // ========== Helper methods ==========

    private PsiElement findFirstElementOfType(PsiElement root, IElementType type) {
        if (root.getNode() != null && root.getNode().getElementType() == type) {
            return root;
        }
        for (PsiElement child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            PsiElement found = findFirstElementOfType(child, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
