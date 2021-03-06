package com.commercetools.pspadapter.payone.notification.common;

import com.commercetools.payments.TransactionStateResolver;
import com.commercetools.pspadapter.payone.domain.payone.model.common.Notification;
import com.commercetools.pspadapter.payone.domain.payone.model.common.NotificationAction;
import com.commercetools.pspadapter.payone.domain.payone.model.common.TransactionStatus;
import com.commercetools.pspadapter.payone.notification.NotificationProcessorBase;
import com.commercetools.pspadapter.tenant.TenantConfig;
import com.commercetools.pspadapter.tenant.TenantFactory;
import com.google.common.collect.ImmutableList;
import io.sphere.sdk.commands.UpdateAction;
import io.sphere.sdk.payments.*;
import io.sphere.sdk.payments.commands.updateactions.AddTransaction;
import io.sphere.sdk.payments.commands.updateactions.ChangeTransactionInteractionId;
import io.sphere.sdk.payments.commands.updateactions.ChangeTransactionState;
import io.sphere.sdk.utils.MoneyImpl;

import javax.annotation.Nonnull;
import javax.money.MonetaryAmount;
import java.util.List;

/**
 * An implementation of a NotificationProcessor specifically for notifications with txaction 'appointed'.
 * Determines the necessary UpdateActions for the payment and applies them.
 *
 * @author fhaertig
 * @author Jan Wolter
 * @since 08.01.16
 */
public class AppointedNotificationProcessor extends NotificationProcessorBase {

    public AppointedNotificationProcessor(TenantFactory tenantFactory, TenantConfig tenantConfig,
                                          TransactionStateResolver transactionStateResolver) {
        super(tenantFactory, tenantConfig, transactionStateResolver);
    }

    @Override
    protected boolean canProcess(final Notification notification) {
        return NotificationAction.APPOINTED.equals(notification.getTxaction());
    }

    @Override
    protected ImmutableList<UpdateAction<Payment>> createPaymentUpdates(final Payment payment,
                                                                        final Notification notification) {
        final ImmutableList.Builder<UpdateAction<Payment>> actionsBuilder = ImmutableList.builder();
        actionsBuilder.addAll(super.createPaymentUpdates(payment, notification));

        final List<Transaction> transactions = payment.getTransactions();
        final String sequenceNumber = toSequenceNumber(notification.getSequencenumber());

        if (findMatchingTransaction(transactions, TransactionType.CHARGE, sequenceNumber).isPresent()) {
            // TODO: https://github.com/commercetools/commercetools-payone-integration/issues/196
            // also: never tested (either unit nor functional)
            return actionsBuilder.build();
        }

        if (sequenceNumber.equals("1")) {

            actionsBuilder.add(matchingChangeInteractionOrChargeTransaction(notification, transactions, sequenceNumber));

            return actionsBuilder.build();
        }

        final MonetaryAmount balance = MoneyImpl.of(notification.getBalance(), notification.getCurrency());
        if (balance.isZero()) {
            return transactions.stream()
                    .filter(t -> t.getType().equals(TransactionType.AUTHORIZATION))
                    .findFirst()
                    .map(transaction -> {
                        //set transactionState if is still not completed and notification has status "complete"
                        if (isNotCompletedTransaction(transaction) &&
                                notification.getTransactionStatus().equals(TransactionStatus.COMPLETED)) {
                            actionsBuilder.add(ChangeTransactionState.of(
                                    notification.getTransactionStatus().getCtTransactionState(), transaction.getId()));
                        }

                        return actionsBuilder;
                    })
                    .orElseGet(() -> {
                        actionsBuilder.add(addAuthorizationTransaction(notification));
                        return actionsBuilder;
                    })
                    .build();
        }

        actionsBuilder.add(addChargePendingTransaction(notification));
        return actionsBuilder.build();
    }

    private UpdateAction<Payment> matchingChangeInteractionOrChargeTransaction(@Nonnull final Notification notification,
                                                                               @Nonnull final List<Transaction> transactions,
                                                                               @Nonnull final String sequenceNumber) {
        // if a CHARGE transaction with "0" interaction id found - update interaction id,
        // otherwise -  create ad new Charge-Pending transaction with interactionId == notification.sequencenumber
        return findMatchingTransaction(transactions, TransactionType.CHARGE, "0")
                .map(transaction -> (UpdateAction<Payment>) ChangeTransactionInteractionId.of(sequenceNumber, transaction.getId()))
                .orElseGet(() -> addChargePendingTransaction(notification));
    }

    private static AddTransaction addAuthorizationTransaction(@Nonnull final Notification notification) {
        return addTransactionFromNotification(notification,
                TransactionType.AUTHORIZATION, notification.getTransactionStatus().getCtTransactionState());
    }

    private static AddTransaction addChargePendingTransaction(@Nonnull final Notification notification) {
        return addTransactionFromNotification(notification, TransactionType.CHARGE, TransactionState.PENDING);
    }

    private static AddTransaction addTransactionFromNotification(@Nonnull final Notification notification,
                                                                 @Nonnull final TransactionType transactionType,
                                                                 @Nonnull final TransactionState transactionState) {
        final MonetaryAmount amount = MoneyImpl.of(notification.getPrice(), notification.getCurrency());
        return AddTransaction.of(TransactionDraftBuilder.of(transactionType, amount)
                .timestamp(toZonedDateTime(notification))
                .state(transactionState)
                .interactionId(notification.getSequencenumber())
                .build());
    }
}
