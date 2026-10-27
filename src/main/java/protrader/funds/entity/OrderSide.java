package protrader.funds.entity;

public enum OrderSide {
    BUY,
    SELL;

    public String json() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
