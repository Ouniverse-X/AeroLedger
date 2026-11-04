package cn.edu.buaa.ledger.domain;

import cn.edu.buaa.ledger.LedgerRules;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local, deterministic text assistance. Ambiguous values always require user selection. */
public final class CampusParser {
    public static final class Result {
        public final List<String> amounts = new ArrayList<>();
        public final List<String> dates = new ArrayList<>();
        public String category = "other", type = "expense", reason = "未命中校园分类规则，请手动选择";
    }
    public static Result parse(String raw, LocalDate today) {
        if (raw.length() > 2000) throw new IllegalArgumentException("一次最多解析 2000 字");
        Result r = new Result();
        Matcher dates = Pattern.compile("(?:(\\d{4})\\s*[-/年]\\s*)?(\\d{1,2})\\s*[-/月]\\s*(\\d{1,2})\\s*日?").matcher(raw);
        StringBuffer withoutDates = new StringBuffer();
        while (dates.find()) {
            try {
                String date = LocalDate.of(dates.group(1) == null ? today.getYear() : Integer.parseInt(dates.group(1)),
                    Integer.parseInt(dates.group(2)), Integer.parseInt(dates.group(3))).toString();
                if (!r.dates.contains(date)) r.dates.add(date);
            } catch (RuntimeException ignored) { }
            dates.appendReplacement(withoutDates, " ");
        }
        dates.appendTail(withoutDates);
        if (raw.contains("今天")) r.dates.add(today.toString());
        if (raw.contains("昨天")) r.dates.add(today.minusDays(1).toString());
        Matcher amount = Pattern.compile("(?<![-+0-9.])(?:[¥￥]\\s*([0-9]+(?:\\.[0-9]+)?)|([0-9]+(?:\\.[0-9]+)?)\\s*(?:元|块))").matcher(withoutDates);
        while (amount.find()) {
            String value = amount.group(1) == null ? amount.group(2) : amount.group(1);
            try { String formatted = LedgerRules.money(LedgerRules.parseMoney(value)); if (!r.amounts.contains(formatted)) r.amounts.add(formatted); }
            catch (IllegalArgumentException ignored) { }
        }
        String[][] rules = {{"奖学金|助学金","scholarship","income"},{"生活费|家人转入","allowance","income"},
            {"兼职|劳务|助教工资","work","income"},{"电费|水费|网费|宿舍缴费","bills","expense"},
            {"食堂|午餐|晚餐|早餐|合一楼|咖啡|奶茶","food","expense"},{"地铁|公交|通勤|打车","transport","expense"},
            {"打印|教材|课程资料|书本","study","expense"},{"日用品|购物|文具","shopping","expense"}};
        for (String[] rule : rules) {
            Matcher match = Pattern.compile(rule[0]).matcher(raw);
            if (match.find()) { r.category = rule[1]; r.type = rule[2]; r.reason = "命中关键词「" + match.group() + "」；可在下方修正"; break; }
        }
        return r;
    }
}
