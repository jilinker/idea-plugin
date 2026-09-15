package com.intellij.psi;

public interface PsiClass extends PsiElement {
    String getQualifiedName();

    PsiModifierList getModifierList();
}
