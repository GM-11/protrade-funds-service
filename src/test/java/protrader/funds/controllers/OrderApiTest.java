package protrader.funds.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import protrader.funds.entity.OrderKey;
import protrader.funds.entity.OrderRecord;
import protrader.funds.entity.OrderSide;
import protrader.funds.entity.OrderStatus;
import protrader.funds.entity.OrderTimeInForce;
import protrader.funds.entity.OrderType;
import protrader.funds.repositories.OrderRepository;
import protrader.funds.support.Http;
import protrader.funds.support.IntegrationTest;
import tools.jackson.databind.JsonNode;

/**
 * GET /v2/funds/orders and /v2/funds/orders/{client_order_id} (real server, real Postgres; rows written with the
 * repository's upsert). Glue tests, except the open/closed filter, which needs OrderStatus.isOpen (core).
 */
class OrderApiTest extends IntegrationTest {

	private static final long ALICE = 1000;
	private static final long BOB = 1001;
	private static final Instant T0 = Instant.parse("2026-10-27T08:00:00.000Z");

	@Autowired
	OrderRepository orders;

	private OrderRecord order(long account, String coid, OrderStatus status, long filled, long notional,
			Instant createdAt) {
		return new OrderRecord(new OrderKey(account, coid), 1, 812, OrderSide.BUY, OrderType.LIMIT,
				OrderTimeInForce.GTC, 100_00, null, 10, filled, notional, status,
				status == OrderStatus.REJECTED ? "price_band" : null, createdAt, createdAt.plusSeconds(5), 9);
	}

	private static List<String> ids(JsonNode body) {
		JsonNode list = body.get("orders");
		List<String> out = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			out.add(list.get(i).get("client_order_id").asString());
		}
		return out;
	}

	private void put(OrderRecord o) {
		orders.upsert(o);
	}

	@Test
	void oneOrderHasTheGatewayShape() {
		put(order(ALICE, "abc", OrderStatus.PARTIALLY_FILLED, 3, 300_01, Instant.parse("2026-10-27T08:30:00.123Z")));

		Http.Response r = as(ALICE).get("/v2/funds/orders/abc");

		assertEquals(200, r.status(), r.body());
		JsonNode o = r.json();
		assertEquals("1-812", o.get("order_id").asString());
		assertEquals("abc", o.get("client_order_id").asString());
		assertEquals("MOOG", o.get("symbol").asString());   // from ../config/instruments.json
		assertEquals("buy", o.get("side").asString());
		assertEquals("limit", o.get("type").asString());
		assertEquals("gtc", o.get("time_in_force").asString());
		assertEquals("100.00", o.get("price").asString());
		assertTrue(o.get("stop_price").isNull());
		assertEquals("10", o.get("qty").asString());
		assertEquals("3", o.get("filled_qty").asString());
		assertEquals("100.00", o.get("filled_avg_price").asString());   // 30001 / 3 = 10000 (rounded down)
		assertEquals("partially_filled", o.get("status").asString());
		assertTrue(o.get("reject_reason").isNull());
		assertEquals("2026-10-27T08:30:00.123Z", o.get("created_at").asString());
		assertEquals("2026-10-27T08:30:05.123Z", o.get("updated_at").asString());
	}

	@Test
	void unfilledOrderHasNoAveragePriceAndRejectedShowsItsReason() {
		put(order(ALICE, "new-1", OrderStatus.NEW, 0, 0, T0));
		put(order(ALICE, "rej-1", OrderStatus.REJECTED, 0, 0, T0));

		assertTrue(as(ALICE).get("/v2/funds/orders/new-1").json().get("filled_avg_price").isNull());
		assertEquals("price_band", as(ALICE).get("/v2/funds/orders/rej-1").json().get("reject_reason").asString());
	}

	@Test
	void upsertKeepsTheFirstCreatedAt() {
		put(order(ALICE, "abc", OrderStatus.NEW, 0, 0, T0));
		put(order(ALICE, "abc", OrderStatus.FILLED, 10, 1000_00, T0.plusSeconds(60)));

		JsonNode o = as(ALICE).get("/v2/funds/orders/abc").json();

		assertEquals("filled", o.get("status").asString());
		assertEquals("2026-10-27T08:00:00.000Z", o.get("created_at").asString());
		assertEquals("2026-10-27T08:01:05.000Z", o.get("updated_at").asString());
	}

	@Test
	void unknownOrAnotherAccountsOrderIs404() {
		put(order(BOB, "bobs", OrderStatus.NEW, 0, 0, T0));

		Http.Response missing = as(ALICE).get("/v2/funds/orders/nope");
		Http.Response bobs = as(ALICE).get("/v2/funds/orders/bobs");

		assertEquals(404, missing.status());
		assertEquals("order_not_found", missing.error());
		assertEquals(404, bobs.status());
	}

	@Test
	void badClientOrderIdIs422() {
		Http.Response r = as(ALICE).get("/v2/funds/orders/bad.id");

		assertEquals(422, r.status(), r.body());
		assertEquals("client_order_id", r.field());
	}

	@Test
	void listAllIsNewestFirstAndPages() {
		for (int i = 1; i <= 5; i++) {
			put(order(ALICE, "o" + i, OrderStatus.NEW, 0, 0, T0.plusSeconds(i)));
		}
		put(order(BOB, "bobs", OrderStatus.NEW, 0, 0, T0.plusSeconds(99)));

		List<String> seen = new ArrayList<>();
		String token = null;
		int pages = 0;
		do {
			String path = "/v2/funds/orders?status=all&limit=2" + (token == null ? "" : "&page_token=" + token);
			Http.Response r = as(ALICE).get(path);
			assertEquals(200, r.status(), r.body());
			JsonNode body = r.json();
			seen.addAll(ids(body));
			token = body.get("next_page_token").isNull() ? null : body.get("next_page_token").asString();
			pages++;
		} while (token != null);

		assertEquals(List.of("o5", "o4", "o3", "o2", "o1"), seen);
		assertEquals(3, pages);
	}

	@Test
	void defaultStatusIsAll() {
		put(order(ALICE, "a", OrderStatus.NEW, 0, 0, T0));
		put(order(ALICE, "b", OrderStatus.FILLED, 10, 1000_00, T0.plusSeconds(1)));

		assertEquals(2, as(ALICE).get("/v2/funds/orders").json().get("orders").size());
	}

	@Test
	void openAndClosedFilter() {   // needs OrderStatus.isOpen (core)
		put(order(ALICE, "new", OrderStatus.NEW, 0, 0, T0));
		put(order(ALICE, "part", OrderStatus.PARTIALLY_FILLED, 4, 400_00, T0.plusSeconds(1)));
		put(order(ALICE, "done", OrderStatus.FILLED, 10, 1000_00, T0.plusSeconds(2)));
		put(order(ALICE, "gone", OrderStatus.CANCELED, 0, 0, T0.plusSeconds(3)));

		List<String> open = ids(as(ALICE).get("/v2/funds/orders?status=open").json());
		List<String> closed = ids(as(ALICE).get("/v2/funds/orders?status=closed").json());

		assertEquals(List.of("part", "new"), open);
		assertEquals(List.of("gone", "done"), closed);
	}

	@Test
	void badStatusOrTokenIs422() {
		Http.Response status = as(ALICE).get("/v2/funds/orders?status=done");
		Http.Response token = as(ALICE).get("/v2/funds/orders?page_token=@@@");

		assertEquals(422, status.status());
		assertEquals("status", status.field());
		assertEquals(422, token.status());
		assertEquals("page_token", token.field());
	}

	@Test
	void historyCompleteFollowsTheGapMark() {
		assertTrue(as(ALICE).get("/v2/funds/orders").json().get("history_complete").asBoolean());   // no row yet

		jdbc.sql("""
				insert into balances (account_id, cash_free_minor, cash_locked_minor, realized_pnl_minor,
				                      last_applied_seq, updated_at, history_gap_seq)
				values (:a, 0, 0, 0, 8, now(), 8)
				""").param("a", ALICE).update();

		assertFalse(as(ALICE).get("/v2/funds/orders").json().get("history_complete").asBoolean());
	}

	@Test
	void noCredentialsIs401() {
		assertEquals(401, withHeaders(Map.of()).get("/v2/funds/orders").status());
		assertEquals(401, withHeaders(Map.of()).get("/v2/funds/orders/abc").status());
	}
}
