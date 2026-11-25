package com.dineflow.common;

import com.dineflow.enums.LabeledEnum;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Đọc + kiểm tra dữ liệu request, thay cho $request->validate([...]) của Laravel.
 *
 * Cách dùng:
 * <pre>
 *   Input in = Input.of(body);
 *   in.field("email").required("Vui lòng nhập email.").email().max(255);
 *   in.validate();               // có lỗi thì ném ApiException 422 { message, errors }
 *   String email = in.str("email");
 * </pre>
 * Mỗi field dừng kiểm tra ở lỗi đầu tiên. Field không gửi (hoặc null) mà không có required()
 * thì các rule sau được bỏ qua, giống "nullable" của Laravel.
 */
public class Input {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final Map<String, Object> data;
    private final Map<String, List<String>> errors = new LinkedHashMap<>();

    private Input(Map<String, Object> data) {
        this.data = data == null ? new LinkedHashMap<>() : data;
    }

    public static Input of(Map<String, Object> body) {
        return new Input(body);
    }

    /** Từ query string (?date=...&mine=1). */
    public static Input ofQuery(Map<String, String> params) {
        return new Input(new LinkedHashMap<>(params));
    }

    // ------------------------------------------------------------------ đọc giá trị

    public Map<String, Object> raw() {
        return data;
    }

    /** Có gửi key này không (kể cả null) — $request->has(). */
    public boolean has(String key) {
        return data.containsKey(key);
    }

    /** Có giá trị khác rỗng — $request->filled(). */
    public boolean filled(String key) {
        return isFilled(data.get(key));
    }

    public Object get(String key) {
        return data.get(key);
    }

    public String str(String key) {
        Object v = data.get(key);
        if (v == null) return null;
        if (v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) return String.valueOf(n.longValue());
        return v.toString();
    }

    public Long lng(String key) {
        return toLong(data.get(key));
    }

    public Integer integer(String key) {
        Long l = lng(key);
        return l == null ? null : l.intValue();
    }

    public Double num(String key) {
        return toDouble(data.get(key));
    }

    /** $request->boolean(): "1", "true", "on", "yes", true → true. */
    public boolean bool(String key) {
        Object v = data.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() == 1;
        if (v == null) return false;
        String s = v.toString().trim().toLowerCase();
        return s.equals("1") || s.equals("true") || s.equals("on") || s.equals("yes");
    }

    /** Boolean có thể null (chỉ đọc khi có gửi). */
    public Boolean boolOrNull(String key) {
        return has(key) && data.get(key) != null ? bool(key) : null;
    }

    @SuppressWarnings("unchecked")
    public List<Object> list(String key) {
        Object v = data.get(key);
        return v instanceof List<?> l ? (List<Object>) l : null;
    }

    public Instant instant(String key) {
        return Times.parse(str(key));
    }

    // ------------------------------------------------------------------ kiểm tra

    public Field field(String key) {
        return new Field(key, data.get(key));
    }

    /** Field con trong mảng, vd items.0.food_id. */
    public Field field(String key, Object value) {
        return new Field(key, value);
    }

    public void error(String key, String message) {
        errors.computeIfAbsent(key, k -> new ArrayList<>()).add(message);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /** Ném lỗi 422 nếu có lỗi (message = lỗi đầu tiên, giống Laravel). */
    public void validate() {
        if (!errors.isEmpty()) {
            String first = errors.values().iterator().next().get(0);
            throw new ApiException(422, first, errors);
        }
    }

    public class Field {
        private final String key;
        private final Object value;
        private boolean stop;

        Field(String key, Object value) {
            this.key = key;
            this.value = value;
            // nullable: không gửi thì bỏ qua các rule (trừ required)
            this.stop = false;
        }

        private boolean skip() {
            return stop || !isFilled(value);
        }

        private Field fail(String message) {
            error(key, message);
            stop = true;
            return this;
        }

        public Field required() {
            return required("Vui lòng nhập " + key + ".");
        }

        public Field required(String message) {
            if (!stop && !isFilled(value)) fail(message);
            return this;
        }

        /** required_without: bắt buộc khi field kia không có. */
        public Field requiredWithout(String other, String message) {
            if (!stop && !isFilled(value) && !filled(other)) fail(message);
            return this;
        }

        /** required_with / required_if: bắt buộc khi điều kiện đúng. */
        public Field requiredIf(boolean condition, String message) {
            if (!stop && condition && !isFilled(value)) fail(message);
            return this;
        }

        public Field string() {
            if (!skip() && !(value instanceof String)) fail(key + " phải là chuỗi.");
            return this;
        }

        public Field max(int length) {
            if (!skip() && value instanceof String s && s.length() > length)
                fail(key + " tối đa " + length + " ký tự.");
            return this;
        }

        public Field max(int length, String message) {
            if (!skip() && value instanceof String s && s.length() > length) fail(message);
            return this;
        }

        public Field min(int length, String message) {
            if (!skip() && value instanceof String s && s.length() < length) fail(message);
            return this;
        }

        public Field email() {
            return email(key + " không đúng định dạng email.");
        }

        public Field email(String message) {
            if (!skip() && !(value instanceof String s && EMAIL.matcher(s).matches())) fail(message);
            return this;
        }

        public Field url() {
            if (!skip() && !(value instanceof String s && (s.startsWith("http://") || s.startsWith("https://"))))
                fail(key + " phải là URL hợp lệ.");
            return this;
        }

        public Field integer() {
            if (!skip() && toLong(value) == null) fail(key + " phải là số nguyên.");
            return this;
        }

        public Field intBetween(long min, long max) {
            if (skip()) return this;
            Long l = toLong(value);
            if (l == null) return fail(key + " phải là số nguyên.");
            if (l < min || l > max) fail(key + " phải từ " + min + " đến " + max + ".");
            return this;
        }

        public Field numeric() {
            if (!skip() && toDouble(value) == null) fail(key + " phải là số.");
            return this;
        }

        public Field numMin(double min) {
            if (skip()) return this;
            Double d = toDouble(value);
            if (d == null) return fail(key + " phải là số.");
            if (d < min) fail(key + " phải lớn hơn hoặc bằng " + (long) min + ".");
            return this;
        }

        public Field bool() {
            if (skip()) return this;
            boolean ok = value instanceof Boolean
                    || (value instanceof Number n && (n.intValue() == 0 || n.intValue() == 1))
                    || (value instanceof String s && List.of("0", "1", "true", "false").contains(s));
            if (!ok) fail(key + " phải là true hoặc false.");
            return this;
        }

        public Field in(Collection<String> allowed, String message) {
            if (!skip() && !allowed.contains(String.valueOf(value))) fail(message);
            return this;
        }

        public <E extends Enum<E> & LabeledEnum> Field enumOf(Class<E> type) {
            return enumOf(type, key + " không hợp lệ.");
        }

        public <E extends Enum<E> & LabeledEnum> Field enumOf(Class<E> type, String message) {
            if (!skip() && LabeledEnum.from(type, String.valueOf(value)) == null) fail(message);
            return this;
        }

        public Field date() {
            if (!skip() && Times.parse(String.valueOf(value)) == null) fail(key + " không phải ngày giờ hợp lệ.");
            return this;
        }

        public Field dateFormatYmd() {
            if (!skip() && Times.parseDate(String.valueOf(value)) == null)
                fail(key + " phải có dạng YYYY-MM-DD.");
            return this;
        }

        public Field after(Instant moment, String message) {
            if (skip()) return this;
            Instant v = Times.parse(String.valueOf(value));
            if (v == null) return fail(key + " không phải ngày giờ hợp lệ.");
            if (!v.isAfter(moment)) fail(message);
            return this;
        }

        public Field afterOrEqualDate(LocalDate other, String message) {
            if (skip() || other == null) return this;
            LocalDate v = Times.parseDate(String.valueOf(value));
            if (v != null && v.isBefore(other)) fail(message);
            return this;
        }

        public Field digits(int count, String message) {
            if (!skip() && !(String.valueOf(value).matches("\\d{" + count + "}"))) fail(message);
            return this;
        }

        public Field array() {
            if (!skip() && !(value instanceof List<?>)) fail(key + " phải là mảng.");
            return this;
        }

        public Field arrayBetween(int min, int max) {
            if (skip()) return this;
            if (!(value instanceof List<?> l)) return fail(key + " phải là mảng.");
            if (l.size() < min) return fail(key + " phải có ít nhất " + min + " phần tử.");
            if (l.size() > max) fail(key + " tối đa " + max + " phần tử.");
            return this;
        }

        /** confirmed: field "password" phải trùng "password_confirmation". */
        public Field confirmed(String message) {
            if (!skip() && !Objects.equals(value, data.get(key + "_confirmation"))) fail(message);
            return this;
        }

        public Field different(String otherKey, String message) {
            if (!skip() && Objects.equals(value, data.get(otherKey))) fail(message);
            return this;
        }

        /** Rule tùy ý (vd exists, unique): condition=false thì báo lỗi. */
        public Field check(boolean condition, String message) {
            if (!skip() && !condition) fail(message);
            return this;
        }

        /** Như check nhưng chỉ tính điều kiện khi field có giá trị và chưa lỗi. */
        public Field check(java.util.function.Predicate<Object> condition, String message) {
            if (!skip() && !condition.test(value)) fail(message);
            return this;
        }

        public boolean ok() {
            return !stop;
        }
    }

    // ------------------------------------------------------------------ tiện ích

    public static boolean isFilled(Object v) {
        if (v == null) return false;
        if (v instanceof String s) return !s.isBlank();
        if (v instanceof Collection<?> c) return !c.isEmpty();
        if (v instanceof Map<?, ?> m) return !m.isEmpty();
        return true;
    }

    public static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Integer || v instanceof Long || v instanceof Short) return ((Number) v).longValue();
        if (v instanceof Number n) {
            double d = n.doubleValue();
            return d == Math.rint(d) ? (long) d : null;
        }
        if (v instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static Double toDouble(Object v) {
        if (v == null || v instanceof Boolean) return null;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
