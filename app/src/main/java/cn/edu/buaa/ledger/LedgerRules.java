package cn.edu.buaa.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure business rules; money is stored as integer cents throughout the app. */
public final class LedgerRules {
    public static final String[] EXPENSE_CATEGORIES = cn.edu.buaa.ledger.domain.CategoryCatalog.names("expense");
    public static final String[] INCOME_CATEGORIES = cn.edu.buaa.ledger.domain.CategoryCatalog.names("income");
    public static final long MAX_CENTS = 99_999_999L;
    private LedgerRules() { }

    public static long parseMoney(String input) {
        String s = input.trim();
        if (!s.matches("(?:0|[1-9][0-9]{0,5})(?:\\.[0-9]{1,2})?"))
            throw new IllegalArgumentException("金额需为正数，最多两位小数，上限 999999.99 元");
        long value = new BigDecimal(s).movePointRight(2).longValueExact();
        if (value <= 0 || value > MAX_CENTS) throw new IllegalArgumentException("金额需大于 0，上限 999999.99 元");
        return value;
    }

    public static String money(long cents) {
        return String.format(Locale.ROOT, "%s%d.%02d", cents < 0 ? "-" : "", Math.abs(cents) / 100, Math.abs(cents) % 100);
    }

    public static void validate(Entry e) {
        if (e.id == null || !e.id.matches("[A-Za-z0-9_-]{1,80}")) throw new IllegalArgumentException("记录 ID 无效");
        if (!"expense".equals(e.type) && !"income".equals(e.type)) throw new IllegalArgumentException("type 必须为 expense 或 income");
        if (e.cents <= 0 || e.cents > MAX_CENTS) throw new IllegalArgumentException("金额超出允许范围");
        try {
            LocalDate date = LocalDate.parse(e.date);
            if (!date.toString().equals(e.date) || date.getYear() < 1900 || date.getYear() > 2100) throw new IllegalArgumentException();
        } catch (Exception ex) { throw new IllegalArgumentException("日期必须为有效的 YYYY-MM-DD，范围为 1900～2100 年"); }
        if (!Arrays.asList(e.isExpense() ? EXPENSE_CATEGORIES : INCOME_CATEGORIES).contains(e.category))
            throw new IllegalArgumentException("类别与收支类型不匹配");
        if (e.note == null || e.note.length() > 200) throw new IllegalArgumentException("备注最多 200 字");
    }

    public static final class Summary {
        public long expense, income;
        public int count;
        public final Map<String, Long> categories = new LinkedHashMap<>();
        public long balance() { return income - expense; }
    }

    public static Summary summarize(List<Entry> entries) {
        Summary s = new Summary();
        for (Entry e : entries) {
            s.count++;
            if (e.isExpense()) {
                s.expense += e.cents;
                s.categories.put(e.category, s.categories.getOrDefault(e.category, 0L) + e.cents);
            } else s.income += e.cents;
        }
        return s;
    }

    public static String review(String month, Summary s, long budget) {
        if (s.count == 0) return month + " 暂无账目，无法生成消费回顾。可在记录收支后重新生成。";
        StringBuilder b = new StringBuilder(month + " 消费回顾\n\n");
        b.append("统计依据：本月全部 ").append(s.count).append(" 笔账目。收入 ¥").append(money(s.income))
            .append("，支出 ¥").append(money(s.expense)).append("，收支结余 ¥").append(money(s.balance())).append("。\n");
        for (Map.Entry<String, Long> c : s.categories.entrySet())
            b.append(c.getKey()).append("支出 ¥").append(money(c.getValue())).append("；");
        b.append("\n\n预算建议：");
        if (budget == 0) b.append("本月尚未设置预算。可参考本月已支出 ¥").append(money(s.expense)).append("，结合剩余天数和计划开销设定预算。");
        else if (s.expense > budget) b.append("预算 ¥").append(money(budget)).append("，已超出 ¥").append(money(s.expense - budget)).append("。建议先核对上述支出类别，再按实际需求调整预算。");
        else b.append("预算 ¥").append(money(budget)).append("，剩余额度 ¥").append(money(budget - s.expense)).append("。后续消费可参考该额度安排。");
        b.append("\n\n本回顾由本地统计规则生成，可编辑；不推断未记录的消费。修改账目后请重新生成。\n统计范围：")
            .append(month).append("-01 至 ").append(java.time.YearMonth.parse(month).atEndOfMonth()).append("；全部类别。");
        return b.toString();
    }
}
