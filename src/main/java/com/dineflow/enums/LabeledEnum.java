package com.dineflow.enums;

/** Enum có giá trị lưu DB (value) và nhãn tiếng Việt (label), giống enum PHP có getLabel(). */
public interface LabeledEnum {
    String value();

    String label();

    static <E extends Enum<E> & LabeledEnum> E from(Class<E> type, String value) {
        if (value == null) return null;
        for (E e : type.getEnumConstants()) {
            if (e.value().equals(value)) return e;
        }
        return null;
    }
}
