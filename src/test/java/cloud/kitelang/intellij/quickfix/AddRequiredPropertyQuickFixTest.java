package cloud.kitelang.intellij.quickfix;

import cloud.kitelang.intellij.KiteTestBase;
import cloud.kitelang.intellij.inspection.KiteMissingPropertyInspection;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for {@link AddRequiredPropertyQuickFix}.
 * Verifies that the quick fix correctly adds missing required properties.
 */
public class AddRequiredPropertyQuickFixTest extends KiteTestBase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new KiteMissingPropertyInspection());
    }

    /**
     * Test that the quick fix adds a missing string property with empty string default.
     */
    public void testAddMissingStringProperty() {
        configureByText("""
                schema Config {
                    string host
                }
                resource Config server {
                }
                """);

        List<IntentionAction> fixes = getAddPropertyFixes();
        assertFalse("Should have a quick fix available", fixes.isEmpty());

        // Apply the fix
        myFixture.launchAction(fixes.get(0));

        String result = myFixture.getFile().getText();
        assertTrue("Should contain the added property", result.contains("host = \"\""));
    }

    /**
     * Test that the quick fix adds a missing number property with 0 default.
     */
    public void testAddMissingNumberProperty() {
        configureByText("""
                schema Config {
                    number port
                }
                resource Config server {
                }
                """);

        List<IntentionAction> fixes = getAddPropertyFixes();
        assertFalse("Should have a quick fix available", fixes.isEmpty());

        // Apply the fix
        myFixture.launchAction(fixes.get(0));

        String result = myFixture.getFile().getText();
        assertTrue("Should contain the added property with number default",
                result.contains("port = 0"));
    }

    /**
     * Test that the quick fix adds a missing boolean property with false default.
     */
    public void testAddMissingBooleanProperty() {
        configureByText("""
                schema Config {
                    boolean enabled
                }
                resource Config server {
                }
                """);

        List<IntentionAction> fixes = getAddPropertyFixes();
        assertFalse("Should have a quick fix available", fixes.isEmpty());

        // Apply the fix
        myFixture.launchAction(fixes.get(0));

        String result = myFixture.getFile().getText();
        assertTrue("Should contain the added property with boolean default",
                result.contains("enabled = false"));
    }

    /**
     * Test that the quick fix is available for missing properties.
     */
    public void testQuickFixAvailability() {
        configureByText("""
                schema Config {
                    string host
                }
                resource Config server {
                }
                """);

        List<IntentionAction> fixes = getAddPropertyFixes();
        assertFalse("Quick fix should be available for missing property", fixes.isEmpty());
        assertTrue("Quick fix should be named correctly",
                fixes.stream().anyMatch(f -> f.getText().contains("Add missing property")));
    }

    /**
     * Test that no quick fix is shown when all properties are provided.
     */
    public void testNoQuickFixWhenAllPropertiesProvided() {
        configureByText("""
                schema Config {
                    string host
                }
                resource Config server {
                    host = "localhost"
                }
                """);

        List<IntentionAction> fixes = getAddPropertyFixes();
        assertTrue("No quick fix should be available when all properties are provided",
                fixes.isEmpty());
    }

    /**
     * Test quick fix text contains property name.
     */
    public void testQuickFixTextContainsPropertyName() {
        configureByText("""
                schema Config {
                    string hostname
                }
                resource Config server {
                }
                """);

        List<IntentionAction> fixes = getAddPropertyFixes();
        assertFalse("Should have a fix", fixes.isEmpty());
        assertTrue("Quick fix text should contain property name",
                fixes.get(0).getText().contains("hostname"));
    }

    /**
     * Helper to get intention actions for adding missing properties.
     */
    private List<IntentionAction> getAddPropertyFixes() {
        myFixture.doHighlighting();
        List<HighlightInfo> warnings = myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Missing required property"))
                .collect(Collectors.toList());

        return warnings.stream()
                .flatMap(w -> w.quickFixActionRanges == null ?
                        java.util.stream.Stream.empty() :
                        w.quickFixActionRanges.stream())
                .map(pair -> pair.getFirst().getAction())
                .filter(action -> action.getText().contains("Add missing property"))
                .collect(Collectors.toList());
    }
}
