package com.dineflow.enums;

public enum PaymentStatus implements LabeledEnum {
    UNPAID("unpaid", "Chưa thanh toán"),
    PENDING("pending", "Chờ xác nhận"),
    PAID("paid", "Đã thanh toán");

    private final String value;
    private final String label;

    PaymentStatus(String value, String label) {
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

    public static PaymentStatus of(String value) {
        return LabeledEnum.from(PaymentStatus.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends EnumConverter<PaymentStatus> {
        public Converter() {
            super(PaymentStatus.class);
        }
    }
}
