package com.github.tinselspoon.intellij.kubernetes;


import com.github.tinselspoon.intellij.kubernetes.model.ArrayItems;
import com.github.tinselspoon.intellij.kubernetes.model.FieldType;
import com.github.tinselspoon.intellij.kubernetes.model.Property;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Utilities for working with model classes.
 */
final class ModelUtil {

    private static final String DEFAULT = "unknown";

    /**
     * Static class private constructor.
     */
    private ModelUtil() {
    }

    /**
     * Get a string that describes the type of a particular property.
     * <p>
     * If the property is a basic type (string, integer etc) then the type name is returned. If the property type refers to a model, the model name is returned. If the property is an array, then the
     * process described previously is carried out for the array item type, and the string "[]" is appended to the end.
     *
     * @param propertySpec the property to obtain the type string for.
     * @return the type string.
     */
    @NotNull
    static String typeStringFor(@NotNull final Property propertySpec) {
        final String typeText;
        final ArrayItems items = propertySpec.getItems();
        if (propertySpec.getType() == FieldType.ARRAY && items != null) {
            typeText = Objects.toString(items.getRef(), Objects.toString(items.getType(), DEFAULT)) + "[]";
        } else {
            typeText = Objects.toString(propertySpec.getRef(), Objects.toString(propertySpec.getType(), DEFAULT));
        }
        return typeText;
    }
}
