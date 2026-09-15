package com.greatonce.oms.idea.tcc;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSuperExpression;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Navigate template calls to TCC hooks
 * @author ajie
 */
public final class TccGotoDeclarationHandler implements GotoDeclarationHandler {
  static final String BASE_CLASS = "com.greatonce.oms.db.tcc.AbstractOmsTccAction";

  /** Locate the hook and preserve the template entry in the native chooser */
  @Override
  public PsiElement @Nullable [] getGotoDeclarationTargets(
      @Nullable PsiElement sourceElement, int offset, @NotNull Editor editor) {
    return targets(sourceElement);
  }

  /** Share dispatch resolution with reverse usage search */
  static PsiElement @Nullable [] targets(@Nullable PsiElement sourceElement) {
    if (!(sourceElement instanceof PsiIdentifier)
        || !(sourceElement.getParent() instanceof PsiReferenceExpression reference)) {
      return null;
    }
    String hookName = switch (sourceElement.getText()) {
      case "prepare" -> "doPrepare";
      case "commit" -> "doCommit";
      case "rollback" -> "doRollback";
      default -> null;
    };
    if (hookName == null || DumbService.isDumb(sourceElement.getProject())
        || !(reference.resolve() instanceof PsiMethod entry)) {
      return null;
    }
    PsiClass base = entry.getContainingClass();
    if (base == null || !BASE_CLASS.equals(base.getQualifiedName())) {
      return null;
    }
    PsiClass receiver = receiverClass(reference, base);
    if (receiver == null || receiver.isEquivalentTo(base) || !receiver.isInheritor(base, true)) {
      return null;
    }
    for (PsiMethod declaration : base.findMethodsByName(hookName, false)) {
      PsiMethod hook = MethodSignatureUtil.findMethodBySuperMethod(receiver, declaration, true);
      if (hook != null && !hook.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return new PsiElement[]{hook.getNavigationElement(), entry.getNavigationElement()};
      }
    }
    return null;
  }

  /** Resolve the static receiver without searching project implementations */
  private static @Nullable PsiClass receiverClass(PsiReferenceExpression reference, PsiClass base) {
    PsiExpression qualifier = reference.getQualifierExpression();
    if (reference instanceof PsiMethodReferenceExpression methodReference) {
      if (methodReference.getQualifierType() != null) {
        return PsiUtil.resolveClassInType(methodReference.getQualifierType().getType());
      }
      if (qualifier instanceof PsiReferenceExpression classReference
          && classReference.resolve() instanceof PsiClass owner) {
        return owner;
      }
    }
    if (qualifier instanceof PsiSuperExpression superExpression
        && superExpression.getQualifier() != null) {
      return superExpression.getQualifier().resolve() instanceof PsiClass owner ? owner : null;
    }
    if (qualifier != null && !(qualifier instanceof PsiSuperExpression)) {
      return PsiUtil.resolveClassInType(qualifier.getType());
    }
    for (PsiClass enclosing = PsiTreeUtil.getParentOfType(reference, PsiClass.class);
         enclosing != null; enclosing = PsiTreeUtil.getParentOfType(enclosing, PsiClass.class)) {
      if (enclosing.isInheritor(base, true)) {
        return enclosing;
      }
    }
    return null;
  }
}
