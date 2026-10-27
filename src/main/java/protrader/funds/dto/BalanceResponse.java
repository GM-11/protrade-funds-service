package protrader.funds.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import protrader.funds.entity.Balance;
import protrader.funds.utils.Money;
import protrader.funds.utils.Timestamps;

/**
 * GET /v2/funds/balance: {account_id, cash, cash_free, cash_locked,
 * realized_pnl, account_seq, updated_at}.
 */
public record BalanceResponse(
        @JsonProperty("account_id") long accountId,
        @JsonProperty("cash") String cash,
        @JsonProperty("cash_free") String cashFree,
        @JsonProperty("cash_locked") String cashLocked,
        @JsonProperty("realized_pnl") String realizedPnl,
        @JsonProperty("account_seq") long accountSeq,
        @JsonProperty("updated_at") String updatedAt) {

    public static BalanceResponse of(Balance b) {
        return new BalanceResponse(b.getAccountId(), Money.format(b.getCashFreeMinor() + b.getCashLockedMinor()),
                Money.format(b.getCashFreeMinor()), Money.format(b.getCashLockedMinor()),
                Money.format(b.getRealizedPnlMinor()), b.getLastAppliedSeq(), Timestamps.format(b.getUpdatedAt()));
    }

    /** No update seen yet for this account. */
    public static BalanceResponse empty(long accountId) {
        return new BalanceResponse(accountId, "0.00", "0.00", "0.00", "0.00", 0, null);
    }
}
