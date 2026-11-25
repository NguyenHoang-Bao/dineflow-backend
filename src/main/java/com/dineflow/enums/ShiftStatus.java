package com.dineflow.enums;

public enum ShiftStatus implements LabeledEnum {
    OPEN("open", "Đang mở"),
    CLOSED("closed", "Đã đóng");

    private final String value;
    private final String label;

    ShiftStatus(String value, String label) {
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

    public static ShiftStatus of(String value) {
        return LabeledEnum.from(ShiftStatus.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends EnumConverter<ShiftStatus> {
        public Converter() {
            super(ShiftStatus.class);
        }
    }
}
