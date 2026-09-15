package com.greatonce.oms.idea.tcc;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Include statically dispatched template calls in hook usage searches
 * @author ajie
 */
public final class TccFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
  /** Handle only overrides of the JEOMS template hooks */
  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    return templateEntry(element) != null;
  }

  /** Extend native usage results without affecting rename references */
  @Override
  public @Nullable FindUsagesHandler createFindUsagesHandler(
      @NotNull PsiElement element, boolean forHighlightUsages) {
    if (!canFindUsages(element)) {
      return null;
    }
    return new FindUsagesHandler(element) {
      /** Search indexed entry references and retain matching dispatch targets */
      @Override
      public boolean processElementUsages(@NotNull PsiElement target,
          @NotNull Processor<? super UsageInfo> processor, @NotNull FindUsagesOptions options) {
        if (!super.processElementUsages(target, processor, options)) {
          return false;
        }
        PsiMethod entry = ReadAction.computeBlocking(() -> templateEntry(target));
        if (!options.isUsages || entry == null) {
          return true;
        }
        return ReferencesSearch.search(entry, options.searchScope).forEach(reference -> {
          return ReadAction.computeBlocking(() -> {
              if (!(reference.getElement() instanceof PsiReferenceExpression expression)) {
                return true;
              }
              PsiElement[] targets = TccGotoDeclarationHandler.targets(expression.getReferenceNameElement());
              return targets == null || !targets[0].isEquivalentTo(target.getNavigationElement())
                  || processor.process(new UsageInfo(reference));
          });
        });
      }
    };
  }

  /** Match hook ancestry rather than accepting unrelated methods with the same name */
  private static @Nullable PsiMethod templateEntry(PsiElement element) {
    if (!(element instanceof PsiMethod hook) || DumbService.isDumb(element.getProject())) {
      return null;
    }
    String entryName = switch (hook.getName()) {
      case "doPrepare" -> "prepare";
      case "doCommit" -> "commit";
      case "doRollback" -> "rollback";
      default -> null;
    };
    if (entryName == null) {
      return null;
    }
    for (PsiMethod parent : hook.findDeepestSuperMethods()) {
      var owner = parent.getContainingClass();
      if (owner != null && TccGotoDeclarationHandler.BASE_CLASS.equals(owner.getQualifiedName())) {
        PsiMethod[] entries = owner.findMethodsByName(entryName, false);
        return entries.length == 1 ? entries[0] : null;
      }
    }
    return null;
  }
}
