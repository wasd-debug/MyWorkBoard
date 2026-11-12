package com.salarytracker.tools;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CigaretteTypesTest {
    @Test
    void regularCigaretteUsesNationalStandardMagnitude() {
        CigaretteTypes.Preset preset = CigaretteTypes.preset("regular").orElseThrow();

        // 国标对卷烟焦油限量为 11 mg/支，一氧化碳常见 11–13 mg/支
        assertEquals(new BigDecimal("11.0"), preset.tarMg());
        assertEquals(new BigDecimal("12.0"), preset.coMg());
        assertEquals(CigaretteTypes.DEFAULT_KEY, preset.key());
    }

    @Test
    void everyPresetStaysWithinValidatedRange() {
        List<CigaretteTypes.Preset> presets = CigaretteTypes.all();
        assertFalse(presets.isEmpty());

        Set<String> keys = new HashSet<>();
        for (CigaretteTypes.Preset preset : presets) {
            assertTrue(keys.add(preset.key()), "预设 key 不能重复：" + preset.key());
            assertTrue(preset.tarMg().signum() >= 0 && preset.tarMg().compareTo(BigDecimal.valueOf(100)) <= 0,
                    preset.key() + " 的焦油量超出 0–100 mg");
            assertTrue(preset.coMg().signum() >= 0 && preset.coMg().compareTo(BigDecimal.valueOf(100)) <= 0,
                    preset.key() + " 的一氧化碳量超出 0–100 mg");
            assertFalse(preset.label().isBlank());
        }
    }

    @Test
    void electronicCigaretteHasNoTarOrCarbonMonoxide() {
        CigaretteTypes.Preset preset = CigaretteTypes.preset("ecig").orElseThrow();

        assertEquals(0, preset.tarMg().signum());
        assertEquals(0, preset.coMg().signum());
    }

    @Test
    void customAndUnknownKeysAreDistinguished() {
        // other 是合法取值，但没有数值预设（沿用用户自己填的含量）
        assertTrue(CigaretteTypes.isKnown("other"));
        assertTrue(CigaretteTypes.preset("other").isEmpty());

        assertFalse(CigaretteTypes.isKnown("hookah"));
        assertTrue(CigaretteTypes.preset("hookah").isEmpty());
        assertTrue(CigaretteTypes.preset(null).isEmpty());
    }
}
