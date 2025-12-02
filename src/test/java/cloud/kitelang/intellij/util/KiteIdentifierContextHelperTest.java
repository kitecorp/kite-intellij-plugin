package cloud.kitelang.intellij.util;

import cloud.kitelang.intellij.KiteTestBase;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;

/**
 * Tests for KiteIdentifierContextHelper utility class.
 */
public class KiteIdentifierContextHelperTest extends KiteTestBase {

    // ========== isDeclarationName tests ==========

    public void testIsDeclarationNameWithAssign() {
        configureByText("""
                var name = "hello"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement identifier = findFirstIdentifierWithText(file, "name");
        assertNotNull("Should find identifier 'name'", identifier);

        assertTrue("'name' followed by = should be a declaration",
                KiteIdentifierContextHelper.isDeclarationName(identifier));
    }

    public void testIsDeclarationNameWithLbrace() {
        configureByText("""
                schema Config {
                    string host
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement identifier = findFirstIdentifierWithText(file, "Config");
        assertNotNull("Should find identifier 'Config'", identifier);

        assertTrue("'Config' followed by { should be a declaration",
                KiteIdentifierContextHelper.isDeclarationName(identifier));
    }

    public void testIsDeclarationNameAfterKeyword() {
        configureByText("""
                input string port = "8080"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement identifier = findFirstIdentifierWithText(file, "port");
        assertNotNull("Should find identifier 'port'", identifier);

        assertTrue("'port' after keyword should be a declaration",
                KiteIdentifierContextHelper.isDeclarationName(identifier));
    }

    public void testIsDeclarationNameReference() {
        configureByText("""
                var name = "hello"
                var greeting = name
                """);

        PsiFile file = myFixture.getFile();
        // Find the second occurrence of 'name' (the reference)
        PsiElement nameDecl = findFirstIdentifierWithText(file, "name");
        PsiElement nameRef = findNextIdentifierWithText(nameDecl, "name");

        assertNotNull("Should find reference to 'name'", nameRef);
        assertFalse("Reference 'name' should not be a declaration",
                KiteIdentifierContextHelper.isDeclarationName(nameRef));
    }

    // ========== isPropertyAccess tests ==========

    public void testIsPropertyAccessAfterDot() {
        configureByText("""
                var result = config.host
                """);

        PsiFile file = myFixture.getFile();
        PsiElement identifier = findFirstIdentifierWithText(file, "host");
        assertNotNull("Should find identifier 'host'", identifier);

        assertTrue("'host' after . should be a property access",
                KiteIdentifierContextHelper.isPropertyAccess(identifier));
    }

    public void testIsPropertyAccessNotAfterDot() {
        configureByText("""
                var host = "localhost"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement identifier = findFirstIdentifierWithText(file, "host");
        assertNotNull("Should find identifier 'host'", identifier);

        assertFalse("'host' not after . should not be a property access",
                KiteIdentifierContextHelper.isPropertyAccess(identifier));
    }

    // ========== isDecoratorName tests ==========

    public void testIsDecoratorNameAfterAt() {
        configureByText("""
                @cloud
                resource VM server {
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement identifier = findFirstIdentifierWithText(file, "cloud");
        assertNotNull("Should find identifier 'cloud'", identifier);

        assertTrue("'cloud' after @ should be a decorator name",
                KiteIdentifierContextHelper.isDecoratorName(identifier));
    }

    public void testIsDecoratorNameNotAfterAt() {
        configureByText("""
                var cloud = "aws"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement identifier = findFirstIdentifierWithText(file, "cloud");
        assertNotNull("Should find identifier 'cloud'", identifier);

        assertFalse("'cloud' not after @ should not be a decorator name",
                KiteIdentifierContextHelper.isDecoratorName(identifier));
    }

    // ========== isInsideImportStatement tests ==========

    public void testIsInsideImportStatementNamed() {
        configureByText("""
                import Config from "common.kite"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement identifier = findFirstIdentifierWithText(file, "Config");
        assertNotNull("Should find identifier 'Config'", identifier);

        assertTrue("'Config' in import statement should be inside import",
                KiteIdentifierContextHelper.isInsideImportStatement(identifier));
    }

    public void testIsInsideImportStatementNotImport() {
        configureByText("""
                var Config = "value"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement identifier = findFirstIdentifierWithText(file, "Config");
        assertNotNull("Should find identifier 'Config'", identifier);

        assertFalse("'Config' in var statement should not be inside import",
                KiteIdentifierContextHelper.isInsideImportStatement(identifier));
    }

    // ========== isTypeAnnotation tests ==========

    public void testIsTypeAnnotationAfterVarKeyword() {
        configureByText("""
                var string name = "hello"
                """);

        PsiFile file = myFixture.getFile();
        // 'string' is the type annotation, 'name' is the variable name
        PsiElement typeAnnotation = findFirstIdentifierWithText(file, "string");

        if (typeAnnotation != null) {
            // If string is parsed as identifier, check if it's a type annotation
            assertTrue("'string' after var should be a type annotation",
                    KiteIdentifierContextHelper.isTypeAnnotation(typeAnnotation));
        }
    }

    // ========== isPropertyDefinition tests ==========

    public void testIsPropertyDefinitionInSchema() {
        configureByText("""
                schema Config {
                    string host
                    number port
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement hostIdentifier = findFirstIdentifierWithText(file, "host");
        assertNotNull("Should find identifier 'host'", hostIdentifier);

        assertTrue("'host' in schema body should be a property definition",
                KiteIdentifierContextHelper.isPropertyDefinition(hostIdentifier));
    }

    public void testIsPropertyDefinitionWithArrayType() {
        configureByText("""
                schema Config {
                    string[] tags
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement tagsIdentifier = findFirstIdentifierWithText(file, "tags");
        assertNotNull("Should find identifier 'tags'", tagsIdentifier);

        assertTrue("'tags' after string[] should be a property definition",
                KiteIdentifierContextHelper.isPropertyDefinition(tagsIdentifier));
    }

    public void testIsPropertyDefinitionNotInSchema() {
        configureByText("""
                var host = "localhost"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement identifier = findFirstIdentifierWithText(file, "host");
        assertNotNull("Should find identifier 'host'", identifier);

        assertFalse("'host' outside schema should not be a property definition",
                KiteIdentifierContextHelper.isPropertyDefinition(identifier));
    }

    // ========== isInsideDeclarationBody tests ==========

    public void testIsInsideDeclarationBodySchema() {
        configureByText("""
                schema Config {
                    string host
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement hostIdentifier = findFirstIdentifierWithText(file, "host");
        assertNotNull("Should find identifier 'host'", hostIdentifier);

        assertTrue("'host' inside schema body should return true",
                KiteIdentifierContextHelper.isInsideDeclarationBody(hostIdentifier));
    }

    public void testIsInsideDeclarationBodyResource() {
        configureByText("""
                resource VM server {
                    name = "web"
                }
                """);

        PsiFile file = myFixture.getFile();
        PsiElement nameIdentifier = findFirstIdentifierWithText(file, "name");
        assertNotNull("Should find identifier 'name'", nameIdentifier);

        assertTrue("'name' inside resource body should return true",
                KiteIdentifierContextHelper.isInsideDeclarationBody(nameIdentifier));
    }

    public void testIsInsideDeclarationBodyOutside() {
        configureByText("""
                var name = "hello"
                """);

        PsiFile file = myFixture.getFile();
        PsiElement identifier = findFirstIdentifierWithText(file, "name");
        assertNotNull("Should find identifier 'name'", identifier);

        assertFalse("'name' at file level should return false",
                KiteIdentifierContextHelper.isInsideDeclarationBody(identifier));
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

    private PsiElement findNextIdentifierWithText(PsiElement after, String text) {
        if (after == null) return null;

        // Continue searching from after's next sibling
        PsiElement current = after.getNextSibling();
        while (current != null) {
            PsiElement found = findFirstIdentifierWithText(current, text);
            if (found != null) {
                return found;
            }
            current = current.getNextSibling();
        }

        // If not found in siblings, try parent's next siblings
        PsiElement parent = after.getParent();
        if (parent != null) {
            return findNextIdentifierWithText(parent, text);
        }

        return null;
    }
}
