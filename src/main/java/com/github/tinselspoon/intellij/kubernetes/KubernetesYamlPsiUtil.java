package com.github.tinselspoon.intellij.kubernetes;

import com.github.tinselspoon.intellij.kubernetes.model.Model;
import com.github.tinselspoon.intellij.kubernetes.model.ModelProvider;
import com.github.tinselspoon.intellij.kubernetes.model.Property;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLPsiElement;
import org.jetbrains.yaml.psi.YAMLSequence;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.YAMLValue;

/**
 * Utilities for working with YAML-based Kubernetes resource documents.
 */
public final class KubernetesYamlPsiUtil {

    /**
     * Static class private constructor.
     */
    private KubernetesYamlPsiUtil() {
        // no construction
    }

    /**
     * Find the {@link ResourceTypeKey} for the current document.
     *
     * @param element an element within the document to check.
     * @return the resource type key, or {@code null} if the "apiVersion" and "kind" fields are not present.
     */
    @Nullable
    public static ResourceTypeKey findResourceKey(final PsiElement element) {
        // Get the top-level mapping
        final YAMLMapping topLevelMapping = getTopLevelMapping(element);
        return getResourceTypeKey(topLevelMapping);
    }

    @Nullable
    public static ResourceTypeKey getResourceTypeKey(YAMLMapping yamlMapping) {
        if (yamlMapping == null) {
            return null;
        }
        String apiVersion = "";
        String kind = "";
        final String regex = "\\s*IntellijIdeaRulezzz\\s*";
        final Pattern pattern = Pattern.compile(regex);
        for (YAMLKeyValue keyValue : yamlMapping.getKeyValues()) {

            String keyText = pattern.matcher(keyValue.getKeyText()).replaceAll("");
            if ("apiVersion".equals(keyText)) {
                apiVersion = keyValue.getValueText().trim();
            }
            if ("kind".equals(keyText)) {
                kind = keyValue.getValueText().trim();
            }
        }

        if (!apiVersion.isEmpty() && !kind.isEmpty()) {
            return new ResourceTypeKey(apiVersion, kind);
        } else {
            return null;
        }
    }


    /**
     * Gets the top-level mapping in the document, if present.
     *
     * @param element an element within the document.
     * @return the top-level mapping, or {@code null} if one is not defined (e.g. in an empty document).
     */
    @Nullable
    public static YAMLMapping getTopLevelMapping(final PsiElement element) {
        final YAMLDocument document = PsiTreeUtil.getParentOfType(element, YAMLDocument.class);
        if (document != null) {
            final YAMLValue topLevelValue = document.getTopLevelValue();
            if (topLevelValue instanceof YAMLMapping) {
                return (YAMLMapping) topLevelValue;
            }
        }
        return null;
    }

    /**
     * Gets the text of the value held by the given key within a mapping.
     *
     * @param mapping the mapping to search through.
     * @param key the key to search for.
     * @return the trimmed text value of the key, or {@code null} if the mapping is null or the key was not found.
     */
    @Nullable
    public static String getValueText(@Nullable final YAMLMapping mapping, @NotNull final String key) {
        return Optional.ofNullable(mapping).map(m -> m.getKeyValueByKey(key)).map(YAMLKeyValue::getValueText).map(String::trim).orElse(null);
    }

    /**
     * Determines whether the element is within a Kubernetes YAML file. This is done by checking for the presence of "apiVersion" or "kind" as top-level keys within the first document of the file.
     *
     * @param element an element within the file to check.
     * @return true if the element is within A Kubernetes YAML file, otherwise, false.
     */
    public static boolean isKubernetesFile(final PsiElement element) {
        final PsiFile file = element.getContainingFile();
        if (file instanceof YAMLFile) {
            final Collection<YAMLKeyValue> keys = YAMLUtil.getTopLevelKeys((YAMLFile) file);
            return keys.stream().map(YAMLKeyValue::getKeyText).anyMatch(s -> "apiVersion".equals(s) || "kind".equals(s));
        }
        return false;
    }

    /**
     * Find the corresponding {@link Model} object that represents the value of a given {@link YAMLKeyValue}.
     *
     * @param modelProvider the model provider to use for looking up schema resources.
     * @param resourceKey the top-level key of the resource.
     * @param keyValue the {@code YAMLKeyValue} to search back from.
     * @return the corresponding model or {@code null} if one cannot be located.
     */
    @Nullable
    public static Model modelForKey(final ModelProvider modelProvider, final ResourceTypeKey resourceKey, final YAMLKeyValue keyValue) {
        // Get the tree of keys leading up to this one
        final List<String> keys = new ArrayList<>();
        YAMLKeyValue currentKey = keyValue;
        do {
            keys.add(currentKey.getKeyText());
        } while ((currentKey = PsiTreeUtil.getParentOfType(currentKey, YAMLKeyValue.class)) != null);

        // We have iterated from the inside out, so flip this around to get it in the correct direction for the ModelProvider
        Collections.reverse(keys);
        return modelProvider.findModel(resourceKey, keys);
    }

    /**
     * Find the corresponding {@link Property} object that relates to the given {@link YAMLKeyValue}.
     *
     * @param modelProvider the model provider to use for looking up schema resources.
     * @param resourceKey the top-level key of the resource.
     * @param keyValue the {@code YAMLKeyValue} to search back from.
     * @return the corresponding property or {@code null} if one cannot be located.
     */
    @Nullable
    public static Property propertyForKey(@NotNull final ModelProvider modelProvider, @NotNull final ResourceTypeKey resourceKey, @NotNull final YAMLKeyValue keyValue) {
        // Get the tree of keys leading up to this one
        final List<String> keys = new ArrayList<>();
        YAMLKeyValue currentKey = keyValue;
        while ((currentKey = PsiTreeUtil.getParentOfType(currentKey, YAMLKeyValue.class)) != null) {
            keys.add(currentKey.getKeyText());
        }

        // We have iterated from the inside out, so flip this around to get it in the correct direction for the ModelProvider
        Collections.reverse(keys);
        return modelProvider.findProperties(resourceKey, keys).get(keyValue.getKeyText());
    }

    public static Map<String, Property> traversePropertiesForKey(final ModelProvider modelProvider, final ResourceTypeKey resourceKey, final PsiElement keyValue) {
        // Get the tree of keys leading up to this one
        final List<PathElement> keys = new ArrayList<>();
        PsiElement lastKey = keyValue;
        PsiElement currentKey;
        while ((currentKey = PsiTreeUtil.getParentOfType(lastKey, YAMLKeyValue.class, YAMLSequenceItem.class)) != null) {
            if (currentKey instanceof YAMLKeyValue) {
                keys.add(new PathElement((YAMLKeyValue) currentKey));
            } else if (currentKey instanceof YAMLSequenceItem) {
                final PsiElement parent = currentKey.getParent();
                if (!(parent instanceof YAMLSequence)) {
                    //We have SequenceItem but Parent is not Sequence. Don't know what to do
                    return Collections.emptyMap();
                }
                Optional<PsiElement> firstChild = Arrays.stream(currentKey.getChildren()).filter(psiElement -> psiElement instanceof YAMLMapping).findFirst();
                if (!(firstChild.isPresent())) {
                    // didn't find expected mapping block
                    return Collections.emptyMap();
                }
                keys.add(new PathElement((YAMLMapping) firstChild.get()));
            }
            lastKey = currentKey;
        }
        keys.add(new PathElement(getTopLevelMapping(lastKey)));
        // We have iterated from the inside out, so flip this around to get it in the correct direction for the ModelProvider
        Collections.reverse(keys);
        return modelProvider.traversePath(resourceKey, keys);
    }

    public static Property traversePropertyForKey(final ModelProvider modelProvider, final ResourceTypeKey resourceKey, final PsiElement keyValue) {
        // Get the tree of keys leading up to this one
        if (!(keyValue instanceof YAMLKeyValue)) {
            return null;
        }
        final String key = ((YAMLKeyValue)keyValue).getKeyText();

        Map<String, Property> stringPropertyMap = traversePropertiesForKey(modelProvider, resourceKey, keyValue);
        return stringPropertyMap.get(key);
    }

    public static class PathElement {

        YAMLPsiElement yamlPsiElement;

        public PathElement(YAMLPsiElement yamlPsiElement) {
            this.yamlPsiElement = yamlPsiElement;
        }

        public String getKey() {
            if (yamlPsiElement instanceof YAMLKeyValue) {
                return ((YAMLKeyValue) yamlPsiElement).getKeyText();
            }
            return null;
        }

        public ResourceTypeKey getResourceTypeKey() {
            if (yamlPsiElement instanceof YAMLMapping) {
                return KubernetesYamlPsiUtil.getResourceTypeKey((YAMLMapping) yamlPsiElement);
            }
            return null;
        }

        @Override
        public String toString() {
            return "PathElement{" +
                    "yamlPsiElement=" + yamlPsiElement +
                    '}';
        }
    }
}
