package cloud.kitelang.intellij.psi;

import cloud.kitelang.intellij.KiteLanguage;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * PSI element types for Kite language structures.
 */
public class KiteElementTypes {

    public static class KiteElementType extends IElementType {
        public KiteElementType(@NotNull String debugName) {
            super(debugName, KiteLanguage.INSTANCE);
        }
    }

    public static final IElementType FILE = new KiteElementType("FILE");
    public static final IElementType RESOURCE_DECLARATION = new KiteElementType("RESOURCE_DECLARATION");
    public static final IElementType COMPONENT_DECLARATION = new KiteElementType("COMPONENT_DECLARATION");
    public static final IElementType SCHEMA_DECLARATION = new KiteElementType("SCHEMA_DECLARATION");
    public static final IElementType FUNCTION_DECLARATION = new KiteElementType("FUNCTION_DECLARATION");
    public static final IElementType TYPE_DECLARATION = new KiteElementType("TYPE_DECLARATION");
    public static final IElementType VARIABLE_DECLARATION = new KiteElementType("VARIABLE_DECLARATION");
    public static final IElementType INPUT_DECLARATION = new KiteElementType("INPUT_DECLARATION");
    public static final IElementType OUTPUT_DECLARATION = new KiteElementType("OUTPUT_DECLARATION");
    public static final IElementType IMPORT_STATEMENT = new KiteElementType("IMPORT_STATEMENT");
    public static final IElementType FOR_STATEMENT = new KiteElementType("FOR_STATEMENT");
    public static final IElementType WHILE_STATEMENT = new KiteElementType("WHILE_STATEMENT");
    public static final IElementType OBJECT_LITERAL = new KiteElementType("OBJECT_LITERAL");
    public static final IElementType ARRAY_LITERAL = new KiteElementType("ARRAY_LITERAL");
}
