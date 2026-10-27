package protrader.funds.entity;

public enum OrderTimeInForce {
	GTC,
	IOC,
	DAY;

	/** The public JSON word: "gtc" / "ioc" / "day". */
	public String json() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}
