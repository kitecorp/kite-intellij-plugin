package cloud.kitelang.intellij.util;

import cloud.kitelang.intellij.KiteTestBase;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;

import java.util.List;

/**
 * Tests for KiteIndexedResourceHelper utility class.
 * Tests @count decorator detection, for-loop context detection,
 * and indexed access validation.
 */
public class KiteIndexedResourceHelperTest extends KiteTestBase {

    // ========== @count decorator tests ==========

    public void testExtractCountValueBasic() {
        configureByText("""
                schema vm { string name }
                @count(5)
                resource vm server {
                    name = "srv"
                }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        assertNotNull(resourceDecl);

        var countValue = KiteIndexedResourceHelper.extractCountValue(resourceDecl);
        assertEquals(Integer.valueOf(5), countValue);
    }

    public void testExtractCountValueWithThree() {
        configureByText("""
                schema vm { string name }
                @count(3)
                resource vm server { name = "srv" }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        assertNotNull(resourceDecl);

        var countValue = KiteIndexedResourceHelper.extractCountValue(resourceDecl);
        assertEquals(Integer.valueOf(3), countValue);
    }

    public void testExtractCountValueNoDecorator() {
        configureByText("""
                schema vm { string name }
                resource vm server { name = "srv" }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        assertNotNull(resourceDecl);

        var countValue = KiteIndexedResourceHelper.extractCountValue(resourceDecl);
        assertNull(countValue);
    }

    public void testExtractCountValueWithOtherDecorators() {
        configureByText("""
                schema vm { string name }
                @tags({env: "prod"})
                @count(10)
                @description("Test")
                resource vm server { name = "srv" }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        assertNotNull(resourceDecl);

        var countValue = KiteIndexedResourceHelper.extractCountValue(resourceDecl);
        assertEquals(Integer.valueOf(10), countValue);
    }

    // ========== getIndexedInfo tests for @count ==========

    public void testGetIndexedInfoWithCount() {
        configureByText("""
                schema vm { string name }
                @count(3)
                resource vm server { name = "srv" }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        assertNotNull(resourceDecl);

        var info = KiteIndexedResourceHelper.getIndexedInfo(resourceDecl);
        assertNotNull(info);
        assertEquals(KiteIndexedResourceHelper.IndexType.NUMERIC, info.indexType());
        assertEquals(Integer.valueOf(3), info.countValue());
        assertNull(info.rangeStart());
        assertNull(info.rangeEnd());
        assertNull(info.stringKeys());
    }

    public void testGetIndexedInfoValidNumericIndices() {
        configureByText("""
                schema vm { string name }
                @count(3)
                resource vm server { name = "srv" }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        var info = KiteIndexedResourceHelper.getIndexedInfo(resourceDecl);
        assertNotNull(info);

        assertTrue(info.isValidNumericIndex(0));
        assertTrue(info.isValidNumericIndex(1));
        assertTrue(info.isValidNumericIndex(2));
        assertFalse(info.isValidNumericIndex(3));
        assertFalse(info.isValidNumericIndex(-1));
    }

    public void testGetIndexedInfoValidIndicesList() {
        configureByText("""
                schema vm { string name }
                @count(4)
                resource vm server { name = "srv" }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        var info = KiteIndexedResourceHelper.getIndexedInfo(resourceDecl);
        assertNotNull(info);

        var indices = info.getValidIndices();
        assertEquals(List.of("0", "1", "2", "3"), indices);
    }

    public void testGetIndexedInfoNonIndexedResource() {
        configureByText("""
                schema vm { string name }
                resource vm server { name = "srv" }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        assertNotNull(resourceDecl);

        var info = KiteIndexedResourceHelper.getIndexedInfo(resourceDecl);
        assertNull(info);
    }

    // ========== For-loop range tests ==========

    public void testGetIndexedInfoForLoopRange() {
        configureByText("""
                schema vm { string name }
                for i in 0..5 {
                    resource vm server { name = "srv-${i}" }
                }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        assertNotNull("Resource declaration not found", resourceDecl);

        var info = KiteIndexedResourceHelper.getIndexedInfo(resourceDecl);
        assertNotNull("IndexedResourceInfo should not be null for resource in for-loop", info);
        assertEquals(KiteIndexedResourceHelper.IndexType.NUMERIC, info.indexType());
        assertEquals(Integer.valueOf(0), info.rangeStart());
        assertEquals(Integer.valueOf(5), info.rangeEnd());
        assertNull(info.countValue());
    }

    public void testGetIndexedInfoForLoopRangeStartingAtOne() {
        configureByText("""
                schema vm { string name }
                for i in 1..10 {
                    resource vm server { name = "srv-${i}" }
                }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        var info = KiteIndexedResourceHelper.getIndexedInfo(resourceDecl);
        assertNotNull(info);

        assertEquals(Integer.valueOf(1), info.rangeStart());
        assertEquals(Integer.valueOf(10), info.rangeEnd());
    }

    public void testGetIndexedInfoForLoopRangeValidIndices() {
        configureByText("""
                schema vm { string name }
                for i in 0..3 {
                    resource vm server { name = "srv" }
                }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        var info = KiteIndexedResourceHelper.getIndexedInfo(resourceDecl);
        assertNotNull(info);

        assertTrue(info.isValidNumericIndex(0));
        assertTrue(info.isValidNumericIndex(1));
        assertTrue(info.isValidNumericIndex(2));
        assertFalse(info.isValidNumericIndex(3)); // End is exclusive
        assertFalse(info.isValidNumericIndex(-1));
    }

    // ========== For-loop array tests ==========

    public void testGetIndexedInfoForLoopInlineArray() {
        configureByText("""
                schema vm { string name }
                for env in ["dev", "staging", "prod"] {
                    resource vm server { name = "srv-${env}" }
                }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        assertNotNull(resourceDecl);

        var info = KiteIndexedResourceHelper.getIndexedInfo(resourceDecl);
        assertNotNull(info);
        assertEquals(KiteIndexedResourceHelper.IndexType.STRING, info.indexType());
        assertEquals(List.of("dev", "staging", "prod"), info.stringKeys());
        assertNull(info.countValue());
        assertNull(info.rangeStart());
    }

    public void testGetIndexedInfoForLoopArrayValidKeys() {
        configureByText("""
                schema vm { string name }
                for env in ["dev", "prod"] {
                    resource vm server { name = "srv" }
                }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        var info = KiteIndexedResourceHelper.getIndexedInfo(resourceDecl);
        assertNotNull(info);

        assertTrue(info.isValidStringKey("dev"));
        assertTrue(info.isValidStringKey("prod"));
        assertFalse(info.isValidStringKey("staging"));
        assertFalse(info.isValidStringKey("unknown"));
    }

    public void testGetIndexedInfoForLoopArrayValidIndicesList() {
        configureByText("""
                schema vm { string name }
                for region in ["us-east", "eu-west"] {
                    resource vm server { name = "srv" }
                }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        var info = KiteIndexedResourceHelper.getIndexedInfo(resourceDecl);
        assertNotNull(info);

        var indices = info.getValidIndices();
        assertEquals(List.of("\"us-east\"", "\"eu-west\""), indices);
    }

    // ========== parseIndexedAccess tests ==========

    public void testParseIndexedAccessNumeric() {
        configureByText("""
                schema vm { string name }
                @count(5)
                resource vm server { name = "srv" }
                var x = server[0]
                """);

        var file = myFixture.getFile();
        // Find the LBRACK token in server[0]
        var lbrack = findTokenOfType(file, KiteTokenTypes.LBRACK);
        assertNotNull(lbrack);

        var accessInfo = KiteIndexedResourceHelper.parseIndexedAccess(lbrack);
        assertNotNull(accessInfo);
        assertEquals("server", accessInfo.baseName());
        assertEquals(Integer.valueOf(0), accessInfo.numericValue());
        assertNull(accessInfo.stringValue());
    }

    public void testParseIndexedAccessNumericTwo() {
        configureByText("""
                schema vm { string name }
                @count(5)
                resource vm server { name = "srv" }
                var x = server[2]
                """);

        var file = myFixture.getFile();
        var lbrack = findTokenOfType(file, KiteTokenTypes.LBRACK);
        assertNotNull(lbrack);

        var accessInfo = KiteIndexedResourceHelper.parseIndexedAccess(lbrack);
        assertNotNull(accessInfo);
        assertEquals("server", accessInfo.baseName());
        assertEquals(Integer.valueOf(2), accessInfo.numericValue());
    }

    public void testParseIndexedAccessString() {
        configureByText("""
                schema vm { string name }
                for env in ["dev", "prod"] {
                    resource vm server { name = "srv" }
                }
                var x = server["dev"]
                """);

        var file = myFixture.getFile();
        // Find the LBRACK that's part of server["dev"], not the array literal
        var lbracks = findAllTokensOfType(file, KiteTokenTypes.LBRACK);
        // The second LBRACK is the one in server["dev"]
        var serverLbrack = lbracks.size() > 1 ? lbracks.get(1) : lbracks.get(0);

        var accessInfo = KiteIndexedResourceHelper.parseIndexedAccess(serverLbrack);
        assertNotNull(accessInfo);
        assertEquals("server", accessInfo.baseName());
        assertEquals("dev", accessInfo.stringValue());
        assertNull(accessInfo.numericValue());
    }

    public void testParseIndexedAccessVariable() {
        configureByText("""
                schema vm { string name }
                @count(5)
                resource vm server { name = "srv" }
                var i = 2
                var x = server[i]
                """);

        var file = myFixture.getFile();
        var lbrack = findTokenOfType(file, KiteTokenTypes.LBRACK);
        assertNotNull(lbrack);

        var accessInfo = KiteIndexedResourceHelper.parseIndexedAccess(lbrack);
        assertNotNull(accessInfo);
        assertEquals("server", accessInfo.baseName());
        // Variable index - neither numeric nor string literal
        assertNull(accessInfo.numericValue());
        assertNull(accessInfo.stringValue());
        assertTrue(accessInfo.isVariableIndex());
    }

    // ========== Validation tests ==========

    public void testValidateValidNumericIndex() {
        configureByText("""
                schema vm { string name }
                @count(5)
                resource vm server { name = "srv" }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        var info = KiteIndexedResourceHelper.getIndexedInfo(resourceDecl);
        assertNotNull(info);

        var result = info.validateNumericIndex(2);
        assertTrue(result.isValid());
        assertNull(result.errorMessage());
    }

    public void testValidateOutOfBoundsIndex() {
        configureByText("""
                schema vm { string name }
                @count(3)
                resource vm server { name = "srv" }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        var info = KiteIndexedResourceHelper.getIndexedInfo(resourceDecl);
        assertNotNull(info);

        var result = info.validateNumericIndex(5);
        assertFalse(result.isValid());
        assertTrue(result.errorMessage().contains("out of bounds"));
        assertTrue(result.errorMessage().contains("0-2"));
    }

    public void testValidateValidStringKey() {
        configureByText("""
                schema vm { string name }
                for env in ["dev", "prod"] {
                    resource vm server { name = "srv" }
                }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        var info = KiteIndexedResourceHelper.getIndexedInfo(resourceDecl);
        assertNotNull(info);

        var result = info.validateStringKey("dev");
        assertTrue(result.isValid());
    }

    public void testValidateInvalidStringKey() {
        configureByText("""
                schema vm { string name }
                for env in ["dev", "prod"] {
                    resource vm server { name = "srv" }
                }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        var info = KiteIndexedResourceHelper.getIndexedInfo(resourceDecl);
        assertNotNull(info);

        var result = info.validateStringKey("staging");
        assertFalse(result.isValid());
        assertTrue(result.errorMessage().contains("staging"));
        assertTrue(result.errorMessage().contains("dev"));
        assertTrue(result.errorMessage().contains("prod"));
    }

    public void testValidateWrongIndexTypeNumericOnString() {
        configureByText("""
                schema vm { string name }
                for env in ["dev", "prod"] {
                    resource vm server { name = "srv" }
                }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        var info = KiteIndexedResourceHelper.getIndexedInfo(resourceDecl);
        assertNotNull(info);

        var result = info.validateNumericIndex(0);
        assertFalse(result.isValid());
        assertTrue(result.errorMessage().contains("string keys"));
    }

    public void testValidateWrongIndexTypeStringOnNumeric() {
        configureByText("""
                schema vm { string name }
                @count(3)
                resource vm server { name = "srv" }
                """);

        var file = myFixture.getFile();
        var resourceDecl = findFirstElementOfType(file, KiteElementTypes.RESOURCE_DECLARATION);
        var info = KiteIndexedResourceHelper.getIndexedInfo(resourceDecl);
        assertNotNull(info);

        var result = info.validateStringKey("test");
        assertFalse(result.isValid());
        assertTrue(result.errorMessage().contains("numeric indices"));
    }

    // ========== Component tests ==========

    public void testGetIndexedInfoForComponent() {
        configureByText("""
                component WebServer {
                    input string port
                }
                @count(3)
                component WebServer servers {
                    port = "8080"
                }
                """);

        var file = myFixture.getFile();
        // Find the second component declaration (the instance)
        var components = findAllElementsOfType(file, KiteElementTypes.COMPONENT_DECLARATION);
        assertEquals(2, components.size());

        var componentInstance = components.get(1);
        var info = KiteIndexedResourceHelper.getIndexedInfo(componentInstance);
        assertNotNull(info);
        assertEquals(KiteIndexedResourceHelper.IndexType.NUMERIC, info.indexType());
        assertEquals(Integer.valueOf(3), info.countValue());
    }

    // ========== Helper methods ==========

    private PsiElement findFirstElementOfType(PsiElement root, IElementType type) {
        if (root.getNode() != null && root.getNode().getElementType() == type) {
            return root;
        }
        for (var child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            var found = findFirstElementOfType(child, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private java.util.List<PsiElement> findAllElementsOfType(PsiElement root, IElementType type) {
        var results = new java.util.ArrayList<PsiElement>();
        findAllElementsOfTypeRecursive(root, type, results);
        return results;
    }

    private void findAllElementsOfTypeRecursive(PsiElement root, IElementType type, java.util.List<PsiElement> results) {
        if (root.getNode() != null && root.getNode().getElementType() == type) {
            results.add(root);
        }
        for (var child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            findAllElementsOfTypeRecursive(child, type, results);
        }
    }

    private PsiElement findTokenOfType(PsiElement root, IElementType tokenType) {
        if (root.getNode() != null && root.getNode().getElementType() == tokenType) {
            return root;
        }
        for (var child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            var found = findTokenOfType(child, tokenType);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private java.util.List<PsiElement> findAllTokensOfType(PsiElement root, IElementType tokenType) {
        var results = new java.util.ArrayList<PsiElement>();
        findAllTokensOfTypeRecursive(root, tokenType, results);
        return results;
    }

    private void findAllTokensOfTypeRecursive(PsiElement root, IElementType tokenType, java.util.List<PsiElement> results) {
        if (root.getNode() != null && root.getNode().getElementType() == tokenType) {
            results.add(root);
        }
        for (var child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            findAllTokensOfTypeRecursive(child, tokenType, results);
        }
    }
}
