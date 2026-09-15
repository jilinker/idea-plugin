package com.idea.plugin.oms.dubbo.highlighter;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 注解解析工具，负责判断任意 PSI 元素是否被 OmsDubbo 注解链标记。
 */
public class OmsDubboAnnotationResolver {

    private static final Set<String> TARGET_ANNOTATIONS;
    private static final Set<String> TARGET_SIMPLE_NAMES;
    private static final int MAX_DEPTH = 16;

    static {
        Set<String> fqNames = new HashSet<>();
        fqNames.add("com.oms.annotations.OmsDubboService");
        fqNames.add("com.oms.annotations.OmsDubboReference");
        fqNames.add("com.greatonce.oms.biz.impl.cloud.anno.OmsDubboService");
        fqNames.add("com.greatonce.oms.biz.impl.cloud.anno.OmsDubboReference");
        fqNames.add("OmsDubboService");
        fqNames.add("OmsDubboReference");
        TARGET_ANNOTATIONS = Collections.unmodifiableSet(fqNames);

        Set<String> simpleNames = new HashSet<>();
        simpleNames.add("OmsDubboService");
        simpleNames.add("OmsDubboReference");
        TARGET_SIMPLE_NAMES = Collections.unmodifiableSet(simpleNames);
    }

    /**
     * 判定元素是否直接或间接被 OmsDubbo 注解标记。
     */
    public boolean hasOmsDubboAnnotation(@Nullable PsiModifierListOwner owner) {
        if (owner == null) {
            return false;
        }
        PsiModifierList modifierList = owner.getModifierList();
        if (modifierList == null) {
            return false;
        }
        Set<String> visited = new HashSet<>();
        for (PsiAnnotation annotation : modifierList.getAnnotations()) {
            if (isTargetOrMetaAnnotated(annotation, visited, 0)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTargetOrMetaAnnotated(@Nullable PsiAnnotation annotation,
                                            @NotNull Set<String> visited,
                                            int depth) {
        if (annotation == null || depth > MAX_DEPTH) {
            return false;
        }
        String qualifiedName = annotation.getQualifiedName();
        if (isTargetAnnotation(qualifiedName)) {
            return true;
        }

        PsiJavaCodeReferenceElement reference = annotation.getNameReferenceElement();
        if (reference == null) {
            return false;
        }
        PsiElement resolved = reference.resolve();
        if (!(resolved instanceof PsiClass)) {
            return false;
        }
        PsiClass annotationClass = (PsiClass) resolved;
        String annotationClassName = annotationClass.getQualifiedName();
        if (annotationClassName != null) {
            if (!visited.add(annotationClassName)) {
                return false;
            }
            if (isTargetAnnotation(annotationClassName)) {
                return true;
            }
        }

        PsiModifierList modifierList = annotationClass.getModifierList();
        if (modifierList == null) {
            return false;
        }
        for (PsiAnnotation meta : modifierList.getAnnotations()) {
            if (isTargetOrMetaAnnotated(meta, visited, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTargetAnnotation(@Nullable String qualifiedName) {
        if (qualifiedName == null) {
            return false;
        }
        if (TARGET_ANNOTATIONS.contains(qualifiedName)) {
            return true;
        }
        String simpleName = extractSimpleName(qualifiedName);
        return TARGET_SIMPLE_NAMES.contains(simpleName);
    }

    private String extractSimpleName(@NotNull String qualifiedName) {
        int pos = qualifiedName.lastIndexOf('.')
                + 1;
        if (pos <= 0 || pos >= qualifiedName.length()) {
            return qualifiedName;
        }
        return qualifiedName.substring(pos);
    }
}
