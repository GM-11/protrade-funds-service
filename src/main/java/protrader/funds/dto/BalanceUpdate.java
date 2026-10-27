package protrader.funds.dto;

import java.time.Instant;

import protrader.funds.entity.OrderRecord;

/**
 * One AccountUpdate, reduced to what Funds keeps. transferId is non-null only
 * when the cause is a TransferEvent;
 * order is non-null only when the cause is an OrderEvent (its photo after the
 * change, lastSeq = accountSeq).
 */
public record BalanceUpdate(long accountId, long accountSeq, Instant ts,
        long cashFreeMinor, long cashLockedMinor, long realizedPnlMinor, String transferId, OrderRecord order) {

    /** No order photo (transfers, and older tests). */
    public BalanceUpdate(long accountId, long accountSeq, Instant ts,
            long cashFreeMinor, long cashLockedMinor, long realizedPnlMinor, String transferId) {
        this(accountId, accountSeq, ts, cashFreeMinor, cashLockedMinor, realizedPnlMinor, transferId, null);
    }
}
