package cloud.kitelang.intellij.navigation;

import cloud.kitelang.intellij.KiteFileType;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.structure.KiteStructureViewIcons;
import cloud.kitelang.intellij.util.KiteDeclarationHelper;
import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Go to Symbol contributor for Kite.
 * Finds all declarations: schemas, components, functions, variables, resources, types.
 * Accessible via Cmd+Alt+O (Mac) or Ctrl+Alt+Shift+N (Windows/Linux).
 */
public class KiteGotoSymbolContributor implements ChooseByNameContributor {

    @Override
    @NotNull
    public String[] getNames(@NotNull Project project, boolean includeNonProjectItems) {
        List<String> names = new ArrayList<>();

        var scope = includeNonProjectItems
                ? GlobalSearchScope.allScope(project)
                : GlobalSearchScope.projectScope(project);

        var psiManager = PsiManager.getInstance(project);
        var virtualFiles = FileTypeIndex.getFiles(KiteFileType.INSTANCE, scope);

        for (var virtualFile : virtualFiles) {
            var psiFile = psiManager.findFile(virtualFile);
            if (psiFile == null) continue;

            collectSymbolNames(psiFile, names);
        }

        return names.toArray(new String[0]);
    }

    @Override
    @NotNull
    public NavigationItem[] getItemsByName(@NotNull String name,
                                            @NotNull String pattern,
                                            @NotNull Project project,
                                            boolean includeNonProjectItems) {
        List<NavigationItem> items = new ArrayList<>();

        var scope = includeNonProjectItems
                ? GlobalSearchScope.allScope(project)
                : GlobalSearchScope.projectScope(project);

        var psiManager = PsiManager.getInstance(project);
        var virtualFiles = FileTypeIndex.getFiles(KiteFileType.INSTANCE, scope);

        for (var virtualFile : virtualFiles) {
            var psiFile = psiManager.findFile(virtualFile);
            if (psiFile == null) continue;

            collectSymbolItems(psiFile, name, items);
        }

        return items.toArray(new NavigationItem[0]);
    }

    /**
     * Collect names of all symbol declarations.
     */
    private void collectSymbolNames(PsiElement element, List<String> names) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        if (isSymbolDeclaration(type)) {
            var declName = KitePsiUtil.findDeclarationName(element, type);
            if (declName != null && !declName.isEmpty()) {
                names.add(declName);
            }
        }

        // Recurse into children (but not into block bodies for nested declarations)
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            collectSymbolNames(child, names);
        }
    }

    /**
     * Collect navigation items matching the given name.
     */
    private void collectSymbolItems(PsiElement element, String targetName, List<NavigationItem> items) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        if (isSymbolDeclaration(type)) {
            var declName = KitePsiUtil.findDeclarationName(element, type);
            if (targetName.equals(declName)) {
                var nameElement = KiteDeclarationHelper.findNameElementInDeclaration(element, type);
                var actualElement = nameElement != null ? nameElement : element;

                String typeName = getTypeName(type);
                Icon icon = getIcon(type);

                items.add(new KiteNavigationItem(actualElement, declName, typeName, icon));
            }
        }

        // Recurse into children
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            collectSymbolItems(child, targetName, items);
        }
    }

    /**
     * Check if the element type represents a symbol declaration.
     */
    private boolean isSymbolDeclaration(IElementType type) {
        return type == KiteElementTypes.SCHEMA_DECLARATION ||
               type == KiteElementTypes.COMPONENT_DECLARATION ||
               type == KiteElementTypes.FUNCTION_DECLARATION ||
               type == KiteElementTypes.VARIABLE_DECLARATION ||
               type == KiteElementTypes.RESOURCE_DECLARATION ||
               type == KiteElementTypes.TYPE_DECLARATION;
    }

    /**
     * Get human-readable type name for the declaration.
     */
    private String getTypeName(IElementType type) {
        if (type == KiteElementTypes.SCHEMA_DECLARATION) return "schema";
        if (type == KiteElementTypes.COMPONENT_DECLARATION) return "component";
        if (type == KiteElementTypes.FUNCTION_DECLARATION) return "function";
        if (type == KiteElementTypes.VARIABLE_DECLARATION) return "variable";
        if (type == KiteElementTypes.RESOURCE_DECLARATION) return "resource";
        if (type == KiteElementTypes.TYPE_DECLARATION) return "type";
        return "symbol";
    }

    /**
     * Get icon for the declaration type.
     */
    private Icon getIcon(IElementType type) {
        if (type == KiteElementTypes.SCHEMA_DECLARATION) return KiteStructureViewIcons.SCHEMA;
        if (type == KiteElementTypes.COMPONENT_DECLARATION) return KiteStructureViewIcons.COMPONENT;
        if (type == KiteElementTypes.FUNCTION_DECLARATION) return KiteStructureViewIcons.FUNCTION;
        if (type == KiteElementTypes.VARIABLE_DECLARATION) return KiteStructureViewIcons.VARIABLE;
        if (type == KiteElementTypes.RESOURCE_DECLARATION) return KiteStructureViewIcons.RESOURCE;
        if (type == KiteElementTypes.TYPE_DECLARATION) return KiteStructureViewIcons.TYPE;
        return null;
    }
}
