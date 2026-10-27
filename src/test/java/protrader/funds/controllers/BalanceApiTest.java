package protrader.funds.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import protrader.funds.support.Http;
import protrader.funds.support.IntegrationTest;
import tools.jackson.databind.JsonNode;

/**
 * GET /v2/funds/balance (real server, real Postgres):
 *  - row → 200 with formatted money (negative realized P&L too), cash = free + locked
 *  - no row → 200 zeros, account_seq 0, updated_at null
 *  - never another account's row; no credentials → 401
 *  - /readyz stays 200 and shows account_updates: false (reader not started in tests)
 */
class BalanceApiTest extends IntegrationTest {

	private static final long ALICE = 1000;
	private static final long BOB = 1001;

	private void insert(long accountId, long free, long locked, long pnl, long seq, Instant ts) {
		jdbc.sql("""
				insert into balances (account_id, cash_free_minor, cash_locked_minor, realized_pnl_minor, last_applied_seq, updated_at)
				values (:a, :f, :l, :p, :s, :t)
				""")
				.param("a", accountId).param("f", free).param("l", locked).param("p", pnl).param("s", seq)
				.param("t", java.sql.Timestamp.from(ts)).update();
	}

	@Test
	void rowIsShownWithFormattedMoney() {
		insert(ALICE, 900_00, 100_50, -50, 12, Instant.parse("2026-10-27T08:30:00.123Z"));

		Http.Response r = as(ALICE).get("/v2/funds/balance");

		assertEquals(200, r.status(), r.body());
		JsonNode b = r.json();
		assertEquals(ALICE, b.get("account_id").asLong());
		assertEquals("1000.50", b.get("cash").asString());
		assertEquals("900.00", b.get("cash_free").asString());
		assertEquals("100.50", b.get("cash_locked").asString());
		assertEquals("-0.50", b.get("realized_pnl").asString());
		assertEquals(12, b.get("account_seq").asLong());
		assertEquals("2026-10-27T08:30:00.123Z", b.get("updated_at").asString());
	}

	@Test
	void noRowAnswersZeros() {
		Http.Response r = as(ALICE).get("/v2/funds/balance");

		assertEquals(200, r.status(), r.body());
		JsonNode b = r.json();
		assertEquals(ALICE, b.get("account_id").asLong());
		for (String f : new String[] { "cash", "cash_free", "cash_locked", "realized_pnl" }) {
			assertEquals("0.00", b.get(f).asString(), f);
		}
		assertEquals(0, b.get("account_seq").asLong());
		assertTrue(b.get("updated_at").isNull());
	}

	@Test
	void anotherAccountsRowIsNeverReturned() {
		insert(BOB, 5_00, 0, 0, 1, Instant.parse("2026-10-27T08:30:00Z"));

		JsonNode b = as(ALICE).get("/v2/funds/balance").json();

		assertEquals("0.00", b.get("cash").asString());
		assertEquals(0, b.get("account_seq").asLong());
	}

	@Test
	void noCredentialsIs401() {
		assertEquals(401, withHeaders(Map.of()).get("/v2/funds/balance").status());
	}

	@Test
	void readyzIgnoresTheReader() {
		Http.Response r = withHeaders(Map.of()).get("/readyz");

		assertEquals(200, r.status(), r.body());
		assertEquals(false, r.json().get("account_updates").asBoolean());
	}
}
