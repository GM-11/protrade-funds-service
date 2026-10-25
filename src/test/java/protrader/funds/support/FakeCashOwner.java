package protrader.funds.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import protrader.funds.entity.Transfer;
import protrader.funds.grpc.CashOwnerClient;
import protrader.funds.grpc.CashOwnerUnavailableException;
import protrader.funds.grpc.TransferAnswer;

/** Stands in for the cash owner: answers what the test says, remembers what it was asked. */
public class FakeCashOwner implements CashOwnerClient {

    private final List<Transfer> requests = new CopyOnWriteArrayList<>();
    private volatile TransferAnswer answer = TransferAnswer.of(TransferAnswer.Outcome.APPLIED);
    private volatile boolean unavailable;
    private volatile Consumer<Transfer> beforeAnswering;

    @Override
    public TransferAnswer apply(Transfer transfer) {
        requests.add(transfer);
        Consumer<Transfer> hook = beforeAnswering;
        if (hook != null) {
            hook.accept(transfer);
        }
        if (unavailable) {
            throw new CashOwnerUnavailableException("fake cash owner is unavailable");
        }
        return answer;
    }

    /** From now on answer this (and stop being unavailable). */
    public void answer(TransferAnswer answer) {
        this.answer = answer;
        this.unavailable = false;
    }

    /** From now on give no answer. */
    public void unavailable() {
        this.unavailable = true;
    }

    /** Runs just before each answer, with the transfer being asked about. */
    public void beforeAnswering(Consumer<Transfer> hook) {
        this.beforeAnswering = hook;
    }

    public int calls() {
        return requests.size();
    }

    public List<Transfer> requests() {
        return List.copyOf(requests);
    }

    public void reset() {
        requests.clear();
        answer = TransferAnswer.of(TransferAnswer.Outcome.APPLIED);
        unavailable = false;
        beforeAnswering = null;
    }
}
