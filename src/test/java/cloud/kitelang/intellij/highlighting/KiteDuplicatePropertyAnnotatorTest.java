package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for duplicate property detection in schemas, resources, and component instances.
 */
public class KiteDuplicatePropertyAnnotatorTest extends KiteTestBase {

    // ========== Schema Tests ==========

    public void testDuplicatePropertyInSchema() {
        configureByText("""
                schema Config {
                    string host
                    number host
                }
                """);

        var highlights = getDuplicatePropertyErrors();
        assertFalse("Should report duplicate property in schema", highlights.isEmpty());
        assertTrue("Error should mention 'host'",
                highlights.stream().anyMatch(h -> h.getDescription().contains("'host'")));
    }

    public void testNoDuplicatesInSchema() {
        configureByText("""
                schema Config {
                    string host
                    number port
                    boolean ssl
                }
                """);

        var highlights = getDuplicatePropertyErrors();
        assertTrue("Schema with unique properties should have no errors", highlights.isEmpty());
    }

    public void testTripleDuplicateInSchema() {
        configureByText("""
                schema Config {
                    string name
                    number name
                    boolean name
                }
                """);

        var highlights = getDuplicatePropertyErrors();
        assertEquals("Should report 2 duplicates", 2, highlights.size());
    }

    // ========== Resource Tests ==========

    public void testDuplicatePropertyInResource() {
        configureByText("""
                schema Config {
                    string host
                }
                resource Config server {
                    host = "localhost"
                    host = "127.0.0.1"
                }
                """);

        var highlights = getDuplicatePropertyErrors();
        assertFalse("Should report duplicate property in resource", highlights.isEmpty());
    }

    public void testNoDuplicatesInResource() {
        configureByText("""
                schema Config {
                    string host
                    number port
                }
                resource Config server {
                    host = "localhost"
                    port = 8080
                }
                """);

        var highlights = getDuplicatePropertyErrors();
        assertTrue("Resource with unique properties should have no errors", highlights.isEmpty());
    }

    // ========== Component Instance Tests ==========

    public void testDuplicateInputInComponentInstance() {
        configureByText("""
                component Server {
                    input string port = "8080"
                }
                component Server srv {
                    port = "3000"
                    port = "4000"
                }
                """);

        var highlights = getDuplicatePropertyErrors();
        assertFalse("Should report duplicate input in component instance", highlights.isEmpty());
    }

    public void testNoDuplicatesInComponentInstance() {
        configureByText("""
                component Server {
                    input string port = "8080"
                    input string host = "localhost"
                }
                component Server srv {
                    port = "3000"
                    host = "api.example.com"
                }
                """);

        var highlights = getDuplicatePropertyErrors();
        assertTrue("Component instance with unique inputs should have no errors", highlights.isEmpty());
    }

    // ========== Decorator Tests ==========

    public void testCloudDecoratorDoesNotCauseFalseDuplicate() {
        configureByText("""
                schema AWSResource {
                    string name
                    @cloud string arn
                    @cloud string id
                }
                """);

        var highlights = getDuplicatePropertyErrors();
        assertTrue("@cloud decorator should not cause false 'Duplicate property' errors", highlights.isEmpty());
    }

    public void testMultipleCloudPropertiesNoDuplicate() {
        configureByText("""
                schema Database {
                    string host
                    number port = 5432
                    @cloud string endpoint
                    @cloud string connectionString
                    string username
                }
                """);

        var highlights = getDuplicatePropertyErrors();
        assertTrue("Mixed @cloud and regular properties should not report duplicates", highlights.isEmpty());
    }

    public void testActualDuplicateWithCloudDecorator() {
        configureByText("""
                schema Config {
                    @cloud string arn
                    string arn
                }
                """);

        var highlights = getDuplicatePropertyErrors();
        assertFalse("Should detect actual duplicate even with @cloud decorator", highlights.isEmpty());
        assertTrue("Error should mention 'arn'",
                highlights.stream().anyMatch(h -> h.getDescription().contains("'arn'")));
    }

    // ========== Edge Cases ==========

    public void testSamePropertyInDifferentSchemas() {
        configureByText("""
                schema ConfigA {
                    string name
                }
                schema ConfigB {
                    string name
                }
                """);

        var highlights = getDuplicatePropertyErrors();
        assertTrue("Same property in different schemas should not conflict", highlights.isEmpty());
    }

    public void testSamePropertyInDifferentResources() {
        configureByText("""
                schema Config {
                    string name
                }
                resource Config serverA {
                    name = "first"
                }
                resource Config serverB {
                    name = "second"
                }
                """);

        var highlights = getDuplicatePropertyErrors();
        assertTrue("Same property in different resources should not conflict", highlights.isEmpty());
    }

    private List<HighlightInfo> getDuplicatePropertyErrors() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter(h -> h.getDescription() != null &&
                             (h.getDescription().contains("Duplicate property") ||
                              h.getDescription().contains("Duplicate input")))
                .collect(Collectors.toList());
    }
}
