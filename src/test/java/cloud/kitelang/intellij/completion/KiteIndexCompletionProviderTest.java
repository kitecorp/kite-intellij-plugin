package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.KiteTestBase;

import java.util.List;

/**
 * Tests for index completion inside brackets for indexed resources.
 * Verifies suggestions for numeric indices and string keys.
 */
public class KiteIndexCompletionProviderTest extends KiteTestBase {

    // ========== Numeric Index Completion Tests (@count) ==========

    public void testNumericIndexCompletionWithCount() {
        configureByText("""
                schema vm { string name }
                @count(3)
                resource vm server { name = "srv" }
                var x = server[<caret>]
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest index 0", lookupStrings.contains("0"));
        assertTrue("Should suggest index 1", lookupStrings.contains("1"));
        assertTrue("Should suggest index 2", lookupStrings.contains("2"));
        assertFalse("Should NOT suggest index 3", lookupStrings.contains("3"));
    }

    public void testNumericIndexCompletionWithLargerCount() {
        configureByText("""
                schema vm { string name }
                @count(5)
                resource vm server { name = "srv" }
                var x = server[<caret>]
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest index 4", lookupStrings.contains("4"));
        assertFalse("Should NOT suggest index 5", lookupStrings.contains("5"));
    }

    // ========== Numeric Index Completion Tests (for-loop range) ==========

    public void testNumericIndexCompletionWithForLoopRange() {
        configureByText("""
                schema vm { string name }
                for i in 0..3 {
                    resource vm server { name = "srv-${i}" }
                }
                var x = server[<caret>]
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest index 0", lookupStrings.contains("0"));
        assertTrue("Should suggest index 1", lookupStrings.contains("1"));
        assertTrue("Should suggest index 2", lookupStrings.contains("2"));
        assertFalse("Should NOT suggest index 3", lookupStrings.contains("3"));
    }

    // ========== String Key Completion Tests (for-loop array) ==========

    public void testStringKeyCompletionWithForLoopArray() {
        configureByText("""
                schema vm { string name }
                for env in ["dev", "staging", "prod"] {
                    resource vm server { name = "srv-${env}" }
                }
                var x = server[<caret>]
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest \"dev\"", lookupStrings.contains("\"dev\""));
        assertTrue("Should suggest \"staging\"", lookupStrings.contains("\"staging\""));
        assertTrue("Should suggest \"prod\"", lookupStrings.contains("\"prod\""));
    }

    public void testStringKeyCompletionWithTwoKeys() {
        configureByText("""
                schema vm { string name }
                for region in ["us-east", "eu-west"] {
                    resource vm server { name = "srv-${region}" }
                }
                var x = server[<caret>]
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest \"us-east\"", lookupStrings.contains("\"us-east\""));
        assertTrue("Should suggest \"eu-west\"", lookupStrings.contains("\"eu-west\""));
    }

    // ========== No Completion for Non-Indexed Resources ==========

    public void testNoIndexCompletionForNonIndexedResource() {
        configureByText("""
                schema vm { string name }
                resource vm server { name = "srv" }
                var x = server[<caret>]
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        // Non-indexed resources shouldn't provide special index completions
        // They might still show general completions like variables
        if (lookupStrings != null) {
            assertFalse("Should NOT suggest numeric indices for non-indexed resource",
                    lookupStrings.contains("0"));
        }
    }

    // ========== Component Index Completion ==========

    public void testIndexCompletionForIndexedComponent() {
        configureByText("""
                component WebServer {
                    input string port
                }
                @count(3)
                component WebServer servers {
                    port = "8080"
                }
                var x = servers[<caret>]
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest index 0", lookupStrings.contains("0"));
        assertTrue("Should suggest index 1", lookupStrings.contains("1"));
        assertTrue("Should suggest index 2", lookupStrings.contains("2"));
    }

    // ========== Property Completion After Indexed Access ==========

    public void testPropertyCompletionAfterIndexedResourceAccess() {
        configureByText("""
                schema vm {
                    string name
                    number port
                }
                @count(3)
                resource vm server { name = "srv" }
                var x = server[0].<caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions after indexed access", lookupStrings);
        assertTrue("Should suggest 'name' property", lookupStrings.contains("name"));
        assertTrue("Should suggest 'port' property", lookupStrings.contains("port"));
    }

    public void testPropertyCompletionAfterStringKeyIndexedAccess() {
        configureByText("""
                schema vm {
                    string name
                    string region
                }
                for env in ["dev", "prod"] {
                    resource vm server { name = "srv" }
                }
                var x = server["dev"].<caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions after string-keyed indexed access", lookupStrings);
        assertTrue("Should suggest 'name' property", lookupStrings.contains("name"));
        assertTrue("Should suggest 'region' property", lookupStrings.contains("region"));
    }

    // ========== Edge Case: Standalone Indexed Access ==========

    public void testPropertyCompletionStandaloneIndexedAccess() {
        // This tests the scenario from decorators.kite where server[0]. is at end of file
        // Wrapped in var assignment to work properly (standalone expressions aren't ideal Kite syntax)
        configureByText("""
                schema vm {
                    string x = ""
                }
                @count(5)
                resource vm server {
                }
                var y = server[0].<caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions for standalone indexed access", lookupStrings);
        assertTrue("Should suggest 'x' property, got: " + lookupStrings, lookupStrings.contains("x"));
    }

    public void testPropertyCompletionEmptyResourceBlock() {
        // Resource with empty block
        configureByText("""
                schema vm {
                    string name
                    number port
                }
                @count(3)
                resource vm server { }
                var x = server[0].<caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions for empty resource block", lookupStrings);
        assertTrue("Should suggest 'name' property", lookupStrings.contains("name"));
        assertTrue("Should suggest 'port' property", lookupStrings.contains("port"));
    }

    public void testPropertyCompletionInFunctionArgument() {
        // Indexed access as function argument
        configureByText("""
                schema vm {
                    string name
                    number port
                }
                @count(3)
                resource vm server { }
                fun process(string arg) void { }
                process(server[0].<caret>)
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions in function argument", lookupStrings);
        assertTrue("Should suggest 'name' property, got: " + lookupStrings, lookupStrings.contains("name"));
        assertTrue("Should suggest 'port' property", lookupStrings.contains("port"));
    }
}
