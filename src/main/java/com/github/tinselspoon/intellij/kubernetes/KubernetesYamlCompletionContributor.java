package com.github.tinselspoon.intellij.kubernetes;

import org.jetbrains.yaml.YAMLLanguage;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.patterns.PlatformPatterns;

/**
 * Completion contributor for Kubernetes YAML files.
 */
public class KubernetesYamlCompletionContributor extends CompletionContributor {

    /** Default constructor. */
    public KubernetesYamlCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().withLanguage(YAMLLanguage.INSTANCE), new KubernetesYamlCompletionProvider());
    }


}
