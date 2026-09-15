package com.idea.plugin.oms.dubbo.highlighter;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 单元测试：验证注解解析逻辑涵盖直接与递归糖注解。
 */
class OmsDubboAnnotationResolverTest {

    private final OmsDubboAnnotationResolver resolver = new OmsDubboAnnotationResolver();

    @Test
    void shouldDetectDirectAnnotation() {
        PsiModifierListOwner owner = mockOwner(withAnnotations(directAnnotation()));
        assertTrue(resolver.hasOmsDubboAnnotation(owner));
    }

    @Test
    void shouldDetectSingleLevelMetaAnnotation() {
        PsiAnnotation metaTarget = directAnnotation();
        PsiAnnotation customAnnotation = wrapWithMeta("com.example.MyOrderService", metaTarget);
        PsiModifierListOwner owner = mockOwner(withAnnotations(customAnnotation));
        assertTrue(resolver.hasOmsDubboAnnotation(owner));
    }

    @Test
    void shouldDetectMultiLevelMetaAnnotation() {
        PsiAnnotation baseTarget = directAnnotation();
        PsiAnnotation level1 = wrapWithMeta("com.example.OrderRpc", baseTarget);
        PsiAnnotation level2 = wrapWithMeta("com.example.MyOrderService", level1);
        PsiModifierListOwner owner = mockOwner(withAnnotations(level2));
        assertTrue(resolver.hasOmsDubboAnnotation(owner));
    }

    @Test
    void shouldStopAtCycleAndReturnFalseWhenNoTarget() {
        PsiAnnotation loopA = wrapWithMeta("com.example.A", null);
        PsiAnnotation loopB = wrapWithMeta("com.example.B", loopA);
        // 构造循环：A -> B -> A
        injectMeta(loopA, loopB);
        injectMeta(loopB, loopA);
        PsiModifierListOwner owner = mockOwner(withAnnotations(loopA));
        assertFalse(resolver.hasOmsDubboAnnotation(owner));
    }

    @Test
    void shouldReturnFalseWhenNoAnnotations() {
        PsiModifierList modifierList = mock(PsiModifierList.class);
        when(modifierList.getAnnotations()).thenReturn(new PsiAnnotation[0]);
        PsiModifierListOwner owner = mock(PsiModifierListOwner.class);
        when(owner.getModifierList()).thenReturn(modifierList);
        assertFalse(resolver.hasOmsDubboAnnotation(owner));
    }

    private PsiAnnotation directAnnotation() {
        PsiAnnotation annotation = mock(PsiAnnotation.class);
        when(annotation.getQualifiedName()).thenReturn("com.oms.annotations.OmsDubboReference");
        return annotation;
    }

    private PsiModifierListOwner mockOwner(PsiAnnotation[] annotations) {
        PsiModifierListOwner owner = mock(PsiModifierListOwner.class);
        PsiModifierList modifierList = mock(PsiModifierList.class);
        when(modifierList.getAnnotations()).thenReturn(annotations);
        when(owner.getModifierList()).thenReturn(modifierList);
        return owner;
    }

    private PsiAnnotation[] withAnnotations(PsiAnnotation annotation) {
        return new PsiAnnotation[]{annotation};
    }

    private PsiAnnotation wrapWithMeta(String annotationClassName, PsiAnnotation metaAnnotation) {
        PsiAnnotation annotation = mock(PsiAnnotation.class);
        when(annotation.getQualifiedName()).thenReturn(annotationClassName);

        PsiClass psiClass = mock(PsiClass.class);
        when(psiClass.getQualifiedName()).thenReturn(annotationClassName);

        PsiModifierList modifierList = mock(PsiModifierList.class);
        if (metaAnnotation != null) {
            when(modifierList.getAnnotations()).thenReturn(new PsiAnnotation[]{metaAnnotation});
        } else {
            when(modifierList.getAnnotations()).thenReturn(new PsiAnnotation[0]);
        }
        when(psiClass.getModifierList()).thenReturn(modifierList);

        PsiJavaCodeReferenceElement referenceElement = mock(PsiJavaCodeReferenceElement.class);
        when(referenceElement.resolve()).thenReturn(psiClass);
        when(annotation.getNameReferenceElement()).thenReturn(referenceElement);
        return annotation;
    }

    private void injectMeta(PsiAnnotation target, PsiAnnotation meta) {
        PsiClass psiClass = mock(PsiClass.class);
        when(psiClass.getQualifiedName()).thenReturn("loop." + target.hashCode());
        PsiModifierList modifierList = mock(PsiModifierList.class);
        when(modifierList.getAnnotations()).thenReturn(new PsiAnnotation[]{meta});
        when(psiClass.getModifierList()).thenReturn(modifierList);
        PsiJavaCodeReferenceElement referenceElement = mock(PsiJavaCodeReferenceElement.class);
        when(referenceElement.resolve()).thenReturn(psiClass);
        when(target.getNameReferenceElement()).thenReturn(referenceElement);
    }
}
