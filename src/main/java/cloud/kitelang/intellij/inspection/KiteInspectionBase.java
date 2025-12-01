package cloud.kitelang.intellij.inspection;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteFile;
import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for Kite language inspections.
 * Provides common infrastructure for all Kite inspections including:
 * - File type checking
 * - Problem descriptor creation helpers
 * - Standard inspection interface
 */
public abstract class KiteInspectionBase extends LocalInspectionTool {

    /**
     * Main entry point for file-level inspection.
     * Delegates to checkKiteFile for Kite files.
     */
    @Override
    public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file,
                                                     @NotNull InspectionManager manager,
                                                     boolean isOnTheFly) {
        if (!(file instanceof KiteFile)) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }

        List<ProblemDescriptor> problems = new ArrayList<>();
        checkKiteFile((KiteFile) file, manager, isOnTheFly, problems);
        return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
    }

    /**
     * Override this method to implement the inspection logic.
     *
     * @param file       The Kite file being inspected
     * @param manager    The inspection manager for creating problem descriptors
     * @param isOnTheFly True if running in the editor (real-time), false for batch inspection
     * @param problems   List to add found problems to
     */
    protected abstract void checkKiteFile(@NotNull KiteFile file,
                                          @NotNull InspectionManager manager,
                                          boolean isOnTheFly,
                                          @NotNull List<ProblemDescriptor> problems);

    /**
     * Helper to create a problem descriptor with WARNING severity.
     */
    protected ProblemDescriptor createWarning(@NotNull InspectionManager manager,
                                               @NotNull PsiElement element,
                                               @NotNull String message,
                                               boolean isOnTheFly,
                                               LocalQuickFix... fixes) {
        return manager.createProblemDescriptor(
                element,
                message,
                isOnTheFly,
                fixes,
                ProblemHighlightType.WARNING
        );
    }

    /**
     * Helper to create a problem descriptor with WEAK_WARNING severity.
     */
    protected ProblemDescriptor createWeakWarning(@NotNull InspectionManager manager,
                                                   @NotNull PsiElement element,
                                                   @NotNull String message,
                                                   boolean isOnTheFly,
                                                   LocalQuickFix... fixes) {
        return manager.createProblemDescriptor(
                element,
                message,
                isOnTheFly,
                fixes,
                ProblemHighlightType.WEAK_WARNING
        );
    }

    /**
     * Helper to create a problem descriptor with ERROR severity.
     */
    protected ProblemDescriptor createError(@NotNull InspectionManager manager,
                                             @NotNull PsiElement element,
                                             @NotNull String message,
                                             boolean isOnTheFly,
                                             LocalQuickFix... fixes) {
        return manager.createProblemDescriptor(
                element,
                message,
                isOnTheFly,
                fixes,
                ProblemHighlightType.ERROR
        );
    }

    /**
     * Helper to create a problem descriptor with INFO severity (like unused).
     */
    protected ProblemDescriptor createInfo(@NotNull InspectionManager manager,
                                            @NotNull PsiElement element,
                                            @NotNull String message,
                                            boolean isOnTheFly,
                                            LocalQuickFix... fixes) {
        return manager.createProblemDescriptor(
                element,
                message,
                isOnTheFly,
                fixes,
                ProblemHighlightType.LIKE_UNUSED_SYMBOL
        );
    }

    /**
     * Default group display name for Kite inspections.
     */
    @Override
    public @NotNull String getGroupDisplayName() {
        return "Kite";
    }

    /**
     * Indicates this inspection is enabled by default.
     */
    @Override
    public boolean isEnabledByDefault() {
        return true;
    }
}
