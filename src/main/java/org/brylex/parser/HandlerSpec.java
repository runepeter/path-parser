package org.brylex.parser;

import org.brylex.parser.annotation.Path;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Cached, per-handler-class metadata. Reflection og path-splitting gjores
 * en gang per Class og deles av alle parser-instanser. Hver applikasjon mot
 * en parser allokerer fortsatt invokere bundet til det konkrete handler-
 * objektet, men slipper a re-reflektere og re-splitte paths.
 */
final class HandlerSpec {

    private final Member[] members;

    private HandlerSpec(Member[] members) {
        this.members = members;
    }

    static HandlerSpec compile(Class<?> handlerClass) {
        List<Member> list = new ArrayList<>();
        for (Method method : handlerClass.getDeclaredMethods()) {
            Path path = method.getAnnotation(Path.class);
            if (path != null) {
                list.add(new Member(method, null, path.value()));
            }
        }
        for (Field field : handlerClass.getDeclaredFields()) {
            Path path = field.getAnnotation(Path.class);
            if (path != null) {
                list.add(new Member(null, field, path.value()));
            }
        }
        return new HandlerSpec(list.toArray(new Member[0]));
    }

    void apply(PathParser parser, Object handler) {
        for (Member member : members) {
            if (member.method != null) {
                parser.applyMethod(member.method, member.path, handler);
            } else {
                parser.applyField(member.field, member.path, handler);
            }
        }
    }

    private record Member(Method method, Field field, String path) {
    }
}
