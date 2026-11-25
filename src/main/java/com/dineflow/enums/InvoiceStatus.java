package com.dineflow.enums;

public enum InvoiceStatus implements LabeledEnum {
    UNPAID("unpaid", "Chưa thanh toán"),
    PAID("paid", "Đã thanh toán");

    private final String value;
    private final String label;

    InvoiceStatus(String value, String label) {
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

    public static InvoiceStatus of(String value) {
        return LabeledEnum.from(InvoiceStatus.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends EnumConverter<InvoiceStatus> {
        public Converter() {
            super(InvoiceStatus.class);
        }
    }
}
