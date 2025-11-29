package cloud.kitelang.intellij.actions;

import cloud.kitelang.intellij.KiteIcons;
import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

/**
 * Action to create a new Kite file from a template.
 * Accessible from File > New > Kite File or right-click > New > Kite File.
 */
public class CreateKiteFileAction extends CreateFileFromTemplateAction implements DumbAware {

    private static final String NEW_KITE_FILE = "Kite File";

    public CreateKiteFileAction() {
        super(NEW_KITE_FILE, "Create a new Kite infrastructure file", KiteIcons.FILE);
    }

    @Override
    protected void buildDialog(@NotNull Project project,
                               @NotNull PsiDirectory directory,
                               @NotNull CreateFileFromTemplateDialog.Builder builder) {
        builder.setTitle("New Kite File")
                .addKind("Empty file", KiteIcons.FILE, "Kite File");
    }

    @Override
    protected String getActionName(PsiDirectory directory, @NotNull String newName, String templateName) {
        return "Create Kite File: " + newName;
    }
}
