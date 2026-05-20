package org.brylex.parser.processor;

import org.brylex.parser.annotation.Path;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.List;

public final class HandlerModelBuilder {

    private final ProcessingEnvironment env;

    public HandlerModelBuilder(ProcessingEnvironment env) {
        this.env = env;
    }

    public HandlerModel build(TypeElement type) {
        List<Binding> bindings = new ArrayList<>();
        boolean allSupported = true;
        for (Element member : type.getEnclosedElements()) {
            Path path = member.getAnnotation(Path.class);
            if (path == null) continue;
            if (member.getKind() == ElementKind.FIELD) {
                VariableElement field = (VariableElement) member;
                String fieldType = field.asType().toString();
                if ("java.lang.String".equals(fieldType)) {
                    bindings.add(new Binding.FieldText(path.value(), field, field.getSimpleName().toString(), fieldType));
                } else {
                    // Non-String field — processor doesn't yet support type conversion;
                    // mark entire handler as unsupported so reflection handles it instead.
                    allSupported = false;
                }
            }
        }
        if (!allSupported) {
            bindings.clear(); // Let reflection handle this handler
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
