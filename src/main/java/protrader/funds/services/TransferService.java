package protrader.funds.services;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import protrader.funds.dto.CreateResult;
import protrader.funds.entity.Transfer;
import protrader.funds.entity.TransferKind;
import protrader.funds.entity.TransferStatus;
import protrader.funds.grpc.CashOwnerClient;
import protrader.funds.grpc.CashOwnerUnavailableException;
import protrader.funds.grpc.TransferAnswer;
import protrader.funds.repositories.TransferRepository;

@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final CashOwnerClient cashOwner;

    public TransferService(TransferRepository transferRepository, CashOwnerClient cashOwner) {
        this.transferRepository = transferRepository;
        this.cashOwner = cashOwner;
    }

    @Transactional
    public CreateResult create(long accountId, String transferId, TransferKind kind, long amountMinor, Instant now) {
        Transfer transfer = Transfer.newPending(accountId, transferId, kind, amountMinor, now);

        int inserted = transferRepository.insertIfAbsent(transfer);
        if (inserted == 1) {
            return new CreateResult(transfer, true);
        }
        Transfer existing = transferRepository.find(accountId, transferId)
                .orElseThrow(() -> new IllegalStateException(
                        "No existing transfer found for accountId=" + accountId + " transferId=" + transferId));

        if (kind != existing.kind() || amountMinor != existing.amountMinor()) {
            throw new TransferIdReusedException(accountId, transferId);
        }
        return new CreateResult(existing, false);
    }

    public Transfer submit(Transfer transfer, Instant now) {
        Transfer existing = loadTransfer(transfer.accountId(), transfer.transferId());
        if (existing.status() != TransferStatus.PENDING) {
            return existing;
        }

        try {
            TransferAnswer answer = cashOwner.apply(transfer);
            switch (answer.outcome()) {
                case APPLIED, DUPLICATE ->
                    transferRepository.markCompleted(transfer.accountId(), transfer.transferId(), now);
                case INSUFFICIENT_CASH ->
                    transferRepository.markRejected(transfer.accountId(), transfer.transferId(), "insufficient_cash",
                            answer.shortfallMinor(), now);
                case UNKNOWN_ACCOUNT -> transferRepository.markRejected(transfer.accountId(), transfer.transferId(),
                        "unknown_account", null,
                        now);
            }

        } catch (CashOwnerUnavailableException e) {
            return transfer;
        }

        return loadTransfer(transfer.accountId(), transfer.transferId());
    }

    private Transfer loadTransfer(long accountId, String transferId) {
        return transferRepository.find(accountId, transferId)
                .orElseThrow(() -> new IllegalStateException("No transfer found for accountId=" + accountId
                        + " transferId=" + transferId));
    }

    public Optional<Transfer> find(long accountId, String transferId) {
        return transferRepository.find(accountId, transferId);
    }

    public List<Transfer> page(long accountId, int limit, TransferRepository.Cursor after) {
        return transferRepository.page(accountId, limit, after);
    }
}
