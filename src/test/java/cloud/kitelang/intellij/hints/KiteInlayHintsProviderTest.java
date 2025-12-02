package cloud.kitelang.intellij.hints;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.psi.PsiFile;

import static org.junit.Assert.assertNotEquals;

/**
 * Tests for KiteInlayHintsProvider.
 * Verifies that inlay hints are generated for the correct elements.
 * <p>
 * Note: IntelliJ inlay hints testing has limitations - these tests verify
 * that the provider is properly configured and doesn't crash, rather than
 * verifying exact hint content which requires more complex setup.
 */
public class KiteInlayHintsProviderTest extends KiteTestBase {

    private final KiteInlayHintsProvider provider = new KiteInlayHintsProvider();

    // ========== Provider Configuration Tests ==========

    public void testProviderIsVisible() {
        assertTrue("Provider should be visible in settings", provider.isVisibleInSettings());
    }

    public void testProviderName() {
        assertEquals("Kite", provider.getName());
    }

    public void testProviderHasPreviewText() {
        String preview = provider.getPreviewText();
        assertNotNull("Provider should have preview text", preview);
        assertTrue("Preview should contain type hint example", preview.contains("var message"));
        assertTrue("Preview should contain function call example", preview.contains("greet"));
    }

    public void testProviderHasSettingsKey() {
        assertNotNull("Provider should have settings key", provider.getKey());
    }

    public void testDefaultSettingsEnableTypeHints() {
        KiteInlayHintsProvider.Settings settings = provider.createSettings();
        assertTrue("Type hints should be enabled by default", settings.showTypeHints);
    }

    public void testDefaultSettingsEnableParameterHints() {
        KiteInlayHintsProvider.Settings settings = provider.createSettings();
        assertTrue("Parameter hints should be enabled by default", settings.showParameterHints);
    }

    // ========== Type Hint Eligibility Tests ==========

    public void testVariableWithInferredTypeShouldGetHint() {
        configureByText("""
                var message = "Hello"
                var count = 42
                var enabled = true
                """);

        // Verify file parses correctly - hints depend on proper PSI
        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should have content", file.getText().contains("var message"));
    }

    public void testVariableWithExplicitTypeShouldNotGetHint() {
        configureByText("""
                var string message = "Hello"
                var number count = 42
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        // Variables with explicit types don't need type hints
    }

    public void testInputWithTypeDoesNotNeedHint() {
        configureByText("""
                input string name = "default"
                input number port = 8080
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        // Inputs already have explicit types, no hint needed
    }

    public void testOutputWithTypeDoesNotNeedHint() {
        configureByText("""
                output string result = "value"
                output boolean success = true
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        // Outputs already have explicit types, no hint needed
    }

    // ========== Resource Property Type Hints Tests ==========

    public void testResourcePropertyShouldGetTypeHint() {
        configureByText("""
                schema Config {
                    string host
                    number port
                }
                resource Config myConfig {
                    host = "localhost"
                    port = 8080
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should have resource", file.getText().contains("resource Config"));
    }

    public void testResourceFromImportedSchemaShouldGetHint() {
        addFile("schemas.kite", """
                schema ServerConfig {
                    string hostname
                    number port
                }
                """);

        configureByText("""
                import ServerConfig from "schemas.kite"
                resource ServerConfig myServer {
                    hostname = "localhost"
                    port = 8080
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should have resource", file.getText().contains("resource ServerConfig"));
    }

    // ========== Component Input Type Hints Tests ==========

    public void testComponentInstanceInputShouldGetHint() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                    output string endpoint = ""
                }
                component WebServer myServer {
                    port = "9000"
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should have component instance", file.getText().contains("component WebServer myServer"));
    }

    // ========== Parameter Hints Tests ==========

    public void testFunctionCallShouldGetParameterHints() {
        configureByText("""
                fun greet(string name, number age) string {
                    return "Hello " + name
                }
                var result = greet("Alice", 30)
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should have function call", file.getText().contains("greet(\"Alice\""));
    }

    public void testFunctionCallWithSingleArgumentShouldGetHint() {
        configureByText("""
                fun process(string data) string {
                    return data
                }
                var result = process("test")
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
    }

    public void testFunctionCallWithNoArgumentsShouldNotNeedHints() {
        configureByText("""
                fun getTime() string {
                    return "12:00"
                }
                var time = getTime()
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        // No arguments means no parameter hints needed
    }

    // ========== Edge Cases ==========

    public void testEmptyFile() {
        configureByText("");

        PsiFile file = myFixture.getFile();
        assertNotNull("Empty file should still be created", file);
    }

    public void testFileWithOnlyComments() {
        configureByText("""
                // This is a comment
                // Another comment
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File with only comments should be created", file);
    }

    public void testNestedFunctionCalls() {
        configureByText("""
                fun inner(string x) string {
                    return x
                }
                fun outer(string y) string {
                    return inner(y)
                }
                var result = outer("test")
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should have nested calls", file.getText().contains("inner(y)"));
    }

    public void testArrayValueInference() {
        configureByText("""
                var items = ["a", "b", "c"]
                var numbers = [1, 2, 3]
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        // Array types should be inferred and could show hints like string[] or number[]
    }

    public void testObjectLiteralValueInference() {
        configureByText("""
                var config = {
                    host: "localhost",
                    port: 8080
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        // Object literals might show as 'object' type
    }

    // ========== Settings Tests ==========

    public void testSettingsEquality() {
        KiteInlayHintsProvider.Settings settings1 = provider.createSettings();
        KiteInlayHintsProvider.Settings settings2 = provider.createSettings();

        assertEquals("Default settings should be equal", settings1, settings2);
        assertEquals("Hash codes should be equal", settings1.hashCode(), settings2.hashCode());

        settings2.showTypeHints = false;
        assertNotEquals("Modified settings should not be equal", settings1, settings2);
    }

    public void testSettingsWithDifferentValues() {
        KiteInlayHintsProvider.Settings settings = provider.createSettings();

        settings.showTypeHints = true;
        settings.showParameterHints = false;

        KiteInlayHintsProvider.Settings settings2 = provider.createSettings();
        settings2.showTypeHints = true;
        settings2.showParameterHints = false;

        assertEquals("Settings with same values should be equal", settings, settings2);
    }

    // ========== Language Support Tests ==========

    public void testSupportsKiteLanguage() {
        assertTrue("Should support Kite language",
                provider.isLanguageSupported(cloud.kitelang.intellij.KiteLanguage.INSTANCE));
    }
}
