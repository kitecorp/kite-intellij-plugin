package cloud.kitelang.intellij.inspection;

import cloud.kitelang.intellij.psi.KiteFile;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract base class for Kite language inspections.
 * Uses the standard buildVisitor() pattern recommended by IntelliJ Platform SDK.
 * See: https://plugins.jetbrains.com/docs/intellij/code-inspections.html
 * <p>
 * Supports two modes:
 * 1. Element-level: Override checkElement() to check individual elements
 * 2. File-level: Override isFileLevelInspection() to return true and checkFile()
 * <p>
 * Note: IntelliJ's inspection engine handles PSI traversal automatically.
 * The visitor returned from buildVisitor() must NOT be recursive.
 */
public abstract class KiteInspectionBase extends LocalInspectionTool {

    /**
     * Override to return true for file-level inspections.
     * File-level inspections analyze the entire file once rather than per-element.
     */
    protected boolean isFileLevelInspection() {
        return false;
    }

    /**
     * Override for file-level inspections. Called once per file.
     * Only called if isFileLevelInspection() returns true.
     */
    protected void checkFile(@NotNull KiteFile file, @NotNull ProblemsHolder holder) {
        // Default: do nothing. Override in subclasses for file-level analysis.
    }

    /**
     * Build a visitor for the inspection.
     * Returns a non-recursive visitor - IntelliJ handles PSI traversal.
     */
    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        PsiFile file = holder.getFile();
        if (!(file instanceof KiteFile kiteFile)) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        // For file-level inspections, run analysis on first element visit
        if (isFileLevelInspection()) {
            return new PsiElementVisitor() {
                private boolean checkedFile = false;

                @Override
                public void visitElement(@NotNull PsiElement element) {
                    if (!checkedFile) {
                        checkedFile = true;
                        checkFile(kiteFile, holder);
                    }
                }
            };
        }

        // For element-level inspections, return visitor that checks each element
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
