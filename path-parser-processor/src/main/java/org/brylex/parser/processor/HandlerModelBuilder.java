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
        for (Element member : type.getEnclosedElements()) {
            Path path = member.getAnnotation(Path.class);
            if (path == null) continue;
            if (member.getKind() == ElementKind.FIELD) {
                VariableElement field = (VariableElement) member;
                String fieldType = field.asType().toString();
                bindings.add(new Binding.FieldText(path.value(), field, field.getSimpleName().toString(), fieldType));
            }
        }
        PackageElement pkg = env.getElementUtils().getPackageOf(type);
        return new HandlerModel(type, pkg.getQualifiedName().toString(),
                type.getSimpleName().toString(), bindings);
    }
}
