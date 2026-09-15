package com.idea.plugin.oms.dubbo.highlighter;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;

/**
 * IntelliJ 扩展：让被 OmsDubbo 注解链标记的元素自动视为已使用。
 */
public class OmsDubboImplicitUsageProvider implements ImplicitUsageProvider {

    private final OmsDubboAnnotationResolver resolver = new OmsDubboAnnotationResolver();

    @Override
    public boolean isImplicitUsage(@NotNull PsiElement element) {
        if (!(element instanceof PsiModifierListOwner)) {
            return false;
        }
        return resolver.hasOmsDubboAnnotation((PsiModifierListOwner) element);
    }

    @Override
    public boolean isImplicitRead(@NotNull PsiElement element) {
        return isImplicitUsage(element);
    }

    @Override
    public boolean isImplicitWrite(@NotNull PsiElement element) {
        return isImplicitUsage(element);
    }
}
