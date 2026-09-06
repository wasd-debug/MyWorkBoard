/**
 * 核心计算（纯函数，无 DOM）—— 从旧版 index.html 原样移植
 */
export const CALC = (() => {
  const toMin = t => { const [h, m] = String(t).split(":").map(Number); return h * 60 + m; };
  const pad = n => String(n).padStart(2, "0");
  const dateKey = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  const WEEK_CN = ["周日","周一","周二","周三","周四","周五","周六"];
  const addDays = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };
  const monday = d => { const x = new Date(d); x.setHours(0,0,0,0); return addDays(x, -((x.getDay() + 6) % 7)); };
  const monthFirst = d => new Date(d.getFullYear(), d.getMonth(), 1);

  /* 标准每日工时（分钟），支持跨零点班次（如 22:00-06:00） */
  function stdWorkMin(s) {
    let st = toMin(s.workStart), en = toMin(s.workEnd);
    if (en < st) en += 1440;
    const m = en - st - s.lunchMin;
    return m > 0 ? m : 0;
  }
  /* 某日实际工时（分钟）；无效返回 0。
     rec.rest 为当日自定义休息（摸鱼/晚饭/健身等）分钟数，不计入工时；历史记录缺省为 0 */
  function actualMin(rec, s) {
    if (!rec || !rec.start || !rec.end) return 0;
    let st = toMin(rec.start), en = toMin(rec.end);
    if (en < st) en += 1440;               // 跨零点下班
    const rest = Number(rec.rest) || 0;
    const m = en - st - (s.lunchMin || 0) - rest;
    return m > 0 ? m : 0;
  }
  /* 当前口径月薪 */
  const salary = (s, basis) => basis === "pre" ? s.salaryPre : s.salaryPost;
  /* 当月月薪：按月配置（settings.salaries["YYYY-MM"].pre/post）优先，未配置回落全局默认 */
  function monthSalary(salaries, s, basis, ym) {
    const m = (salaries && ym && salaries[ym]) || {};
    const v = basis === "pre" ? m.pre : m.post;
    return Number(v) > 0 ? Number(v) : salary(s, basis);
  }
  /* 带当月工资的设置上下文（用于日薪/时薪/单月统计，工资按每月浮动） */
  function monthCtx(salaries, s, ym) {
    const m = (salaries && ym && salaries[ym]) || {};
    const pre = Number(m.pre) > 0 ? Number(m.pre) : s.salaryPre;
    const post = Number(m.post) > 0 ? Number(m.post) : s.salaryPost;
    if (pre === s.salaryPre && post === s.salaryPost) return s;
    return { ...s, salaryPre: pre, salaryPost: post };
  }
  /* 日薪（days 可选：自动工作日模式下传当月有效工作日数） */
  const dayPay = (s, basis, days) => salary(s, basis) / (days > 0 ? days : s.daysPerMonth);
  const baseRate = (s, basis, days) => {                              // 基准时薪
    const std = stdWorkMin(s);
    return std > 0 ? dayPay(s, basis, days) / (std / 60) : 0;
  };
  /* 某日实际时薪 */
  function dayRate(rec, s, basis, days) {
    const m = actualMin(rec, s);
    return m > 0 ? dayPay(s, basis, days) / (m / 60) : 0;
  }
  /* 周期统计：dates 为日期 key 数组；days 可选（日薪折算天数）；holidays 可选（节假日感知：
     休息日上班的工时全额计入加班） */
  function periodStats(dates, records, s, basis, days, holidays, salaryOf) {
    const std = stdWorkMin(s);
    let dTotal = days > 0 ? days : s.daysPerMonth;
    let daysWorked = 0, totalMin = 0, otMin = 0;
    for (const k of dates) {
      const m = actualMin(records[k], s);
      if (m > 0) {
        daysWorked++; totalMin += m;
        otMin += (holidays && dayType(k, holidays) === "off") ? m : m - std;
      }
    }
    let earned = 0, baseAcc = 0, baseN = 0;
    if (salaryOf) {
      // 按月分组：每月用各自月薪封顶（工资允许每月波动）
      const groups = {};
      for (const k of dates) { const ym = k.slice(0, 7); (groups[ym] = groups[ym] || []).push(k); }
      for (const ym of Object.keys(groups).sort()) {
        const dW = groups[ym].filter(k => actualMin(records[k], s) > 0).length;
        const sal = salaryOf(ym, basis);
        if (sal > 0) {
          const dp = sal / dTotal;                  // 该月按天计酬的日薪
          earned += Math.min(sal, dW * dp);         // 该月月薪封顶
          if (std > 0) { baseAcc += dp / (std / 60); baseN++; }
        }
      }
    } else {
      const sal = salary(s, basis);
      const raw = daysWorked * dayPay(s, basis, dTotal);              // 按天计酬
      // 月薪封顶：工资 = min(月薪 × 覆盖月数, 按天计酬)。固定月薪制下加班不计入工资，
      // 工资不会因加班/休息日加班而上浮，避免出现"本月打卡多导致工资超月薪"的异常。
      earned = sal > 0 ? Math.min(spanMonths(dates) * sal, raw) : 0;
      if (sal > 0 && std > 0) { baseAcc = dayPay(s, basis, dTotal) / (std / 60); baseN = 1; }
    }
    const realRate = totalMin > 0 ? earned / (totalMin / 60) : 0;
    return { days: daysWorked, totalMin, otMin, realRate, baseRate: baseN > 0 ? baseAcc / baseN : 0, earned };
  }
  /* 日期集合覆盖的自然月数（至少 1；空集合返回 0） */
  function spanMonths(dates) {
    if (!dates || !dates.length) return 0;
    const a = new Date(dates[0] + "T00:00:00"), b = new Date(dates[dates.length - 1] + "T00:00:00");
    return Math.max(1, (b.getFullYear() - a.getFullYear()) * 12 + (b.getMonth() - a.getMonth()) + 1);
  }
  /* 汇总日期区间（含头含尾，截至今天） */
  function rangeKeys(start, end) {
    const out = []; let d = new Date(start);
    const e = new Date(end);
    while (d <= e) { out.push(dateKey(d)); d = addDays(d, 1); }
    return out;
  }
  const weekKeysTo = today => rangeKeys(monday(today), addDays(monday(today), 6));
  const monthKeysTo = today => rangeKeys(monthFirst(today), today);
  /* 数字/金额格式化 */
  const fmtHours = min => {
    const h = min / 60;
    return Math.abs(h) >= 100 ? h.toFixed(0) : (Math.round(h * 10) / 10).toString();
  };
  const fmtMoney = x => "¥" + Number(x || 0).toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const fmtSigned = min => (min >= 0 ? "+" : "") + fmtHours(min) + "h";

  /* ================= 节假日感知（法定工作日自动计算） =================
     holidays: { "YYYY-MM-DD": {name, off} }
       off=true  法定放假日（含调休放假日）
       off=false 周末调休补班日（按工作日算）
     未命中 holidays 的日期按自然周末判断 */
  const isWeekendKey = k => { const w = new Date(k + "T00:00:00").getDay(); return w === 0 || w === 6; };
  /* 某日类型：'off' 休息日 / 'work' 工作日 */
  function dayType(k, holidays) {
    const h = holidays && holidays[k];
    if (h) return h.off ? "off" : "work";
    return isWeekendKey(k) ? "off" : "work";
  }
  /* 某日节假日名（非节假日返回 ''） */
  const holidayName = (k, holidays) => {
    const h = holidays && holidays[k];
    return h && h.name ? h.name : "";
  };
  /* 某月天数 */
  const daysInMonth = ym => {
    const [y, m] = ym.split("-").map(Number);
    return new Date(y, m, 0).getDate();
  };
  /* 某月法定工作日数（扣除节假日与周末，含调休补班） */
  function statutoryWorkdays(ym, holidays) {
    let n = 0;
    const total = daysInMonth(ym);
    for (let i = 1; i <= total; i++) {
      if (dayType(`${ym}-${pad(i)}`, holidays) === "work") n++;
    }
    return n;
  }
  /* 某月非工作日但打了卡的天数（假期加班） */
  function offDaysWorked(ym, holidays, records, s) {
    let n = 0;
    const total = daysInMonth(ym);
    for (let i = 1; i <= total; i++) {
      const k = `${ym}-${pad(i)}`;
      if (dayType(k, holidays) === "off" && actualMin(records[k], s) > 0) n++;
    }
    return n;
  }
  /* 某月有效工作日数 = 法定工作日 + 假期加班天数（用于日薪折算） */
  function monthWorkdays(ym, holidays, records, s) {
    return statutoryWorkdays(ym, holidays) + offDaysWorked(ym, holidays, records, s);
  }
  /* 当前生效的每月排班天数：自动模式取当月有效工作日，否则手动设置值 */
  function effDaysPerMonth(s, ym, holidays, records) {
    if (s.autoDays && ym) return monthWorkdays(ym, holidays, records, s);
    return s.daysPerMonth;
  }

  return { toMin, dateKey, WEEK_CN, addDays, monday, monthFirst, stdWorkMin, actualMin,
           salary, dayPay, baseRate, dayRate, periodStats, spanMonths, rangeKeys, weekKeysTo, monthKeysTo,
           monthSalary, monthCtx,
           fmtHours, fmtMoney, fmtSigned,
           isWeekendKey, dayType, holidayName, daysInMonth, statutoryWorkdays, offDaysWorked,
           monthWorkdays, effDaysPerMonth };
})();
