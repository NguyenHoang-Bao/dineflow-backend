package com.dineflow.enums;

public enum FoodOptionGroup implements LabeledEnum {
    SIZE("size", "Kích cỡ"),
    TOPPING("topping", "Topping");

    private final String value;
    private final String label;

    FoodOptionGroup(String value, String label) {
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

    public static FoodOptionGroup of(String value) {
        return LabeledEnum.from(FoodOptionGroup.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends EnumConverter<FoodOptionGroup> {
        public Converter() {
            super(FoodOptionGroup.class);
        }
    }
}
