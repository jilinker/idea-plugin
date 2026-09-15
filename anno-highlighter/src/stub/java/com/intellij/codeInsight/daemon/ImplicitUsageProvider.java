package com.intellij.codeInsight.daemon;

import com.intellij.psi.PsiElement;

/**
 * 轻量级 Stub，仅用于在 Maven 环境中编译插件代码，运行时由 IntelliJ SDK 提供真实实现。
 */
public interface ImplicitUsageProvider {
    boolean isImplicitUsage(PsiElement element);

    boolean isImplicitRead(PsiElement element);

    boolean isImplicitWrite(PsiElement element);
}
