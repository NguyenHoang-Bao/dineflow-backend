package com.dineflow.enums;

public enum OrderStatus implements LabeledEnum {
    PLACED("placed", "Đã đặt"),
    PREPARING("preparing", "Đang chuẩn bị"),
    SERVING("serving", "Đang phục vụ"),
    COMPLETED("completed", "Hoàn thành"),
    CANCELLED("cancelled", "Đã hủy");

    private final String value;
    private final String label;

    OrderStatus(String value, String label) {
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

    public static OrderStatus of(String value) {
        return LabeledEnum.from(OrderStatus.class, value);
    }

    /** Đơn còn đang mở (chưa hoàn thành / chưa hủy). */
    public boolean isOpen() {
        return this == PLACED || this == PREPARING || this == SERVING;
    }

    /**
     * Đơn chỉ đi tiến placed → preparing → serving → completed (được bỏ qua bước),
     * và chỉ hủy được khi đơn còn đang mở. Đơn đã hoàn thành / đã hủy thì khóa.
     */
    public boolean canTransitionTo(OrderStatus to) {
        if (!isOpen()) return false;
        if (to == CANCELLED) return true;
        java.util.List<OrderStatus> flow = java.util.List.of(PLACED, PREPARING, SERVING, COMPLETED);
        return flow.indexOf(to) > flow.indexOf(this);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends EnumConverter<OrderStatus> {
        public Converter() {
            super(OrderStatus.class);
        }
    }
}
