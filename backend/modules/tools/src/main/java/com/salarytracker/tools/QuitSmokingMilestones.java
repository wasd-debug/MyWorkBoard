package com.salarytracker.tools;

import com.salarytracker.tools.ToolsModels.QuitSmokingMilestone;
import com.salarytracker.tools.ToolsModels.QuitSmokingRecovery;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 戒烟恢复里程碑：按「最后一支烟」之后的经过时间，给出四个维度的身体修复节点。
 * <p>
 * 时间点取自 CDC / 美国卫生总监报告《The Health Consequences of Smoking—What It Means to You》(2004)
 * 的戒烟获益时间线，以及临床戒烟指南对尼古丁戒断（生理依赖）与心理依赖的通行结论：
 * <ul>
 *   <li>生理依赖：血中尼古丁约 24 小时清除，戒断症状在 2–3 天达到峰值，2–4 周明显缓解；</li>
 *   <li>心理依赖：与场景绑定的「下意识点烟」在 1–3 个月明显减少，6–12 个月趋于稳定；</li>
 *   <li>血管：20 分钟心率开始回落、12 小时一氧化碳回到正常，1 年冠心病风险减半，15 年回到不吸烟水平；</li>
 *   <li>肺：72 小时支气管舒张，2 周–3 个月肺功能提升，1–9 个月咳嗽气短减少，10 年肺癌死亡风险减半。</li>
 * </ul>
 * 这些都是群体层面的统计结论，个体差异很大，页面必须标注「仅作参考、不是医学诊断」。
 * <p>
 * 纯函数：只依赖 {@code startAt} 与 {@code now}，便于单测；不做任何 I/O。
 */
public final class QuitSmokingMilestones {
    public static final String CATEGORY_PHYSIOLOGICAL = "physiological";
    public static final String CATEGORY_PSYCHOLOGICAL = "psychological";
    public static final String CATEGORY_VASCULAR = "vascular";
    public static final String CATEGORY_LUNG = "lung";

    private static final long MINUTE = 1;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    /**
     * @param atMinutes 距最后一支烟的分钟数；同一分钟内的多条按声明顺序展示
     */
    public record Definition(String key, String category, String label, String detail, long atMinutes,
                             String atLabel, String encouragement) {
    }

    public static final List<Definition> DEFINITIONS = List.of(
            new Definition("vascular-20min", CATEGORY_VASCULAR, "心率与血压开始回落",
                    "最后一支烟之后，心率和血压就从峰值往下走。", 20 * MINUTE, "20 分钟",
                    "最难的决定已经做完了。接下来交给身体，它会自己开始修复。"),
            new Definition("vascular-8h", CATEGORY_VASCULAR, "血中一氧化碳减半",
                    "一氧化碳少了，血液能携带的氧气变多。", 8 * HOUR, "8 小时",
                    "血液里的氧气正在变多，头晕和乏力会一天比一天轻。"),
            new Definition("vascular-12h", CATEGORY_VASCULAR, "一氧化碳回到正常水平",
                    "血液里的一氧化碳降到不吸烟者的水平，携氧能力恢复。", 12 * HOUR, "12 小时",
                    "只用半天，血液就干净了一轮。爬楼梯会比昨天轻松一点。"),
            new Definition("physiological-24h", CATEGORY_PHYSIOLOGICAL, "血中尼古丁基本清除",
                    "体内尼古丁代谢殆尽，身体开始真正「断供」。", 24 * HOUR, "24 小时",
                    "整整一天没有让尼古丁进门。接下来几天会难受，但那是身体在重启。"),
            new Definition("physiological-48h", CATEGORY_PHYSIOLOGICAL, "神经末梢开始再生",
                    "被烟麻痹的味觉与嗅觉神经末梢重新生长。", 48 * HOUR, "48 小时",
                    "饭菜的味道会一点点回来。这两天的坚持，先从一顿饭开始兑现。"),
            new Definition("physiological-72h", CATEGORY_PHYSIOLOGICAL, "戒断反应到达峰值",
                    "尼古丁戒断症状在 2–3 天最强，之后只会越来越轻。", 72 * HOUR, "72 小时",
                    "这是全程最陡的一段坡。你已经站在坡顶，往后每一步都是下坡。"),
            new Definition("lung-72h", CATEGORY_LUNG, "支气管舒张，呼吸变顺畅",
                    "气道平滑肌放松，肺活量开始回升。", 72 * HOUR, "72 小时",
                    "试着深吸一口气——现在能吸到底了，这就是肺在松开。"),
            new Definition("lung-2w", CATEGORY_LUNG, "肺功能开始提升",
                    "肺功能可提升约 10%–30%，走路气短明显减少。", 14 * DAY, "2 周",
                    "两周前你还在为爬楼喘气，现在肺已经在悄悄扩容。"),
            new Definition("vascular-2w", CATEGORY_VASCULAR, "血液循环改善",
                    "末梢循环恢复，手脚冰凉的情况会减轻。", 14 * DAY, "2 周",
                    "手指不再那么容易发凉——这是血管在恢复弹性。"),
            new Definition("physiological-4w", CATEGORY_PHYSIOLOGICAL, "生理依赖基本解除",
                    "戒断症状在 2–4 周内基本消退，身体不再「索要」尼古丁。", 28 * DAY, "4 周",
                    "生理上的瘾已经过去了。剩下的是习惯，而习惯是可以被替换的。"),
            new Definition("psychological-3m", CATEGORY_PSYCHOLOGICAL, "心理依赖明显减弱",
                    "饭后、开车、喝酒这些场景的「下意识摸烟」显著减少。", 90 * DAY, "3 个月",
                    "那些曾经自动触发的手部动作，正在从你的生活里退场。"),
            new Definition("lung-3m", CATEGORY_LUNG, "气管纤毛恢复工作",
                    "纤毛重新摆动，把积存的焦油和痰往外排。", 90 * DAY, "3 个月",
                    "如果最近咳出一点灰黑色的痰，那是气管在打扫卫生，不是变差。"),
            new Definition("psychological-6m", CATEGORY_PSYCHOLOGICAL, "情绪调节不再依赖香烟",
                    "压力大时不再第一时间想到点一支烟。", 180 * DAY, "6 个月",
                    "你已经能用别的方式处理压力了。这才是真正戒掉的标志。"),
            new Definition("lung-9m", CATEGORY_LUNG, "咳嗽与气短明显减少",
                    "纤毛功能恢复，肺部自我清洁能力接近正常。", 270 * DAY, "9 个月",
                    "早上那阵咳嗽，应该已经很久没出现了。"),
            new Definition("vascular-1y", CATEGORY_VASCULAR, "冠心病风险降到吸烟者的一半",
                    "与继续吸烟相比，冠心病的超额风险下降约 50%。", 365 * DAY, "1 年",
                    "一整年。心脏病风险被砍掉一半，这是你能给自己的最实在的礼物。"),
            new Definition("psychological-1y", CATEGORY_PSYCHOLOGICAL, "心理依赖趋于稳定",
                    "一年不靠香烟调节情绪，行为模式已经重建。", 365 * DAY, "1 年",
                    "一年前你还在数着小时，现在烟已经不在你的选项里了。"),
            new Definition("vascular-5y", CATEGORY_VASCULAR, "中风风险接近不吸烟者",
                    "戒烟 5–15 年后，中风风险降到与不吸烟者相近。", 5 * 365 * DAY, "5 年",
                    "五年。你的脑血管已经回到接近从未吸烟的状态。"),
            new Definition("lung-10y", CATEGORY_LUNG, "肺癌死亡风险减半",
                    "与继续吸烟者相比，肺癌死亡风险约为其一半。", 10 * 365 * DAY, "10 年",
                    "十年，肺癌风险减半。这些年你没抽的每一支烟，都在这里兑现。"),
            new Definition("vascular-15y", CATEGORY_VASCULAR, "冠心病风险回到不吸烟水平",
                    "戒烟 15 年后，冠心病风险与从不吸烟者基本相同。", 15 * 365 * DAY, "15 年",
                    "从统计上看，你的心脏已经和从未吸烟的人站在同一条线上。")
    );

    private QuitSmokingMilestones() {
    }

    /**
     * @param startAt 戒烟起始时刻（最后一支烟）
     * @param now     计算基准时刻；早于 {@code startAt} 时按 0 分钟处理
     */
    public static QuitSmokingRecovery recovery(Instant startAt, Instant now) {
        Instant start = startAt == null ? (now == null ? Instant.EPOCH : now) : startAt;
        Instant reference = now == null ? start : now;
        long elapsed = Math.max(0L, Duration.between(start, reference).toMinutes());

        List<QuitSmokingMilestone> milestones = new ArrayList<>(DEFINITIONS.size());
        QuitSmokingMilestone next = null;
        int reachedCount = 0;
        for (Definition definition : DEFINITIONS) {
            boolean reached = elapsed >= definition.atMinutes();
            long remaining = reached ? 0 : definition.atMinutes() - elapsed;
            double progress = definition.atMinutes() <= 0
                    ? 1.0 : Math.min(1.0, round(elapsed / (double) definition.atMinutes()));
            QuitSmokingMilestone milestone = new QuitSmokingMilestone(definition.key(), definition.category(),
                    definition.label(), definition.detail(), definition.atMinutes(), definition.atLabel(), reached,
                    reached ? start.plus(definition.atMinutes(), ChronoUnit.MINUTES).toString() : "",
                    remaining, progress, definition.encouragement());
            milestones.add(milestone);
            if (reached) {
                reachedCount++;
            } else if (next == null) {
                next = milestone;
            }
        }

        String encouragement;
        if (next == null) {
            encouragement = "所有里程碑都已达成。你把一件很难的事，变成了生活里不需要再想起的默认设置。";
        } else if (reachedCount == 0) {
            encouragement = "别急着看长远，先把下一个节点熬过去——身体比你想象的更配合。";
        } else {
            encouragement = "已经跨过 " + reachedCount + " 个节点了。下一个是「" + next.label() + "」，还差 "
                    + humanize(next.remainingMinutes()) + "。";
        }

        return new QuitSmokingRecovery(elapsed, next == null ? "" : next.key(), next == null ? "" : next.label(),
                next == null ? 0 : next.remainingMinutes(), next == null ? 1.0 : next.progress(),
                reachedCount, DEFINITIONS.size(), encouragement, List.copyOf(milestones));
    }

    /** 供后端拼鼓励语与日志使用的粗粒度中文时长；前端另有更细的格式化实现。 */
    static String humanize(long minutes) {
        if (minutes <= 0) return "0 分钟";
        if (minutes < 60) return minutes + " 分钟";
        if (minutes < 24 * 60) {
            long hours = minutes / 60;
            long rest = minutes % 60;
            return rest == 0 ? hours + " 小时" : hours + " 小时 " + rest + " 分钟";
        }
        long days = minutes / (24 * 60);
        long hours = (minutes % (24 * 60)) / 60;
        if (days >= 100) return days + " 天";
        return hours == 0 ? days + " 天" : days + " 天 " + hours + " 小时";
    }

    /** 按类别分组时的稳定顺序；未知类别排在最后。 */
    public static int categoryOrder(String category) {
        return switch (category == null ? "" : category) {
            case CATEGORY_PHYSIOLOGICAL -> 0;
            case CATEGORY_PSYCHOLOGICAL -> 1;
            case CATEGORY_VASCULAR -> 2;
            case CATEGORY_LUNG -> 3;
            default -> 9;
        };
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
