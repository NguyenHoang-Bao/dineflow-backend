package com.dineflow.enums;

import jakarta.persistence.AttributeConverter;

/** Lưu enum xuống DB bằng chuỗi value (vd 'order_staff'), giống cast enum của Laravel. */
public abstract class EnumConverter<E extends Enum<E> & LabeledEnum> implements AttributeConverter<E, String> {
    private final Class<E> type;

    protected EnumConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        return LabeledEnum.from(type, dbData);
    }
}
