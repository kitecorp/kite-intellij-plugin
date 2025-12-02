package cloud.kitelang.intellij.structure;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.psi.PsiFile;

/**
 * Tests for Kite Structure View.
 * Verifies KiteStructureViewElement, KiteStructureViewModel, and KiteStructureViewIcons.
 */
public class KiteStructureViewTest extends KiteTestBase {

    // ========== Structure View Model Tests ==========

    public void testStructureViewModelCreation() {
        configureByText("""
                var x = 1
                """);

        PsiFile file = myFixture.getFile();
        KiteStructureViewModel model = new KiteStructureViewModel(file);

        assertNotNull("Model should be created", model);
        assertNotNull("Model should have root element", model.getRoot());
    }

    public void testStructureViewModelHasAlphaSorter() {
        configureByText("var x = 1");

        PsiFile file = myFixture.getFile();
        KiteStructureViewModel model = new KiteStructureViewModel(file);

        assertTrue("Model should have sorters", model.getSorters().length > 0);
    }

    public void testIsAlwaysShowsPlusReturnsFalse() {
        configureByText("var x = 1");

        PsiFile file = myFixture.getFile();
        KiteStructureViewModel model = new KiteStructureViewModel(file);
        KiteStructureViewElement element = new KiteStructureViewElement(file);

        assertFalse("isAlwaysShowsPlus should return false", model.isAlwaysShowsPlus(element));
    }

    public void testIsAlwaysLeafReturnsFalse() {
        configureByText("var x = 1");

        PsiFile file = myFixture.getFile();
        KiteStructureViewModel model = new KiteStructureViewModel(file);
        KiteStructureViewElement element = new KiteStructureViewElement(file);

        assertFalse("isAlwaysLeaf should return false", model.isAlwaysLeaf(element));
    }

    // ========== Structure View Element Tests ==========

    public void testElementGetValue() {
        configureByText("var x = 1");

        PsiFile file = myFixture.getFile();
        KiteStructureViewElement element = new KiteStructureViewElement(file);

        assertEquals("getValue should return the element", file, element.getValue());
    }

    public void testElementGetPresentation() {
        configureByText("var x = 1");

        PsiFile file = myFixture.getFile();
        KiteStructureViewElement element = new KiteStructureViewElement(file);

        assertNotNull("Element should have presentation", element.getPresentation());
    }

    public void testElementGetAlphaSortKey() {
        configureByText("var myVariable = 1");

        PsiFile file = myFixture.getFile();
        KiteStructureViewElement element = new KiteStructureViewElement(file);

        assertNotNull("Element should have alpha sort key", element.getAlphaSortKey());
    }

    // ========== Children Collection Tests ==========

    public void testVariableDeclarationAppearsAsChild() {
        configureByText("""
                var myVar = "hello"
                """);

        PsiFile file = myFixture.getFile();
        KiteStructureViewElement root = new KiteStructureViewElement(file);
        TreeElement[] children = root.getChildren();

        assertTrue("Should have at least one child", children.length >= 1);
    }

    public void testMultipleDeclarationsAppearAsChildren() {
        configureByText("""
                var a = 1
                var b = 2
                var c = 3
                """);

        PsiFile file = myFixture.getFile();
        KiteStructureViewElement root = new KiteStructureViewElement(file);
        TreeElement[] children = root.getChildren();

        assertTrue("Should have multiple children", children.length >= 3);
    }

    public void testSchemaAppearsAsChild() {
        configureByText("""
                schema Config {
                    string host
                }
                """);

        PsiFile file = myFixture.getFile();
        KiteStructureViewElement root = new KiteStructureViewElement(file);
        TreeElement[] children = root.getChildren();

        assertTrue("Should have at least one child for schema", children.length >= 1);
    }

    public void testResourceAppearsAsChild() {
        configureByText("""
                schema Config {
                    string host
                }
                resource Config db {
                    host = "localhost"
                }
                """);

        PsiFile file = myFixture.getFile();
        KiteStructureViewElement root = new KiteStructureViewElement(file);
        TreeElement[] children = root.getChildren();

        assertTrue("Should have children for schema and resource", children.length >= 2);
    }

    public void testComponentAppearsAsChild() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                }
                """);

        PsiFile file = myFixture.getFile();
        KiteStructureViewElement root = new KiteStructureViewElement(file);
        TreeElement[] children = root.getChildren();

        assertTrue("Should have at least one child for component", children.length >= 1);
    }

    public void testFunctionAppearsAsChild() {
        configureByText("""
                fun greet() string {
                    return "hello"
                }
                """);

        PsiFile file = myFixture.getFile();
        KiteStructureViewElement root = new KiteStructureViewElement(file);
        TreeElement[] children = root.getChildren();

        assertTrue("Should have at least one child for function", children.length >= 1);
    }

    public void testInputDeclarationAppearsAsChild() {
        configureByText("""
                input string name = "default"
                """);

        PsiFile file = myFixture.getFile();
        KiteStructureViewElement root = new KiteStructureViewElement(file);
        TreeElement[] children = root.getChildren();

        assertTrue("Should have at least one child for input", children.length >= 1);
    }

    public void testOutputDeclarationAppearsAsChild() {
        configureByText("""
                output string result = "value"
                """);

        PsiFile file = myFixture.getFile();
        KiteStructureViewElement root = new KiteStructureViewElement(file);
        TreeElement[] children = root.getChildren();

        assertTrue("Should have at least one child for output", children.length >= 1);
    }

    public void testTypeDeclarationAppearsAsChild() {
        configureByText("""
                type Region = "us-east-1" | "us-west-2"
                """);

        PsiFile file = myFixture.getFile();
        KiteStructureViewElement root = new KiteStructureViewElement(file);
        TreeElement[] children = root.getChildren();

        assertTrue("Should have at least one child for type", children.length >= 1);
    }

    // ========== Icon Tests ==========

    public void testResourceIconExists() {
        assertNotNull("Resource icon should exist", KiteStructureViewIcons.RESOURCE);
    }

    public void testComponentIconExists() {
        assertNotNull("Component icon should exist", KiteStructureViewIcons.COMPONENT);
    }

    public void testSchemaIconExists() {
        assertNotNull("Schema icon should exist", KiteStructureViewIcons.SCHEMA);
    }

    public void testFunctionIconExists() {
        assertNotNull("Function icon should exist", KiteStructureViewIcons.FUNCTION);
    }

    public void testTypeIconExists() {
        assertNotNull("Type icon should exist", KiteStructureViewIcons.TYPE);
    }

    public void testVariableIconExists() {
        assertNotNull("Variable icon should exist", KiteStructureViewIcons.VARIABLE);
    }

    public void testInputIconExists() {
        assertNotNull("Input icon should exist", KiteStructureViewIcons.INPUT);
    }

    public void testOutputIconExists() {
        assertNotNull("Output icon should exist", KiteStructureViewIcons.OUTPUT);
    }

    public void testImportIconExists() {
        assertNotNull("Import icon should exist", KiteStructureViewIcons.IMPORT);
    }

    public void testPropertyIconExists() {
        assertNotNull("Property icon should exist", KiteStructureViewIcons.PROPERTY);
    }

    public void testDecoratorIconExists() {
        assertNotNull("Decorator icon should exist", KiteStructureViewIcons.DECORATOR);
    }

    public void testIconsHaveCorrectSize() {
        assertEquals("Icon width should be 16", 16, KiteStructureViewIcons.RESOURCE.getIconWidth());
        assertEquals("Icon height should be 16", 16, KiteStructureViewIcons.RESOURCE.getIconHeight());
    }

    // ========== Edge Cases ==========

    public void testEmptyFile() {
        configureByText("");

        PsiFile file = myFixture.getFile();
        KiteStructureViewElement root = new KiteStructureViewElement(file);
        TreeElement[] children = root.getChildren();

        assertEquals("Empty file should have no children", 0, children.length);
    }

    public void testFileWithOnlyComments() {
        configureByText("""
                // This is a comment
                // Another comment
                """);

        PsiFile file = myFixture.getFile();
        KiteStructureViewElement root = new KiteStructureViewElement(file);

        // Should not crash
        assertNotNull("Root should exist", root);
    }

    public void testComplexFile() {
        configureByText("""
                import * from "common.kite"
                
                schema DatabaseConfig {
                    string host
                    number port
                }
                
                @description("Production database")
                resource DatabaseConfig prod {
                    host = "db.example.com"
                    port = 5432
                }
                
                component WebServer {
                    input string port = "8080"
                    output string endpoint = ""
                }
                
                fun getUrl() string {
                    return "http://localhost"
                }
                
                var config = {}
                input string env = "dev"
                output string status = "ok"
                type Region = "us-east-1" | "eu-west-1"
                """);

        PsiFile file = myFixture.getFile();
        KiteStructureViewElement root = new KiteStructureViewElement(file);
        TreeElement[] children = root.getChildren();

        // Should have multiple children: schema, resource, component, function, var, input, output, type
        assertTrue("Complex file should have many children", children.length >= 7);
    }

    // ========== Navigation Tests ==========

    public void testCanNavigate() {
        configureByText("var x = 1");

        PsiFile file = myFixture.getFile();
        KiteStructureViewElement element = new KiteStructureViewElement(file);

        // Files should generally be navigable
        // This depends on how NavigationItem is implemented
        assertNotNull("Navigation check should not throw", element.canNavigate());
    }

    public void testCanNavigateToSource() {
        configureByText("var x = 1");

        PsiFile file = myFixture.getFile();
        KiteStructureViewElement element = new KiteStructureViewElement(file);

        // Files should generally be navigable to source
        assertNotNull("Navigation to source check should not throw", element.canNavigateToSource());
    }
}
