package protrader.funds.services;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import protrader.funds.dto.CreateResult;
import protrader.funds.entity.Transfer;
import protrader.funds.entity.TransferKind;
import protrader.funds.grpc.CashOwnerClient;
import protrader.funds.repositories.TransferRepository;

@Service
public class TransferService {

    private final TransferRepository transfers;
    private final CashOwnerClient cashOwner;

    public TransferService(TransferRepository transfers, CashOwnerClient cashOwner) {
        this.transfers = transfers;
        this.cashOwner = cashOwner;
    }

    @Transactional
    public CreateResult create(long accountId, String transferId, TransferKind kind, long amountMinor, Instant now) {
        Transfer transfer = Transfer.newPending(accountId, transferId, kind, amountMinor, now);

        int inserted = transfers.insertIfAbsent(transfer);
        if (inserted == 1) {
            return new CreateResult(transfer, true);
        }
        Transfer existing = transfers.find(accountId, transferId)
                .orElseThrow(() -> new IllegalStateException(
                        "No existing transfer found for accountId=" + accountId + " transferId=" + transferId));

        if (kind != existing.kind() || amountMinor != existing.amountMinor()) {
            throw new TransferIdReusedException(accountId, transferId);
        }
        return new CreateResult(existing, false);
    }

    /**
     * Asks the cash owner about a transfer and records its answer. Rules:
     * <ol>
     * <li>Not PENDING → return it as is (don't ask again).</li>
     * <li>{@code cashOwner.apply(t)}; {@code CashOwnerUnavailableException} → return {@code t} unchanged (stays
     * PENDING).</li>
     * <li>APPLIED / DUPLICATE → {@code markCompleted}; INSUFFICIENT_CASH →
     * {@code markRejected(..., "insufficient_cash", shortfall, now)}; UNKNOWN_ACCOUNT →
     * {@code markRejected(..., "unknown_account", null, now)}.</li>
     * <li>Re-read with {@code find(...)} and return it; ignore the 1/0 (0 = someone else answered first, the
     * re-read shows theirs).</li>
     * <li>NOT {@code @Transactional}: no database transaction is open during the network call.</li>
     * </ol>
     */
    public Transfer submit(Transfer transfer, Instant now) {
        // TODO(Gopal): piece 2
        throw new UnsupportedOperationException("TransferService.submit is not written yet (piece 2)");
    }

    public Optional<Transfer> find(long accountId, String transferId) {
        return transfers.find(accountId, transferId);
    }

    public List<Transfer> page(long accountId, int limit, TransferRepository.Cursor after) {
        return transfers.page(accountId, limit, after);
    }
}
