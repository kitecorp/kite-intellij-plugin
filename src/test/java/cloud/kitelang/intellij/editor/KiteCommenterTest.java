package cloud.kitelang.intellij.editor;

import cloud.kitelang.intellij.KiteCommenter;

/**
 * Tests for KiteCommenter - verifies comment syntax configuration.
 */
public class KiteCommenterTest extends junit.framework.TestCase {

    private final KiteCommenter commenter = new KiteCommenter();

    // ========== Line Comment Tests ==========

    public void testLineCommentPrefix() {
        assertEquals("Line comment prefix should be //", "//", commenter.getLineCommentPrefix());
    }

    public void testLineCommentPrefixNotNull() {
        assertNotNull("Line comment prefix should not be null", commenter.getLineCommentPrefix());
    }

    // ========== Block Comment Tests ==========

    public void testBlockCommentPrefix() {
        assertEquals("Block comment prefix should be /*", "/*", commenter.getBlockCommentPrefix());
    }

    public void testBlockCommentSuffix() {
        assertEquals("Block comment suffix should be */", "*/", commenter.getBlockCommentSuffix());
    }

    public void testBlockCommentPrefixNotNull() {
        assertNotNull("Block comment prefix should not be null", commenter.getBlockCommentPrefix());
    }

    public void testBlockCommentSuffixNotNull() {
        assertNotNull("Block comment suffix should not be null", commenter.getBlockCommentSuffix());
    }

    // ========== Commented Block Comment Tests ==========

    public void testCommentedBlockCommentPrefix() {
        // Kite doesn't support nested block comments
        assertNull("Commented block comment prefix should be null", commenter.getCommentedBlockCommentPrefix());
    }

    public void testCommentedBlockCommentSuffix() {
        // Kite doesn't support nested block comments
        assertNull("Commented block comment suffix should be null", commenter.getCommentedBlockCommentSuffix());
    }

    // ========== Comment Style Consistency Tests ==========

    public void testCommentStyleMatchesKiteSyntax() {
        // Verify comment style matches Kite language specification
        String linePrefix = commenter.getLineCommentPrefix();
        String blockPrefix = commenter.getBlockCommentPrefix();
        String blockSuffix = commenter.getBlockCommentSuffix();

        // Line comments use C-style //
        assertEquals("Line comment should use C-style", "//", linePrefix);

        // Block comments use C-style /* */
        assertEquals("Block comment should use C-style start", "/*", blockPrefix);
        assertEquals("Block comment should use C-style end", "*/", blockSuffix);
    }
}
