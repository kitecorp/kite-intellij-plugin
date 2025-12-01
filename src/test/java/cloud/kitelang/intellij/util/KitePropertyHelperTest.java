package cloud.kitelang.intellij.util;

import cloud.kitelang.intellij.KiteTestBase;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests for KitePropertyHelper utility class.
 */
public class KitePropertyHelperTest extends KiteTestBase {

    // ========== collectExistingPropertyNames tests ==========

    public void testCollectExistingPropertyNamesBasic() {
        configureByText("""
                schema VM {
                    string name
                    number memory
                }
                resource VM server {
                    name = "web"
                    memory = 1024
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);

        assertNotNull(resourceDecl);
        Set<String> properties = KitePropertyHelper.collectExistingPropertyNames(resourceDecl);

        assertEquals(2, properties.size());
        assertTrue(properties.contains("name"));
        assertTrue(properties.contains("memory"));
    }

    public void testCollectExistingPropertyNamesWithPlusAssign() {
        configureByText("""
                schema Config {
                    string[] tags
                }
                resource Config myConfig {
                    tags = ["base"]
                    tags += ["extra"]
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);

        assertNotNull(resourceDecl);
        Set<String> properties = KitePropertyHelper.collectExistingPropertyNames(resourceDecl);

        assertTrue(properties.contains("tags"));
    }

    public void testCollectExistingPropertyNamesEmpty() {
        configureByText("""
                schema VM {
                    string name
                }
                resource VM server {
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);

        assertNotNull(resourceDecl);
        Set<String> properties = KitePropertyHelper.collectExistingPropertyNames(resourceDecl);

        assertTrue(properties.isEmpty());
    }

    // ========== collectPropertiesFromDeclaration tests ==========

    public void testCollectPropertiesFromResourceDeclaration() {
        configureByText("""
                schema VM {
                    string name
                    number memory
                }
                resource VM server {
                    name = "web"
                    memory = 1024
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);

        assertNotNull(resourceDecl);
        List<String> properties = new ArrayList<>();

        KitePropertyHelper.collectPropertiesFromDeclaration(resourceDecl, (name, element) -> {
            properties.add(name);
        });

        assertEquals(2, properties.size());
        assertTrue(properties.contains("name"));
        assertTrue(properties.contains("memory"));
    }

    public void testCollectPropertiesFromComponentShowsOnlyOutputs() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                    input string host = "localhost"
                    output string endpoint = "http://localhost:8080"
                    output string status = "running"
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement componentDecl = findFirstElementOfType(file, KiteElementTypes.COMPONENT_DECLARATION);

        assertNotNull(componentDecl);
        List<String> properties = new ArrayList<>();

        KitePropertyHelper.collectPropertiesFromDeclaration(componentDecl, (name, element) -> {
            properties.add(name);
        });

        // Only outputs should be accessible from outside the component
        assertEquals(2, properties.size());
        assertTrue(properties.contains("endpoint"));
        assertTrue(properties.contains("status"));
        assertFalse(properties.contains("port"));
        assertFalse(properties.contains("host"));
    }

    public void testCollectPropertiesFromSchemaShowsInputsAndOutputs() {
        configureByText("""
                schema Config {
                    input string setting
                    output string result
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement schemaDecl = findFirstElementOfType(file, KiteElementTypes.SCHEMA_DECLARATION);

        assertNotNull(schemaDecl);
        List<String> properties = new ArrayList<>();

        KitePropertyHelper.collectPropertiesFromDeclaration(schemaDecl, (name, element) -> {
            properties.add(name);
        });

        // Schemas show both inputs and outputs
        assertEquals(2, properties.size());
        assertTrue(properties.contains("setting"));
        assertTrue(properties.contains("result"));
    }

    // ========== collectPropertiesFromObjectLiteral tests ==========

    public void testCollectPropertiesFromObjectLiteral() {
        configureByText("""
                var config = {
                    host = "localhost"
                    port = 8080
                    ssl = true
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement varDecl = findFirstElementOfType(file, KiteElementTypes.VARIABLE_DECLARATION);
        assertNotNull(varDecl);

        // Find the object literal within the variable declaration
        PsiElement objectLiteral = findFirstElementOfType(varDecl, KiteElementTypes.OBJECT_LITERAL);
        if (objectLiteral != null) {
            List<String> properties = new ArrayList<>();
            KitePropertyHelper.collectPropertiesFromObjectLiteral(objectLiteral, (name, element) -> {
                properties.add(name);
            });

            assertEquals(3, properties.size());
            assertTrue(properties.contains("host"));
            assertTrue(properties.contains("port"));
            assertTrue(properties.contains("ssl"));
        }
    }

    // ========== findPropertyValue tests ==========

    public void testFindPropertyValue() {
        configureByText("""
                schema VM {
                    string name
                }
                resource VM server {
                    name = "web-server"
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);

        assertNotNull(resourceDecl);
        PsiElement value = KitePropertyHelper.findPropertyValue(resourceDecl, "name");

        // Simply verify we found a value - the element structure varies
        assertNotNull("Should find value for 'name' property", value);
    }

    public void testFindPropertyValueNotFound() {
        configureByText("""
                schema VM {
                    string name
                }
                resource VM server {
                    name = "web"
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);

        assertNotNull(resourceDecl);
        PsiElement value = KitePropertyHelper.findPropertyValue(resourceDecl, "nonexistent");

        assertNull(value);
    }

    public void testFindPropertyValueNestedObject() {
        configureByText("""
                schema VM {
                    any tags
                }
                resource VM server {
                    tags = {
                        Environment = "production"
                        Team = "backend"
                    }
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);

        assertNotNull(resourceDecl);
        PsiElement value = KitePropertyHelper.findPropertyValue(resourceDecl, "tags");

        assertNotNull(value);
        assertTrue(value.getText().contains("Environment"));
    }

    // ========== visitPropertiesInContext tests ==========

    public void testVisitPropertiesInContext() {
        configureByText("""
                schema VM {
                    string name
                    number memory
                }
                resource VM server {
                    name = "web"
                    memory = 1024
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);

        assertNotNull(resourceDecl);
        Set<String> visitedProperties = new HashSet<>();

        KitePropertyHelper.visitPropertiesInContext(resourceDecl, (name, valueElement) -> {
            visitedProperties.add(name);
            assertNotNull(valueElement);
        });

        assertEquals(2, visitedProperties.size());
        assertTrue(visitedProperties.contains("name"));
        assertTrue(visitedProperties.contains("memory"));
    }

    // ========== Helper methods ==========

    private PsiElement findFirstElementOfType(PsiElement root, com.intellij.psi.tree.IElementType type) {
        if (root.getNode() != null && root.getNode().getElementType() == type) {
            return root;
        }
        for (PsiElement child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            PsiElement found = findFirstElementOfType(child, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
