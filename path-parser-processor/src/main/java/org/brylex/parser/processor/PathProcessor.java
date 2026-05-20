package org.brylex.parser.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes("org.brylex.parser.annotation.Path")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class PathProcessor extends AbstractProcessor {

    private final List<HandlerModel> collected = new ArrayList<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        HandlerModelBuilder builder = new HandlerModelBuilder(processingEnv);
        HandlerCodeGenerator generator = new HandlerCodeGenerator(processingEnv);

        for (TypeElement annotation : annotations) {
            Set<TypeElement> handlerTypes = new LinkedHashSet<>();
            for (Element annotated : roundEnv.getElementsAnnotatedWith(annotation)) {
                Element enclosing = annotated.getEnclosingElement();
                if (enclosing instanceof TypeElement type) handlerTypes.add(type);
            }
            for (TypeElement type : handlerTypes) {
                HandlerModel model = builder.build(type);
                if (model.bindings().isEmpty()) continue; // no APT-supported bindings, skip
                try {
                    generator.generate(model);
                    collected.add(model);
                } catch (IOException e) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR, "Codegen feilet: " + e.getMessage(), type);
                }
            }
        }

        if (roundEnv.processingOver() && !collected.isEmpty()) {
            emitRegistry();
        }
        return false;
    }

    private void emitRegistry() {
        RegistryCodeGenerator regGen = new RegistryCodeGenerator(processingEnv);
        String pkg = collected.get(0).packageName();
        String simple = "Generated_PathParserRegistry";
        String fingerprint = Fingerprint.over(collected);
        try {
            regGen.generate(pkg, simple, collected, fingerprint);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Registry-emit feilet: " + e.getMessage());
        }
    }
}
