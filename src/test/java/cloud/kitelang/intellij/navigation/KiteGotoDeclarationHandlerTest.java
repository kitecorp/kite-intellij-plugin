package cloud.kitelang.intellij.navigation;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;

/**
 * Tests for general go-to-declaration functionality.
 * Verifies navigation for variables, functions, resources, components, and cross-file imports.
 */
public class KiteGotoDeclarationHandlerTest extends KiteTestBase {

    private final KiteGotoDeclarationHandler handler = new KiteGotoDeclarationHandler();

    // ========== Local Variable Navigation Tests ==========

    public void testNavigateToVariableDeclaration() {
        configureByText("""
                var myVar = "hello"
                var x = myVar
                """);

        // Find the reference to myVar in the second line
        int offset = myFixture.getFile().getText().lastIndexOf("myVar");
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element at myVar reference", elementAtCaret);

        Editor editor = myFixture.getEditor();
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, offset, editor);

        assertNotNull("Should have navigation targets", targets);
        assertTrue("Should have at least one target", targets.length > 0);
        assertEquals("myVar", targets[0].getText());
    }

    public void testNavigateToInputDeclaration() {
        configureByText("""
                input string name = "default"
                var x = name
                """);

        int offset = myFixture.getFile().getText().lastIndexOf("name");
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element at name reference", elementAtCaret);

        Editor editor = myFixture.getEditor();
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, offset, editor);

        assertNotNull("Should have navigation targets for input", targets);
        assertTrue("Should have at least one target", targets.length > 0);
        assertEquals("name", targets[0].getText());
    }

    public void testNavigateToOutputDeclaration() {
        configureByText("""
                output string result = "value"
                var x = result
                """);

        int offset = myFixture.getFile().getText().lastIndexOf("result");
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element at result reference", elementAtCaret);

        Editor editor = myFixture.getEditor();
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, offset, editor);

        assertNotNull("Should have navigation targets for output", targets);
        assertTrue("Should have at least one target", targets.length > 0);
        assertEquals("result", targets[0].getText());
    }

    public void testNavigateToFunctionDeclaration() {
        configureByText("""
                fun greet(string name) string {
                    return "Hello " + name
                }
                var x = greet("World")
                """);

        int offset = myFixture.getFile().getText().lastIndexOf("greet");
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element at greet call", elementAtCaret);

        Editor editor = myFixture.getEditor();
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, offset, editor);

        assertNotNull("Should have navigation targets for function", targets);
        assertTrue("Should have at least one target", targets.length > 0);
        assertEquals("greet", targets[0].getText());
    }

    public void testNavigateToResourceDeclaration() {
        configureByText("""
                schema Config {
                    string host
                }
                resource Config myDb {
                    host = "localhost"
                }
                var x = myDb
                """);

        int offset = myFixture.getFile().getText().lastIndexOf("myDb");
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element at myDb reference", elementAtCaret);

        Editor editor = myFixture.getEditor();
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, offset, editor);

        assertNotNull("Should have navigation targets for resource", targets);
        assertTrue("Should have at least one target", targets.length > 0);
        assertEquals("myDb", targets[0].getText());
    }

    // ========== Cross-File Navigation Tests ==========

    public void testNavigateToImportedVariable() {
        addFile("common.kite", """
                var sharedVar = "shared"
                """);

        configureByText("""
                import sharedVar from "common.kite"
                var x = sharedVar
                """);

        int offset = myFixture.getFile().getText().lastIndexOf("sharedVar");
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element", elementAtCaret);

        Editor editor = myFixture.getEditor();
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, offset, editor);

        assertNotNull("Should have navigation targets", targets);
        assertTrue("Should have at least one target", targets.length > 0);
        assertEquals("sharedVar", targets[0].getText());
        assertEquals("common.kite", targets[0].getContainingFile().getName());
    }

    public void testNavigateViaWildcardImport() {
        addFile("common.kite", """
                var alpha = "a"
                var beta = "b"
                """);

        configureByText("""
                import * from "common.kite"
                var x = alpha
                """);

        int offset = myFixture.getFile().getText().indexOf("= alpha") + 2;
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element at alpha", elementAtCaret);

        Editor editor = myFixture.getEditor();
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, offset, editor);

        assertNotNull("Should have navigation targets via wildcard import", targets);
        assertTrue("Should have at least one target", targets.length > 0);
        assertEquals("alpha", targets[0].getText());
        assertEquals("common.kite", targets[0].getContainingFile().getName());
    }

    public void testNavigateToImportedFunction() {
        addFile("utils.kite", """
                fun helper() string {
                    return "help"
                }
                """);

        configureByText("""
                import helper from "utils.kite"
                var x = helper()
                """);

        int offset = myFixture.getFile().getText().lastIndexOf("helper");
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element at helper call", elementAtCaret);

        Editor editor = myFixture.getEditor();
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, offset, editor);

        assertNotNull("Should have navigation targets for imported function", targets);
        assertTrue("Should have at least one target", targets.length > 0);
        assertEquals("helper", targets[0].getText());
        assertEquals("utils.kite", targets[0].getContainingFile().getName());
    }

    // ========== Function Parameter Navigation Tests ==========

    public void testNavigateToFunctionParameter() {
        configureByText("""
                fun greet(string name) string {
                    return "Hello " + name
                }
                """);

        // Find the 'name' reference inside the function body (after the +)
        int offset = myFixture.getFile().getText().lastIndexOf("name");
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element at name usage in body", elementAtCaret);

        Editor editor = myFixture.getEditor();
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, offset, editor);

        assertNotNull("Should have navigation targets for parameter", targets);
        assertTrue("Should have at least one target", targets.length > 0);
        assertEquals("name", targets[0].getText());
    }

    // ========== For Loop Variable Navigation Tests ==========

    public void testNavigateToForLoopVariable() {
        configureByText("""
                var items = ["a", "b", "c"]
                for item in items {
                    var x = item
                }
                """);

        // Find the 'item' reference inside the loop body
        int offset = myFixture.getFile().getText().lastIndexOf("item");
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element at item usage", elementAtCaret);

        Editor editor = myFixture.getEditor();
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, offset, editor);

        assertNotNull("Should have navigation targets for loop variable", targets);
        assertTrue("Should have at least one target", targets.length > 0);
        assertEquals("item", targets[0].getText());
    }

    // ========== Edge Cases ==========

    public void testUndefinedSymbolNoNavigation() {
        configureByText("""
                var x = undefined
                """);

        int offset = myFixture.getFile().getText().indexOf("undefined");
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element", elementAtCaret);

        Editor editor = myFixture.getEditor();
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, offset, editor);

        assertTrue("Undefined symbol should not resolve",
                targets == null || targets.length == 0);
    }

    public void testKeywordNoNavigation() {
        configureByText("""
                var x = 1
                """);

        // Try navigating from 'var' keyword
        int offset = myFixture.getFile().getText().indexOf("var");
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element", elementAtCaret);

        Editor editor = myFixture.getEditor();
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, offset, editor);

        // Keywords should not navigate
        assertTrue("Keywords should not have navigation targets",
                targets == null || targets.length == 0);
    }

    public void testSelfReferenceIsDeclaration() {
        configureByText("""
                var myVar = 123
                """);

        // Navigate from the declaration name itself
        int offset = myFixture.getFile().getText().indexOf("myVar");
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element", elementAtCaret);

        Editor editor = myFixture.getEditor();
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, offset, editor);

        // When on declaration, may return usages or nothing
        // This tests that it doesn't crash
        // Declaration names typically don't navigate to themselves
    }
}
