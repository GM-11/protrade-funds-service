package protrader.funds.repositories;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import protrader.funds.entity.OrderKey;
import protrader.funds.entity.OrderRecord;

public interface OrderRepository extends Repository<OrderRecord, OrderKey> {

    /**
     * Inserts the order, or overwrites every column of an existing row except created_at (the first photo's time
     * stays). Unconditional: the caller decides whether this photo is newer. Returns 1.
     */
    default int upsert(OrderRecord o) {
        return upsert(o.getAccountId(), o.getClientOrderId(), o.getSymbol(), o.getOrderId(), o.getSide().name(),
                o.getOrderType().name(), o.getTimeInForce().name(), o.getPriceMinor(), o.getStopPriceMinor(), o.getQuantity(),
                o.getFilledQuantity(), o.getFilledNotionalMinor(), o.getStatus().name(), o.getRejectReason(), o.getCreatedAt(),
                o.getUpdatedAt(), o.getLastSeq());
    }

    @Modifying
    @Query("""
            insert into orders (account_id, client_order_id, symbol, order_id, side, order_type, time_in_force,
                                price_minor, stop_price_minor, quantity, filled_quantity, filled_notional_minor,
                                status, reject_reason, created_at, updated_at, last_seq)
            values (:accountId, :clientOrderId, :symbol, :orderId, :side, :orderType, :timeInForce,
                    :priceMinor, :stopPriceMinor, :quantity, :filledQuantity, :filledNotionalMinor,
                    :status, :rejectReason, :createdAt, :updatedAt, :lastSeq)
            on conflict (account_id, client_order_id) do update set
                symbol = excluded.symbol, order_id = excluded.order_id, side = excluded.side,
                order_type = excluded.order_type, time_in_force = excluded.time_in_force,
                price_minor = excluded.price_minor, stop_price_minor = excluded.stop_price_minor,
                quantity = excluded.quantity, filled_quantity = excluded.filled_quantity,
                filled_notional_minor = excluded.filled_notional_minor, status = excluded.status,
                reject_reason = excluded.reject_reason, updated_at = excluded.updated_at,
                last_seq = excluded.last_seq
            """)
    int upsert(@Param("accountId") long accountId, @Param("clientOrderId") String clientOrderId,
            @Param("symbol") int symbol, @Param("orderId") long orderId, @Param("side") String side,
            @Param("orderType") String orderType, @Param("timeInForce") String timeInForce,
            @Param("priceMinor") long priceMinor, @Param("stopPriceMinor") Long stopPriceMinor,
            @Param("quantity") long quantity, @Param("filledQuantity") long filledQuantity,
            @Param("filledNotionalMinor") long filledNotionalMinor, @Param("status") String status,
            @Param("rejectReason") String rejectReason, @Param("createdAt") Instant createdAt,
            @Param("updatedAt") Instant updatedAt, @Param("lastSeq") long lastSeq);

    /** One order of one account. */
    @Query("select * from orders where account_id = :accountId and client_order_id = :clientOrderId")
    Optional<OrderRecord> find(@Param("accountId") long accountId, @Param("clientOrderId") String clientOrderId);

    /** Newest first, only rows whose status is in {@code statuses} (enum names). Keyset paging like transfers. */
    default List<OrderRecord> page(long accountId, Collection<String> statuses, int limit, Cursor after) {
        return after == null
                ? firstPage(accountId, statuses, limit)
                : pageAfter(accountId, statuses, after.createdAt(), after.clientOrderId(), limit);
    }

    @Query("""
            select * from orders where account_id = :accountId and status in (:statuses)
            order by created_at desc, client_order_id desc limit :limit
            """)
    List<OrderRecord> firstPage(@Param("accountId") long accountId, @Param("statuses") Collection<String> statuses,
            @Param("limit") int limit);

    @Query("""
            select * from orders
            where account_id = :accountId and status in (:statuses)
              and (created_at, client_order_id) < (:createdAt, :clientOrderId)
            order by created_at desc, client_order_id desc limit :limit
            """)
    List<OrderRecord> pageAfter(@Param("accountId") long accountId, @Param("statuses") Collection<String> statuses,
            @Param("createdAt") Instant createdAt, @Param("clientOrderId") String clientOrderId,
            @Param("limit") int limit);

    record Cursor(Instant createdAt, String clientOrderId) {
    }
}
