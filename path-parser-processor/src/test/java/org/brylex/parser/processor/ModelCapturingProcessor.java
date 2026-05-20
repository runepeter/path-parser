package org.brylex.parser.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Set;

@SupportedAnnotationTypes("org.brylex.parser.annotation.Path")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
class ModelCapturingProcessor extends AbstractProcessor {
    HandlerModel last;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (Element annotated : roundEnv.getElementsAnnotatedWith(annotation)) {
                TypeElement type = (TypeElement) annotated.getEnclosingElement();
                last = new HandlerModelBuilder(processingEnv).build(type);
                return true;
            }
        }
        return false;
    }
}
