package protrader.funds.kafka;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.google.protobuf.InvalidProtocolBufferException;
import com.protrader.proto.exchange.v1.AccountUpdate;

import protrader.funds.dto.ApplyOutcome;
import protrader.funds.dto.BalanceUpdate;
import protrader.funds.services.BalanceService;

/**
 * Reads cash.account-updates.v1 into the balance copy. A record that can't be
 * parsed or applied throws, which
 * stops the container (KafkaErrorHandling); its offset is not committed, so it
 * is read again after a restart.
 */
@Component
public class AccountUpdatesListener {

    private static final Logger log = LoggerFactory.getLogger(AccountUpdatesListener.class);
    private static final int LOG_EVERY = 1000;

    private final BalanceService service;
    private final AtomicLong applied = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();

    public AccountUpdatesListener(BalanceService service) {
        this.service = service;
    }

    @KafkaListener(id = "account-updates", topics = "${funds.kafka.account-updates-topic}")
    void onRecord(ConsumerRecord<String, byte[]> record) {
        try {
            AccountUpdate update = AccountUpdate.parseFrom(record.value());
            BalanceUpdate balance = AccountUpdateMapper.toBalanceUpdate(update);
            ApplyOutcome outcome = service.apply(balance);
            (outcome == ApplyOutcome.APPLIED ? applied : skipped).incrementAndGet();
            log.debug("account {} seq {} {}", balance.accountId(), balance.accountSeq(), outcome);
            if ((applied.get() + skipped.get()) % LOG_EVERY == 0) {
                log.info("account updates: {} applied, {} skipped", applied.get(), skipped.get());
            }
        } catch (InvalidProtocolBufferException | RuntimeException e) {
            log.error("account update at partition {} offset {} failed: {}", record.partition(), record.offset(),
                    e.toString());
            throw e instanceof RuntimeException re ? re : new IllegalStateException("bad protobuf bytes", e);
        }
    }
}
