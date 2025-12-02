package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.KiteTestBase;

import java.util.List;

/**
 * Tests for resource block completion.
 * Verifies schema property suggestions and value completion.
 */
public class KiteResourceCompletionProviderTest extends KiteTestBase {

    // ========== Property Name Completion (Before =) Tests ==========

    public void testSchemaPropertySuggested() {
        configureByText("""
                schema DbConfig {
                    string host
                    number port
                }
                resource DbConfig myDb {
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'host' property", lookupStrings.contains("host"));
        assertTrue("Should suggest 'port' property", lookupStrings.contains("port"));
    }

    public void testAlreadyDefinedPropertyExcluded() {
        configureByText("""
                schema DbConfig {
                    string host
                    number port
                    boolean ssl
                }
                resource DbConfig myDb {
                    host = "localhost"
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertFalse("Should NOT suggest already defined 'host'", lookupStrings.contains("host"));
        assertTrue("Should suggest undefined 'port'", lookupStrings.contains("port"));
        assertTrue("Should suggest undefined 'ssl'", lookupStrings.contains("ssl"));
    }

    public void testSchemaFromImportedFile() {
        addFile("schemas.kite", """
                schema ServerConfig {
                    string hostname
                    number port
                }
                """);

        configureByText("""
                import * from "schemas.kite"
                resource ServerConfig myServer {
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions from imported schema", lookupStrings);
        assertTrue("Should suggest 'hostname' from imported schema", lookupStrings.contains("hostname"));
        assertTrue("Should suggest 'port' from imported schema", lookupStrings.contains("port"));
    }

    // ========== Value Completion (After =) Tests ==========

    public void testVariablesSuggestedAfterEquals() {
        configureByText("""
                var myHost = "localhost"
                schema DbConfig {
                    string host
                }
                resource DbConfig myDb {
                    host = <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest variable 'myHost'", lookupStrings.contains("myHost"));
    }

    public void testInputsSuggestedAfterEquals() {
        configureByText("""
                input string dbHost = "localhost"
                schema DbConfig {
                    string host
                }
                resource DbConfig myDb {
                    host = <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest input 'dbHost'", lookupStrings.contains("dbHost"));
    }

    public void testOutputsSuggestedAfterEquals() {
        configureByText("""
                output string endpoint = "http://localhost"
                schema DbConfig {
                    string host
                }
                resource DbConfig myDb {
                    host = <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest output 'endpoint'", lookupStrings.contains("endpoint"));
    }

    public void testResourcesSuggestedAfterEquals() {
        configureByText("""
                schema DbConfig {
                    string host
                }
                resource DbConfig primaryDb {
                    host = "primary"
                }
                resource DbConfig replicaDb {
                    host = <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest resource 'primaryDb'", lookupStrings.contains("primaryDb"));
    }

    public void testFunctionsSuggestedAfterEquals() {
        configureByText("""
                fun getHost() string {
                    return "localhost"
                }
                schema DbConfig {
                    string host
                }
                resource DbConfig myDb {
                    host = <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest function 'getHost'", lookupStrings.contains("getHost"));
    }

    // ========== Context Detection Tests ==========

    public void testNoCompletionOutsideResourceBlock() {
        configureByText("""
                schema DbConfig {
                    string host
                }
                var x = <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        // Should not suggest schema properties outside resource context
        if (lookupStrings != null) {
            assertFalse("Should NOT suggest schema property outside resource block",
                    lookupStrings.contains("host"));
        }
    }

    public void testMultiplePropertiesAllSuggested() {
        configureByText("""
                schema Config {
                    string host
                    number port
                    boolean ssl
                    string username
                    string password
                }
                resource Config myConfig {
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'host'", lookupStrings.contains("host"));
        assertTrue("Should suggest 'port'", lookupStrings.contains("port"));
        assertTrue("Should suggest 'ssl'", lookupStrings.contains("ssl"));
        assertTrue("Should suggest 'username'", lookupStrings.contains("username"));
        assertTrue("Should suggest 'password'", lookupStrings.contains("password"));
    }

    public void testArrayTypePropertySuggested() {
        configureByText("""
                schema Config {
                    string[] tags
                    number[] ports
                }
                resource Config myConfig {
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest array property 'tags'", lookupStrings.contains("tags"));
        assertTrue("Should suggest array property 'ports'", lookupStrings.contains("ports"));
    }

    public void testAnyTypePropertySuggested() {
        configureByText("""
                schema Config {
                    any data
                }
                resource Config myConfig {
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'any' type property", lookupStrings.contains("data"));
    }
}
