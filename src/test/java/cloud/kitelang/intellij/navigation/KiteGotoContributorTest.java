package cloud.kitelang.intellij.navigation;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;

/**
 * Tests for Go to Class and Go to Symbol contributors.
 * Verifies that Kite declarations can be found via IDE navigation.
 */
public class KiteGotoContributorTest extends KiteTestBase {

    // ========== Go to Class Tests (Schemas and Components) ==========

    public void testGotoClassFindsSchema() {
        addFile("types.kite", """
                schema DatabaseConfig {
                    string host
                    number port
                }
                """);

        var contributor = new KiteGotoClassContributor();
        String[] names = contributor.getNames(getProject(), false);

        assertContains(names, "DatabaseConfig");
    }

    public void testGotoClassFindsComponent() {
        addFile("components.kite", """
                component WebServer {
                    input string port = "8080"
                    output string endpoint
                }
                """);

        var contributor = new KiteGotoClassContributor();
        String[] names = contributor.getNames(getProject(), false);

        assertContains(names, "WebServer");
    }

    public void testGotoClassFindsMultipleSchemas() {
        addFile("schemas.kite", """
                schema First {
                    string a
                }
                schema Second {
                    string b
                }
                schema Third {
                    string c
                }
                """);

        var contributor = new KiteGotoClassContributor();
        String[] names = contributor.getNames(getProject(), false);

        assertContains(names, "First");
        assertContains(names, "Second");
        assertContains(names, "Third");
    }

    public void testGotoClassDoesNotFindVariables() {
        addFile("vars.kite", """
                var myVariable = "value"
                """);

        var contributor = new KiteGotoClassContributor();
        String[] names = contributor.getNames(getProject(), false);

        assertNotContains(names, "myVariable");
    }

    public void testGotoClassDoesNotFindFunctions() {
        addFile("funcs.kite", """
                fun myFunction() string {
                    return "hello"
                }
                """);

        var contributor = new KiteGotoClassContributor();
        String[] names = contributor.getNames(getProject(), false);

        assertNotContains(names, "myFunction");
    }

    public void testGotoClassReturnsNavigationItems() {
        addFile("nav.kite", """
                schema NavigableSchema {
                    string field
                }
                """);

        var contributor = new KiteGotoClassContributor();
        NavigationItem[] items = contributor.getItemsByName(
                "NavigableSchema", "NavigableSchema", getProject(), false);

        assertTrue("Should return at least one item", items.length >= 1);
        assertEquals("NavigableSchema", items[0].getName());
    }

    public void testGotoClassAcrossMultipleFiles() {
        addFile("file1.kite", """
                schema SchemaInFile1 {
                    string a
                }
                """);
        addFile("file2.kite", """
                schema SchemaInFile2 {
                    string b
                }
                """);

        var contributor = new KiteGotoClassContributor();
        String[] names = contributor.getNames(getProject(), false);

        assertContains(names, "SchemaInFile1");
        assertContains(names, "SchemaInFile2");
    }

    // ========== Go to Symbol Tests (All Declarations) ==========

    public void testGotoSymbolFindsSchema() {
        addFile("types.kite", """
                schema Config {
                    string host
                }
                """);

        var contributor = new KiteGotoSymbolContributor();
        String[] names = contributor.getNames(getProject(), false);

        assertContains(names, "Config");
    }

    public void testGotoSymbolFindsComponent() {
        addFile("comp.kite", """
                component Server {
                    input string port
                }
                """);

        var contributor = new KiteGotoSymbolContributor();
        String[] names = contributor.getNames(getProject(), false);

        assertContains(names, "Server");
    }

    public void testGotoSymbolFindsFunction() {
        addFile("funcs.kite", """
                fun calculateTotal(number a, number b) number {
                    return a + b
                }
                """);

        var contributor = new KiteGotoSymbolContributor();
        String[] names = contributor.getNames(getProject(), false);

        assertContains(names, "calculateTotal");
    }

    public void testGotoSymbolFindsVariable() {
        addFile("vars.kite", """
                var globalConfig = "production"
                """);

        var contributor = new KiteGotoSymbolContributor();
        String[] names = contributor.getNames(getProject(), false);

        assertContains(names, "globalConfig");
    }

    public void testGotoSymbolFindsResource() {
        addFile("resources.kite", """
                schema Database {
                    string host
                }
                resource Database primaryDb {
                    host = "localhost"
                }
                """);

        var contributor = new KiteGotoSymbolContributor();
        String[] names = contributor.getNames(getProject(), false);

        assertContains(names, "primaryDb");
    }

    public void testGotoSymbolFindsTypeAlias() {
        addFile("types.kite", """
                type Region = "us-east-1" | "us-west-2"
                """);

        var contributor = new KiteGotoSymbolContributor();
        String[] names = contributor.getNames(getProject(), false);

        assertContains(names, "Region");
    }

    public void testGotoSymbolFindsAllDeclarationTypes() {
        addFile("all.kite", """
                schema MySchema {
                    string field
                }
                component MyComponent {
                    input string x
                }
                fun myFunction() {
                }
                var myVariable = 1
                type MyType = "a" | "b"
                resource MySchema myResource {
                    field = "value"
                }
                """);

        var contributor = new KiteGotoSymbolContributor();
        String[] names = contributor.getNames(getProject(), false);

        assertContains(names, "MySchema");
        assertContains(names, "MyComponent");
        assertContains(names, "myFunction");
        assertContains(names, "myVariable");
        assertContains(names, "MyType");
        assertContains(names, "myResource");
    }

    public void testGotoSymbolReturnsNavigationItems() {
        addFile("nav.kite", """
                fun navigableFunction() string {
                    return "hello"
                }
                """);

        var contributor = new KiteGotoSymbolContributor();
        NavigationItem[] items = contributor.getItemsByName(
                "navigableFunction", "navigableFunction", getProject(), false);

        assertTrue("Should return at least one item", items.length >= 1);
        assertEquals("navigableFunction", items[0].getName());
    }

    public void testGotoSymbolAcrossMultipleFiles() {
        addFile("file1.kite", """
                var varInFile1 = "a"
                fun funcInFile1() {}
                """);
        addFile("file2.kite", """
                var varInFile2 = "b"
                schema SchemaInFile2 {}
                """);

        var contributor = new KiteGotoSymbolContributor();
        String[] names = contributor.getNames(getProject(), false);

        assertContains(names, "varInFile1");
        assertContains(names, "funcInFile1");
        assertContains(names, "varInFile2");
        assertContains(names, "SchemaInFile2");
    }

    // ========== Edge Cases ==========

    public void testGotoClassEmptyProject() {
        // No files added
        var contributor = new KiteGotoClassContributor();
        String[] names = contributor.getNames(getProject(), false);

        assertNotNull("Should return non-null array", names);
    }

    public void testGotoSymbolEmptyProject() {
        // No files added
        var contributor = new KiteGotoSymbolContributor();
        String[] names = contributor.getNames(getProject(), false);

        assertNotNull("Should return non-null array", names);
    }

    public void testGotoClassWithEmptyFile() {
        addFile("empty.kite", "");

        var contributor = new KiteGotoClassContributor();
        String[] names = contributor.getNames(getProject(), false);

        assertNotNull("Should handle empty file", names);
    }

    public void testGotoSymbolWithComments() {
        addFile("comments.kite", """
                // This is a comment about the schema
                schema DocumentedSchema {
                    string field
                }
                """);

        var contributor = new KiteGotoSymbolContributor();
        String[] names = contributor.getNames(getProject(), false);

        assertContains(names, "DocumentedSchema");
    }

    public void testGotoSymbolDoesNotFindImports() {
        addFile("imports.kite", """
                import something from "other.kite"
                """);

        var contributor = new KiteGotoSymbolContributor();
        String[] names = contributor.getNames(getProject(), false);

        // Import names should not appear as symbols
        assertNotContains(names, "something");
    }

    // ========== Helper Methods ==========

    private void assertContains(String[] array, String expected) {
        for (String item : array) {
            if (expected.equals(item)) {
                return;
            }
        }
        fail("Expected to find '" + expected + "' in array");
    }

    private void assertNotContains(String[] array, String unexpected) {
        for (String item : array) {
            if (unexpected.equals(item)) {
                fail("Did not expect to find '" + unexpected + "' in array");
            }
        }
    }
}
