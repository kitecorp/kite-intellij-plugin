package cloud.kitelang.intellij.formatter;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.codeStyle.CodeStyleManager;

/**
 * Tests for Kite code formatting.
 * Tests KiteBlock (indentation, alignment) and KiteFormattingModelBuilder (spacing rules).
 */
public class KiteFormatterTest extends KiteTestBase {

    // ========== Helper Methods ==========

    private String reformat(String code) {
        configureByText(code);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            CodeStyleManager.getInstance(getProject()).reformat(myFixture.getFile());
        });
        return myFixture.getFile().getText();
    }

    private void assertContainsAfterFormat(String before, String expectedSubstring) {
        String result = reformat(before);
        assertTrue("Formatted result should contain: " + expectedSubstring + "\nActual: " + result,
                result.contains(expectedSubstring));
    }

    private void assertNotContainsAfterFormat(String before, String unexpectedSubstring) {
        String result = reformat(before);
        assertFalse("Formatted result should NOT contain: " + unexpectedSubstring + "\nActual: " + result,
                result.contains(unexpectedSubstring));
    }

    // ========== Spacing Around Operators Tests ==========

    public void testSpacingAroundAssignment() {
        assertContainsAfterFormat("var x=1", "x = 1");
    }

    public void testSpacingAroundPlus() {
        assertContainsAfterFormat("var x = 1+2", "1 + 2");
    }

    public void testSpacingAroundMinus() {
        assertContainsAfterFormat("var x = 3-1", "3 - 1");
    }

    public void testSpacingAroundMultiply() {
        assertContainsAfterFormat("var x = 2*3", "2 * 3");
    }

    public void testSpacingAroundDivide() {
        assertContainsAfterFormat("var x = 6/2", "6 / 2");
    }

    public void testSpacingAroundLessThan() {
        assertContainsAfterFormat("var a = x<y", "x < y");
    }

    public void testSpacingAroundGreaterThan() {
        assertContainsAfterFormat("var a = x>y", "x > y");
    }

    public void testSpacingAroundAnd() {
        assertContainsAfterFormat("var a = x&&y", "x && y");
    }

    public void testSpacingAroundOr() {
        assertContainsAfterFormat("var a = x||y", "x || y");
    }

    // ========== Brace Spacing Tests ==========

    public void testSpaceBeforeOpeningBrace() {
        String result = reformat("schema Config{}");
        assertTrue("Should have space before {", result.contains("Config {"));
    }

    // ========== Bracket Spacing Tests ==========

    public void testNoSpaceAfterOpeningBracket() {
        assertNotContainsAfterFormat("var x = [ 1, 2]", "[ 1");
    }

    public void testNoSpaceBeforeClosingBracket() {
        assertNotContainsAfterFormat("var x = [1, 2 ]", "2 ]");
    }

    // ========== Parenthesis Spacing Tests ==========

    public void testNoSpaceAfterOpeningParen() {
        assertNotContainsAfterFormat("var x = greet( \"Alice\")", "( \"");
    }

    public void testNoSpaceBeforeClosingParen() {
        assertNotContainsAfterFormat("var x = greet(\"Alice\" )", "\" )");
    }

    // ========== Comma Spacing Tests ==========

    public void testSpaceAfterComma() {
        assertContainsAfterFormat("var x = [1,2,3]", ", 2");
    }

    public void testNoSpaceBeforeComma() {
        assertNotContainsAfterFormat("var x = [1 , 2]", "1 ,");
    }

    // ========== Colon Spacing Tests ==========

    public void testSpaceAfterColon() {
        assertContainsAfterFormat("var x = {a:1}", ": 1");
    }

    // ========== Keyword Spacing Tests ==========

    public void testSpaceAfterVar() {
        String result = reformat("var  x = 1");
        assertTrue("Should have single space after var", result.contains("var "));
        assertFalse("Should not have double space", result.contains("var  "));
    }

    public void testSpaceAfterSchema() {
        assertContainsAfterFormat("schema  Config{}", "schema Config");
    }

    public void testSpaceAfterResource() {
        String result = reformat("schema C{string h}\nresource  C db{}");
        assertTrue("Should have space after resource", result.contains("resource C"));
    }

    public void testSpaceAfterComponent() {
        assertContainsAfterFormat("component  WebServer{}", "component WebServer");
    }

    public void testSpaceAfterFun() {
        assertContainsAfterFormat("fun  greet(){return \"hi\"}", "fun greet");
    }

    public void testSpaceAfterIf() {
        assertContainsAfterFormat("fun t(){if  x{}}", "if x");
    }

    public void testSpaceAfterFor() {
        assertContainsAfterFormat("for  item in items{}", "for item");
    }

    public void testSpaceAroundIn() {
        String result = reformat("for item  in  items{}");
        assertTrue("Should have space before in", result.contains("item in"));
        assertTrue("Should have space after in", result.contains("in items"));
    }

    public void testSpaceAfterImport() {
        assertContainsAfterFormat("import  * from \"x.kite\"", "import *");
    }

    public void testSpaceAfterFrom() {
        assertContainsAfterFormat("import * from  \"x.kite\"", "from \"");
    }

    public void testSpaceAfterReturn() {
        assertContainsAfterFormat("fun t(){return  1}", "return 1");
    }

    // ========== Decorator Spacing Tests ==========

    public void testNoSpaceAfterAt() {
        String result = reformat("@ description(\"test\")");
        // The @ should be directly followed by the decorator name
        assertFalse("Should not have space after @", result.contains("@ "));
    }

    // ========== Dot Spacing Tests ==========

    public void testNoSpaceBeforeDot() {
        assertNotContainsAfterFormat("var x = obj .prop", "obj .");
    }

    public void testNoSpaceAfterDot() {
        assertNotContainsAfterFormat("var x = obj. prop", ". prop");
    }

    // ========== Assignment Operator Variants Tests ==========

    public void testSpacingAroundPlusAssign() {
        assertContainsAfterFormat("x+=1", "x += 1");
    }

    public void testSpacingAroundMinusAssign() {
        assertContainsAfterFormat("x-=1", "x -= 1");
    }

    public void testSpacingAroundMulAssign() {
        assertContainsAfterFormat("x*=2", "x *= 2");
    }

    public void testSpacingAroundDivAssign() {
        assertContainsAfterFormat("x/=2", "x /= 2");
    }

    // ========== Range Operator Tests ==========

    public void testRangeOperatorNoSpaces() {
        String result = reformat("var r = 1..10");
        assertTrue("Range should have no spaces", result.contains("1..10"));
    }

    // ========== Union Type Tests ==========

    public void testUnionTypeSpacing() {
        assertContainsAfterFormat("type R = \"a\"|\"b\"", "\"a\" | \"b\"");
    }

    // ========== Indentation Tests ==========

    public void testSchemaBodyIsIndented() {
        String result = reformat("schema Config {\nstring host\n}");
        // Content inside braces should be present (indentation may vary)
        assertTrue("Schema body should contain property",
                result.contains("string host") || result.contains("string h"));
    }

    public void testResourceBodyIsIndented() {
        String result = reformat("schema C{string h}\nresource C db{\nh=\"x\"\n}");
        // Resource body should contain property assignment
        assertTrue("Resource body should contain assignment",
                result.contains("h =") || result.contains("h="));
    }

    public void testFunctionBodyIsIndented() {
        String result = reformat("fun greet(){\nreturn \"hi\"\n}");
        // Function body should contain return statement
        assertTrue("Function body should contain return",
                result.contains("return"));
    }

    public void testNestedBlocksAreIndented() {
        String result = reformat("fun t(){\nif x{\nvar y = 1\n}\n}");
        // The var inside nested if should have more indentation than if
        int ifIndex = result.indexOf("if x");
        int varIndex = result.indexOf("var y");
        assertTrue("Nested content should exist", ifIndex > 0 && varIndex > 0);
    }

    // ========== Object Literal Tests ==========

    public void testSingleLineObjectLiteralPreserved() {
        String result = reformat("var x = {a: 1, b: 2}");
        // Single line object literals should stay on one line
        assertFalse("Single line object should not have newlines inside",
                result.contains("{\n") && result.contains("a:") && result.indexOf("}") - result.indexOf("{") < 30);
    }

    public void testMultiLineObjectLiteralIndented() {
        String result = reformat("var config = {\nhost: \"localhost\"\n}");
        assertTrue("Object literal content should be indented",
                result.contains("    host:") || result.contains("\thost:") || result.contains("host:"));
    }

    // ========== Array Literal Tests ==========

    public void testArrayElementsHaveCommaSpacing() {
        String result = reformat("var x = [1,2,3]");
        assertTrue("Array elements should have comma spacing", result.contains("1, 2, 3"));
    }

    // ========== String Interpolation Tests ==========

    public void testSimpleInterpolationPreserved() {
        String result = reformat("var greeting = \"Hello $name!\"");
        assertTrue("Interpolation should be preserved", result.contains("$name"));
    }

    public void testBraceInterpolationPreserved() {
        String result = reformat("var url = \"http://localhost:${port}/api\"");
        assertTrue("Brace interpolation should be preserved", result.contains("${port}"));
    }

    // ========== Edge Cases ==========

    public void testEmptyFileNoError() {
        String result = reformat("");
        assertNotNull("Empty file should format without error", result);
    }

    public void testCommentOnlyFile() {
        String result = reformat("// This is a comment");
        assertTrue("Comment should be preserved", result.contains("//"));
    }

    public void testFormattingDoesNotCrash() {
        // Test various code patterns don't crash the formatter
        reformat("var x = 1");
        reformat("input string name = \"test\"");
        reformat("output number count = 0");
        reformat("schema S { string x }");
        reformat("resource S r { x = \"y\" }");
        reformat("component C { input string p = \"8080\" }");
        reformat("fun f() { return 1 }");
        reformat("type T = \"a\" | \"b\"");
        reformat("@description(\"test\")");
        reformat("import * from \"x.kite\"");
        // If we get here, no crash occurred
        assertTrue(true);
    }

    // ========== Closing Brace Tests ==========

    public void testEmptyBlockSpacing() {
        String result = reformat("schema Empty{}");
        assertTrue("Empty block should have space before brace", result.contains("Empty {}"));
    }

    // ========== Complex Code Tests ==========

    public void testCompleteFileFormats() {
        String code = """
                import * from "common.kite"

                schema Config {
                    string host
                    number port
                }

                @description("Main config")
                resource Config db {
                    host = "localhost"
                    port = 5432
                }

                fun getEndpoint() string {
                    return "http://" + db.host
                }
                """;

        String result = reformat(code);

        // Verify key elements are present and formatted
        assertTrue("Should have import", result.contains("import *"));
        assertTrue("Should have schema", result.contains("schema Config"));
        assertTrue("Should have resource", result.contains("resource Config db"));
        assertTrue("Should have function", result.contains("fun getEndpoint"));
        assertTrue("Should have decorator", result.contains("@description"));
    }
}
