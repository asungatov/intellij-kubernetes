package com.github.tinselspoon.intellij.kubernetes.model;

import com.github.tinselspoon.intellij.kubernetes.KubernetesYamlPsiUtil.PathElement;
import com.github.tinselspoon.intellij.kubernetes.ResourceTypeKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides information on the schema of Kubernetes resources.
 */
public class ModelProvider {

    public static final String RUNTIME_RAW_EXTENSION = "runtime.RawExtension";
    /**
     * Singleton instance.
     */
    public static final ModelProvider INSTANCE = new ModelProvider();

    private final ModelLoader modelLoader = new ModelLoader();

    /**
     * Singleton private constructor.
     */
    private ModelProvider() {
    }

    /**
     * Suggest a set of values for the "apiVersion" field.
     *
     * @return all possible API versions.
     */
    @NotNull
    public Set<String> suggestApiVersions() {
        return getSpecs().stream().map(SwaggerSpec::getApiVersion).filter(v -> v != null && !"".equals(v)).collect(Collectors.toSet());
    }

    /**
     * Suggest a set of values for the "kind" field.
     *
     * @param apiVersion the API version for which kinds will be returned; if null, then kinds for all API versions will be returned.
     * @return a set of possible kinds.
     */
    public Set<ResourceTypeKey> suggestKinds(@Nullable final String apiVersion) {
        Stream<SwaggerSpec> applicableSpecs = getSpecs().stream();
        if (apiVersion != null) {
            applicableSpecs = applicableSpecs.filter(s -> apiVersion.equals(s.getApiVersion()));
        }

        // Make a map of kinds to apiVersions - this is not the final data structure we want but is helpful for when we preserve only the most recent API version for a particular kind
        // TODO This does assume that no two API groups will declare the same kind - currently this doesn't happen but the Kubernetes API structure does allow for it
        Map<String, String> kindToApi = new HashMap<>();
        // Suggest any resource that appears as a return type from an API request
        applicableSpecs.forEach(s -> {
            final Set<String> apiTypes = getApiTypes(s);

            final Set<String> models = getModels(s);
            // Make sure all API types have an associated model, otherwise there isn't much point in suggesting them as we can't offer anything useful
            apiTypes.retainAll(models);
            for (final String apiType : apiTypes) {
                String kind = stripModelIdPrefix(apiType);
                // Only keep the "highest" API version for a kind to ensure we are using the latest version available
                kindToApi.merge(kind, s.getApiVersion(), (a, b) -> ApiVersionComparator.INSTANCE.compare(a, b) > 0 ? a : b);
            }
        });

        // Convert map of type keys to ResourceTypeKey objects
        return kindToApi.entrySet().stream().map(e -> new ResourceTypeKey(e.getValue(), e.getKey())).collect(Collectors.toSet());
    }

    @NotNull
    private Set<String> getModels(SwaggerSpec s) {
        Set<String> models = new HashSet<>();
        for (final Model model : s.getModels().values()) {
            models.add(model.getId());
        }
        return models;
    }

    @NotNull
    private Set<String> getApiTypes(SwaggerSpec s) {
        Set<String> apiTypes = new HashSet<>();
        for (final Api api : s.getApis()) {
            for (final ApiOperation operation : api.getOperations()) {
                if ("POST".equals(operation.getMethod())) {
                    apiTypes.add(operation.getType());
                }
            }
        }
        return apiTypes;
    }

    /**
     * Find the model that governs the property described by navigating from the base model of the {@link ResourceTypeKey} through the properties given in the {@code path}.
     *
     * @param resourceTypeKey the resource at which to begin the search.
     * @param path a series of properties to navigate through, may be empty to return the root model of the {@code ResourceTypeKey}.
     * @return the model, or {@code null} if one cannot be found.
     */
    @Nullable
    public Model findModel(final ResourceTypeKey resourceTypeKey, final List<String> path) {
        final SwaggerSpec spec = getSpec(resourceTypeKey);
        if (spec != null) {
            return findModel(spec, resourceTypeKey, path);
        }
        return null;
    }

    @Nullable
    private Model findModel(SwaggerSpec spec, ResourceTypeKey resourceTypeKey, List<String> path) {
        Model model = getModel(spec, resourceTypeKey);
        for (final String target : path) {
            if (model != null) {
                final Property property = model.getProperties().get(target);
                model = property != null ? getModelByProperty(spec, property) : null;
            }
        }
        return model;
    }

    @Nullable
    private Model getModelByProperty(SwaggerSpec spec, Property property) {
        if (property==null) {
            return null;
        }
        Model newModel = null;
        if (property.getRef() != null) {
            // Look up the ref for the referenced object
            newModel = spec.getModels().get(property.getRef());
        } else if (isArrayOfModels(property)) {
            // Look up the ref for the array items
            newModel = spec.getModels().get(property.getItems().getRef());
        }
        return newModel;
    }

    public boolean isArrayOfModels(@NotNull Property property) {
        return property.getType() == FieldType.ARRAY && property.getItems() != null && property.getItems().getRef() != null;
    }

    /**
     * Gets the {@link SwaggerSpec} that contains a definition for the given resource key.
     *
     * @param resourceTypeKey the resource key to search for.
     * @return the corresponding spec, or {@code null} if one cannot be found.
     */
    @Nullable
    private SwaggerSpec getSpec(@NotNull final ResourceTypeKey resourceTypeKey) {
        final String apiVersion = resourceTypeKey.getApiVersion();
        final String modelId = modelIdFromResourceKey(resourceTypeKey);
        for (SwaggerSpec s : getSpecs()) {
            if (apiVersion.equals(s.getApiVersion()) && s.getModels().containsKey(modelId)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Get a set of loaded {@link SwaggerSpec}s, initialising if necessary.
     *
     * @return the swagger specs.
     */
    @NotNull
    private List<SwaggerSpec> getSpecs() {
        return modelLoader.getActiveSpecs();
    }

    /**
     * Generates the model ID from the specified {@link ResourceTypeKey}.
     * <p>
     * This is achieved by concatenating the "version" part (e.g. {@code v1} in {@code batch/v1}) of the resource key {@linkplain ResourceTypeKey#apiVersion API version}, a dot, and the {@linkplain
     * ResourceTypeKey#getKind() kind}.
     *
     * @param resourceTypeKey the resource type key.
     * @return the model ID.
     */
    @NotNull
    private String modelIdFromResourceKey(final ResourceTypeKey resourceTypeKey) {
        String resourceApiVersion = resourceTypeKey.getApiVersion();
        if (resourceApiVersion.indexOf('/') > -1) {
            resourceApiVersion = resourceApiVersion.substring(resourceApiVersion.indexOf('/') + 1);
        }
        return resourceApiVersion + "." + resourceTypeKey.getKind();
    }

    /**
     * Removes the API version prefix from a model identifier.
     *
     * @param modelId the model ID to clean.
     * @return the cleaned model ID.
     */
    @NotNull
    private String stripModelIdPrefix(@NotNull final String modelId) {
        final int dot = modelId.indexOf('.');
        if (dot > -1) {
            return modelId.substring(dot + 1);
        } else {
            return modelId;
        }
    }


    /**
     * Find the properties that may exist as children of the property described by navigating from the base model of the {@link ResourceTypeKey} through the properties given in the {@code path}.
     *
     * @param resourceTypeKey the resource at which to begin the search.
     * @param path a series of properties to navigate through, may be empty to return the properties that may be defined on the root of the {@code ResourceTypeKey}.
     * @return the map of property names to property specifications, may be empty if none can be found.
     */
    @NotNull
    public Map<String, Property> findProperties(final ResourceTypeKey resourceTypeKey, final List<String> path) {
        final SwaggerSpec spec = getSpec(resourceTypeKey);
        if (spec != null) {
            return findProperties(spec, resourceTypeKey, path);
        }
        return Collections.emptyMap();
    }


    /**
     * Find the properties that may exist as children of the property described by navigating from the base model of the {@link ResourceTypeKey} through the properties given in the {@code path}.
     *
     * @param spec the spec to search within.
     * @param resourceTypeKey the resource at which to begin the search.
     * @param path a series of properties to navigate through, may be empty to return the properties that may be defined on the root of the {@code ResourceTypeKey}.
     * @return the map of property names to property specifications, may be empty if none can be found.
     */
    @NotNull
    private Map<String, Property> findProperties(final SwaggerSpec spec, final ResourceTypeKey resourceTypeKey, final List<String> path) {
        final Model model = findModel(spec, resourceTypeKey, path);
        if (model != null && model.getProperties() != null) {
            return model.getProperties();
        }
        return Collections.emptyMap();
    }

    @NotNull
    public Map<String, Property> traversePath(final ResourceTypeKey resourceTypeKey, List<PathElement> path) {
        Model model = null;
        SwaggerSpec spec = null;
        for (int i = 0, pathSize = path.size(); i < pathSize; i++) {
            PathElement targetKey = path.get(i);
            if (model == null || RUNTIME_RAW_EXTENSION.equals(model.getId())) {
                ResourceTypeKey key = targetKey.getResourceTypeKey();
                if (key != null) {
                    spec = getSpec(key);
                    if (spec == null) {
                        return Collections.emptyMap();
                    }
                    model = getModel(spec, key);
                }
                if (model == null) {
                    return Collections.emptyMap();
                }
                continue;
            }
            Map<String, Property> properties = model.getProperties();
            if (targetKey.getKey() == null) {
                continue;
            }
            final Property property = properties.get(targetKey.getKey());
            if (property == null) {
                if(i != pathSize-1) {
                    model = null;
                }
                break;
            }

            model = getModelByProperty(spec, property);
            if (model == null) {
                return Collections.emptyMap();
            }
        }
        if (model == null) {
            return Collections.emptyMap();
        }
        return model.getProperties();
    }

    private Model getModel(SwaggerSpec spec, ResourceTypeKey resourceTypeKey) {
        final String search = modelIdFromResourceKey(resourceTypeKey);
        return spec.getModels().get(search);
    }
}
