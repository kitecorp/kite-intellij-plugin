package cloud.kitelang.intellij;

import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * Debug test to understand PSI structure.
 */
public class KitePsiDebugTest extends KiteTestBase {

    public void testAssignmentPsiStructure() {
        configureByText("""
                var x = 5
                x = x
                """);

        PsiFile file = myFixture.getFile();
        System.out.println("=== PSI Structure ===");
        printPsiTree(file, 0);
    }

    public void testSelfAssignmentDetection() {
        configureByText("""
                var x = 5
                x = x
                """);

        PsiFile file = myFixture.getFile();
        System.out.println("=== Checking Self Assignment ===");

        for (PsiElement child = file.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null && child.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                System.out.println("Found IDENTIFIER: \"" + child.getText() + "\"");

                PsiElement next = KitePsiUtil.skipWhitespace(child.getNextSibling());
                System.out.println("  Next (after skip whitespace): " + (next != null ? next.getNode().getElementType() + " \"" + next.getText() + "\"" : "null"));

                if (next != null && next.getNode() != null && next.getNode().getElementType() == KiteTokenTypes.ASSIGN) {
                    System.out.println("  Found ASSIGN");
                    PsiElement rightSide = KitePsiUtil.skipWhitespace(next.getNextSibling());
                    System.out.println("  Right side: " + (rightSide != null ? rightSide.getNode().getElementType() + " \"" + rightSide.getText() + "\"" : "null"));

                    if (rightSide != null && rightSide.getNode() != null && rightSide.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                        System.out.println("  Right side is IDENTIFIER");
                        System.out.println("  Same name? " + child.getText().equals(rightSide.getText()));
                    }
                }
            }
        }
    }

    public void testAllWarnings() {
        configureByText("""
                var x = 5
                x = x
                """);

        System.out.println("=== All Highlights ===");
        var highlights = myFixture.doHighlighting();
        for (var h : highlights) {
            System.out.println(h.getSeverity() + ": " + h.getDescription() + " at " + h.getStartOffset() + "-" + h.getEndOffset());
        }
    }

    private void printPsiTree(PsiElement element, int indent) {
        String indentStr = "  ".repeat(indent);
        String nodeType = element.getNode() != null ? element.getNode().getElementType().toString() : "null";
        String text = element.getText().replace("\n", "\\n").replace("\r", "\\r");
        if (text.length() > 50) {
            text = text.substring(0, 50) + "...";
        }
        System.out.println(indentStr + nodeType + " [" + element.getClass().getSimpleName() + "]: \"" + text + "\"");

        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            printPsiTree(child, indent + 1);
        }
    }
}
