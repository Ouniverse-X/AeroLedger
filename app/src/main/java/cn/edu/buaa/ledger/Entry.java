package cn.edu.buaa.ledger;

public final class Entry {
    public final String id, type, date, category, note;
    public final long cents;

    public Entry(String id, String type, long cents, String date, String category, String note) {
        this.id = id; this.type = type; this.cents = cents;
        this.date = date; this.category = category; this.note = note;
    }

    public boolean isExpense() { return "expense".equals(type); }
    public String fingerprint() {
        return type + "\u001f" + cents + "\u001f" + date + "\u001f" + category + "\u001f" + note;
    }
}
