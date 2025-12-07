package cloud.kitelang.intellij.util;

import cloud.kitelang.intellij.KiteTestBase;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.util.Map;

/**
 * Tests for KiteSchemaHelper utility class.
 */
public class KiteSchemaHelperTest extends KiteTestBase {

    // ========== findSchemaProperties tests ==========

    public void testFindSchemaPropertiesBasic() {
        configureByText("""
                schema DatabaseConfig {
                    string host
                    number port
                    boolean ssl
                }
                """);

        PsiFile file = myFixture.getFile();
        Map<String, KiteSchemaHelper.SchemaPropertyInfo> properties =
                KiteSchemaHelper.findSchemaProperties(file, "DatabaseConfig");

        assertEquals(3, properties.size());
        assertTrue(properties.containsKey("host"));
        assertTrue(properties.containsKey("port"));
        assertTrue(properties.containsKey("ssl"));

        assertEquals("string", properties.get("host").type());
        assertEquals("number", properties.get("port").type());
        assertEquals("boolean", properties.get("ssl").type());
    }

    public void testFindSchemaPropertiesWithDefaults() {
        configureByText("""
                schema Config {
                    string host = "localhost"
                    number port = 5432
                }
                """);

        PsiFile file = myFixture.getFile();
        Map<String, KiteSchemaHelper.SchemaPropertyInfo> properties =
                KiteSchemaHelper.findSchemaProperties(file, "Config");

        assertEquals(2, properties.size());
        assertTrue(properties.containsKey("host"));
        assertTrue(properties.containsKey("port"));
    }

    public void testFindSchemaPropertiesWithAnyType() {
        configureByText("""
                schema FlexibleConfig {
                    any data
                    string name
                }
                """);

        PsiFile file = myFixture.getFile();
        Map<String, KiteSchemaHelper.SchemaPropertyInfo> properties =
                KiteSchemaHelper.findSchemaProperties(file, "FlexibleConfig");

        assertEquals(2, properties.size());
        assertTrue(properties.containsKey("data"));
        assertEquals("any", properties.get("data").type());
    }

    public void testFindSchemaPropertiesNotFound() {
        configureByText("""
                schema Other {
                    string value
                }
                """);

        PsiFile file = myFixture.getFile();
        Map<String, KiteSchemaHelper.SchemaPropertyInfo> properties =
                KiteSchemaHelper.findSchemaProperties(file, "NonExistent");

        assertTrue(properties.isEmpty());
    }

    public void testFindSchemaPropertiesWithCloudDecorator() {
        configureByText("""
                schema DatabaseConfig {
                    string host
                    @cloud string endpoint
                    number port = 5432
                }
                """);

        PsiFile file = myFixture.getFile();
        Map<String, KiteSchemaHelper.SchemaPropertyInfo> properties =
                KiteSchemaHelper.findSchemaProperties(file, "DatabaseConfig");

        assertEquals(3, properties.size());

        // Regular property - required
        var hostProp = properties.get("host");
        assertNotNull(hostProp);
        assertEquals("string", hostProp.type());
        assertFalse(hostProp.isCloudProvided());
        assertTrue(hostProp.isRequired());

        // Cloud-provided property - not required
        var endpointProp = properties.get("endpoint");
        assertNotNull(endpointProp);
        assertEquals("string", endpointProp.type());
        assertTrue(endpointProp.isCloudProvided());
        assertFalse(endpointProp.isRequired());

        // Property with default - not required
        var portProp = properties.get("port");
        assertNotNull(portProp);
        assertEquals("number", portProp.type());
        assertFalse(portProp.isCloudProvided());
        assertFalse(portProp.isRequired());
    }

    public void testFindSchemaPropertiesCloudWithDefault() {
        configureByText("""
                schema ServerConfig {
                    @cloud string arn = "placeholder"
                    string name
                }
                """);

        PsiFile file = myFixture.getFile();
        Map<String, KiteSchemaHelper.SchemaPropertyInfo> properties =
                KiteSchemaHelper.findSchemaProperties(file, "ServerConfig");

        assertEquals(2, properties.size());

        // Cloud property with default
        var arnProp = properties.get("arn");
        assertNotNull(arnProp);
        assertTrue(arnProp.isCloudProvided());
        assertTrue(arnProp.hasDefaultValue());
        assertFalse(arnProp.isRequired());

        // Regular property without default
        var nameProp = properties.get("name");
        assertNotNull(nameProp);
        assertFalse(nameProp.isCloudProvided());
        assertTrue(nameProp.isRequired());
    }

    public void testFindSchemaPropertiesMultipleCloudProperties() {
        configureByText("""
                schema AWSResource {
                    string name
                    @cloud string arn
                    @cloud string id
                    @cloud string region
                }
                """);

        PsiFile file = myFixture.getFile();
        Map<String, KiteSchemaHelper.SchemaPropertyInfo> properties =
                KiteSchemaHelper.findSchemaProperties(file, "AWSResource");

        assertEquals(4, properties.size());

        // User-provided property
        assertFalse(properties.get("name").isCloudProvided());
        assertTrue(properties.get("name").isRequired());

        // Cloud-provided properties
        assertTrue(properties.get("arn").isCloudProvided());
        assertTrue(properties.get("id").isCloudProvided());
        assertTrue(properties.get("region").isCloudProvided());

        // Cloud properties should not be required
        assertFalse(properties.get("arn").isRequired());
        assertFalse(properties.get("id").isRequired());
        assertFalse(properties.get("region").isRequired());
    }

    public void testFindSchemaPropertiesFromImportedFile() {
        addFile("schemas.kite", """
                schema ImportedSchema {
                    string importedProp
                    number importedNum
                }
                """);

        configureByText("""
                import * from "schemas.kite"
                
                resource ImportedSchema myResource {
                    importedProp = "value"
                }
                """);

        PsiFile file = myFixture.getFile();
        Map<String, KiteSchemaHelper.SchemaPropertyInfo> properties =
                KiteSchemaHelper.findSchemaProperties(file, "ImportedSchema");

        assertEquals(2, properties.size());
        assertTrue(properties.containsKey("importedProp"));
        assertTrue(properties.containsKey("importedNum"));
    }

    // ========== extractResourceTypeName tests ==========

    public void testExtractResourceTypeName() {
        configureByText("""
                resource DatabaseConfig mydb {
                    host = "localhost"
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);

        assertNotNull(resourceDecl);
        String typeName = KiteSchemaHelper.extractResourceTypeName(resourceDecl);
        assertEquals("DatabaseConfig", typeName);
    }

    public void testExtractResourceTypeNameQualified() {
        configureByText("""
                resource VM.Instance server {
                    name = "web-server"
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);

        assertNotNull(resourceDecl);
        String typeName = KiteSchemaHelper.extractResourceTypeName(resourceDecl);
        assertEquals("VM", typeName); // Returns first identifier (type namespace)
    }

    // ========== extractSchemaName tests ==========

    public void testExtractSchemaName() {
        configureByText("""
                schema MySchema {
                    string prop
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement schemaDecl = findFirstElementOfType(file, KiteElementTypes.SCHEMA_DECLARATION);

        assertNotNull(schemaDecl);
        String name = KiteSchemaHelper.extractSchemaName(schemaDecl);
        assertEquals("MySchema", name);
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
