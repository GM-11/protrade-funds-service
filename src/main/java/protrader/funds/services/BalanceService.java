package protrader.funds.services;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import protrader.funds.dto.ApplyOutcome;
import protrader.funds.dto.BalanceUpdate;
import protrader.funds.entity.Balance;
import protrader.funds.repositories.BalanceRepository;
import protrader.funds.repositories.OrderRepository;
import protrader.funds.repositories.TransferRepository;

@Service
public class BalanceService {

    private static final Logger log = LoggerFactory.getLogger(BalanceService.class);

    private final BalanceRepository balanceRepository;
    private final TransferRepository transferRepository;
    private final OrderRepository orderRepository;

    public BalanceService(BalanceRepository balanceRepository, TransferRepository transferRepository,
            OrderRepository orderRepository) {
        this.balanceRepository = balanceRepository;
        this.transferRepository = transferRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public ApplyOutcome apply(BalanceUpdate update) {
        Optional<Balance> found = balanceRepository.findForUpdate(update.accountId());

        if (found.isEmpty()) {
            Balance fresh = new Balance(update.accountId(), update.cashFreeMinor(), update.cashLockedMinor(),
                    update.realizedPnlMinor(), update.accountSeq(), update.ts(),
                    update.accountSeq() > 1 ? update.accountSeq() : null);
            if (balanceRepository.insertIfAbsent(fresh) == 1) {
                if (update.accountSeq() > 1) {
                    log.error("account {} seq jumped 0 -> {}: order history may be incomplete",
                            update.accountId(), update.accountSeq());
                }
                completeTransfer(update);
                saveOrder(update);
                return ApplyOutcome.APPLIED;
            }

            found = balanceRepository.findForUpdate(update.accountId());
        }

        Balance balance = found.orElseThrow(() -> new IllegalStateException(
                "Balance row not found after attempting to insert it for account " + update.accountId()));

        if (update.accountSeq() <= balance.getLastAppliedSeq()) {
            return ApplyOutcome.SKIPPED_OLD;
        }

        if (update.accountSeq() > balance.getLastAppliedSeq() + 1) {
            log.error("account {} seq jumped {} -> {}: order history may be incomplete",
                    update.accountId(), balance.getLastAppliedSeq(), update.accountSeq());
            if (balance.getHistoryGapSeq() == null) {
                balance.setHistoryGapSeq(update.accountSeq());
            }
        }

        balance.setCashFreeMinor(update.cashFreeMinor());
        balance.setCashLockedMinor(update.cashLockedMinor());
        balance.setRealizedPnlMinor(update.realizedPnlMinor());
        balance.setLastAppliedSeq(update.accountSeq());
        balance.setUpdatedAt(update.ts());
        balanceRepository.update(balance);

        completeTransfer(update);
        saveOrder(update);
        return ApplyOutcome.APPLIED;
    }

    private void completeTransfer(BalanceUpdate update) {
        if (update.transferId() != null) {
            transferRepository.markCompleted(update.accountId(), update.transferId(), update.ts());
        }
    }

    private void saveOrder(BalanceUpdate update) {
        if (update.order() != null) {
            orderRepository.upsert(update.order());
        }
    }
}
