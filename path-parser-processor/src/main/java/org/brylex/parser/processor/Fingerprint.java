package org.brylex.parser.processor;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class Fingerprint {
    private Fingerprint() {}

    public static String over(List<HandlerModel> models) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (HandlerModel m : models) {
                md.update((m.packageName() + "." + m.simpleName() + "\n").getBytes());
                for (Binding b : m.bindings()) {
                    md.update((b.path() + "|" + b.getClass().getSimpleName() + "\n").getBytes());
                    if (b instanceof Binding.FieldText ft) {
                        md.update((ft.fieldName() + "|" + ft.fieldType()).getBytes());
                    }
                    if (b instanceof Binding.MethodText mt) {
                        md.update((mt.methodName() + "|" + mt.paramType()).getBytes());
                    }
                    if (b instanceof Binding.MethodEvent me) {
                        md.update((me.methodName() + "|" + me.eventKind()).getBytes());
                    }
                    if (b instanceof Binding.Collection c) {
                        md.update((c.fieldName() + "|" + c.elementType()).getBytes());
                    }
                    if (b instanceof Binding.SubHandler sh) {
                        md.update((sh.targetName() + "|" + sh.subHandlerType()).getBytes());
                    }
                    if (b instanceof Binding.Attribute a) {
                        md.update((a.fieldName() + "|" + a.attrName()).getBytes());
                    }
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 ikke tilgjengelig", e);
        }
    }
}
