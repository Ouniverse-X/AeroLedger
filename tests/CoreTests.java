package cn.edu.buaa.ledger;

import java.util.Arrays;
import java.util.List;

public final class CoreTests {
    private static int assertions;
    private static void check(boolean value, String label) {
        assertions++; if (!value) throw new AssertionError(label);
    }
    private static void rejects(Runnable action, String label) {
        try { action.run(); } catch (IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError("Accepted invalid value: " + label);
    }
    public static void main(String[] args) {
        check(LedgerRules.parseMoney("0.01") == 1, "one cent");
        check(LedgerRules.parseMoney("12.5") == 1250, "one decimal");
        check(LedgerRules.parseMoney("999999.99") == LedgerRules.MAX_CENTS, "max amount");
        for (String invalid : new String[]{"", "0", "0.00", "-1", "1.001", "NaN", "1e3", "1000000", "01.00", "1,000", ".5", "1.", "∞"})
            rejects(() -> LedgerRules.parseMoney(invalid), invalid);
        check(LedgerRules.money(-1001).equals("-10.01"), "negative balance");
        Entry a = new Entry("a", "expense", 10, "2024-02-29", "餐饮", "午餐, \"小碗\"\r\n第二行");
        Entry b = new Entry("b", "income", 1000, "2024-02-29", "生活费", "");
        Entry c = new Entry("c", "expense", 20, "2024-03-01", "交通", "公交");
        LedgerRules.validate(a);
        rejects(() -> LedgerRules.validate(new Entry("bad", "expense", 10, "2025-02-29", "餐饮", "")), "invalid leap day");
        rejects(() -> LedgerRules.validate(new Entry("bad", "income", 10, "2024-02-29", "餐饮", "")), "type/category mismatch");
        rejects(() -> LedgerRules.validate(new Entry("bad", "invalid", 10, "2024-02-29", "餐饮", "")), "invalid type");
        rejects(() -> LedgerRules.validate(new Entry("bad", "expense", 10, "2024-2-29", "餐饮", "")), "non ISO date");
        LedgerRules.Summary s = LedgerRules.summarize(Arrays.asList(a, b, c));
        check(s.expense == 30 && s.income == 1000 && s.balance() == 970 && s.count == 3, "integer totals");
        check(s.categories.size() == 2 && s.categories.get("餐饮") == 10, "expense-only chart");
        check(LedgerRules.summarize(List.of()).expense == 0, "empty totals");
        String csv = CsvCodec.encode(Arrays.asList(a, b, c));
        List<List<String>> rows = CsvCodec.parse(csv);
        check(rows.size() == 4, "multiline CSV record count");
        check(CsvCodec.decodeRow(rows.get(1)).fingerprint().equals(a.fingerprint()), "quoted multiline roundtrip");
        check(CsvCodec.decodeRow(rows.get(2)).note.isEmpty(), "empty last field");
        check(CsvCodec.parse("id,type,amount,date,category,note\na,expense,0.10,2024-02-29,餐饮,").size() == 2, "no final newline");
        rejects(() -> CsvCodec.parse("id,type,amount,date,category,note\na,expense,0.10,2024-02-29,餐饮,\"unfinished"), "unclosed quote");
        rejects(() -> CsvCodec.parse("id,type,amount,date,category,note\na,expense,0.10,2024-02-29,餐饮,\"note\"suffix"), "junk after quote");
        rejects(() -> CsvCodec.parse("wrong,header"), "invalid header");
        rejects(() -> CsvCodec.decodeRow(List.of("a", "expense")), "short row");
        String review = LedgerRules.review("2024-02", s, 20);
        check(review.contains("0.30") && review.contains("10.00") && review.contains("已超出 ¥0.10"), "review sources totals");
        check(LedgerRules.review("2024-02", s, 100).contains("剩余额度 ¥0.70"), "remaining budget");
        check(LedgerRules.review("2024-02", LedgerRules.summarize(List.of()), 0).contains("暂无账目"), "empty review");
        check(new Bill(1, "test", 100, 0, "pending", true, false).needsReminder(), "pending reminder");
        check(!new Bill(1, "test", 100, 0, "paid", true, false).needsReminder(), "paid reminder disabled");
        check(!new Bill(1, "test", 100, 0, "cancelled", true, false).needsReminder(), "cancelled reminder disabled");
        check(!new Bill(1, "test", 100, 0, "pending", false, false).needsReminder(), "per-bill off");
        check(!new Bill(1, "test", 100, 0, "pending", true, true).needsReminder(), "no repeated delivery");
        var today = java.time.LocalDate.of(2026, 11, 4);
        var parsed = cn.edu.buaa.ledger.domain.CampusParser.parse("2026-11-04 学一食堂午餐 12.5 元", today);
        check(parsed.amounts.equals(List.of("12.50")) && parsed.dates.equals(List.of("2026-11-04")), "campus text amount and date");
        check(parsed.category.equals("food") && parsed.reason.contains("命中关键词"), "explainable food classification");
        var ambiguous = cn.edu.buaa.ledger.domain.CampusParser.parse("午餐12元，晚餐18元", today);
        check(ambiguous.amounts.size() == 2 && ambiguous.dates.isEmpty(), "ambiguous amounts and missing date preserved");
        var income = cn.edu.buaa.ledger.domain.CampusParser.parse("昨天兼职劳务500元", today);
        check(income.type.equals("income") && income.category.equals("work") && income.dates.equals(List.of("2026-11-03")), "income and relative date");
        check(cn.edu.buaa.ledger.domain.CampusParser.parse("11 月 4 日打印 20 元", today).category.equals("study"), "spaced Chinese date and study rule");
        check(cn.edu.buaa.ledger.domain.CampusParser.parse("2026-02-30 餐费12元", today).dates.isEmpty(), "invalid text date not guessed");
        check(cn.edu.buaa.ledger.domain.CampusParser.parse("午餐-12元", today).amounts.isEmpty(), "negative text money not silently made positive");
        rejects(() -> LedgerRules.validate(new Entry("old", "expense", 1, "1800-01-01", "餐饮", "")), "out of range import date");
        System.out.println("PASS: " + assertions + " core assertions");
    }
}
