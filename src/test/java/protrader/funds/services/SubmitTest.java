package protrader.funds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import protrader.funds.entity.Transfer;
import protrader.funds.entity.TransferKind;
import protrader.funds.entity.TransferStatus;
import protrader.funds.grpc.TransferAnswer;
import protrader.funds.grpc.TransferAnswer.Outcome;
import protrader.funds.repositories.TransferRepository;
import protrader.funds.support.IntegrationTest;

/**
 * Piece 2 — TransferService.submit, against real Postgres and a fake cash owner.
 *
 *  1. APPLIED                      → COMPLETED, completed_at = now, created_at unchanged, equals the stored row
 *  2. DUPLICATE                    → COMPLETED
 *  3. INSUFFICIENT_CASH(12000)     → REJECTED, reason insufficient_cash, shortfall 12000
 *  4. UNKNOWN_ACCOUNT              → REJECTED, reason unknown_account, no shortfall
 *  5. no answer                    → still PENDING, stored row unchanged
 *  6. already COMPLETED            → returned as is, cash owner not asked again
 *  7. no answer, then DUPLICATE    → COMPLETED after the second submit
 *  8. someone else answered first  → their answer stays, ours is ignored
 *  9. no transaction open while the cash owner is being asked
 * 10. a DEPOSIT is sent with the same id, kind and amount
 */
class SubmitTest extends IntegrationTest {

    private static final Instant T0 = Instant.parse("2026-10-25T10:00:00.123Z");
    private static final Instant T1 = Instant.parse("2026-10-25T10:00:01.456Z");
    private static final Instant T2 = Instant.parse("2026-10-25T10:00:02.789Z");

    @Autowired
    TransferService service;

    @Autowired
    TransferRepository repository;

    private Transfer created(TransferKind kind, long amountMinor) {
        return service.create(1000, "wd-1", kind, amountMinor, T0).transfer();
    }

    private Transfer stored() {
        return service.find(1000, "wd-1").orElseThrow();
    }

    @Test
    void appliedCompletesTheTransfer() {
        Transfer pending = created(TransferKind.WITHDRAW, 30_000);

        Transfer result = service.submit(pending, T1);

        assertEquals(TransferStatus.COMPLETED, result.status());
        assertEquals(T1, result.completedAt());
        assertEquals(T0, result.createdAt());
        assertNull(result.reason());
        assertEquals(stored(), result);
        assertEquals(1, cashOwner.calls());
    }

    @Test
    void duplicateAlsoCompletesTheTransfer() {
        cashOwner.answer(TransferAnswer.of(Outcome.DUPLICATE));

        Transfer result = service.submit(created(TransferKind.WITHDRAW, 30_000), T1);

        assertEquals(TransferStatus.COMPLETED, result.status());
        assertEquals(T1, result.completedAt());
    }

    @Test
    void insufficientCashRejectsWithTheShortfall() {
        cashOwner.answer(new TransferAnswer(Outcome.INSUFFICIENT_CASH, 12_000));

        Transfer result = service.submit(created(TransferKind.WITHDRAW, 30_000), T1);

        assertEquals(TransferStatus.REJECTED, result.status());
        assertEquals("insufficient_cash", result.reason());
        assertEquals(12_000L, result.shortfallMinor());
        assertEquals(T1, result.completedAt());
        assertEquals(stored(), result);
    }

    @Test
    void unknownAccountRejects() {
        cashOwner.answer(TransferAnswer.of(Outcome.UNKNOWN_ACCOUNT));

        Transfer result = service.submit(created(TransferKind.WITHDRAW, 30_000), T1);

        assertEquals(TransferStatus.REJECTED, result.status());
        assertEquals("unknown_account", result.reason());
        assertNull(result.shortfallMinor());
        assertEquals(stored(), result);
    }

    @Test
    void noAnswerLeavesItPending() {
        Transfer pending = created(TransferKind.WITHDRAW, 30_000);
        cashOwner.unavailable();

        Transfer result = service.submit(pending, T1);

        assertEquals(pending, result);
        assertEquals(pending, stored());
        assertEquals(TransferStatus.PENDING, result.status());
        assertEquals(1, cashOwner.calls());
    }

    @Test
    void anAnsweredTransferIsNotAskedAgain() {
        Transfer done = service.submit(created(TransferKind.WITHDRAW, 30_000), T1);
        assertEquals(1, cashOwner.calls());

        Transfer again = service.submit(done, T2);

        assertEquals(done, again);
        assertEquals(1, cashOwner.calls());
        assertEquals(T1, stored().completedAt());
    }

    @Test
    void noAnswerNowAnswerLater() {
        Transfer pending = created(TransferKind.WITHDRAW, 30_000);
        cashOwner.unavailable();
        assertEquals(TransferStatus.PENDING, service.submit(pending, T1).status());

        cashOwner.answer(TransferAnswer.of(Outcome.DUPLICATE));
        Transfer result = service.submit(pending, T2);

        assertEquals(TransferStatus.COMPLETED, result.status());
        assertEquals(T2, result.completedAt());
        assertEquals(2, cashOwner.calls());
    }

    @Test
    void theFirstRecordedAnswerWins() {
        Transfer pending = created(TransferKind.WITHDRAW, 30_000);
        // another worker records "completed" while we are waiting for the cash owner
        cashOwner.beforeAnswering(t -> repository.markCompleted(t.accountId(), t.transferId(), T1));
        cashOwner.answer(new TransferAnswer(Outcome.INSUFFICIENT_CASH, 12_000));

        Transfer result = service.submit(pending, T2);

        assertEquals(TransferStatus.COMPLETED, result.status());
        assertEquals(T1, result.completedAt());
        assertNull(result.reason());
        assertNull(result.shortfallMinor());
        assertEquals(stored(), result);
    }

    @Test
    void noTransactionIsOpenWhileAskingTheCashOwner() {
        AtomicBoolean sawTransaction = new AtomicBoolean(true);
        cashOwner.beforeAnswering(t -> sawTransaction.set(TransactionSynchronizationManager.isActualTransactionActive()));

        service.submit(created(TransferKind.WITHDRAW, 30_000), T1);

        assertEquals(1, cashOwner.calls());
        assertFalse(sawTransaction.get());
    }

    @Test
    void aDepositIsSentAsADeposit() {
        Transfer pending = created(TransferKind.DEPOSIT, 10_000_000);

        service.submit(pending, T1);

        assertEquals(1, cashOwner.calls());
        Transfer sent = cashOwner.requests().get(0);
        assertEquals("wd-1", sent.transferId());
        assertEquals(1000, sent.accountId());
        assertEquals(TransferKind.DEPOSIT, sent.kind());
        assertEquals(10_000_000, sent.amountMinor());
    }
}
