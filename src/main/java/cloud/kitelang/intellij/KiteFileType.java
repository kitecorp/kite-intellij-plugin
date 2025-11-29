package cloud.kitelang.intellij;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Kite file type definition for .kite files.
 */
public class KiteFileType extends LanguageFileType {
    public static final KiteFileType INSTANCE = new KiteFileType();

    private KiteFileType() {
        super(KiteLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "Kite";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Kite Infrastructure as Code file";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "kite";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return KiteIcons.FILE;
    }
}