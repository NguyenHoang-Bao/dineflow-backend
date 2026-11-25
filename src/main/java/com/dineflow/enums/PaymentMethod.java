package com.dineflow.enums;

public enum PaymentMethod implements LabeledEnum {
    CASH("cash", "Tiền mặt"),
    QR("qr", "QR / Chuyển khoản"),
    CARD("card", "Thẻ ngân hàng");

    private final String value;
    private final String label;

    PaymentMethod(String value, String label) {
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

    public static PaymentMethod of(String value) {
        return LabeledEnum.from(PaymentMethod.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends EnumConverter<PaymentMethod> {
        public Converter() {
            super(PaymentMethod.class);
        }
    }
}
