package com.dineflow.enums;

public enum UserStatus implements LabeledEnum {
    ACTIVE("active", "Hoạt động"),
    LOCKED("locked", "Đã khóa");

    private final String value;
    private final String label;

    UserStatus(String value, String label) {
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

    public static UserStatus of(String value) {
        return LabeledEnum.from(UserStatus.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends EnumConverter<UserStatus> {
        public Converter() {
            super(UserStatus.class);
        }
    }
}
