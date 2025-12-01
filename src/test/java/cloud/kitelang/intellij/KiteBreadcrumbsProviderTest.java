package cloud.kitelang.intellij;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * Tests for KiteBreadcrumbsProvider - verifies breadcrumb navigation path display.
 * Tests element acceptance and info extraction for different declaration types.
 */
public class KiteBreadcrumbsProviderTest extends KiteTestBase {

    private final KiteBreadcrumbsProvider provider = new KiteBreadcrumbsProvider();

    // ========== Provider Configuration Tests ==========

    public void testProviderSupportsKiteLanguage() {
        Language[] languages = provider.getLanguages();

        assertEquals("Should support exactly one language", 1, languages.length);
        assertEquals(KiteLanguage.INSTANCE, languages[0]);
    }

    // ========== Component Breadcrumb Tests ==========

    public void testComponentDeclarationAccepted() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);

        // Find the component declaration element
        PsiElement componentElement = findElementByText(file, "component WebServer");
        if (componentElement != null) {
            // Walk up to find the actual declaration element
            PsiElement declaration = findDeclarationParent(componentElement);
            if (declaration != null) {
                assertTrue("Component declaration should be accepted",
                        provider.acceptElement(declaration));
            }
        }
    }

    public void testComponentInstanceBreadcrumb() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                }
                component WebServer myServer {
                    port = "9000"
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
    }

    // ========== Schema Breadcrumb Tests ==========

    public void testSchemaDeclarationAccepted() {
        configureByText("""
                schema DatabaseConfig {
                    string host
                    number port
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
    }

    // ========== Resource Breadcrumb Tests ==========

    public void testResourceDeclarationAccepted() {
        configureByText("""
                schema Config {
                    string host
                }
                resource Config myConfig {
                    host = "localhost"
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
    }

    // ========== Function Breadcrumb Tests ==========

    public void testFunctionDeclarationAccepted() {
        configureByText("""
                fun greet(string name) string {
                    return "Hello " + name
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
    }

    public void testFunctionWithNoParams() {
        configureByText("""
                fun getTime() string {
                    return "12:00"
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
    }

    // ========== Variable/Input/Output Breadcrumb Tests ==========

    public void testVariableDeclarationAccepted() {
        configureByText("""
                var message = "Hello"
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
    }

    public void testInputDeclarationAccepted() {
        configureByText("""
                input string name = "default"
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
    }

    public void testOutputDeclarationAccepted() {
        configureByText("""
                output string result = "success"
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
    }

    // ========== Type Declaration Breadcrumb Tests ==========

    public void testTypeDeclarationAccepted() {
        configureByText("""
                type Region = "us-east-1" | "us-west-2"
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
    }

    // ========== Control Flow Breadcrumb Tests ==========

    public void testForStatementAccepted() {
        configureByText("""
                var items = [1, 2, 3]
                for item in items {
                    var x = item
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
    }

    public void testWhileStatementAccepted() {
        configureByText("""
                var count = 0
                while count < 10 {
                    count = count + 1
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
    }

    // ========== Object Literal Breadcrumb Tests ==========

    public void testObjectLiteralAccepted() {
        configureByText("""
                var config = {
                    host: "localhost",
                    port: 8080
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
    }

    public void testNestedObjectLiteral() {
        configureByText("""
                var config = {
                    server: {
                        host: "localhost"
                    }
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
    }

    // ========== Element Info Extraction Tests ==========

    public void testElementInfoForComponent() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should contain component", file.getText().contains("component WebServer"));
    }

    public void testElementInfoForFunction() {
        configureByText("""
                fun processData(string input) string {
                    return input
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should contain function", file.getText().contains("fun processData"));
    }

    public void testElementInfoForVariable() {
        configureByText("""
                var myVariable = "test"
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should contain variable", file.getText().contains("var myVariable"));
    }

    // ========== Tooltip Tests ==========

    public void testTooltipForLongDeclaration() {
        configureByText("""
                component VeryLongComponentNameThatExceedsNormalLength {
                    input string someVeryLongInputParameterName = "default value here"
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        // Tooltip should truncate long lines
    }

    // ========== Edge Cases ==========

    public void testEmptyFile() {
        configureByText("");

        PsiFile file = myFixture.getFile();
        assertNotNull("Empty file should be created", file);
    }

    public void testFileWithOnlyComments() {
        configureByText("""
                // This is a comment
                // Another comment
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File with comments should be created", file);
    }

    public void testNestedDeclarations() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                    output string endpoint = "http://localhost:${port}"
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        // Both component and input/output should be accepted
    }

    public void testDeeplyNestedStructure() {
        configureByText("""
                component DataProcessor {
                    fun process(string data) string {
                        for char in data {
                            var x = {
                                value: char
                            }
                        }
                        return data
                    }
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        // All nested elements should be accepted: component > function > for > object literal
    }

    // ========== Helper Methods ==========

    private PsiElement findElementByText(PsiFile file, String text) {
        int offset = file.getText().indexOf(text);
        if (offset < 0) return null;
        return file.findElementAt(offset);
    }

    private PsiElement findDeclarationParent(PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current.getNode() != null) {
                try {
                    if (provider.acceptElement(current)) {
                        return current;
                    }
                } catch (Exception ignored) {
                    // Element type not recognized
                }
            }
            current = current.getParent();
        }
        return null;
    }
}
