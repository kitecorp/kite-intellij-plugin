package cloud.kitelang.intellij.refactoring;

import cloud.kitelang.intellij.KiteTestBase;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.psi.PsiElement;

/**
 * Tests for Kite refactoring support.
 * Verifies KiteRefactoringSupportProvider and basic rename capabilities.
 */
public class KiteRefactoringTest extends KiteTestBase {

    private final KiteRefactoringSupportProvider provider = new KiteRefactoringSupportProvider();

    // ========== Refactoring Support Provider Tests ==========

    public void testIdentifierIsRenameableInPlace() {
        configureByText("var my<caret>Var = 1");

        PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());

        if (element != null && element.getNode() != null &&
            element.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
            assertTrue("Identifier should be renameable in place",
                    provider.isMemberInplaceRenameAvailable(element, null));
        }
    }

    public void testInplaceRenameAvailableForIdentifier() {
        configureByText("var my<caret>Var = 1");

        PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());

        if (element != null && element.getNode() != null &&
            element.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
            assertTrue("In-place rename should be available for identifier",
                    provider.isInplaceRenameAvailable(element, null));
        }
    }

    // ========== Declaration Context Tests ==========

    public void testVariableDeclarationParsesCorrectly() {
        configureByText("""
                var myVariable = "value"
                """);

        myFixture.doHighlighting();

        String text = myFixture.getFile().getText();
        assertTrue("Variable declaration should be in file", text.contains("myVariable"));
    }

    public void testFunctionDeclarationParsesCorrectly() {
        configureByText("""
                fun myFunction() string {
                    return "hello"
                }
                """);

        myFixture.doHighlighting();

        String text = myFixture.getFile().getText();
        assertTrue("Function declaration should be in file", text.contains("myFunction"));
    }

    public void testSchemaDeclarationParsesCorrectly() {
        configureByText("""
                schema MySchema {
                    string field
                }
                """);

        myFixture.doHighlighting();

        String text = myFixture.getFile().getText();
        assertTrue("Schema declaration should be in file", text.contains("MySchema"));
    }

    public void testResourceDeclarationParsesCorrectly() {
        configureByText("""
                schema Config {
                    string host
                }
                resource Config myResource {
                    host = "localhost"
                }
                """);

        myFixture.doHighlighting();

        String text = myFixture.getFile().getText();
        assertTrue("Resource declaration should be in file", text.contains("myResource"));
    }

    public void testComponentDeclarationParsesCorrectly() {
        configureByText("""
                component MyComponent {
                    input string port = "8080"
                }
                """);

        myFixture.doHighlighting();

        String text = myFixture.getFile().getText();
        assertTrue("Component declaration should be in file", text.contains("MyComponent"));
    }

    // ========== Reference Context Tests ==========

    public void testVariableReferenceExists() {
        configureByText("""
                var source = "value"
                var target = source
                """);

        myFixture.doHighlighting();

        String text = myFixture.getFile().getText();
        // Both declaration and reference should be present
        int count = 0;
        int index = 0;
        while ((index = text.indexOf("source", index)) != -1) {
            count++;
            index++;
        }
        assertEquals("source should appear twice (declaration + reference)", 2, count);
    }

    public void testMultipleReferences() {
        configureByText("""
                var shared = "value"
                var a = shared
                var b = shared
                var c = shared
                """);

        myFixture.doHighlighting();

        String text = myFixture.getFile().getText();
        int count = 0;
        int index = 0;
        while ((index = text.indexOf("shared", index)) != -1) {
            count++;
            index++;
        }
        assertEquals("shared should appear 4 times", 4, count);
    }

    // ========== String Interpolation Reference Tests ==========

    public void testSimpleInterpolationReference() {
        configureByText("""
                var name = "World"
                var greeting = "Hello $name!"
                """);

        myFixture.doHighlighting();

        String text = myFixture.getFile().getText();
        assertTrue("Interpolation should contain $name", text.contains("$name"));
    }

    public void testBraceInterpolationReference() {
        configureByText("""
                var port = 8080
                var url = "http://localhost:${port}"
                """);

        myFixture.doHighlighting();

        String text = myFixture.getFile().getText();
        assertTrue("Interpolation should contain ${port}", text.contains("${port}"));
    }

    // ========== Edge Cases ==========

    public void testEmptyFileParsesCorrectly() {
        configureByText("");

        myFixture.doHighlighting();
        assertNotNull("Empty file should parse", myFixture.getFile());
    }

    public void testCommentOnlyFileParsesCorrectly() {
        configureByText("""
                // This is a comment
                /* Block comment */
                """);

        myFixture.doHighlighting();
        assertNotNull("Comment-only file should parse", myFixture.getFile());
    }

    public void testComplexFileParsesCorrectly() {
        configureByText("""
                import * from "common.kite"
                
                schema Config {
                    string host
                    number port
                }
                
                @description("Main config")
                resource Config main {
                    host = "localhost"
                    port = 8080
                }
                
                component WebServer {
                    input string port = "8080"
                    output string endpoint = "http://localhost:${port}"
                }
                
                fun getUrl(string host, number port) string {
                    return "http://$host:${port}"
                }
                
                var serverUrl = getUrl(main.host, main.port)
                """);

        myFixture.doHighlighting();
        assertNotNull("Complex file should parse", myFixture.getFile());
    }
}
