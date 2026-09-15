package com.greatonce.oms.idea.tcc;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.navigation.GotoImplementationHandler;
import com.intellij.codeInsight.navigation.actions.GotoImplementationAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.OverridingAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Preserve native implementation actions while resolving TCC hooks at call sites
 * @author ajie
 */
public final class TccGotoImplementationAction extends GotoImplementationAction implements OverridingAction {
  /** Retain localized native labels when replacing the registered action */
  public TccGotoImplementationAction() {
    getTemplatePresentation().setText(ActionsBundle.messagePointer("action.GotoImplementation.text"));
    getTemplatePresentation().setDescription(ActionsBundle.messagePointer("action.GotoImplementation.description"));
  }

  /** Reuse the native navigation lifecycle for keyboard and menu invocation */
  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new GotoImplementationHandler() {
      /** Select the dispatched hook or fall back to ordinary implementation search */
      @Override
      public GotoData getSourceAndTargetElements(Editor editor, PsiFile file) {
        int offset = TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
        PsiElement[] targets = TccGotoDeclarationHandler.targets(file.findElementAt(offset));
        return targets == null ? super.getSourceAndTargetElements(editor, file)
            : new GotoData(targets[1], new PsiElement[]{targets[0]}, List.of());
      }
    };
  }
}
