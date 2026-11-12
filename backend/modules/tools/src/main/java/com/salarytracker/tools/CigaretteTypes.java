package com.salarytracker.tools;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 香烟种类的焦油/一氧化碳预设值。
 * <p>
 * 只作为「预设来源」：真正参与计算的是设置里落库的每支毫克数，用户可以在预设基础上微调，
 * 因为同一种类不同品牌的实测值差异很大。数值取中国市售卷烟的常见量级
 * （国标对卷烟焦油限量为 11 mg/支、一氧化碳约 11–13 mg/支）。
 * <p>
 * 前端 {@code components/tools/quitSmoking.js} 有一份同内容的 {@code CIGARETTE_TYPES} 用于即时填充，
 * 两处改动必须同步；{@code CigaretteTypesTest} 与前端单测各自钉住数值，改错了会立刻失败。
 */
public final class CigaretteTypes {
    public static final String DEFAULT_KEY = "regular";

    public record Preset(String key, String label, BigDecimal tarMg, BigDecimal coMg) {
    }

    private static final Map<String, Preset> PRESETS = new LinkedHashMap<>();

    static {
        put("regular", "普通卷烟", "11.0", "12.0");
        put("fine", "细支烟", "8.0", "8.0");
        put("medium", "中支烟", "9.0", "9.0");
        put("thick", "粗支烟", "13.0", "14.0");
        put("hand-rolled", "手卷烟", "12.0", "14.0");
        put("cigar", "雪茄", "15.0", "15.0");
        put("ecig", "电子烟", "0.0", "0.0");
    }

    private CigaretteTypes() {
    }

    private static void put(String key, String label, String tar, String co) {
        PRESETS.put(key, new Preset(key, label, new BigDecimal(tar), new BigDecimal(co)));
    }

    /** 未知 key 返回 empty；{@code other}（自定义）同样返回 empty，表示沿用用户当前填写的数值。 */
    public static Optional<Preset> preset(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(PRESETS.get(key));
    }

    public static boolean isKnown(String key) {
        return key != null && (PRESETS.containsKey(key) || "other".equals(key));
    }

    public static List<Preset> all() {
        return List.copyOf(PRESETS.values());
    }
}
