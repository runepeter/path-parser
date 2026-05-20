package org.brylex.parser.processor;

import org.brylex.parser.annotation.Path;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class HandlerModelBuilder {

    /** Types (fully-qualified) that Conversions.convert() handles directly — no generics, no arrays. */
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "java.lang.String", "java.lang.CharSequence",
            "int", "java.lang.Integer",
            "long", "java.lang.Long",
            "short", "java.lang.Short",
            "byte", "java.lang.Byte",
            "double", "java.lang.Double",
            "float", "java.lang.Float",
            "boolean", "java.lang.Boolean",
            "char", "java.lang.Character",
            "java.math.BigInteger", "java.math.BigDecimal",
            "java.time.LocalDate", "java.time.LocalDateTime",
            "java.time.Instant", "java.util.UUID"
    );

    private final ProcessingEnvironment env;

    public HandlerModelBuilder(ProcessingEnvironment env) {
        this.env = env;
    }

    /** Returns true if the field type can be handled by Conversions.convert() in generated code. */
    private boolean isConvertibleFieldType(VariableElement field) {
        String typeName = field.asType().toString();
        // Generic types (e.g. List<String>) and array types are not convertible directly.
        if (typeName.contains("<") || typeName.contains("[")) return false;
        if (SUPPORTED_TYPES.contains(typeName)) return true;
        // Enum types are also supported — check via TypeKind (DECLARED) and element kind.
        if (field.asType().getKind() == TypeKind.DECLARED) {
            var typeElement = (TypeElement) env.getTypeUtils().asElement(field.asType());
            if (typeElement != null && typeElement.getKind() == ElementKind.ENUM) return true;
        }
        return false;
    }

    public HandlerModel build(TypeElement type) {
        List<Binding> bindings = new ArrayList<>();
        for (Element member : type.getEnclosedElements()) {
            Path path = member.getAnnotation(Path.class);
            if (path == null) continue;
            if (member.getKind() == ElementKind.FIELD) {
                VariableElement field = (VariableElement) member;
                String fieldType = field.asType().toString();
                if (!isConvertibleFieldType(field)) {
                    // Non-convertible types (collections, generics, unknown types) are left for reflection.
                    continue;
                }
                String pathValue = path.value();
                int lastSlash = pathValue.lastIndexOf('/');
                String lastSegment = pathValue.substring(lastSlash + 1);
                if (lastSegment.startsWith("@")) {
                    String parentPath = pathValue.substring(0, lastSlash);
                    if (parentPath.isEmpty()) parentPath = "/";
                    bindings.add(new Binding.Attribute(parentPath, field,
                            field.getSimpleName().toString(), fieldType, lastSegment.substring(1)));
                    continue;
                }
                bindings.add(new Binding.FieldText(pathValue, field, field.getSimpleName().toString(), fieldType));
            }
        }
        PackageElement pkg = env.getElementUtils().getPackageOf(type);
        // For nested classes (e.g. OuterTest$Item), use qualified name relative to the package
        // to avoid collisions between identically-named inner classes in different enclosing types.
        String qualifiedName = type.getQualifiedName().toString();
        String pkgPrefix = pkg.getQualifiedName().toString();
        String relativeName = pkgPrefix.isEmpty()
                ? qualifiedName
                : qualifiedName.substring(pkgPrefix.length() + 1);
        // Replace '$' (inner class separator) and '.' with '_' to form a valid Java identifier
        String simpleName = relativeName.replace('.', '_').replace('$', '_');
        return new HandlerModel(type, pkg.getQualifiedName().toString(), simpleName, bindings);
    }
}
