package io.kite.intellij.parser;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * Base PSI element for Kite language constructs.
 */
public class KitePsiElement extends ASTWrapperPsiElement {
    public KitePsiElement(@NotNull ASTNode node) {
        super(node);
    }
}
