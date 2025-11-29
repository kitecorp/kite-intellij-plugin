package cloud.kitelang.intellij.psi;

import cloud.kitelang.intellij.KiteFileType;
import cloud.kitelang.intellij.KiteLanguage;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * PSI file representation for Kite files.
 */
public class KiteFile extends PsiFileBase {
    public KiteFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, KiteLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return KiteFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "Kite File";
    }
}
