package com.dineflow.enums;

public enum TableStatus implements LabeledEnum {
    EMPTY("empty", "Trống"),
    SERVING("serving", "Đang phục vụ"),
    RESERVED("reserved", "Đã đặt trước");

    private final String value;
    private final String label;

    TableStatus(String value, String label) {
        this.value = value;
        this.label = label;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public String label() {
        return label;
    }

    public static TableStatus of(String value) {
        return LabeledEnum.from(TableStatus.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends EnumConverter<TableStatus> {
        public Converter() {
            super(TableStatus.class);
        }
    }
}
