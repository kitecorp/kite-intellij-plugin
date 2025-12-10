package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.KiteTestBase;

import java.util.List;

/**
 * Tests for instance name completion.
 * Verifies smart name suggestions for resource and component instances.
 */
public class KiteInstanceNameCompletionProviderTest extends KiteTestBase {

    // ========== Resource Instance Name Tests ==========

    public void testResourceInstanceNameSuggested() {
        configureByText("""
                schema DatabaseConfig {
                    string host
                }
                resource DatabaseConfig <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest camelCase name 'databaseConfig'",
                lookupStrings.contains("databaseConfig"));
    }

    public void testResourceInstanceNameWithSimpleType() {
        configureByText("""
                schema Server {
                    string host
                }
                resource Server <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'server'", lookupStrings.contains("server"));
    }

    public void testResourceInstanceNameWithPrefixedType() {
        configureByText("""
                schema AWSLambdaFunction {
                    string name
                }
                resource AWSLambdaFunction <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'awsLambdaFunction'",
                lookupStrings.contains("awsLambdaFunction"));
    }

    public void testResourceInstanceNameSuggestsMyPrefix() {
        configureByText("""
                schema Database {
                    string host
                }
                resource Database <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'myDatabase'", lookupStrings.contains("myDatabase"));
    }

    public void testResourceInstanceNameSuggestsPrimaryPrefix() {
        configureByText("""
                schema Database {
                    string host
                }
                resource Database <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'primaryDatabase'", lookupStrings.contains("primaryDatabase"));
    }

    public void testNoInstanceNameSuggestionInsideBraces() {
        configureByText("""
                schema DatabaseConfig {
                    string host
                }
                resource DatabaseConfig myDb {
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        // Inside braces should show property completions, not instance names
        if (lookupStrings != null) {
            assertFalse("Should NOT suggest instance names inside braces",
                    lookupStrings.contains("databaseConfig"));
        }
    }

    // ========== Component Instance Name Tests ==========

    public void testComponentInstanceNameSuggested() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                }
                component WebServer <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'webServer'", lookupStrings.contains("webServer"));
    }

    public void testComponentInstanceNameWithComplexType() {
        configureByText("""
                component SharedLogger {
                    input string level = "info"
                }
                component SharedLogger <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'sharedLogger'", lookupStrings.contains("sharedLogger"));
    }

    public void testComponentInstanceNameWithMyPrefix() {
        configureByText("""
                component Logger {
                    input string level = "info"
                }
                component Logger <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'myLogger'", lookupStrings.contains("myLogger"));
    }

    public void testNoComponentInstanceNameSuggestionInsideBraces() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                }
                component WebServer myServer {
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        // Inside braces should show input completions, not instance names
        if (lookupStrings != null) {
            assertFalse("Should NOT suggest instance names inside braces",
                    lookupStrings.contains("webServer"));
        }
    }

    // ========== Edge Cases ==========

    public void testNoSuggestionAfterResourceKeywordOnly() {
        configureByText("""
                resource <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        // After 'resource' keyword, should suggest types, not instance names
        if (lookupStrings != null) {
            // Should not contain typical instance names
            assertFalse("Should NOT suggest instance names after 'resource' keyword only",
                    lookupStrings.stream().anyMatch(s -> s.startsWith("my") || s.equals("database")));
        }
    }

    public void testNoSuggestionAfterComponentKeywordOnly() {
        configureByText("""
                component <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        // After 'component' keyword, should suggest types, not instance names
        if (lookupStrings != null) {
            // Should not contain typical instance names
            assertFalse("Should NOT suggest instance names after 'component' keyword only",
                    lookupStrings.stream().anyMatch(s -> s.startsWith("my") && s.length() > 2));
        }
    }

    public void testResourceFromImportedSchema() {
        addFile("schemas.kite", """
                schema VirtualMachine {
                    string name
                    number cpu
                }
                """);

        configureByText("""
                import * from "schemas.kite"
                resource VirtualMachine <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'virtualMachine'", lookupStrings.contains("virtualMachine"));
    }

    public void testComponentFromImportedFile() {
        addFile("components.kite", """
                component LoadBalancer {
                    input string algorithm = "round-robin"
                }
                """);

        configureByText("""
                import * from "components.kite"
                component LoadBalancer <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'loadBalancer'", lookupStrings.contains("loadBalancer"));
    }

    // ========== Abbreviated Name Tests ==========

    public void testDatabaseSuggestsDbAbbreviation() {
        configureByText("""
                schema Database {
                    string host
                }
                resource Database <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'db' abbreviation", lookupStrings.contains("db"));
    }

    public void testServerSuggestsShortName() {
        configureByText("""
                schema WebServer {
                    string host
                }
                resource WebServer <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'server' abbreviation", lookupStrings.contains("server"));
    }

    public void testConfigSuggestsShortName() {
        configureByText("""
                schema AppConfig {
                    string name
                }
                resource AppConfig <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'config' abbreviation", lookupStrings.contains("config"));
    }
}
