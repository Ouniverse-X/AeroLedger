package cn.edu.buaa.ledger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** RFC 4180-style quoting, including commas, quotes, CRLF and multiline notes. */
public final class CsvCodec {
    public static final List<String> HEADER = Arrays.asList("id", "type", "amount", "date", "category", "note");
    private CsvCodec() { }

    public static String encode(List<Entry> entries) {
        StringBuilder out = new StringBuilder("\uFEFFid,type,amount,date,category,note\r\n");
        for (Entry e : entries) {
            String[] fields = {e.id, e.type, LedgerRules.money(e.cents), e.date, e.category, e.note};
            for (int i = 0; i < fields.length; i++) {
                if (i > 0) out.append(',');
                out.append('"').append(fields[i].replace("\"", "\"\"")).append('"');
            }
            out.append("\r\n");
        }
        return out.toString();
    }

    public static List<List<String>> parse(String text) {
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        if (text.length() > 2_000_000) throw new IllegalArgumentException("文件超过 2 MB 字符限制");
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false, afterQuote = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else { quoted = false; afterQuote = true; }
                } else field.append(c);
            } else if (c == ',' || c == '\n' || c == '\r') {
                row.add(field.toString()); field.setLength(0); afterQuote = false;
                if (c != ',') {
                    if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                    rows.add(row); row = new ArrayList<>();
                    if (rows.size() > 10001) throw new IllegalArgumentException("一次最多导入 10000 条账目");
                }
            } else if (c == '"' && field.length() == 0 && !afterQuote) quoted = true;
            else {
                if (afterQuote || c == '"') throw new IllegalArgumentException("CSV 引号格式错误，文件未导入");
                field.append(c);
            }
        }
        if (quoted) throw new IllegalArgumentException("CSV 引号未闭合，文件未导入");
        if (field.length() > 0 || !row.isEmpty() || afterQuote) { row.add(field.toString()); rows.add(row); }
        if (rows.size() > 10001) throw new IllegalArgumentException("一次最多导入 10000 条账目");
        if (rows.isEmpty() || !rows.get(0).equals(HEADER))
            throw new IllegalArgumentException("表头应为 id,type,amount,date,category,note（顺序固定）");
        return rows;
    }

    public static Entry decodeRow(List<String> row) {
        if (row.size() != 6) throw new IllegalArgumentException("列数应为 6");
        Entry e = new Entry(row.get(0), row.get(1), LedgerRules.parseMoney(row.get(2)), row.get(3), row.get(4), row.get(5));
        LedgerRules.validate(e);
        return e;
    }
}
