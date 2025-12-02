package cloud.kitelang.intellij.inspection;

import cloud.kitelang.intellij.psi.KiteFile;
import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract base class for Kite language inspections.
 * Uses the standard buildVisitor() pattern recommended by IntelliJ Platform SDK.
 * See: https://plugins.jetbrains.com/docs/intellij/code-inspections.html
 *
 * Note: IntelliJ's inspection engine handles PSI traversal automatically.
 * The visitor returned from buildVisitor() must NOT be recursive.
 */
public abstract class KiteInspectionBase extends LocalInspectionTool {

    /**
     * Build a visitor for the inspection.
     * Returns a non-recursive visitor - IntelliJ handles PSI traversal.
     */
    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        PsiFile file = holder.getFile();
        if (!(file instanceof KiteFile)) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        // Return non-recursive visitor - IntelliJ calls visitElement for each PSI element
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                checkElement(element, holder);
            }
        };
    }

    /**
     * Override this method to check individual PSI elements.
     * Called for every element in the file during inspection.
     *
     * @param element The PSI element being inspected
     * @param holder  The problems holder to register problems with
     */
    protected abstract void checkElement(@NotNull PsiElement element,
                                         @NotNull ProblemsHolder holder);

    /**
     * Register a WARNING problem.
     */
    protected void registerWarning(@NotNull ProblemsHolder holder,
                                   @NotNull PsiElement element,
                                   @NotNull String message,
                                   LocalQuickFix... fixes) {
        holder.registerProblem(element, message, ProblemHighlightType.WARNING, fixes);
    }

    /**
     * Register a WEAK_WARNING problem.
     */
    protected void registerWeakWarning(@NotNull ProblemsHolder holder,
                                       @NotNull PsiElement element,
                                       @NotNull String message,
                                       LocalQuickFix... fixes) {
        holder.registerProblem(element, message, ProblemHighlightType.WEAK_WARNING, fixes);
    }

    /**
     * Register an ERROR problem.
     */
    protected void registerError(@NotNull ProblemsHolder holder,
                                 @NotNull PsiElement element,
                                 @NotNull String message,
                                 LocalQuickFix... fixes) {
        holder.registerProblem(element, message, ProblemHighlightType.ERROR, fixes);
    }

    /**
     * Register an INFO problem (like unused symbol).
     */
    protected void registerInfo(@NotNull ProblemsHolder holder,
                                @NotNull PsiElement element,
                                @NotNull String message,
                                LocalQuickFix... fixes) {
        holder.registerProblem(element, message, ProblemHighlightType.LIKE_UNUSED_SYMBOL, fixes);
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
