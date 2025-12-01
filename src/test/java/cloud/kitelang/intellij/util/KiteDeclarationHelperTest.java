package cloud.kitelang.intellij.util;

import cloud.kitelang.intellij.KiteTestBase;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for KiteDeclarationHelper utility class.
 */
public class KiteDeclarationHelperTest extends KiteTestBase {

    // ========== collectDeclarations tests ==========

    public void testCollectVariableDeclarations() {
        configureByText("""
                var alpha = "a"
                var beta = 42
                var gamma = true
                """);

        PsiFile file = myFixture.getFile();
        List<String> names = new ArrayList<>();
        List<IElementType> types = new ArrayList<>();

        KiteDeclarationHelper.collectDeclarations(file, (name, type, element) -> {
            names.add(name);
            types.add(type);
        });

        assertEquals(3, names.size());
        assertTrue(names.contains("alpha"));
        assertTrue(names.contains("beta"));
        assertTrue(names.contains("gamma"));

        for (IElementType type : types) {
            assertEquals(KiteElementTypes.VARIABLE_DECLARATION, type);
        }
    }

    public void testCollectInputDeclarations() {
        configureByText("""
                input string hostname
                input number port = 8080
                """);

        PsiFile file = myFixture.getFile();
        Map<String, IElementType> declarations = new HashMap<>();

        KiteDeclarationHelper.collectDeclarations(file, (name, type, element) -> {
            declarations.put(name, type);
        });

        assertEquals(2, declarations.size());
        assertEquals(KiteElementTypes.INPUT_DECLARATION, declarations.get("hostname"));
        assertEquals(KiteElementTypes.INPUT_DECLARATION, declarations.get("port"));
    }

    public void testCollectOutputDeclarations() {
        configureByText("""
                output string result = "done"
                output number count = 10
                """);

        PsiFile file = myFixture.getFile();
        Map<String, IElementType> declarations = new HashMap<>();

        KiteDeclarationHelper.collectDeclarations(file, (name, type, element) -> {
            declarations.put(name, type);
        });

        assertEquals(2, declarations.size());
        assertEquals(KiteElementTypes.OUTPUT_DECLARATION, declarations.get("result"));
        assertEquals(KiteElementTypes.OUTPUT_DECLARATION, declarations.get("count"));
    }

    public void testCollectResourceDeclarations() {
        configureByText("""
                schema VM {
                    string name
                }
                resource VM server {
                    name = "web"
                }
                resource VM database {
                    name = "db"
                }
                """);

        PsiFile file = myFixture.getFile();
        Map<String, IElementType> declarations = new HashMap<>();

        KiteDeclarationHelper.collectDeclarations(file, (name, type, element) -> {
            declarations.put(name, type);
        });

        assertTrue(declarations.containsKey("VM"));
        assertEquals(KiteElementTypes.SCHEMA_DECLARATION, declarations.get("VM"));
        assertTrue(declarations.containsKey("server"));
        assertEquals(KiteElementTypes.RESOURCE_DECLARATION, declarations.get("server"));
        assertTrue(declarations.containsKey("database"));
        assertEquals(KiteElementTypes.RESOURCE_DECLARATION, declarations.get("database"));
    }

    public void testCollectComponentDeclarations() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                    output string endpoint = "http://localhost"
                }
                """);

        PsiFile file = myFixture.getFile();
        Map<String, IElementType> declarations = new HashMap<>();

        KiteDeclarationHelper.collectDeclarations(file, (name, type, element) -> {
            declarations.put(name, type);
        });

        assertTrue(declarations.containsKey("WebServer"));
        assertEquals(KiteElementTypes.COMPONENT_DECLARATION, declarations.get("WebServer"));
        // Component inputs/outputs are nested, not top-level declarations
    }

    public void testCollectFunctionDeclarations() {
        configureByText("""
                fun calculateCost(number count) number {
                    return count * 10
                }

                fun getName() string {
                    return "test"
                }
                """);

        PsiFile file = myFixture.getFile();
        Map<String, IElementType> declarations = new HashMap<>();

        KiteDeclarationHelper.collectDeclarations(file, (name, type, element) -> {
            declarations.put(name, type);
        });

        assertTrue(declarations.containsKey("calculateCost"));
        assertEquals(KiteElementTypes.FUNCTION_DECLARATION, declarations.get("calculateCost"));
        assertTrue(declarations.containsKey("getName"));
        assertEquals(KiteElementTypes.FUNCTION_DECLARATION, declarations.get("getName"));
    }

    public void testCollectSchemaDeclarations() {
        configureByText("""
                schema DatabaseConfig {
                    string host
                    number port
                }

                schema CacheConfig {
                    string endpoint
                }
                """);

        PsiFile file = myFixture.getFile();
        Map<String, IElementType> declarations = new HashMap<>();

        KiteDeclarationHelper.collectDeclarations(file, (name, type, element) -> {
            declarations.put(name, type);
        });

        assertTrue(declarations.containsKey("DatabaseConfig"));
        assertEquals(KiteElementTypes.SCHEMA_DECLARATION, declarations.get("DatabaseConfig"));
        assertTrue(declarations.containsKey("CacheConfig"));
        assertEquals(KiteElementTypes.SCHEMA_DECLARATION, declarations.get("CacheConfig"));
    }

    public void testCollectMixedDeclarations() {
        configureByText("""
                var config = "test"
                input string name
                output string result = "done"

                schema Config {
                    string value
                }

                resource Config myConfig {
                    value = "hello"
                }

                fun process() string {
                    return "processed"
                }
                """);

        PsiFile file = myFixture.getFile();
        Map<String, IElementType> declarations = new HashMap<>();

        KiteDeclarationHelper.collectDeclarations(file, (name, type, element) -> {
            declarations.put(name, type);
        });

        assertEquals(6, declarations.size());
        assertEquals(KiteElementTypes.VARIABLE_DECLARATION, declarations.get("config"));
        assertEquals(KiteElementTypes.INPUT_DECLARATION, declarations.get("name"));
        assertEquals(KiteElementTypes.OUTPUT_DECLARATION, declarations.get("result"));
        assertEquals(KiteElementTypes.SCHEMA_DECLARATION, declarations.get("Config"));
        assertEquals(KiteElementTypes.RESOURCE_DECLARATION, declarations.get("myConfig"));
        assertEquals(KiteElementTypes.FUNCTION_DECLARATION, declarations.get("process"));
    }

    // ========== findDeclaration tests ==========

    public void testFindDeclarationExists() {
        configureByText("""
                var alpha = "a"
                var beta = "b"
                var gamma = "c"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement found = KiteDeclarationHelper.findDeclaration(file, "beta");

        assertNotNull(found);
        assertTrue(found.getText().contains("beta"));
    }

    public void testFindDeclarationNotExists() {
        configureByText("""
                var alpha = "a"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement found = KiteDeclarationHelper.findDeclaration(file, "nonexistent");

        assertNull(found);
    }

    // ========== isDeclarationType tests ==========

    public void testIsDeclarationTypeVariable() {
        assertTrue(KiteDeclarationHelper.isDeclarationType(KiteElementTypes.VARIABLE_DECLARATION));
    }

    public void testIsDeclarationTypeInput() {
        assertTrue(KiteDeclarationHelper.isDeclarationType(KiteElementTypes.INPUT_DECLARATION));
    }

    public void testIsDeclarationTypeOutput() {
        assertTrue(KiteDeclarationHelper.isDeclarationType(KiteElementTypes.OUTPUT_DECLARATION));
    }

    public void testIsDeclarationTypeResource() {
        assertTrue(KiteDeclarationHelper.isDeclarationType(KiteElementTypes.RESOURCE_DECLARATION));
    }

    public void testIsDeclarationTypeComponent() {
        assertTrue(KiteDeclarationHelper.isDeclarationType(KiteElementTypes.COMPONENT_DECLARATION));
    }

    public void testIsDeclarationTypeSchema() {
        assertTrue(KiteDeclarationHelper.isDeclarationType(KiteElementTypes.SCHEMA_DECLARATION));
    }

    public void testIsDeclarationTypeFunction() {
        assertTrue(KiteDeclarationHelper.isDeclarationType(KiteElementTypes.FUNCTION_DECLARATION));
    }

    // ========== isTypeDeclaration tests ==========

    public void testIsTypeDeclarationSchema() {
        assertTrue(KiteDeclarationHelper.isTypeDeclaration(KiteElementTypes.SCHEMA_DECLARATION));
    }

    public void testIsTypeDeclarationComponent() {
        assertTrue(KiteDeclarationHelper.isTypeDeclaration(KiteElementTypes.COMPONENT_DECLARATION));
    }

    public void testIsTypeDeclarationVariable() {
        assertFalse(KiteDeclarationHelper.isTypeDeclaration(KiteElementTypes.VARIABLE_DECLARATION));
    }

    public void testIsTypeDeclarationResource() {
        assertFalse(KiteDeclarationHelper.isTypeDeclaration(KiteElementTypes.RESOURCE_DECLARATION));
    }

    // ========== getTypeTextForDeclaration tests ==========

    public void testGetTypeTextForVariable() {
        assertEquals("variable", KiteDeclarationHelper.getTypeTextForDeclaration(KiteElementTypes.VARIABLE_DECLARATION));
    }

    public void testGetTypeTextForInput() {
        assertEquals("input", KiteDeclarationHelper.getTypeTextForDeclaration(KiteElementTypes.INPUT_DECLARATION));
    }

    public void testGetTypeTextForOutput() {
        assertEquals("output", KiteDeclarationHelper.getTypeTextForDeclaration(KiteElementTypes.OUTPUT_DECLARATION));
    }

    public void testGetTypeTextForResource() {
        assertEquals("resource", KiteDeclarationHelper.getTypeTextForDeclaration(KiteElementTypes.RESOURCE_DECLARATION));
    }

    public void testGetTypeTextForComponent() {
        assertEquals("component", KiteDeclarationHelper.getTypeTextForDeclaration(KiteElementTypes.COMPONENT_DECLARATION));
    }

    public void testGetTypeTextForSchema() {
        assertEquals("schema", KiteDeclarationHelper.getTypeTextForDeclaration(KiteElementTypes.SCHEMA_DECLARATION));
    }

    public void testGetTypeTextForFunction() {
        assertEquals("function", KiteDeclarationHelper.getTypeTextForDeclaration(KiteElementTypes.FUNCTION_DECLARATION));
    }

    // ========== getIconForDeclaration tests ==========

    public void testGetIconForVariable() {
        assertNotNull(KiteDeclarationHelper.getIconForDeclaration(KiteElementTypes.VARIABLE_DECLARATION));
    }

    public void testGetIconForInput() {
        assertNotNull(KiteDeclarationHelper.getIconForDeclaration(KiteElementTypes.INPUT_DECLARATION));
    }

    public void testGetIconForOutput() {
        assertNotNull(KiteDeclarationHelper.getIconForDeclaration(KiteElementTypes.OUTPUT_DECLARATION));
    }

    public void testGetIconForResource() {
        assertNotNull(KiteDeclarationHelper.getIconForDeclaration(KiteElementTypes.RESOURCE_DECLARATION));
    }

    public void testGetIconForComponent() {
        assertNotNull(KiteDeclarationHelper.getIconForDeclaration(KiteElementTypes.COMPONENT_DECLARATION));
    }

    public void testGetIconForSchema() {
        assertNotNull(KiteDeclarationHelper.getIconForDeclaration(KiteElementTypes.SCHEMA_DECLARATION));
    }

    public void testGetIconForFunction() {
        assertNotNull(KiteDeclarationHelper.getIconForDeclaration(KiteElementTypes.FUNCTION_DECLARATION));
    }

    // ========== collectForLoopVariables tests ==========

    public void testCollectForLoopVariables() {
        configureByText("""
                var items = [1, 2, 3]
                for item in items {
                    var x = item
                }
                """);

        PsiFile file = myFixture.getFile();
        List<String> forLoopVars = new ArrayList<>();

        KiteDeclarationHelper.collectForLoopVariables(file, (name, element) -> {
            forLoopVars.add(name);
        });

        assertEquals(1, forLoopVars.size());
        assertTrue(forLoopVars.contains("item"));
    }

    public void testCollectMultipleForLoopVariables() {
        // Use sequential for-loops instead of nested ones for simpler testing
        configureByText("""
                var items = [1, 2, 3]
                var names = ["a", "b"]

                for item in items {
                    var x = item
                }

                for name in names {
                    var y = name
                }
                """);

        PsiFile file = myFixture.getFile();
        List<String> forLoopVars = new ArrayList<>();

        KiteDeclarationHelper.collectForLoopVariables(file, (name, element) -> {
            forLoopVars.add(name);
        });

        assertEquals(2, forLoopVars.size());
        assertTrue(forLoopVars.contains("item"));
        assertTrue(forLoopVars.contains("name"));
    }
}
