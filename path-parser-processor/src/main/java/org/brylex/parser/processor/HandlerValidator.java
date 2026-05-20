package org.brylex.parser.processor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import java.util.HashMap;
import java.util.Map;

public final class HandlerValidator {

    private final ProcessingEnvironment env;

    public HandlerValidator(ProcessingEnvironment env) {
        this.env = env;
    }

    public boolean validate(HandlerModel model) {
        boolean ok = true;
        Map<String, Binding> seen = new HashMap<>();
        for (Binding b : model.bindings()) {
            if (b.path().isEmpty()) {
                err(b.element(), "@Path-verdien kan ikke være tom.");
                ok = false;
                continue;
            }
            Binding prior = seen.put(b.path(), b);
            if (prior != null) {
                err(b.element(),
                        "@Path('" + b.path() + "') deklareres to ganger i "
                                + model.simpleName() + ".");
                ok = false;
            }
            if (b instanceof Binding.FieldText ft && isUnknownJavaType(ft.fieldType())) {
                err(b.element(),
                        "Felt-type '" + ft.fieldType()
                                + "' kan ikke konverteres fra tekst, og inneholder ingen @Path-annoterte elementer for sub-handler-bruk.");
                ok = false;
            }
            // Sub-handler bindings with a java.* type are not real sub-handlers — they are
            // unsupported java types that cannot be converted from text.
            if (b instanceof Binding.SubHandler sh
                    && sh.kind() == Binding.SubHandler.Kind.FIELD
                    && isUnknownJavaType(sh.subHandlerType())) {
                err(b.element(),
                        "Felt-type '" + sh.subHandlerType()
                                + "' kan ikke konverteres fra tekst, og inneholder ingen @Path-annoterte elementer for sub-handler-bruk.");
                ok = false;
            }
        }
        return ok;
    }

    private boolean isUnknownJavaType(String type) {
        return type.startsWith("java.") && !isSupportedFieldType(type);
    }

    private boolean isSupportedFieldType(String type) {
        return type.equals("java.lang.String") || type.equals("int") || type.equals("java.lang.Integer")
                || type.equals("long") || type.equals("java.lang.Long")
                || type.equals("short") || type.equals("java.lang.Short")
                || type.equals("byte") || type.equals("java.lang.Byte")
                || type.equals("double") || type.equals("java.lang.Double")
                || type.equals("float") || type.equals("java.lang.Float")
                || type.equals("boolean") || type.equals("java.lang.Boolean")
                || type.equals("char") || type.equals("java.lang.Character")
                || type.equals("java.math.BigDecimal") || type.equals("java.math.BigInteger")
                || type.equals("java.time.LocalDate") || type.equals("java.time.LocalDateTime")
                || type.equals("java.time.Instant") || type.equals("java.util.UUID")
                || type.startsWith("java.util.List") || type.startsWith("java.util.Set")
                || type.startsWith("java.util.Queue") || type.startsWith("java.util.Collection");
    }

    private void err(Element element, String msg) {
        env.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
    }
}
