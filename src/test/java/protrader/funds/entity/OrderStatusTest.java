package protrader.funds.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Piece 5 core: which statuses are open (can still fill or be cancelled). Fails until isOpen is written. */
class OrderStatusTest {

	@Test
	void openStatuses() {
		Set<OrderStatus> open = EnumSet.noneOf(OrderStatus.class);
		for (OrderStatus s : OrderStatus.values()) {
			if (s.isOpen()) {
				open.add(s);
			}
		}

		assertEquals(EnumSet.of(OrderStatus.PENDING_NEW, OrderStatus.NEW, OrderStatus.PARTIALLY_FILLED,
				OrderStatus.PENDING_CANCEL, OrderStatus.PENDING_REPLACE), open);
	}

	@Test
	void finishedStatusesAreClosed() {
		for (OrderStatus s : EnumSet.of(OrderStatus.FILLED, OrderStatus.CANCELED, OrderStatus.EXPIRED,
				OrderStatus.REJECTED)) {
			assertEquals(false, s.isOpen(), s.name());
		}
	}
}
