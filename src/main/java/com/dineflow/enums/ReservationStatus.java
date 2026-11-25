package com.dineflow.enums;

public enum ReservationStatus implements LabeledEnum {
    UPCOMING("upcoming", "Sắp tới"),
    COMPLETED("completed", "Đã hoàn thành"),
    CANCELLED("cancelled", "Đã hủy");

    private final String value;
    private final String label;

    ReservationStatus(String value, String label) {
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

    public static ReservationStatus of(String value) {
        return LabeledEnum.from(ReservationStatus.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends EnumConverter<ReservationStatus> {
        public Converter() {
            super(ReservationStatus.class);
        }
    }
}
