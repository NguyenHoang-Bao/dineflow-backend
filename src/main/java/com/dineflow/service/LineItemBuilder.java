package com.dineflow.service;

import com.dineflow.common.ApiException;
import com.dineflow.common.Input;
import com.dineflow.entity.Food;
import com.dineflow.entity.FoodOption;
import com.dineflow.enums.FoodOptionGroup;
import com.dineflow.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Dựng các dòng món (đơn hàng / món đặt trước) từ dữ liệu client gửi lên.
 * Client chỉ gửi food_id, quantity, option_ids. Tên món, đơn giá và phụ thu
 * luôn lấy từ database, không bao giờ tin giá do client gửi.
 */
@Service
@RequiredArgsConstructor
public class LineItemBuilder {
    private final FoodRepository foods;

    /**
     * Kiểm tra mảng món trong request (LineItemBuilder::rules của PHP).
     *
     * @param required true = bắt buộc có ít nhất 1 món
     */
    public void validate(Input in, String field, boolean required) {
        Input.Field f = in.field(field);
        if (required) f.required("Vui lòng chọn ít nhất một món.");
        f.array();
        if (!f.ok() || !(in.get(field) instanceof List<?> list)) return;
        for (int i = 0; i < list.size(); i++) {
            String p = field + "." + i + ".";
            Object o = list.get(i);
            Map<?, ?> item = o instanceof Map<?, ?> m ? m : Map.of();
            in.field(p + "food_id", item.get("food_id")).required("Vui lòng chọn món.").integer()
                    .check(v -> foods.existsById(Input.toLong(v)), "Món không tồn tại.");
            in.field(p + "quantity", item.get("quantity")).required("Vui lòng nhập số lượng.").intBetween(1, 99);
            in.field(p + "option_ids", item.get("option_ids")).array();
            if (item.get("option_ids") instanceof List<?> ids) {
                for (int j = 0; j < ids.size(); j++) in.field(p + "option_ids." + j, ids.get(j)).integer();
            }
            in.field(p + "note", item.get("note")).string().max(255);
        }
    }

    /** Dựng danh sách dòng món: [{food_id, food_name, quantity, price, options, note}] */
    public List<Map<String, Object>> build(List<Object> items, String field) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Map<?, ?> item = (Map<?, ?>) items.get(i);
            Long foodId = Input.toLong(item.get("food_id"));
            Food food = foods.findById(foodId).orElse(null);
            if (food == null) throw ApiException.validation(field + "." + i + ".food_id", "Món không tồn tại.");
            if (!food.getIsAvailable())
                throw ApiException.validation(field + "." + i + ".food_id", "Món \"" + food.getName() + "\" hiện không phục vụ.");

            LinkedHashSet<Long> optionIds = new LinkedHashSet<>();
            if (item.get("option_ids") instanceof List<?> ids) for (Object id : ids) optionIds.add(Input.toLong(id));
            List<FoodOption> options = food.getOptions().stream().filter(o -> optionIds.contains(o.getId())).toList();
            if (options.size() != optionIds.size())
                throw ApiException.validation(field + "." + i + ".option_ids", "Tùy chọn không hợp lệ cho món \"" + food.getName() + "\".");
            if (options.stream().filter(o -> o.getGroup() == FoodOptionGroup.SIZE).count() > 1)
                throw ApiException.validation(field + "." + i + ".option_ids", "Món \"" + food.getName() + "\" chỉ được chọn một size.");

            Map<String, Object> line = new LinkedHashMap<>();
            line.put("food_id", food.getId());
            line.put("food_name", food.getName());
            line.put("quantity", Input.toLong(item.get("quantity")).intValue());
            line.put("price", food.getPrice().doubleValue());
            line.put("options", options.stream().map(FoodOption::toSnapshot).toList());
            line.put("note", item.get("note"));
            lines.add(line);
        }
        return lines;
    }
}
