package com.dineflow.enums;

public enum UserRole implements LabeledEnum {
    ADMIN("admin", "Admin"),
    ORDER_STAFF("order_staff", "Nhân viên Order"),
    CASHIER("cashier", "Thu ngân"),
    CUSTOMER("customer", "Khách hàng");

    private final String value;
    private final String label;

    UserRole(String value, String label) {
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

    public static UserRole of(String value) {
        return LabeledEnum.from(UserRole.class, value);
    }

    /** Nhân viên nội bộ (không phải khách hàng). */
    public boolean isStaff() {
        return this != CUSTOMER;
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends EnumConverter<UserRole> {
        public Converter() {
            super(UserRole.class);
        }
    }
}
