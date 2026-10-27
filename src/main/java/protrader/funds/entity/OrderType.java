package protrader.funds.entity;

public enum OrderType {
	LIMIT,
	MARKET;

	/** The public JSON word: "limit" / "market". */
	public String json() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}
