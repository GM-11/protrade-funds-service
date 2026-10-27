package protrader.funds.entity;

/** Same statuses as the contract's Order.status. */
public enum OrderStatus {
    PENDING_NEW,
    NEW,
    PARTIALLY_FILLED,
    FILLED,
    CANCELED,
    REJECTED,
    EXPIRED,
    PENDING_CANCEL,
    PENDING_REPLACE;

    public String json() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean isOpen() {
        return switch (this) {
            case PENDING_NEW, NEW, PARTIALLY_FILLED, PENDING_CANCEL, PENDING_REPLACE -> true;
            case FILLED, CANCELED, EXPIRED, REJECTED -> false;
        };
    }
}
