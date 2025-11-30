package cloud.kitelang.intellij.navigation;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;

/**
 * Tests for string interpolation navigation (Cmd+Click on $region in strings).
 * Verifies that navigation works for both local and imported symbols.
 */
public class KiteStringInterpolationNavigationTest extends KiteTestBase {

    private final KiteGotoDeclarationHandler handler = new KiteGotoDeclarationHandler();

    /**
     * Test navigation for local variable in simple interpolation ($var).
     */
    public void testLocalVariableSimpleInterpolation() {
        configureByText("""
                var region = "us-east-1"
                var endpoint = "https://$region.api.example.com"
                """);

        // Find the interpolation token at $region
        int offset = myFixture.getFile().getText().indexOf("$region");
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element at $region", elementAtCaret);

        // Call the handler directly
        Editor editor = myFixture.getEditor();
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, offset, editor);

        assertNotNull("Should have navigation targets for $region", targets);
        assertTrue("Should have at least one target", targets.length > 0);
        assertEquals("region", targets[0].getText());
    }

    /**
     * Test navigation for local variable in braced interpolation (${var}).
     */
    public void testLocalVariableBracedInterpolation() {
        configureByText("""
                var region = "us-east-1"
                var endpoint = "https://${region}.api.example.com"
                """);

        // Find the interpolation identifier inside ${...}
        int offset = myFixture.getFile().getText().indexOf("${region}") + 2; // Position inside braces
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element at ${region}", elementAtCaret);

        // Call the handler directly
        Editor editor = myFixture.getEditor();
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, offset, editor);

        assertNotNull("Should have navigation targets for ${region}", targets);
        assertTrue("Should have at least one target", targets.length > 0);
        assertEquals("region", targets[0].getText());
    }

    /**
     * Test navigation for imported variable in simple interpolation.
     * This is the main use case described in FUTURE_TASKS.md.
     */
    public void testImportedVariableSimpleInterpolation() {
        addFile("common.kite", """
                var region = "us-east-1"
                """);

        configureByText("""
                import region from "common.kite"
                
                var endpoint = "https://$region.api.example.com"
                """);

        // Find the interpolation token at $region
        int offset = myFixture.getFile().getText().indexOf("$region");
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element at $region", elementAtCaret);

        // Call the handler directly
        Editor editor = myFixture.getEditor();
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, offset, editor);

        assertNotNull("Should have navigation targets for imported $region", targets);
        assertTrue("Should have at least one target", targets.length > 0);
        assertEquals("region", targets[0].getText());
        // Should resolve to the declaration in common.kite
        assertEquals("common.kite", targets[0].getContainingFile().getName());
    }

    /**
     * Test navigation for imported variable in braced interpolation.
     */
    public void testImportedVariableBracedInterpolation() {
        addFile("common.kite", """
                var region = "us-east-1"
                """);

        configureByText("""
                import region from "common.kite"
                
                var endpoint = "https://${region}.api.example.com"
                """);

        // Find the interpolation identifier inside ${...}
        int offset = myFixture.getFile().getText().indexOf("${region}") + 2;
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element at ${region}", elementAtCaret);

        // Call the handler directly
        Editor editor = myFixture.getEditor();
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, offset, editor);

        assertNotNull("Should have navigation targets for imported ${region}", targets);
        assertTrue("Should have at least one target", targets.length > 0);
        assertEquals("region", targets[0].getText());
        assertEquals("common.kite", targets[0].getContainingFile().getName());
    }

    /**
     * Test navigation with wildcard import.
     */
    public void testWildcardImportInterpolation() {
        addFile("common.kite", """
                var appName = "myapp"
                """);

        configureByText("""
                import * from "common.kite"
                
                var title = "Welcome to $appName"
                """);

        int offset = myFixture.getFile().getText().indexOf("$appName");
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element at $appName", elementAtCaret);

        Editor editor = myFixture.getEditor();
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, offset, editor);

        assertNotNull("Should have navigation targets for wildcard imported $appName", targets);
        assertTrue("Should have at least one target", targets.length > 0);
        assertEquals("appName", targets[0].getText());
        assertEquals("common.kite", targets[0].getContainingFile().getName());
    }

    /**
     * Test navigation with multiple interpolations in same string.
     */
    public void testMultipleInterpolationsInString() {
        addFile("common.kite", """
                var region = "us-east-1"
                var service = "api"
                """);

        configureByText("""
                import region, service from "common.kite"
                
                var endpoint = "https://$region.$service.example.com"
                """);

        Editor editor = myFixture.getEditor();

        // Test first interpolation ($region)
        int regionOffset = myFixture.getFile().getText().indexOf("$region");
        PsiElement regionElement = myFixture.getFile().findElementAt(regionOffset);
        assertNotNull("Should find element at $region", regionElement);

        PsiElement[] regionTargets = handler.getGotoDeclarationTargets(regionElement, regionOffset, editor);
        assertNotNull("Should have targets for $region", regionTargets);
        assertTrue("Should have at least one target for region", regionTargets.length > 0);
        assertEquals("region", regionTargets[0].getText());

        // Test second interpolation ($service)
        int serviceOffset = myFixture.getFile().getText().indexOf("$service");
        PsiElement serviceElement = myFixture.getFile().findElementAt(serviceOffset);
        assertNotNull("Should find element at $service", serviceElement);

        PsiElement[] serviceTargets = handler.getGotoDeclarationTargets(serviceElement, serviceOffset, editor);
        assertNotNull("Should have targets for $service", serviceTargets);
        assertTrue("Should have at least one target for service", serviceTargets.length > 0);
        assertEquals("service", serviceTargets[0].getText());
    }

    /**
     * Test that interpolation of undefined variable doesn't resolve.
     */
    public void testUndefinedVariableInterpolationNoResolution() {
        configureByText("""
                var endpoint = "https://$undefined.api.example.com"
                """);

        int offset = myFixture.getFile().getText().indexOf("$undefined");
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element", elementAtCaret);

        Editor editor = myFixture.getEditor();
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, offset, editor);

        // Undefined variable should not resolve
        assertTrue("Undefined variable should not resolve",
                targets == null || targets.length == 0);
    }
}
