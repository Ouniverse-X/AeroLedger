package cn.edu.buaa.ledger;

public final class Entry {
    public final String id, type, date, category, note, customCategory, transactionTime, transactionZone;
    public final long cents;

    public Entry(String id, String type, long cents, String date, String category, String note) {
        this(id, type, cents, date, category, note, "");
    }

    public Entry(String id, String type, long cents, String date, String category, String note, String customCategory) {
        this(id, type, cents, date, category, note, customCategory, "", "");
    }

    public Entry(String id, String type, long cents, String date, String category, String note,
                 String customCategory, String transactionTime, String transactionZone) {
        this.transactionTime = transactionTime;
        this.transactionZone = transactionZone;
        this.customCategory = customCategory;
        this.id = id; this.type = type; this.cents = cents;
        this.date = date; this.category = category; this.note = note;
    }

    public boolean isExpense() { return "expense".equals(type); }
    public String fingerprint() {
        return type + "\u001f" + cents + "\u001f" + date + "\u001f" + category + "\u001f" + note + "\u001f" + customCategory + "\u001f" + transactionTime + "\u001f" + transactionZone;
    }
}
