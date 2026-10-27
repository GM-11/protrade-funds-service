package protrader.funds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import protrader.funds.dto.ApplyOutcome;
import protrader.funds.dto.BalanceUpdate;
import protrader.funds.entity.Balance;
import protrader.funds.entity.OrderKey;
import protrader.funds.entity.OrderRecord;
import protrader.funds.entity.OrderSide;
import protrader.funds.entity.OrderStatus;
import protrader.funds.entity.OrderTimeInForce;
import protrader.funds.entity.OrderType;
import protrader.funds.entity.Transfer;
import protrader.funds.entity.TransferKind;
import protrader.funds.entity.TransferStatus;
import protrader.funds.repositories.BalanceRepository;
import protrader.funds.repositories.OrderRepository;
import protrader.funds.repositories.TransferRepository;
import protrader.funds.support.IntegrationTest;

/**
 * Tests sequence ordering and transfer completion when applying balance
 * updates.
 */
class BalanceServiceTest extends IntegrationTest {

    private static final long ACCOUNT = 42;
    private static final Instant T1 = Instant.parse("2026-10-27T08:00:00.000Z");
    private static final Instant T2 = Instant.parse("2026-10-27T08:01:00.000Z");

    @Autowired
    private BalanceService service;

    @Autowired
    private BalanceRepository balances;

    @Autowired
    private TransferRepository transfers;

    private static BalanceUpdate update(long seq, Instant ts, long free, long locked, long realized,
            String transferId) {
        return new BalanceUpdate(ACCOUNT, seq, ts, free, locked, realized, transferId);
    }

    private Balance row() {
        return balances.find(ACCOUNT).orElseThrow();
    }

    @Test
    void firstUpdateCreatesTheRow() {
        service.apply(update(1, T1, 100, 20, 5, null));

        Balance balance = row();
        assertEquals(100, balance.getCashFreeMinor());
        assertEquals(20, balance.getCashLockedMinor());
        assertEquals(5, balance.getRealizedPnlMinor());
        assertEquals(1, balance.getLastAppliedSeq());
        assertEquals(T1, balance.getUpdatedAt());
    }

    @Test
    void newerSeqReplacesTheBalances() {
        service.apply(update(1, T1, 100, 20, 5, null));
        service.apply(update(2, T2, 200, 30, 10, null));

        Balance balance = row();
        assertEquals(200, balance.getCashFreeMinor());
        assertEquals(30, balance.getCashLockedMinor());
        assertEquals(10, balance.getRealizedPnlMinor());
        assertEquals(2, balance.getLastAppliedSeq());
        assertEquals(T2, balance.getUpdatedAt());
    }

    @Test
    void sameSeqAgainChangesNothing() {
        service.apply(update(1, T1, 100, 20, 5, null));
        service.apply(update(1, T2, 200, 30, 10, null));

        assertEquals(100, row().getCashFreeMinor());
        assertEquals(1, row().getLastAppliedSeq());
        assertEquals(T1, row().getUpdatedAt());
    }

    @Test
    void olderSeqAfterANewerOneChangesNothing() {
        service.apply(update(2, T2, 200, 30, 10, null));
        service.apply(update(1, T1, 100, 20, 5, null));

        assertEquals(200, row().getCashFreeMinor());
        assertEquals(2, row().getLastAppliedSeq());
        assertEquals(T2, row().getUpdatedAt());
    }

    @Test
    void aGapIsAppliedAndSeqJumps() {
        service.apply(update(1, T1, 100, 20, 5, null));
        service.apply(update(8, T2, 800, 80, 40, null));

        assertEquals(800, row().getCashFreeMinor());
        assertEquals(8, row().getLastAppliedSeq());
    }

    @Test
    void transferUpdateCompletesAPendingTransfer() {
        service.apply(update(1, T1, 100, 20, 5, null));
        transfers.insertIfAbsent(Transfer.newPending(ACCOUNT, "wd-1", TransferKind.WITHDRAW, 10, T1));

        service.apply(update(2, T2, 90, 20, 5, "wd-1"));

        Transfer transfer = transfers.find(ACCOUNT, "wd-1").orElseThrow();
        assertEquals(TransferStatus.COMPLETED, transfer.status());
        assertEquals(T2, transfer.completedAt());
    }

    @Test
    void transferUpdateLeavesFinishedTransfersAlone() {
        service.apply(update(1, T1, 100, 20, 5, null));
        transfers.insertIfAbsent(Transfer.newPending(ACCOUNT, "done", TransferKind.WITHDRAW, 10, T1));
        transfers.markCompleted(ACCOUNT, "done", T1);
        transfers.insertIfAbsent(Transfer.newPending(ACCOUNT, "rejected", TransferKind.WITHDRAW, 10, T1));
        transfers.markRejected(ACCOUNT, "rejected", "declined", 10L, T1);

        service.apply(update(2, T2, 90, 20, 5, "done"));
        service.apply(update(3, T2, 80, 20, 5, "rejected"));

        Transfer completed = transfers.find(ACCOUNT, "done").orElseThrow();
        assertEquals(TransferStatus.COMPLETED, completed.status());
        assertEquals(T1, completed.completedAt());
        Transfer rejected = transfers.find(ACCOUNT, "rejected").orElseThrow();
        assertEquals(TransferStatus.REJECTED, rejected.status());
        assertEquals(T1, rejected.completedAt());
    }

    @Test
    void firstEverUpdateCarryingATransferCompletesIt() {
        // the season grant is usually the very first thing that happens to an account
        transfers.insertIfAbsent(Transfer.newPending(ACCOUNT, "grant-s1-42", TransferKind.DEPOSIT, 100, T1));

        assertEquals(ApplyOutcome.APPLIED, service.apply(update(1, T2, 100, 0, 0, "grant-s1-42")));

        assertEquals(1, row().getLastAppliedSeq());
        Transfer grant = transfers.find(ACCOUNT, "grant-s1-42").orElseThrow();
        assertEquals(TransferStatus.COMPLETED, grant.status());
        assertEquals(T2, grant.completedAt());
    }

    @Test
    void transferUpdateWithoutATransferRowStillAppliesTheBalance() {
        service.apply(update(1, T1, 100, 20, 5, null));
        service.apply(update(2, T2, 90, 20, 5, "deposit-by-someone-else"));

        assertEquals(90, row().getCashFreeMinor());
        assertEquals(2, row().getLastAppliedSeq());
        assertTrue(transfers.find(ACCOUNT, "deposit-by-someone-else").isEmpty());
    }

    // --- piece 5 core: order rows + the gap mark (option C) ---

    private static final Instant T3 = Instant.parse("2026-10-27T08:02:00.000Z");

    @Autowired
    private OrderRepository orders;

    private static OrderRecord photo(long seq, Instant ts, OrderStatus status, long filled) {
        return new OrderRecord(new OrderKey(ACCOUNT, "abc"), 1, 812, OrderSide.BUY, OrderType.LIMIT,
                OrderTimeInForce.GTC, 100_00, null, 10, filled, filled * 100_00, status, null, ts, ts, seq);
    }

    private static BalanceUpdate withOrder(long seq, Instant ts, OrderStatus status, long filled) {
        return new BalanceUpdate(ACCOUNT, seq, ts, 100, 20, 5, null, photo(seq, ts, status, filled));
    }

    private OrderRecord orderRow() {
        return orders.find(ACCOUNT, "abc").orElseThrow();
    }

    @Test
    void appliedUpdateWithAnOrderCreatesItsRow() {
        service.apply(update(1, T1, 100, 20, 5, null));
        service.apply(withOrder(2, T2, OrderStatus.NEW, 0));

        OrderRecord o = orderRow();
        assertEquals(OrderStatus.NEW, o.getStatus());
        assertEquals(T2, o.getCreatedAt());
        assertEquals(2, o.getLastSeq());
    }

    @Test
    void firstEverUpdateCarryingAnOrderCreatesBothRows() {
        service.apply(withOrder(1, T1, OrderStatus.NEW, 0));

        assertEquals(1, row().getLastAppliedSeq());
        assertEquals(OrderStatus.NEW, orderRow().getStatus());
    }

    @Test
    void laterPhotoOverwritesTheRowButKeepsCreatedAt() {
        service.apply(withOrder(1, T1, OrderStatus.NEW, 0));
        service.apply(withOrder(2, T2, OrderStatus.PARTIALLY_FILLED, 4));

        OrderRecord o = orderRow();
        assertEquals(OrderStatus.PARTIALLY_FILLED, o.getStatus());
        assertEquals(4, o.getFilledQuantity());
        assertEquals(T1, o.getCreatedAt());
        assertEquals(T2, o.getUpdatedAt());
    }

    @Test
    void skippedUpdateLeavesTheOrderRowAlone() {
        service.apply(withOrder(1, T1, OrderStatus.NEW, 0));
        service.apply(withOrder(2, T2, OrderStatus.FILLED, 10));
        service.apply(withOrder(1, T1, OrderStatus.NEW, 0));   // Kafka redelivers an old photo

        assertEquals(OrderStatus.FILLED, orderRow().getStatus());
        assertEquals(2, orderRow().getLastSeq());
    }

    @Test
    void noGapLeavesTheMarkEmpty() {
        service.apply(update(1, T1, 100, 20, 5, null));
        service.apply(update(2, T2, 90, 20, 5, null));

        assertNull(row().getHistoryGapSeq());
    }

    @Test
    void aGapSetsTheMark() {
        service.apply(update(1, T1, 100, 20, 5, null));
        service.apply(update(3, T2, 90, 20, 5, null));

        assertEquals(3L, row().getHistoryGapSeq());
        assertEquals(3, row().getLastAppliedSeq());
    }

    @Test
    void theFirstGapIsKept() {   // your rule: change this test if you decide to keep the latest gap instead
        service.apply(update(1, T1, 100, 20, 5, null));
        service.apply(update(3, T2, 90, 20, 5, null));
        service.apply(update(6, T3, 80, 20, 5, null));

        assertEquals(3L, row().getHistoryGapSeq());
    }

    @Test
    void laterUpdatesKeepTheMark() {
        service.apply(update(1, T1, 100, 20, 5, null));
        service.apply(update(3, T2, 90, 20, 5, null));
        service.apply(update(4, T3, 80, 20, 5, null));

        assertEquals(3L, row().getHistoryGapSeq());
    }
}
