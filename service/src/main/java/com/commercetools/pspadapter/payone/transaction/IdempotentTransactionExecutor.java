package com.commercetools.pspadapter.payone.transaction;

import com.commercetools.pspadapter.payone.domain.ctp.CustomTypeBuilder;
import com.commercetools.pspadapter.payone.domain.ctp.PaymentWithCartLike;
import com.commercetools.pspadapter.payone.mapping.CustomFieldKeys;
import com.google.common.cache.LoadingCache;
import io.sphere.sdk.payments.Transaction;
import io.sphere.sdk.payments.TransactionType;
import io.sphere.sdk.types.CustomFields;
import io.sphere.sdk.types.Type;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Idempotently executes a Transaction of one Type (e.g. Charge) for a specific PaymentWithCartLike Method.
 * <p>
 * If no Transaction of that type in State Pending exists, the same PaymentWithCartLike is returned.
 */
public abstract class IdempotentTransactionExecutor implements TransactionExecutor {

    private LoadingCache<String, Type> typeCache;

    public IdempotentTransactionExecutor(@Nonnull final LoadingCache<String, Type> typeCache) {
        this.typeCache = typeCache;
    }

    /**
     * @return The Type that is supported.
     */
    @Nonnull
    public abstract TransactionType supportedTransactionType();

    /**
     * Executes a transaction idempotently.
     *
     * @param paymentWithCartLike payment/cart, for which transaction is executed
     * @param transaction         transaction to execute
     * @return A new version of the PaymentWithCartLike.
     */
    @Override
    @Nonnull
    public PaymentWithCartLike executeTransaction(@Nonnull PaymentWithCartLike paymentWithCartLike,
                                                  @Nonnull Transaction transaction) {
        if (transaction.getType() != supportedTransactionType()) {
            throw new IllegalArgumentException("Unsupported Transaction Type");
        }

        if (wasExecuted(paymentWithCartLike, transaction)) {
            return paymentWithCartLike;
        }

        return findLastExecutionAttempt(paymentWithCartLike, transaction)
                .map(customFields -> paymentWithCartLike)
                .orElseGet(() -> attemptFirstExecution(paymentWithCartLike, transaction));
    }

    /**
     * Whether the transaction was executed and nothing else can be done by the executor.
     *
     * @param paymentWithCartLike payment/cart, which has to be verified
     * @param transaction         transaction from {@code paymentWithCartLike}, which has to be verified
     * @return <b>true</b> if transaction has been already executed
     */
    protected abstract boolean wasExecuted(PaymentWithCartLike paymentWithCartLike, Transaction transaction);

    /**
     * Tries to execute the transaction for the first time.
     * To ensure Idempotency, an InterfaceInteraction is first added to the PaymentWithCartLike that can be found later.
     *
     * @param paymentWithCartLike
     * @param transaction
     * @return A new version of the PaymentWithCartLike. If the attempt has concluded, the state of the Transaction is now either Success or Failure.
     */
    protected abstract PaymentWithCartLike attemptFirstExecution(PaymentWithCartLike paymentWithCartLike, Transaction transaction);

    /**
     * Finds a previous execution attempt for the transaction. If there are multiple ones, it selects the last one.
     *
     * @param paymentWithCartLike
     * @param transaction
     * @return An attempt, or Optional.empty if there was no previous attempt
     */
    protected abstract Optional<CustomFields> findLastExecutionAttempt(PaymentWithCartLike paymentWithCartLike, Transaction transaction);

    /**
     * Determines the next sequence number to use from already received notifications.
     *
     * @param paymentWithCartLike the payment with cart/order to search in
     * @return 0 if no notifications received yet, else the highest sequence number received + 1
     */
    protected int getNextSequenceNumber(final PaymentWithCartLike paymentWithCartLike) {
        Predicate<String> isInteger = (i) -> i != null && i.matches("-?[0-9]+");

        return IntStream.concat(
            getCustomFieldsOfType(paymentWithCartLike, CustomTypeBuilder.PAYONE_INTERACTION_NOTIFICATION)
                .map(f -> f.getFieldAsString(CustomFieldKeys.SEQUENCE_NUMBER_FIELD))
                .filter(isInteger)
                .mapToInt(Integer::parseInt),
            paymentWithCartLike
                .getPayment()
                .getTransactions()
                .stream()
                .map(t -> StringUtils.trim(t.getInteractionId()))
                .filter(isInteger)
                .mapToInt(Integer::parseInt)
        )
        .map(i -> i + 1)
        .max()
        .orElse(0);
    }

    protected Stream<CustomFields> getCustomFieldsOfType(PaymentWithCartLike paymentWithCartLike, String... typeKeys) {
        return paymentWithCartLike
                .getPayment()
                .getInterfaceInteractions()
                .stream()
                .filter(i -> Arrays.stream(typeKeys)
                        .map(t -> getTypeCache().getUnchecked(t).toReference())
                        .anyMatch(t -> t.getId().equals(i.getType().getId())));
    }

    private LoadingCache<String, Type> getTypeCache() {
        return this.typeCache;
    }
}
