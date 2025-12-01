package cloud.kitelang.intellij.util;

import cloud.kitelang.intellij.KiteTestBase;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;

/**
 * Tests for KiteTypeInferenceHelper utility class.
 */
public class KiteTypeInferenceHelperTest extends KiteTestBase {

    // ========== inferType tests ==========

    public void testInferTypeString() {
        configureByText("""
                var name = "hello"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement stringLiteral = findFirstElementOfType(file, KiteTokenTypes.DQUOTE);
        assertNotNull("Should find string literal", stringLiteral);

        String type = KiteTypeInferenceHelper.inferType(stringLiteral);
        assertEquals("string", type);
    }

    public void testInferTypeSingleQuoteString() {
        configureByText("""
                var name = 'hello'
                """);

        PsiFile file = myFixture.getFile();
        PsiElement stringLiteral = findFirstElementOfType(file, KiteTokenTypes.SINGLE_STRING);
        assertNotNull("Should find single-quoted string", stringLiteral);

        String type = KiteTypeInferenceHelper.inferType(stringLiteral);
        assertEquals("string", type);
    }

    public void testInferTypeNumber() {
        configureByText("""
                var count = 42
                """);

        PsiFile file = myFixture.getFile();
        PsiElement numberLiteral = findFirstElementOfType(file, KiteTokenTypes.NUMBER);
        assertNotNull("Should find number literal", numberLiteral);

        String type = KiteTypeInferenceHelper.inferType(numberLiteral);
        assertEquals("number", type);
    }

    public void testInferTypeBooleanTrue() {
        configureByText("""
                var enabled = true
                """);

        PsiFile file = myFixture.getFile();
        PsiElement boolLiteral = findFirstElementOfType(file, KiteTokenTypes.TRUE);
        assertNotNull("Should find true literal", boolLiteral);

        String type = KiteTypeInferenceHelper.inferType(boolLiteral);
        assertEquals("boolean", type);
    }

    public void testInferTypeBooleanFalse() {
        configureByText("""
                var disabled = false
                """);

        PsiFile file = myFixture.getFile();
        PsiElement boolLiteral = findFirstElementOfType(file, KiteTokenTypes.FALSE);
        assertNotNull("Should find false literal", boolLiteral);

        String type = KiteTypeInferenceHelper.inferType(boolLiteral);
        assertEquals("boolean", type);
    }

    public void testInferTypeNull() {
        configureByText("""
                var nothing = null
                """);

        PsiFile file = myFixture.getFile();
        PsiElement nullLiteral = findFirstElementOfType(file, KiteTokenTypes.NULL);
        assertNotNull("Should find null literal", nullLiteral);

        String type = KiteTypeInferenceHelper.inferType(nullLiteral);
        assertEquals("null", type);
    }

    public void testInferTypeNullElement() {
        String type = KiteTypeInferenceHelper.inferType(null);
        assertNull("Should return null for null element", type);
    }

    // ========== isTypeCompatible tests ==========

    public void testIsTypeCompatibleExactMatch() {
        assertTrue(KiteTypeInferenceHelper.isTypeCompatible("string", "string"));
        assertTrue(KiteTypeInferenceHelper.isTypeCompatible("number", "number"));
        assertTrue(KiteTypeInferenceHelper.isTypeCompatible("boolean", "boolean"));
    }

    public void testIsTypeCompatibleCaseInsensitive() {
        assertTrue(KiteTypeInferenceHelper.isTypeCompatible("String", "string"));
        assertTrue(KiteTypeInferenceHelper.isTypeCompatible("NUMBER", "number"));
    }

    public void testIsTypeCompatibleAnyAcceptsEverything() {
        assertTrue(KiteTypeInferenceHelper.isTypeCompatible("any", "string"));
        assertTrue(KiteTypeInferenceHelper.isTypeCompatible("any", "number"));
        assertTrue(KiteTypeInferenceHelper.isTypeCompatible("any", "boolean"));
        assertTrue(KiteTypeInferenceHelper.isTypeCompatible("any", "object"));
    }

    public void testIsTypeCompatibleArrayTypes() {
        assertTrue(KiteTypeInferenceHelper.isTypeCompatible("string[]", "array"));
        assertTrue(KiteTypeInferenceHelper.isTypeCompatible("number[]", "array"));
    }

    public void testIsTypeCompatibleNullIsLenient() {
        assertTrue(KiteTypeInferenceHelper.isTypeCompatible("string", "null"));
        assertTrue(KiteTypeInferenceHelper.isTypeCompatible("number", "null"));
    }

    public void testIsTypeCompatibleCustomTypesAcceptPrimitives() {
        // Custom types like type Environment = "dev" | "prod" should accept strings
        assertTrue(KiteTypeInferenceHelper.isTypeCompatible("Environment", "string"));
        assertTrue(KiteTypeInferenceHelper.isTypeCompatible("MyCustomType", "number"));
    }

    public void testIsTypeCompatibleIncompatible() {
        assertFalse(KiteTypeInferenceHelper.isTypeCompatible("string", "number"));
        assertFalse(KiteTypeInferenceHelper.isTypeCompatible("boolean", "string"));
    }

    // ========== isBuiltinType tests ==========

    public void testIsBuiltinTypeTrue() {
        assertTrue(KiteTypeInferenceHelper.isBuiltinType("string"));
        assertTrue(KiteTypeInferenceHelper.isBuiltinType("number"));
        assertTrue(KiteTypeInferenceHelper.isBuiltinType("boolean"));
        assertTrue(KiteTypeInferenceHelper.isBuiltinType("any"));
        assertTrue(KiteTypeInferenceHelper.isBuiltinType("object"));
        assertTrue(KiteTypeInferenceHelper.isBuiltinType("void"));
        assertTrue(KiteTypeInferenceHelper.isBuiltinType("null"));
        assertTrue(KiteTypeInferenceHelper.isBuiltinType("array"));
    }

    public void testIsBuiltinTypeFalse() {
        assertFalse(KiteTypeInferenceHelper.isBuiltinType("String"));  // Case sensitive
        assertFalse(KiteTypeInferenceHelper.isBuiltinType("Environment"));
        assertFalse(KiteTypeInferenceHelper.isBuiltinType("MyCustomType"));
        assertFalse(KiteTypeInferenceHelper.isBuiltinType("DatabaseConfig"));
    }

    // ========== BUILTIN_TYPES constant tests ==========

    public void testBuiltinTypesContainsExpectedValues() {
        assertTrue(KiteTypeInferenceHelper.BUILTIN_TYPES.contains("string"));
        assertTrue(KiteTypeInferenceHelper.BUILTIN_TYPES.contains("number"));
        assertTrue(KiteTypeInferenceHelper.BUILTIN_TYPES.contains("boolean"));
        assertTrue(KiteTypeInferenceHelper.BUILTIN_TYPES.contains("any"));
        assertTrue(KiteTypeInferenceHelper.BUILTIN_TYPES.contains("object"));
        assertTrue(KiteTypeInferenceHelper.BUILTIN_TYPES.contains("void"));
        // Also uppercase variants
        assertTrue(KiteTypeInferenceHelper.BUILTIN_TYPES.contains("String"));
        assertTrue(KiteTypeInferenceHelper.BUILTIN_TYPES.contains("Number"));
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
}
