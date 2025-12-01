package cloud.kitelang.intellij.refactoring;

/**
 * Tests for KiteNamesValidator - verifies identifier validation and keyword detection.
 */
public class KiteNamesValidatorTest extends junit.framework.TestCase {

    private final KiteNamesValidator validator = new KiteNamesValidator();

    // ========== Valid Identifier Tests ==========

    public void testSimpleIdentifier() {
        assertTrue("Simple identifier should be valid", validator.isIdentifier("foo", null));
    }

    public void testIdentifierWithNumbers() {
        assertTrue("Identifier with numbers should be valid", validator.isIdentifier("foo123", null));
    }

    public void testIdentifierWithUnderscore() {
        assertTrue("Identifier with underscore should be valid", validator.isIdentifier("foo_bar", null));
    }

    public void testIdentifierStartingWithUnderscore() {
        assertTrue("Identifier starting with underscore should be valid", validator.isIdentifier("_foo", null));
    }

    public void testSingleCharIdentifier() {
        assertTrue("Single char identifier should be valid", validator.isIdentifier("x", null));
    }

    public void testSingleUnderscore() {
        assertTrue("Single underscore should be valid", validator.isIdentifier("_", null));
    }

    public void testCamelCaseIdentifier() {
        assertTrue("CamelCase identifier should be valid", validator.isIdentifier("myVariable", null));
    }

    public void testPascalCaseIdentifier() {
        assertTrue("PascalCase identifier should be valid", validator.isIdentifier("MyClass", null));
    }

    public void testSnakeCaseIdentifier() {
        assertTrue("Snake_case identifier should be valid", validator.isIdentifier("my_variable", null));
    }

    public void testUppercaseIdentifier() {
        assertTrue("Uppercase identifier should be valid", validator.isIdentifier("CONSTANT", null));
    }

    public void testMixedCaseWithNumbers() {
        assertTrue("Mixed case with numbers should be valid", validator.isIdentifier("myVar123", null));
    }

    // ========== Invalid Identifier Tests ==========

    public void testEmptyString() {
        assertFalse("Empty string should not be valid", validator.isIdentifier("", null));
    }

    public void testStartingWithNumber() {
        assertFalse("Identifier starting with number should not be valid", validator.isIdentifier("123foo", null));
    }

    public void testContainsSpace() {
        assertFalse("Identifier with space should not be valid", validator.isIdentifier("foo bar", null));
    }

    public void testContainsDash() {
        assertFalse("Identifier with dash should not be valid", validator.isIdentifier("foo-bar", null));
    }

    public void testContainsDot() {
        assertFalse("Identifier with dot should not be valid", validator.isIdentifier("foo.bar", null));
    }

    public void testContainsSpecialChars() {
        assertFalse("Identifier with special chars should not be valid", validator.isIdentifier("foo@bar", null));
    }

    public void testContainsHash() {
        assertFalse("Identifier with hash should not be valid", validator.isIdentifier("foo#bar", null));
    }

    public void testContainsDollar() {
        assertFalse("Identifier with dollar should not be valid", validator.isIdentifier("foo$bar", null));
    }

    public void testStartingWithDash() {
        assertFalse("Identifier starting with dash should not be valid", validator.isIdentifier("-foo", null));
    }

    public void testOnlyNumbers() {
        assertFalse("Only numbers should not be valid", validator.isIdentifier("123", null));
    }

    // ========== Keyword Tests ==========

    public void testResourceKeyword() {
        assertTrue("resource should be a keyword", validator.isKeyword("resource", null));
    }

    public void testComponentKeyword() {
        assertTrue("component should be a keyword", validator.isKeyword("component", null));
    }

    public void testSchemaKeyword() {
        assertTrue("schema should be a keyword", validator.isKeyword("schema", null));
    }

    public void testInputKeyword() {
        assertTrue("input should be a keyword", validator.isKeyword("input", null));
    }

    public void testOutputKeyword() {
        assertTrue("output should be a keyword", validator.isKeyword("output", null));
    }

    public void testIfKeyword() {
        assertTrue("if should be a keyword", validator.isKeyword("if", null));
    }

    public void testElseKeyword() {
        assertTrue("else should be a keyword", validator.isKeyword("else", null));
    }

    public void testWhileKeyword() {
        assertTrue("while should be a keyword", validator.isKeyword("while", null));
    }

    public void testForKeyword() {
        assertTrue("for should be a keyword", validator.isKeyword("for", null));
    }

    public void testInKeyword() {
        assertTrue("in should be a keyword", validator.isKeyword("in", null));
    }

    public void testReturnKeyword() {
        assertTrue("return should be a keyword", validator.isKeyword("return", null));
    }

    public void testImportKeyword() {
        assertTrue("import should be a keyword", validator.isKeyword("import", null));
    }

    public void testFromKeyword() {
        assertTrue("from should be a keyword", validator.isKeyword("from", null));
    }

    public void testFunKeyword() {
        assertTrue("fun should be a keyword", validator.isKeyword("fun", null));
    }

    public void testVarKeyword() {
        assertTrue("var should be a keyword", validator.isKeyword("var", null));
    }

    public void testTypeKeyword() {
        assertTrue("type should be a keyword", validator.isKeyword("type", null));
    }

    public void testInitKeyword() {
        assertTrue("init should be a keyword", validator.isKeyword("init", null));
    }

    public void testThisKeyword() {
        assertTrue("this should be a keyword", validator.isKeyword("this", null));
    }

    public void testObjectKeyword() {
        assertTrue("object should be a keyword", validator.isKeyword("object", null));
    }

    public void testAnyKeyword() {
        assertTrue("any should be a keyword", validator.isKeyword("any", null));
    }

    public void testStringKeyword() {
        assertTrue("string should be a keyword", validator.isKeyword("string", null));
    }

    public void testNumberKeyword() {
        assertTrue("number should be a keyword", validator.isKeyword("number", null));
    }

    public void testBooleanKeyword() {
        assertTrue("boolean should be a keyword", validator.isKeyword("boolean", null));
    }

    public void testTrueKeyword() {
        assertTrue("true should be a keyword", validator.isKeyword("true", null));
    }

    public void testFalseKeyword() {
        assertTrue("false should be a keyword", validator.isKeyword("false", null));
    }

    public void testNullKeyword() {
        assertTrue("null should be a keyword", validator.isKeyword("null", null));
    }

    // ========== Non-Keyword Tests ==========

    public void testFooNotKeyword() {
        assertFalse("foo should not be a keyword", validator.isKeyword("foo", null));
    }

    public void testMyVarNotKeyword() {
        assertFalse("myVar should not be a keyword", validator.isKeyword("myVar", null));
    }

    public void testConfigNotKeyword() {
        assertFalse("Config should not be a keyword", validator.isKeyword("Config", null));
    }

    // ========== Keywords Cannot Be Identifiers ==========

    public void testKeywordCannotBeIdentifier() {
        assertFalse("var keyword should not be valid identifier", validator.isIdentifier("var", null));
        assertFalse("fun keyword should not be valid identifier", validator.isIdentifier("fun", null));
        assertFalse("schema keyword should not be valid identifier", validator.isIdentifier("schema", null));
        assertFalse("resource keyword should not be valid identifier", validator.isIdentifier("resource", null));
        assertFalse("component keyword should not be valid identifier", validator.isIdentifier("component", null));
    }

    public void testTrueCannotBeIdentifier() {
        assertFalse("true should not be valid identifier", validator.isIdentifier("true", null));
    }

    public void testFalseCannotBeIdentifier() {
        assertFalse("false should not be valid identifier", validator.isIdentifier("false", null));
    }

    public void testNullCannotBeIdentifier() {
        assertFalse("null should not be valid identifier", validator.isIdentifier("null", null));
    }

    // ========== Case Sensitivity Tests ==========

    public void testUppercaseKeywordNotDetected() {
        // Keywords are case-sensitive in Kite
        assertFalse("VAR (uppercase) should not be a keyword", validator.isKeyword("VAR", null));
        assertFalse("FUN (uppercase) should not be a keyword", validator.isKeyword("FUN", null));
    }

    public void testMixedCaseKeywordNotDetected() {
        assertFalse("Var (mixed case) should not be a keyword", validator.isKeyword("Var", null));
        assertFalse("Schema (mixed case) should not be a keyword", validator.isKeyword("Schema", null));
    }

    public void testUppercaseCanBeIdentifier() {
        assertTrue("VAR (uppercase) should be valid identifier", validator.isIdentifier("VAR", null));
        assertTrue("FUN (uppercase) should be valid identifier", validator.isIdentifier("FUN", null));
    }
}
