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
                md.update((m.packageName() + "." + m.simpleName()).getBytes());
                for (Binding b : m.bindings()) {
                    md.update(b.path().getBytes());
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 ikke tilgjengelig", e);
        }
    }
}
