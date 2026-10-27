package protrader.funds.repositories;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import protrader.funds.entity.Balance;

public interface BalanceRepository extends Repository<Balance, Long> {

    /** Plain read of one account's row (for the GETs). */
    @Query("select * from balances where account_id = :accountId")
    Optional<Balance> find(@Param("accountId") long accountId);

    /** Reads the row and locks it until the transaction ends. */
    @Query("select * from balances where account_id = :accountId for update")
    Optional<Balance> findForUpdate(@Param("accountId") long accountId);

    /** Inserts the row unless one exists. Returns 1 = inserted, 0 = already there. */
    default int insertIfAbsent(Balance b) {
        return insertIfAbsent(b.getAccountId(), b.getCashFreeMinor(), b.getCashLockedMinor(), b.getRealizedPnlMinor(),
                b.getLastAppliedSeq(), b.getUpdatedAt(), b.getHistoryGapSeq());
    }

    @Modifying
    @Query("""
            insert into balances (account_id, cash_free_minor, cash_locked_minor, realized_pnl_minor,
                                  last_applied_seq, updated_at, history_gap_seq)
            values (:accountId, :cashFreeMinor, :cashLockedMinor, :realizedPnlMinor, :lastAppliedSeq, :updatedAt,
                    :historyGapSeq)
            on conflict (account_id) do nothing
            """)
    int insertIfAbsent(@Param("accountId") long accountId, @Param("cashFreeMinor") long cashFreeMinor,
            @Param("cashLockedMinor") long cashLockedMinor, @Param("realizedPnlMinor") long realizedPnlMinor,
            @Param("lastAppliedSeq") long lastAppliedSeq, @Param("updatedAt") Instant updatedAt,
            @Param("historyGapSeq") Long historyGapSeq);

    /** Overwrites the account's row with this one (all columns). Returns the number of rows changed (0 = no row). */
    default int update(Balance b) {
        return update(b.getAccountId(), b.getCashFreeMinor(), b.getCashLockedMinor(), b.getRealizedPnlMinor(),
                b.getLastAppliedSeq(), b.getUpdatedAt(), b.getHistoryGapSeq());
    }

    @Modifying
    @Query("""
            update balances set cash_free_minor = :cashFreeMinor, cash_locked_minor = :cashLockedMinor,
                                realized_pnl_minor = :realizedPnlMinor, last_applied_seq = :lastAppliedSeq,
                                updated_at = :updatedAt, history_gap_seq = :historyGapSeq
            where account_id = :accountId
            """)
    int update(@Param("accountId") long accountId, @Param("cashFreeMinor") long cashFreeMinor,
            @Param("cashLockedMinor") long cashLockedMinor, @Param("realizedPnlMinor") long realizedPnlMinor,
            @Param("lastAppliedSeq") long lastAppliedSeq, @Param("updatedAt") Instant updatedAt,
            @Param("historyGapSeq") Long historyGapSeq);
}
