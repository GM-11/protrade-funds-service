package protrader.funds.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Table("balances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Balance {
    @Id
    private long accountId;
    private long cashFreeMinor;
    private long cashLockedMinor;
    private long realizedPnlMinor;
    private long lastAppliedSeq;
    private Instant updatedAt;

}
