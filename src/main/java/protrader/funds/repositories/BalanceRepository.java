package protrader.funds.repositories;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import protrader.funds.entity.Balance;

public interface BalanceRepository extends Repository<Balance, Long> {

    @Query("SELECT * FROM balances WHERE account_id = :accountId")
    Optional<Balance> find(@Param("accountId") long accountId);

    @Query("SELECT * FROM balances WHERE account_id = :accountId FOR UPDATE")
    Optional<Balance> findForUpdate(@Param("accountId") long accountId);

    default int insertIfAbsent(Balance b) {
        return insertIfAbsent(b.getAccountId(), b.getCashFreeMinor(), b.getCashLockedMinor(), b.getRealizedPnlMinor(),
                b.getLastAppliedSeq(), b.getUpdatedAt());
    }

    @Modifying
    @Query("""
            INSERT INTO balances (account_id, cash_free_minor, cash_locked_minor, realized_pnl_minor,
                                  last_applied_seq, updated_at)
            VALUES (:accountId, :cashFreeMinor, :cashLockedMinor, :realizedPnlMinor, :lastAppliedSeq, :updatedAt)
            ON CONFLICT (account_id) DO NOTHING
            """)
    int insertIfAbsent(@Param("accountId") long accountId, @Param("cashFreeMinor") long cashFreeMinor,
            @Param("cashLockedMinor") long cashLockedMinor, @Param("realizedPnlMinor") long realizedPnlMinor,
            @Param("lastAppliedSeq") long lastAppliedSeq, @Param("updatedAt") Instant updatedAt);

    default int update(Balance b) {
        return update(b.getAccountId(), b.getCashFreeMinor(), b.getCashLockedMinor(), b.getRealizedPnlMinor(),
                b.getLastAppliedSeq(), b.getUpdatedAt());
    }

    @Modifying
    @Query("""
            UPDATE balances SET cash_free_minor = :cashFreeMinor, cash_locked_minor = :cashLockedMinor,
                                realized_pnl_minor = :realizedPnlMinor, last_applied_seq = :lastAppliedSeq,
                                updated_at = :updatedAt
            WHERE account_id = :accountId
            """)
    int update(@Param("accountId") long accountId, @Param("cashFreeMinor") long cashFreeMinor,
            @Param("cashLockedMinor") long cashLockedMinor, @Param("realizedPnlMinor") long realizedPnlMinor,
            @Param("lastAppliedSeq") long lastAppliedSeq, @Param("updatedAt") Instant updatedAt);
}
