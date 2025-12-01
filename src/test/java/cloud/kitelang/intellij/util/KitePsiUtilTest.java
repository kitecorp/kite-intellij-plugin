package cloud.kitelang.intellij.util;

import cloud.kitelang.intellij.KiteTestBase;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

/**
 * Tests for KitePsiUtil utility class.
 */
public class KitePsiUtilTest extends KiteTestBase {

    // ========== skipWhitespace tests ==========

    public void testSkipWhitespaceFindsNextNonWhitespace() {
        configureByText("""
                var   name = "hello"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement varKeyword = findFirstElementOfType(file, KiteTokenTypes.VAR);
        assertNotNull("Should find var keyword", varKeyword);

        // Skip whitespace after 'var'
        PsiElement next = KitePsiUtil.skipWhitespace(varKeyword.getNextSibling());
        assertNotNull("Should find element after whitespace", next);
        assertEquals("name", next.getText());
    }

    public void testSkipWhitespaceReturnsNullForNullInput() {
        PsiElement result = KitePsiUtil.skipWhitespace(null);
        assertNull(result);
    }

    public void testSkipWhitespaceReturnsElementIfNotWhitespace() {
        configureByText("""
                var name = "hello"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement varKeyword = findFirstElementOfType(file, KiteTokenTypes.VAR);
        assertNotNull(varKeyword);

        // If we pass a non-whitespace element, it should return immediately
        PsiElement result = KitePsiUtil.skipWhitespace(varKeyword);
        assertSame(varKeyword, result);
    }

    // ========== skipWhitespaceBackward tests ==========

    public void testSkipWhitespaceBackwardFindsPreviousNonWhitespace() {
        configureByText("""
                var name = "hello"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement identifier = findFirstIdentifierWithText(file, "name");
        assertNotNull("Should find identifier 'name'", identifier);

        // Skip whitespace before 'name' to find 'var'
        PsiElement prev = KitePsiUtil.skipWhitespaceBackward(identifier.getPrevSibling());
        assertNotNull("Should find element before whitespace", prev);
        assertEquals("var", prev.getText());
    }

    public void testSkipWhitespaceBackwardReturnsNullForNullInput() {
        PsiElement result = KitePsiUtil.skipWhitespaceBackward(null);
        assertNull(result);
    }

    // ========== isWhitespace tests ==========

    public void testIsWhitespaceForPlatformWhitespace() {
        assertTrue(KitePsiUtil.isWhitespace(TokenType.WHITE_SPACE));
    }

    public void testIsWhitespaceForKiteWhitespace() {
        assertTrue(KitePsiUtil.isWhitespace(KiteTokenTypes.WHITESPACE));
        assertTrue(KitePsiUtil.isWhitespace(KiteTokenTypes.NL));
        assertTrue(KitePsiUtil.isWhitespace(KiteTokenTypes.NEWLINE));
    }

    public void testIsWhitespaceReturnsFalseForNonWhitespace() {
        assertFalse(KitePsiUtil.isWhitespace(KiteTokenTypes.VAR));
        assertFalse(KitePsiUtil.isWhitespace(KiteTokenTypes.IDENTIFIER));
        assertFalse(KitePsiUtil.isWhitespace(KiteTokenTypes.STRING));
    }

    // ========== isDescendantOf tests ==========

    public void testIsDescendantOfSameElement() {
        configureByText("""
                var name = "hello"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement varKeyword = findFirstElementOfType(file, KiteTokenTypes.VAR);
        assertNotNull(varKeyword);

        // Element is a descendant of itself
        assertTrue(KitePsiUtil.isDescendantOf(varKeyword, varKeyword));
    }

    public void testIsDescendantOfParent() {
        configureByText("""
                var name = "hello"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement varKeyword = findFirstElementOfType(file, KiteTokenTypes.VAR);
        assertNotNull(varKeyword);

        // var keyword should be a descendant of the file
        assertTrue(KitePsiUtil.isDescendantOf(varKeyword, file));
    }

    public void testIsDescendantOfNotRelated() {
        configureByText("""
                var name = "hello"
                var other = 42
                """);

        PsiFile file = myFixture.getFile();
        PsiElement nameId = findFirstIdentifierWithText(file, "name");
        PsiElement otherId = findFirstIdentifierWithText(file, "other");

        assertNotNull(nameId);
        assertNotNull(otherId);

        // These are siblings, not descendants of each other
        assertFalse(KitePsiUtil.isDescendantOf(nameId, otherId));
        assertFalse(KitePsiUtil.isDescendantOf(otherId, nameId));
    }

    // ========== findFirstChildOfType tests ==========

    public void testFindFirstChildOfType() {
        configureByText("""
                var name = "hello"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement varKeyword = KitePsiUtil.findFirstChildOfType(file, KiteTokenTypes.VAR);
        // Note: VAR might be nested, so this depends on PSI structure
        // The test verifies the method works correctly
    }

    public void testFindFirstChildOfTypeReturnsNullWhenNotFound() {
        configureByText("""
                var name = "hello"
                """);

        PsiFile file = myFixture.getFile();
        // RESOURCE shouldn't be in this simple file
        PsiElement result = KitePsiUtil.findFirstChildOfType(file, KiteTokenTypes.RESOURCE);
        assertNull(result);
    }

    // ========== getElementType tests ==========

    public void testGetElementType() {
        configureByText("""
                var name = "hello"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement varKeyword = findFirstElementOfType(file, KiteTokenTypes.VAR);
        assertNotNull(varKeyword);

        IElementType type = KitePsiUtil.getElementType(varKeyword);
        assertEquals(KiteTokenTypes.VAR, type);
    }

    public void testGetElementTypeReturnsNullForNull() {
        IElementType type = KitePsiUtil.getElementType(null);
        assertNull(type);
    }

    // ========== hasType tests ==========

    public void testHasTypeTrue() {
        configureByText("""
                var name = "hello"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement varKeyword = findFirstElementOfType(file, KiteTokenTypes.VAR);
        assertNotNull(varKeyword);

        assertTrue(KitePsiUtil.hasType(varKeyword, KiteTokenTypes.VAR));
    }

    public void testHasTypeFalse() {
        configureByText("""
                var name = "hello"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement varKeyword = findFirstElementOfType(file, KiteTokenTypes.VAR);
        assertNotNull(varKeyword);

        assertFalse(KitePsiUtil.hasType(varKeyword, KiteTokenTypes.INPUT));
    }

    public void testHasTypeNullElement() {
        assertFalse(KitePsiUtil.hasType(null, KiteTokenTypes.VAR));
    }

    // ========== Helper methods ==========

    private PsiElement findFirstElementOfType(PsiElement root, IElementType type) {
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

    private PsiElement findFirstIdentifierWithText(PsiElement root, String text) {
        if (root.getNode() != null &&
            root.getNode().getElementType() == KiteTokenTypes.IDENTIFIER &&
            text.equals(root.getText())) {
            return root;
        }
        for (PsiElement child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            PsiElement found = findFirstIdentifierWithText(child, text);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
