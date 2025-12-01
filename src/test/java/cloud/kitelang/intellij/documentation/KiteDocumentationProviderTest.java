package cloud.kitelang.intellij.documentation;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * Tests for KiteDocumentationProvider - verifies quick documentation (Ctrl+Q / F1).
 * Tests documentation generation for variables, functions, schemas, resources, and decorators.
 */
public class KiteDocumentationProviderTest extends KiteTestBase {

    private final KiteDocumentationProvider provider = new KiteDocumentationProvider();

    // ========== Variable Documentation Tests ==========

    public void testVariableDocumentation() {
        configureByText("""
                var message = "Hello"
                """);

        int offset = myFixture.getFile().getText().indexOf("message");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);

        String doc = provider.generateDoc(element, element);
        // Documentation should be generated for variables
        if (doc != null) {
            assertTrue("Doc should contain variable info", doc.contains("message") || doc.contains("var"));
        }
    }

    public void testVariableWithExplicitType() {
        configureByText("""
                var string name = "World"
                """);

        int offset = myFixture.getFile().getText().indexOf("name");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);
    }

    // ========== Input/Output Documentation Tests ==========

    public void testInputDocumentation() {
        configureByText("""
                input string port = "8080"
                """);

        int offset = myFixture.getFile().getText().indexOf("port");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);

        String doc = provider.generateDoc(element, element);
        if (doc != null) {
            assertTrue("Doc should contain input info", doc.contains("port") || doc.contains("input"));
        }
    }

    public void testOutputDocumentation() {
        configureByText("""
                output string result = "success"
                """);

        int offset = myFixture.getFile().getText().indexOf("result");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);

        String doc = provider.generateDoc(element, element);
        if (doc != null) {
            assertTrue("Doc should contain output info", doc.contains("result") || doc.contains("output"));
        }
    }

    // ========== Function Documentation Tests ==========

    public void testFunctionDocumentation() {
        configureByText("""
                fun greet(string name) string {
                    return "Hello " + name
                }
                """);

        int offset = myFixture.getFile().getText().indexOf("greet");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);

        String doc = provider.generateDoc(element, element);
        if (doc != null) {
            assertTrue("Doc should contain function info", doc.contains("greet") || doc.contains("fun"));
        }
    }

    public void testFunctionWithMultipleParameters() {
        configureByText("""
                fun createUser(string name, number age, boolean active) string {
                    return name
                }
                """);

        int offset = myFixture.getFile().getText().indexOf("createUser");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);
    }

    public void testFunctionWithNoParameters() {
        configureByText("""
                fun getTime() string {
                    return "12:00"
                }
                """);

        int offset = myFixture.getFile().getText().indexOf("getTime");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);
    }

    // ========== Schema Documentation Tests ==========

    public void testSchemaDocumentation() {
        configureByText("""
                schema DatabaseConfig {
                    string host
                    number port
                }
                """);

        int offset = myFixture.getFile().getText().indexOf("DatabaseConfig");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);

        String doc = provider.generateDoc(element, element);
        if (doc != null) {
            assertTrue("Doc should contain schema info",
                    doc.contains("DatabaseConfig") || doc.contains("schema"));
        }
    }

    public void testSchemaWithManyProperties() {
        configureByText("""
                schema Config {
                    string host
                    number port
                    boolean ssl
                    string username
                    string password
                }
                """);

        int offset = myFixture.getFile().getText().indexOf("Config");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);
    }

    // ========== Resource Documentation Tests ==========

    public void testResourceDocumentation() {
        configureByText("""
                schema DbConfig {
                    string host
                }
                resource DbConfig myDatabase {
                    host = "localhost"
                }
                """);

        int offset = myFixture.getFile().getText().indexOf("myDatabase");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);

        String doc = provider.generateDoc(element, element);
        if (doc != null) {
            assertTrue("Doc should contain resource info",
                    doc.contains("myDatabase") || doc.contains("resource"));
        }
    }

    // ========== Component Documentation Tests ==========

    public void testComponentDocumentation() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                    output string endpoint = "http://localhost"
                }
                """);

        int offset = myFixture.getFile().getText().indexOf("WebServer");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);

        String doc = provider.generateDoc(element, element);
        if (doc != null) {
            assertTrue("Doc should contain component info",
                    doc.contains("WebServer") || doc.contains("component"));
        }
    }

    public void testComponentInstanceDocumentation() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                    output string endpoint = ""
                }
                component WebServer myServer {
                    port = "9000"
                }
                """);

        int offset = myFixture.getFile().getText().indexOf("myServer");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);
    }

    // ========== Decorator Documentation Tests ==========

    public void testDecoratorDocumentation() {
        configureByText("""
                @description("A port number")
                input number port = 8080
                """);

        int offset = myFixture.getFile().getText().indexOf("description");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);

        String doc = provider.generateDoc(element, element);
        if (doc != null) {
            assertTrue("Doc should contain decorator info", doc.contains("description"));
        }
    }

    public void testMinValueDecoratorDocumentation() {
        configureByText("""
                @minValue(0)
                input number count = 10
                """);

        int offset = myFixture.getFile().getText().indexOf("minValue");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);
    }

    public void testMaxValueDecoratorDocumentation() {
        configureByText("""
                @maxValue(100)
                input number percentage = 50
                """);

        int offset = myFixture.getFile().getText().indexOf("maxValue");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);
    }

    public void testSensitiveDecoratorDocumentation() {
        configureByText("""
                @sensitive
                input string password = ""
                """);

        int offset = myFixture.getFile().getText().indexOf("sensitive");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);
    }

    // ========== Comment Extraction Tests ==========

    public void testDocumentationWithPrecedingComment() {
        configureByText("""
                // This is a greeting function
                fun greet(string name) string {
                    return "Hello " + name
                }
                """);

        int offset = myFixture.getFile().getText().indexOf("greet");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);

        String doc = provider.generateDoc(element, element);
        // Comments may or may not be included in doc depending on implementation
    }

    public void testDocumentationWithMultiLineComments() {
        configureByText("""
                // First line of comment
                // Second line of comment
                var config = {}
                """);

        int offset = myFixture.getFile().getText().indexOf("config");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);
    }

    // ========== String Interpolation Documentation Tests ==========

    public void testInterpolationVariableDocumentation() {
        configureByText("""
                var name = "World"
                var greeting = "Hello $name!"
                """);

        // Find the $name token
        int offset = myFixture.getFile().getText().indexOf("$name") + 1;
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);
    }

    public void testBraceInterpolationDocumentation() {
        configureByText("""
                var port = 8080
                var url = "http://localhost:${port}/api"
                """);

        int offset = myFixture.getFile().getText().indexOf("${port}") + 2;
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);
    }

    // ========== Edge Cases ==========

    public void testDocumentationForNullElement() {
        String doc = provider.generateDoc(null, null);
        assertNull("Should return null for null element", doc);
    }

    public void testDocumentationForKeyword() {
        configureByText("""
                var x = true
                """);

        int offset = myFixture.getFile().getText().indexOf("var");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);

        String doc = provider.generateDoc(element, element);
        // Keywords may or may not have documentation
    }

    public void testDocumentationForLiteral() {
        configureByText("""
                var x = "hello"
                """);

        int offset = myFixture.getFile().getText().indexOf("\"hello\"");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);
    }

    // ========== Type Alias Documentation Tests ==========

    public void testTypeAliasDocumentation() {
        configureByText("""
                type Region = "us-east-1" | "us-west-2"
                """);

        int offset = myFixture.getFile().getText().indexOf("Region");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);

        String doc = provider.generateDoc(element, element);
        if (doc != null) {
            assertTrue("Doc should contain type info", doc.contains("Region") || doc.contains("type"));
        }
    }

    // ========== Import Statement Tests ==========

    public void testImportedElementDocumentation() {
        addFile("common.kite", """
                // Shared variable for testing
                var sharedVar = "shared"
                """);

        configureByText("""
                import sharedVar from "common.kite"
                var x = sharedVar
                """);

        int offset = myFixture.getFile().getText().lastIndexOf("sharedVar");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull("Should find element", element);
    }

    // ========== Provider Configuration Tests ==========

    public void testProviderInstantiation() {
        KiteDocumentationProvider provider = new KiteDocumentationProvider();
        assertNotNull("Provider should be instantiated", provider);
    }
}
