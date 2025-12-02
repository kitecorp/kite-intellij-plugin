package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.KiteTestBase;

import java.util.List;

/**
 * Tests for component instance completion.
 * Verifies that component instances show only input properties from the component definition.
 */
public class KiteComponentInstanceCompletionProviderTest extends KiteTestBase {

    // ========== Input Property Name Completion Tests ==========

    public void testInputPropertiesSuggestedInComponentInstance() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                    input string host = "localhost"
                    output string endpoint = "http://localhost"
                }
                component WebServer server {
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'port' input", lookupStrings.contains("port"));
        assertTrue("Should suggest 'host' input", lookupStrings.contains("host"));
        // Outputs should NOT be suggested as property names
        assertFalse("Should NOT suggest 'endpoint' output", lookupStrings.contains("endpoint"));
    }

    public void testAlreadyDefinedInputExcluded() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                    input string host = "localhost"
                    input boolean ssl = false
                }
                component WebServer server {
                    port = "3000"
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertFalse("Should NOT suggest already defined 'port'", lookupStrings.contains("port"));
        assertTrue("Should suggest undefined 'host'", lookupStrings.contains("host"));
        assertTrue("Should suggest undefined 'ssl'", lookupStrings.contains("ssl"));
    }

    public void testMultipleInputsAllSuggested() {
        configureByText("""
                component Database {
                    input string host = "localhost"
                    input number port = 5432
                    input string username = "admin"
                    input string password = "secret"
                    input boolean ssl = true
                }
                component Database db {
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'host'", lookupStrings.contains("host"));
        assertTrue("Should suggest 'port'", lookupStrings.contains("port"));
        assertTrue("Should suggest 'username'", lookupStrings.contains("username"));
        assertTrue("Should suggest 'password'", lookupStrings.contains("password"));
        assertTrue("Should suggest 'ssl'", lookupStrings.contains("ssl"));
    }

    public void testComponentFromImportedFile() {
        addFile("components.kite", """
                component ApiServer {
                    input string baseUrl = "http://localhost"
                    input number timeout = 30
                    output string status = "running"
                }
                """);

        configureByText("""
                import * from "components.kite"
                component ApiServer api {
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions from imported component", lookupStrings);
        assertTrue("Should suggest 'baseUrl' from imported component", lookupStrings.contains("baseUrl"));
        assertTrue("Should suggest 'timeout' from imported component", lookupStrings.contains("timeout"));
        assertFalse("Should NOT suggest 'status' output", lookupStrings.contains("status"));
    }

    // ========== Value Completion Tests (After =) ==========

    public void testValueCompletionAfterEquals() {
        configureByText("""
                var myHost = "localhost"
                component WebServer {
                    input string port = "8080"
                    input string host = "localhost"
                }
                component WebServer server {
                    host = <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest variable 'myHost'", lookupStrings.contains("myHost"));
    }

    public void testVariablesAndComponentsSuggestedInValue() {
        configureByText("""
                var myPort = "3000"
                component WebServer {
                    input string port = "8080"
                    output string endpoint = "http://localhost"
                }
                component WebServer serverA {
                }
                component WebServer serverB {
                    port = <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        // In value position, should suggest variables and components
        assertNotNull("Should have completions in value position", lookupStrings);
        assertTrue("Should suggest variable 'myPort'", lookupStrings.contains("myPort"));
        assertTrue("Should suggest component 'serverA'", lookupStrings.contains("serverA"));
    }

    // ========== Edge Cases ==========

    public void testNoCompletionInComponentDefinition() {
        configureByText("""
                component WebServer {
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        // In component definition, should show input/output keywords, not instance properties
        if (lookupStrings != null) {
            assertTrue("Should suggest 'input' keyword in definition",
                    lookupStrings.contains("input"));
            assertTrue("Should suggest 'output' keyword in definition",
                    lookupStrings.contains("output"));
        }
    }

    public void testInputWithAnyType() {
        configureByText("""
                component FlexibleComponent {
                    input any data = null
                    input any config = {}
                }
                component FlexibleComponent fc {
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'data' with any type", lookupStrings.contains("data"));
        assertTrue("Should suggest 'config' with any type", lookupStrings.contains("config"));
    }
}
