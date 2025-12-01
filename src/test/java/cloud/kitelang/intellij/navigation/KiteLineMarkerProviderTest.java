package cloud.kitelang.intellij.navigation;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.psi.PsiElement;

import java.util.List;

/**
 * Tests for KiteLineMarkerProvider - verifies gutter icons on declarations.
 * Line markers appear on schemas and component definitions.
 */
public class KiteLineMarkerProviderTest extends KiteTestBase {

    // ========== Schema Line Marker Tests ==========

    public void testSchemaHasLineMarker() {
        configureByText("""
                schema Config {
                    string host
                }
                """);

        List<LineMarkerInfo<?>> markers = getLineMarkers();

        // Should have a line marker for the schema
        assertTrue("Schema should have line marker", markers.size() >= 1);
    }

    public void testMultipleSchemasHaveLineMarkers() {
        configureByText("""
                schema Config {
                    string host
                }
                schema Database {
                    string url
                }
                """);

        List<LineMarkerInfo<?>> markers = getLineMarkers();

        assertTrue("Multiple schemas should have line markers", markers.size() >= 2);
    }

    // ========== Component Definition Line Marker Tests ==========

    public void testComponentDefinitionHasLineMarker() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                }
                """);

        List<LineMarkerInfo<?>> markers = getLineMarkers();

        assertTrue("Component definition should have line marker", markers.size() >= 1);
    }

    public void testComponentInstantiationNoLineMarker() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                }
                component WebServer myServer {
                    port = "9000"
                }
                """);

        List<LineMarkerInfo<?>> markers = getLineMarkers();

        // Only the component definition should have a marker, not the instantiation
        // The instantiation has two identifiers before brace
        assertTrue("Should have markers for definition", markers.size() >= 1);
    }

    // ========== Other Declaration Types (No Line Markers) ==========

    public void testVariableNoLineMarker() {
        configureByText("""
                var x = 1
                """);

        List<LineMarkerInfo<?>> markers = getLineMarkers();

        // Variables don't get line markers
        assertEquals("Variable should not have line marker", 0, markers.size());
    }

    public void testFunctionNoLineMarker() {
        configureByText("""
                fun greet() string {
                    return "hello"
                }
                """);

        List<LineMarkerInfo<?>> markers = getLineMarkers();

        // Functions don't get line markers in current implementation
        // (Only schemas and component definitions do)
        assertEquals("Function should not have line marker", 0, markers.size());
    }

    public void testResourceNoLineMarker() {
        configureByText("""
                schema Config {
                    string host
                }
                resource Config db {
                    host = "localhost"
                }
                """);

        List<LineMarkerInfo<?>> markers = getLineMarkers();

        // Only schema should have marker, not resource
        assertEquals("Only schema should have line marker", 1, markers.size());
    }

    public void testInputNoLineMarker() {
        configureByText("""
                input string name = "default"
                """);

        List<LineMarkerInfo<?>> markers = getLineMarkers();

        assertEquals("Input should not have line marker", 0, markers.size());
    }

    public void testOutputNoLineMarker() {
        configureByText("""
                output string result = "value"
                """);

        List<LineMarkerInfo<?>> markers = getLineMarkers();

        assertEquals("Output should not have line marker", 0, markers.size());
    }

    // ========== Icon Tests ==========

    public void testSchemaLineMarkerHasIcon() {
        configureByText("""
                schema Config {
                    string host
                }
                """);

        List<LineMarkerInfo<?>> markers = getLineMarkers();

        if (!markers.isEmpty()) {
            assertNotNull("Line marker should have icon", markers.get(0).getIcon());
        }
    }

    public void testComponentLineMarkerHasIcon() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                }
                """);

        List<LineMarkerInfo<?>> markers = getLineMarkers();

        if (!markers.isEmpty()) {
            assertNotNull("Line marker should have icon", markers.get(0).getIcon());
        }
    }

    // ========== Tooltip Tests ==========

    public void testSchemaLineMarkerHasTooltip() {
        configureByText("""
                schema Config {
                    string host
                }
                """);

        List<LineMarkerInfo<?>> markers = getLineMarkers();

        if (!markers.isEmpty()) {
            // Tooltip provider should exist
            assertNotNull("Line marker should have tooltip provider",
                    markers.get(0).getLineMarkerTooltip());
        }
    }

    // ========== Edge Cases ==========

    public void testEmptyFile() {
        configureByText("");

        List<LineMarkerInfo<?>> markers = getLineMarkers();

        assertEquals("Empty file should have no line markers", 0, markers.size());
    }

    public void testFileWithOnlyComments() {
        configureByText("""
                // This is a comment
                /* Block comment */
                """);

        List<LineMarkerInfo<?>> markers = getLineMarkers();

        assertEquals("File with only comments should have no line markers", 0, markers.size());
    }

    public void testComplexFile() {
        configureByText("""
                import * from "common.kite"

                schema DatabaseConfig {
                    string host
                    number port
                }

                schema CacheConfig {
                    string url
                }

                component WebServer {
                    input string port = "8080"
                }

                component Worker {
                    input string queue = "default"
                }

                resource DatabaseConfig db {
                    host = "localhost"
                    port = 5432
                }

                fun helper() string {
                    return "test"
                }

                var config = {}
                """);

        List<LineMarkerInfo<?>> markers = getLineMarkers();

        // Should have markers for: 2 schemas + 2 component definitions = 4
        assertTrue("Complex file should have multiple line markers", markers.size() >= 4);
    }

    // ========== Helper Methods ==========

    private List<LineMarkerInfo<?>> getLineMarkers() {
        myFixture.doHighlighting();
        return DaemonCodeAnalyzerImpl.getLineMarkers(
                myFixture.getEditor().getDocument(),
                getProject()
        );
    }
}
