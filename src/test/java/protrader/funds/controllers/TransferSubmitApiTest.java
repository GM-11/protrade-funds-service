package protrader.funds.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import protrader.funds.entity.TransferKind;
import protrader.funds.grpc.TransferAnswer;
import protrader.funds.grpc.TransferAnswer.Outcome;
import protrader.funds.support.Http;
import protrader.funds.support.IntegrationTest;

/**
 * What the HTTP caller is told once the cash owner has (or hasn't) answered.
 *
 *  - APPLIED          → 201 completed; retry → 200 identical; cash owner asked once
 *  - INSUFFICIENT_CASH → 403 insufficient_cash + shortfall; a retry gets the same 403 without asking again;
 *                        the transfer reads back as rejected
 *  - UNKNOWN_ACCOUNT  → 403 account_not_found
 *  - no answer        → 201 pending; a later retry that gets an answer → 200 completed
 *  - internal deposit → 201 completed, sent to the cash owner as a DEPOSIT
 */
class TransferSubmitApiTest extends IntegrationTest {

    private static final long ALICE = 1000;
    private static final String WITHDRAW = "{\"transfer_id\":\"wd-7\",\"amount\":\"300.00\"}";

    @Test
    void appliedIsCreatedCompletedAndARetryIsTheSame() {
        Http.Response first = as(ALICE).post("/v2/funds/withdrawals", WITHDRAW);
        assertEquals(201, first.status(), first.body());
        assertEquals("completed", first.json().get("status").asString());
        assertFalse(first.json().get("completed_at").isNull());

        Http.Response retry = as(ALICE).post("/v2/funds/withdrawals", WITHDRAW);
        assertEquals(200, retry.status());
        assertEquals(first.json(), retry.json());
        assertEquals(1, cashOwner.calls());
    }

    @Test
    void insufficientCashIs403WithTheShortfallAndStaysSo() {
        cashOwner.answer(new TransferAnswer(Outcome.INSUFFICIENT_CASH, 12_000));

        Http.Response first = as(ALICE).post("/v2/funds/withdrawals", WITHDRAW);
        assertEquals(403, first.status(), first.body());
        assertEquals("insufficient_cash", first.error());
        assertEquals("120.00", first.json().get("shortfall").asString());

        Http.Response retry = as(ALICE).post("/v2/funds/withdrawals", WITHDRAW);
        assertEquals(403, retry.status());
        assertEquals(first.json(), retry.json());
        assertEquals(1, cashOwner.calls());

        Http.Response read = as(ALICE).get("/v2/funds/transfers/wd-7");
        assertEquals(200, read.status());
        assertEquals("rejected", read.json().get("status").asString());
        assertEquals("insufficient_cash", read.json().get("reason").asString());
    }

    @Test
    void unknownAccountIs403AccountNotFound() {
        cashOwner.answer(TransferAnswer.of(Outcome.UNKNOWN_ACCOUNT));

        Http.Response r = as(ALICE).post("/v2/funds/withdrawals", WITHDRAW);
        assertEquals(403, r.status(), r.body());
        assertEquals("account_not_found", r.error());
    }

    @Test
    void noAnswerIsCreatedPendingAndAnAnsweredRetryCompletesIt() {
        cashOwner.unavailable();
        Http.Response first = as(ALICE).post("/v2/funds/withdrawals", WITHDRAW);
        assertEquals(201, first.status(), first.body());
        assertEquals("pending", first.json().get("status").asString());
        assertTrue(first.json().get("completed_at").isNull());

        cashOwner.answer(TransferAnswer.of(Outcome.DUPLICATE));
        Http.Response retry = as(ALICE).post("/v2/funds/withdrawals", WITHDRAW);
        assertEquals(200, retry.status(), retry.body());
        assertEquals("completed", retry.json().get("status").asString());
        assertEquals(2, cashOwner.calls());
    }

    @Test
    void internalDepositIsSentToTheCashOwnerAsADeposit() {
        Http.Response r = internal().post("/internal/v1/funds/deposits",
                "{\"account_id\":\"1000\",\"transfer_id\":\"grant-s1-1000\",\"amount\":\"100000.00\"}");
        assertEquals(201, r.status(), r.body());
        assertEquals("completed", r.json().get("status").asString());

        assertEquals(1, cashOwner.calls());
        var sent = cashOwner.requests().get(0);
        assertEquals(TransferKind.DEPOSIT, sent.kind());
        assertEquals(10_000_000, sent.amountMinor());
    }
}
