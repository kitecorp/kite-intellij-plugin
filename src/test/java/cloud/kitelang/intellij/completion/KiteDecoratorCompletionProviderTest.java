package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.KiteTestBase;

import java.util.List;

/**
 * Tests for decorator completion.
 * Verifies that decorator names are suggested after @ symbol.
 */
public class KiteDecoratorCompletionProviderTest extends KiteTestBase {

    // ========== Basic Decorator Completion Tests ==========

    public void testDecoratorNamesSuggestedAfterAt() {
        configureByText("""
                @<caret>
                input string name = "default"
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'description'", lookupStrings.contains("description"));
    }

    public void testValidationDecoratorsSuggested() {
        configureByText("""
                @<caret>
                input number count = 0
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'minValue'", lookupStrings.contains("minValue"));
        assertTrue("Should suggest 'maxValue'", lookupStrings.contains("maxValue"));
        assertTrue("Should suggest 'minLength'", lookupStrings.contains("minLength"));
        assertTrue("Should suggest 'maxLength'", lookupStrings.contains("maxLength"));
    }

    public void testNonEmptyDecoratorSuggested() {
        configureByText("""
                @<caret>
                input string name = ""
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'nonEmpty'", lookupStrings.contains("nonEmpty"));
    }

    public void testAllowedDecoratorSuggested() {
        configureByText("""
                @<caret>
                input string region = "us-east-1"
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'allowed'", lookupStrings.contains("allowed"));
    }

    // ========== Resource Decorator Tests ==========

    public void testResourceDecoratorsSuggested() {
        configureByText("""
                schema Config {
                    string host
                }
                @<caret>
                resource Config myConfig {
                    host = "localhost"
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'existing'", lookupStrings.contains("existing"));
        assertTrue("Should suggest 'dependsOn'", lookupStrings.contains("dependsOn"));
        assertTrue("Should suggest 'tags'", lookupStrings.contains("tags"));
        assertTrue("Should suggest 'provider'", lookupStrings.contains("provider"));
    }

    public void testSensitiveDecoratorSuggested() {
        configureByText("""
                @<caret>
                input string password = ""
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'sensitive'", lookupStrings.contains("sensitive"));
    }

    // ========== Schema Property Decorator Tests ==========

    public void testCloudDecoratorInSchemaSuggested() {
        configureByText("""
                schema Instance {
                    @<caret>
                    string id
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'cloud'", lookupStrings.contains("cloud"));
    }

    // ========== Metadata Decorator Tests ==========

    public void testDescriptionDecoratorSuggested() {
        configureByText("""
                @<caret>
                var config = {}
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'description'", lookupStrings.contains("description"));
    }

    public void testCountDecoratorSuggested() {
        configureByText("""
                schema Server {
                    string host
                }
                @<caret>
                resource Server servers {
                    host = "localhost"
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'count'", lookupStrings.contains("count"));
    }

    // ========== Prefix Filtering Tests ==========

    public void testDecoratorCompletionWithPrefix() {
        configureByText("""
                @min<caret>
                input number value = 0
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'minValue'", lookupStrings.contains("minValue"));
        assertTrue("Should suggest 'minLength'", lookupStrings.contains("minLength"));
    }

    public void testDecoratorCompletionWithMaxPrefix() {
        configureByText("""
                @max<caret>
                input number value = 0
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'maxValue'", lookupStrings.contains("maxValue"));
        assertTrue("Should suggest 'maxLength'", lookupStrings.contains("maxLength"));
    }

    // ========== Multiple Decorator Tests ==========

    public void testMultipleDecoratorsOnSameDeclaration() {
        configureByText("""
                @description("A port number")
                @<caret>
                input number port = 8080
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions after existing decorator", lookupStrings);
        assertTrue("Should suggest 'minValue'", lookupStrings.contains("minValue"));
        assertTrue("Should suggest 'maxValue'", lookupStrings.contains("maxValue"));
    }

    // ========== No Completion Outside Decorator Context Tests ==========

    public void testNoDecoratorCompletionInString() {
        configureByText("""
                var x = "@<caret>"
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        // Inside a string, should not suggest decorators
        if (lookupStrings != null) {
            assertFalse("Should NOT suggest decorators inside string",
                    lookupStrings.contains("description"));
        }
    }
}
