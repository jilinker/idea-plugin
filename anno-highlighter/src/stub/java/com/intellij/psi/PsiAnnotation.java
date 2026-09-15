package com.intellij.psi;

public interface PsiAnnotation extends PsiElement {
    String getQualifiedName();

    PsiJavaCodeReferenceElement getNameReferenceElement();
}
