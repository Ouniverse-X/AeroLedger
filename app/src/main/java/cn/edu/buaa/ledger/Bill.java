package cn.edu.buaa.ledger;

public final class Bill {
    public final long id, cents, dueAt;
    public final String name, status;
    public final boolean enabled, notified;

    public Bill(long id, String name, long cents, long dueAt, String status, boolean enabled, boolean notified) {
        this.id = id; this.name = name; this.cents = cents; this.dueAt = dueAt;
        this.status = status; this.enabled = enabled; this.notified = notified;
    }

    public boolean needsReminder() { return "pending".equals(status) && enabled && !notified; }
}
