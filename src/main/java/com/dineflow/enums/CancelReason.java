package com.dineflow.enums;

public enum CancelReason implements LabeledEnum {
    WRONG_ENTRY("wrong_entry", "Nhập nhầm"),
    CUSTOMER_LEFT("customer_left", "Khách bỏ về"),
    OUT_OF_STOCK("out_of_stock", "Hết món"),
    OTHER("other", "Khác");

    private final String value;
    private final String label;

    CancelReason(String value, String label) {
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

    public static CancelReason of(String value) {
        return LabeledEnum.from(CancelReason.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends EnumConverter<CancelReason> {
        public Converter() {
            super(CancelReason.class);
        }
    }
}
