package protrader.funds.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Table("orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class OrderRecord {

    @Id
    private OrderKey id;
    private int symbol;
    private long orderId;
    private OrderSide side;
    private OrderType orderType;
    private OrderTimeInForce timeInForce;
    private long priceMinor;
    private Long stopPriceMinor;
    private long quantity;
    private long filledQuantity;
    private long filledNotionalMinor;
    private OrderStatus status;
    private String rejectReason;
    private Instant createdAt;
    private Instant updatedAt;
    private long lastSeq;

    public long getAccountId() {
        return id.getAccountId();
    }

    public String getClientOrderId() {
        return id.getClientOrderId();
    }
}
