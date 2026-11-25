package com.dineflow.seed;

import com.dineflow.common.Times;
import com.dineflow.config.AppProperties;
import com.dineflow.entity.*;
import com.dineflow.enums.*;
import com.dineflow.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Dữ liệu mẫu (database/seeders/DatabaseSeeder.php). Mật khẩu mọi tài khoản mẫu: 123456.
 * Chỉ chạy khi bảng users còn trống (lần chạy đầu). Muốn làm lại: xóa database rồi chạy lại app.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    private final AppProperties props;
    private final UserRepository users;
    private final CategoryRepository categories;
    private final FoodRepository foods;
    private final DiningTableRepository tables;
    private final PromotionRepository promotions;
    private final OrderRepository orders;
    private final InvoiceRepository invoices;
    private final ReservationRepository reservations;
    private final BCryptPasswordEncoder encoder;

    private int orderSeq = 1000;

    @Override
    @Transactional
    public void run(String... args) {
        if (!props.isSeed() || users.count() > 0) return;
        log.info("Database trống → tạo dữ liệu mẫu DineFlow...");
        Map<String, User> u = seedUsers();
        Map<String, Category> c = seedCategories();
        Map<String, Food> f = seedFoods(c);
        Map<Integer, DiningTable> t = seedTables();
        seedPromotions();
        seedOrders(u, f, t);
        seedReservations(u, f, t);
        log.info("Xong dữ liệu mẫu. Đăng nhập: admin@dineflow.com / 123456");
    }

    private Map<String, User> seedUsers() {
        Object[][] rows = {
                {"admin", "Quản trị viên", "admin@dineflow.com", "0900000001", UserRole.ADMIN, null, 0, UserStatus.ACTIVE},
                {"staff", "Nguyễn Văn Order", "staff@dineflow.com", "0900000002", UserRole.ORDER_STAFF, null, 0, UserStatus.ACTIVE},
                {"cashier", "Trần Thị Thu Ngân", "cashier@dineflow.com", "0900000003", UserRole.CASHIER, null, 0, UserStatus.ACTIVE},
                {"customer", "Lê Minh Khách", "customer@dineflow.com", "0900000004", UserRole.CUSTOMER, "DF-MB0001", 320, UserStatus.ACTIVE},
                {"locked", "Phạm Bị Khóa", "locked@dineflow.com", "0900000005", UserRole.ORDER_STAFF, null, 0, UserStatus.LOCKED},
        };
        String hash = encoder.encode("123456");
        Map<String, User> result = new LinkedHashMap<>();
        for (Object[] r : rows) {
            User x = new User();
            x.setName((String) r[1]);
            x.setEmail((String) r[2]);
            x.setPhone((String) r[3]);
            x.setRole((UserRole) r[4]);
            x.setMemberCode((String) r[5]);
            x.setPoints((Integer) r[6]);
            x.setStatus((UserStatus) r[7]);
            x.setPassword(hash);
            result.put((String) r[0], users.save(x));
        }
        return result;
    }

    private Map<String, Category> seedCategories() {
        String[][] rows = {
                {"c1", "Cà phê", "coffee"}, {"c2", "Trà", "emoji_food_beverage"}, {"c3", "Nước ép", "local_bar"},
                {"c4", "Món chính", "restaurant"}, {"c5", "Khai vị", "tapas"}, {"c6", "Tráng miệng", "icecream"},
        };
        Map<String, Category> result = new LinkedHashMap<>();
        int order = 0;
        for (String[] r : rows) {
            Category c = new Category();
            c.setName(r[1]);
            c.setIcon(r[2]);
            c.setSortOrder(order++);
            result.put(r[0], categories.save(c));
        }
        return result;
    }

    private Map<String, Food> seedFoods(Map<String, Category> cats) {
        Object[][] sizes = {{"Size S", 0}, {"Size M", 8000}, {"Size L", 14000}};
        Object[][] toppings = {{"Trân châu", 6000}, {"Pudding", 7000}, {"Kem cheese", 10000}};
        // key, category, name, price, description, icon, color, rating, bestSeller, available, groups, image
        Object[][] rows = {
                {"f1", "c1", "Cà phê sữa đá", 29000, "Cà phê phin truyền thống pha cùng sữa đặc, đá viên mát lạnh.", "coffee", "#8B5A2B", 4.8, true, true, "size", "ca_phe_sua_da"},
                {"f2", "c1", "Bạc xỉu", 32000, "Vị cà phê nhẹ nhàng hòa quyện cùng sữa tươi béo ngậy.", "coffee", "#B08968", 4.6, false, true, "size", "bac_xiu"},
                {"f3", "c1", "Espresso", 35000, "Cà phê nguyên chất đậm đà, chiết xuất từ máy espresso.", "local_cafe", "#5C3A21", 4.4, false, true, "", "espresso"},
                {"f4", "c2", "Trà đào cam sả", 39000, "Trà đào thơm mát kết hợp cam tươi và sả.", "emoji_food_beverage", "#E8A22C", 4.7, true, true, "size,topping", "tra_dao_cam_sa"},
                {"f5", "c2", "Trà sữa trân châu", 35000, "Trà sữa béo thơm cùng trân châu đường đen dẻo dai.", "emoji_food_beverage", "#C08552", 4.9, true, true, "size,topping", "tra_sua_tran_chau"},
                {"f6", "c2", "Trà chanh", 25000, "Trà chanh mật ong giải khát, chua ngọt hài hòa.", "emoji_food_beverage", "#A8C66C", 4.3, false, true, "", "tra_chanh"},
                {"f7", "c3", "Nước ép cam", 35000, "Cam tươi ép nguyên chất, giàu vitamin C.", "local_bar", "#F2994A", 4.5, false, true, "", "nuoc_cam"},
                {"f8", "c3", "Sinh tố bơ", 42000, "Bơ sáp xay cùng sữa đặc, sánh mịn béo ngậy.", "local_bar", "#6B8E23", 4.6, false, true, "", "sinh_to_bo"},
                {"f9", "c4", "Cơm sườn nướng", 55000, "Cơm tấm sườn nướng mật ong ăn kèm đồ chua, trứng ốp la.", "rice_bowl", "#D8433D", 4.7, true, true, "", "com_suon_nuong"},
                {"f10", "c4", "Mì Ý sốt bò bằm", 65000, "Mì Ý sốt cà chua bò bằm đậm đà, phô mai bào.", "dinner_dining", "#C94D1C", 4.5, false, true, "", "mi_y_sot_bo_bam"},
                {"f11", "c5", "Khoai tây chiên", 32000, "Khoai tây chiên giòn rụm, ăn kèm sốt phô mai.", "tapas", "#E8A22C", 4.4, false, true, "", "khoai_tay_chien"},
                {"f12", "c5", "Gà cánh chiên nước mắm", 48000, "Cánh gà chiên giòn áo sốt nước mắm tỏi ớt.", "tapas", "#E8622C", 4.8, true, true, "", "ga_canh_chien_nuoc_mam"},
                {"f13", "c6", "Bánh flan", 22000, "Bánh flan caramel mềm mịn, thơm vị trứng sữa.", "icecream", "#F2C94C", 4.6, false, true, "", "banh_flan"},
                {"f14", "c6", "Tiramisu", 38000, "Bánh tiramisu vị cà phê nguyên bản kiểu Ý.", "icecream", "#6B4423", 4.7, false, false, "", "tiramisu"},
        };
        Map<String, Food> result = new LinkedHashMap<>();
        for (Object[] r : rows) {
            Food food = new Food();
            food.setCategoryId(cats.get((String) r[1]).getId());
            food.setName((String) r[2]);
            food.setPrice(BigDecimal.valueOf((Integer) r[3]));
            food.setDescription((String) r[4]);
            food.setIcon((String) r[5]);
            food.setColor((String) r[6]);
            food.setRating(BigDecimal.valueOf((Double) r[7]));
            food.setIsBestSeller((Boolean) r[8]);
            food.setIsAvailable((Boolean) r[9]);
            // Ảnh nằm trong src/main/resources/static/storage/foods → URL /storage/foods/<tên>.jpg
            food.setImage("foods/" + r[11] + ".jpg");
            for (String group : ((String) r[10]).split(",")) {
                if (group.isBlank()) continue;
                for (Object[] o : group.equals("size") ? sizes : toppings) {
                    FoodOption opt = new FoodOption();
                    opt.setFood(food);
                    opt.setName((String) o[0]);
                    opt.setGroup(FoodOptionGroup.of(group));
                    opt.setPriceModifier(BigDecimal.valueOf((Integer) o[1]));
                    food.getOptions().add(opt);
                }
            }
            result.put((String) r[0], foods.save(food));
        }
        return result;
    }

    private Map<Integer, DiningTable> seedTables() {
        Object[][] rows = {
                {1, "Tầng 1", 4}, {2, "Tầng 1", 4}, {3, "Tầng 1", 6}, {4, "Tầng 1", 4}, {5, "Tầng 1", 4}, {6, "Tầng 1", 4},
                {7, "Tầng 2", 4}, {8, "Tầng 2", 4}, {9, "Tầng 2", 2}, {10, "Tầng 2", 4},
                {11, "Ngoài trời", 4}, {12, "Ngoài trời", 6},
        };
        Map<Integer, DiningTable> result = new LinkedHashMap<>();
        for (Object[] r : rows) {
            DiningTable t = new DiningTable();
            t.setNumber("Bàn " + r[0]);
            t.setArea((String) r[1]);
            t.setCapacity((Integer) r[2]);
            t.setStatus(TableStatus.EMPTY);
            result.put((Integer) r[0], tables.save(t));
        }
        return result;
    }

    private void seedPromotions() {
        Instant now = Instant.now();
        Object[][] rows = {
                {"WELCOME10", "Chào bạn mới", "Giảm 10% cho đơn đầu tiên.", 10, 30000, 0, now.minus(30, ChronoUnit.DAYS), now.plus(90, ChronoUnit.DAYS)},
                {"COMBO15", "Combo bạn bè", "Giảm 15% cho đơn từ 200.000đ.", 15, 50000, 200000, now.minus(7, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS)},
                {"SUMMER20", "Hè rực rỡ", "Giảm 20% mùa hè, tối đa 60.000đ.", 20, 60000, 100000, now.plus(7, ChronoUnit.DAYS), now.plus(60, ChronoUnit.DAYS)},
                {"TET2025", "Khuyến mãi cũ", "Chương trình đã kết thúc.", 25, 80000, 0, now.minus(240, ChronoUnit.DAYS), now.minus(180, ChronoUnit.DAYS)},
        };
        for (Object[] r : rows) {
            Promotion p = new Promotion();
            p.setCode((String) r[0]);
            p.setTitle((String) r[1]);
            p.setDescription((String) r[2]);
            p.setDiscountPercent((Integer) r[3]);
            p.setMaxDiscount(BigDecimal.valueOf((Integer) r[4]));
            p.setMinOrderAmount(BigDecimal.valueOf((Integer) r[5]));
            p.setValidFrom((Instant) r[6]);
            p.setValidTo((Instant) r[7]);
            p.setIsActive(true);
            promotions.save(p);
        }
    }

    /** Một dòng món mẫu: [foodKey, số lượng, tên các option..., (note)] */
    private record Line(String food, int qty, List<String> options, String note) {
        static Line of(String food, int qty, String... options) {
            return new Line(food, qty, List.of(options), null);
        }
    }

    private List<Map<String, Object>> snapshots(Food food, List<String> optionNames) {
        return food.getOptions().stream().filter(o -> optionNames.contains(o.getName())).map(FoodOption::toSnapshot).toList();
    }

    private Order makeOrder(Order o, List<Line> lines, Map<String, Food> foodsByKey) {
        o.setCode(String.format("OD%s-%d", java.time.format.DateTimeFormatter.ofPattern("yyMMdd").format(LocalDate.now(Times.UTC)), orderSeq++));
        for (Line l : lines) {
            Food food = foodsByKey.get(l.food());
            OrderItem item = new OrderItem();
            item.setFoodId(food.getId());
            item.setFoodName(food.getName());
            item.setQuantity(l.qty());
            item.setOptions(new ArrayList<>(snapshots(food, l.options())));
            item.setPrice(food.getPrice());
            item.setNote(l.note());
            o.addItem(item);
        }
        o.recalculateTotal();
        return orders.save(o);
    }

    private void occupy(DiningTable t, Order o, Instant since) {
        t.setStatus(TableStatus.SERVING);
        t.setServingSince(since);
        t.setCurrentOrderId(o.getId());
        tables.save(t);
    }

    private void seedOrders(Map<String, User> u, Map<String, Food> f, Map<Integer, DiningTable> t) {
        Instant now = Instant.now();

        // 1) Bàn 2 đang phục vụ, chưa thanh toán
        Order o1 = order(t.get(2), u.get("staff"), null, OrderStatus.SERVING, PaymentStatus.UNPAID, null, now.minus(45, ChronoUnit.MINUTES));
        makeOrder(o1, List.of(Line.of("f1", 2, "Size M"), Line.of("f12", 1), Line.of("f11", 1)), f);
        occupy(t.get(2), o1, o1.getCreatedAt());

        // 2) Bàn 5 vừa gọi món, đang chuẩn bị
        Order o2 = order(t.get(5), u.get("staff"), null, OrderStatus.PREPARING, PaymentStatus.UNPAID, "Ít đá", now.minus(10, ChronoUnit.MINUTES));
        makeOrder(o2, List.of(Line.of("f5", 2, "Size L", "Trân châu"), new Line("f9", 2, List.of(), "Không hành")), f);
        occupy(t.get(5), o2, o2.getCreatedAt());

        // 3) Bàn 8 vừa đặt, chờ bếp
        Order o3 = order(t.get(8), u.get("staff"), null, OrderStatus.PLACED, PaymentStatus.UNPAID, null, now.minus(2, ChronoUnit.MINUTES));
        makeOrder(o3, List.of(Line.of("f10", 1), Line.of("f7", 1)), f);
        occupy(t.get(8), o3, o3.getCreatedAt());

        // 4) Khách tự đặt (không gắn bàn), chờ xác nhận thanh toán
        Order o4 = order(null, null, u.get("customer"), OrderStatus.PLACED, PaymentStatus.PENDING, null, now.minus(5, ChronoUnit.MINUTES));
        makeOrder(o4, List.of(Line.of("f4", 1, "Size M", "Pudding")), f);

        // Đơn đã thanh toán trong 7 ngày gần nhất → dữ liệu cho dashboard
        List<List<Line>> paidPlans = List.of(
                List.of(Line.of("f1", 2, "Size M"), Line.of("f9", 1)),
                List.of(Line.of("f5", 3, "Size L"), Line.of("f12", 2), Line.of("f13", 2)),
                List.of(Line.of("f9", 4), Line.of("f10", 2)),
                List.of(Line.of("f4", 2, "Size M", "Trân châu"), Line.of("f11", 2)),
                List.of(Line.of("f1", 5, "Size S"), Line.of("f8", 2)),
                List.of(Line.of("f10", 3), Line.of("f12", 3), Line.of("f7", 3)),
                List.of(Line.of("f9", 2), Line.of("f5", 2, "Size M")));
        PaymentMethod[] methods = {PaymentMethod.CASH, PaymentMethod.QR, PaymentMethod.CARD};
        for (int daysAgo = 0; daysAgo < paidPlans.size(); daysAgo++) {
            // 12:30 trưa theo giờ quán
            Instant paidAt = Times.today().minusDays(daysAgo).atTime(LocalTime.of(12, 30)).atZone(Times.tz()).toInstant();
            if (paidAt.isAfter(now)) paidAt = now.minus(1, ChronoUnit.MINUTES);
            Order o = order(t.get((daysAgo % 6) + 1), u.get("staff"), null, OrderStatus.COMPLETED, PaymentStatus.PAID, null,
                    paidAt.minus(50, ChronoUnit.MINUTES));
            makeOrder(o, paidPlans.get(daysAgo), f);
            Invoice inv = new Invoice();
            inv.setOrder(o);
            inv.setCashier(u.get("cashier"));
            inv.setSubtotal(o.getTotalAmount());
            inv.setDiscount(BigDecimal.ZERO);
            inv.setTotal(o.getTotalAmount());
            inv.setPaymentMethod(methods[daysAgo % 3]);
            inv.setStatus(InvoiceStatus.PAID);
            inv.setPaidAt(paidAt);
            invoices.save(inv);
        }
    }

    private Order order(DiningTable table, User staff, User customer, OrderStatus status, PaymentStatus pay, String note, Instant createdAt) {
        Order o = new Order();
        o.setTable(table);
        o.setStaff(staff);
        o.setCustomer(customer);
        o.setStatus(status);
        o.setPaymentStatus(pay);
        o.setNote(note);
        o.setCreatedAt(createdAt);
        return o;
    }

    private Map<String, Object> preOrderLine(Food food, int qty) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("food_id", food.getId());
        m.put("food_name", food.getName());
        m.put("quantity", qty);
        m.put("price", food.getPrice().doubleValue());
        m.put("options", List.of());
        m.put("note", null);
        return m;
    }

    private void seedReservations(Map<String, User> u, Map<String, Food> f, Map<Integer, DiningTable> t) {
        LocalDate today = Times.today();
        // Sắp tới: ghép Bàn 3 + Bàn 4 (Tầng 1), có đặt món trước — 19:00 ngày mai giờ quán
        reservation("DF-1001", u.get("customer"), List.of(t.get(3).getId(), t.get(4).getId()), "Tầng 1",
                today.plusDays(1).atTime(19, 0).atZone(Times.tz()).toInstant(), 8,
                "Sinh nhật bạn, cần bàn gần cửa sổ.", ReservationStatus.UPCOMING,
                new ArrayList<>(List.of(preOrderLine(f.get("f9"), 4), preOrderLine(f.get("f13"), 2))), PaymentMethod.CASH);
        // Sắp tới trong vài giờ: Bàn 9
        reservation("DF-1002", u.get("customer"), List.of(t.get(9).getId()), "Tầng 2",
                Instant.now().plus(3, ChronoUnit.HOURS), 2, null, ReservationStatus.UPCOMING, null, null);
        // Đã hoàn thành và đã hủy
        reservation("DF-1003", u.get("customer"), List.of(t.get(7).getId()), "Tầng 2",
                today.minusDays(5).atTime(18, 30).atZone(Times.tz()).toInstant(), 3, null, ReservationStatus.COMPLETED, null, null);
        reservation("DF-1004", u.get("customer"), List.of(t.get(10).getId()), "Tầng 2",
                today.minusDays(2).atTime(20, 0).atZone(Times.tz()).toInstant(), 2, "Khách báo bận.", ReservationStatus.CANCELLED, null, null);
    }

    private void reservation(String code, User customer, List<Long> tableIds, String area, Instant at, int guests, String note,
                             ReservationStatus status, List<Map<String, Object>> items, PaymentMethod payment) {
        Reservation r = new Reservation();
        r.setCode(code);
        r.setCustomer(customer);
        r.setTableIds(new ArrayList<>(tableIds));
        r.setArea(area);
        r.setDateTime(at);
        r.setGuestCount(guests);
        r.setNote(note);
        r.setStatus(status);
        r.setPreOrderItems(items);
        r.setPreOrderPayment(payment);
        reservations.save(r);
    }
}
