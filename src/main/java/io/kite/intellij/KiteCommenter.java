package io.kite.intellij;

import com.intellij.lang.Commenter;
import org.jetbrains.annotations.Nullable;

/* *
 * Provides comment/uncomment functionality for Kite language.
 * Supports line comments (//) and block comments (/* *\/).
 * */
public class KiteCommenter implements Commenter {
    @Nullable
    @Override
    public String getLineCommentPrefix() {
        return "//";
    }

    @Nullable
    @Override
    public String getBlockCommentPrefix() {
        return "/*";
    }

    @Nullable
    @Override
    public String getBlockCommentSuffix() {
        return "*/";
    }

    @Nullable
    @Override
    public String getCommentedBlockCommentPrefix() {
        return null;
    }

    @Nullable
    @Override
    public String getCommentedBlockCommentSuffix() {
        return null;
    }
}