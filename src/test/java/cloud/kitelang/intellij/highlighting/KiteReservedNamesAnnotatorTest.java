package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for reserved names detection (using keywords as property names).
 */
public class KiteReservedNamesAnnotatorTest extends KiteTestBase {

    public void testSchemaPropertyTypeKeyword() {
        configureByText("""
                schema Config {
                    string string
                }
                """);

        var errors = getReservedNameErrors();
        assertFalse("Should error for 'string' as property name", errors.isEmpty());
        assertTrue("Error should mention reserved word",
                errors.stream().anyMatch(h -> h.getDescription().contains("reserved")));
    }

    public void testSchemaPropertyIfKeyword() {
        configureByText("""
                schema Config {
                    number if
                }
                """);

        var errors = getReservedNameErrors();
        assertFalse("Should error for 'if' as property name", errors.isEmpty());
    }

    public void testInputReservedName() {
        configureByText("""
                component Server {
                    input string var
                }
                """);

        var errors = getReservedNameErrors();
        assertFalse("Should error for 'var' as input name", errors.isEmpty());
    }

    public void testOutputReservedName() {
        configureByText("""
                component Server {
                    output number return
                }
                """);

        var errors = getReservedNameErrors();
        assertFalse("Should error for 'return' as output name", errors.isEmpty());
    }

    public void testValidPropertyName() {
        configureByText("""
                schema Config {
                    string name
                    number port
                }
                """);

        var errors = getReservedNameErrors();
        assertTrue("Should not error for valid property names", errors.isEmpty());
    }

    public void testValidInputOutput() {
        configureByText("""
                component Server {
                    input string hostname
                    output number port
                }
                """);

        var errors = getReservedNameErrors();
        assertTrue("Should not error for valid input/output names", errors.isEmpty());
    }

    private List<HighlightInfo> getReservedNameErrors() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().toLowerCase().contains("reserved"))
                .collect(Collectors.toList());
    }
}
