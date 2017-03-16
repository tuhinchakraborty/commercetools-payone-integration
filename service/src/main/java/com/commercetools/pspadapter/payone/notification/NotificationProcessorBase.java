package com.commercetools.pspadapter.payone.notification;

import com.commercetools.pspadapter.payone.domain.ctp.CustomTypeBuilder;
import com.commercetools.pspadapter.payone.domain.payone.model.common.Notification;
import com.commercetools.pspadapter.payone.mapping.CustomFieldKeys;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.commands.UpdateAction;
import io.sphere.sdk.payments.Payment;
import io.sphere.sdk.payments.Transaction;
import io.sphere.sdk.payments.TransactionType;
import io.sphere.sdk.payments.commands.PaymentUpdateCommand;
import io.sphere.sdk.payments.commands.updateactions.AddInterfaceInteraction;
import io.sphere.sdk.payments.commands.updateactions.SetStatusInterfaceCode;
import io.sphere.sdk.payments.commands.updateactions.SetStatusInterfaceText;

import javax.annotation.Nonnull;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Optional;

/**
 * Base for notification processor implementations.
 *
 * @author Jan Wolter
 */
public abstract class NotificationProcessorBase implements NotificationProcessor {
    private static final String DEFAULT_SEQUENCE_NUMBER = "0";

    private final BlockingSphereClient client;

    /**
     * Constructor for implementations.
     *
     * @param client the commercetools platform client to update payments
     */
    protected NotificationProcessorBase(final BlockingSphereClient client) {
        this.client = client;
    }

    @Override
    public final void processTransactionStatusNotification(final Notification notification, final Payment payment) {
        if (!canProcess(notification)) {
            throw new IllegalArgumentException(String.format(
                    "txaction \"%s\" is not supported by %s",
                    notification.getTxaction(),
                    this.getClass().getName()));
        }

        try {
            client.executeBlocking(PaymentUpdateCommand.of(payment, createPaymentUpdates(payment, notification)));
        } catch (final io.sphere.sdk.client.ConcurrentModificationException e) {
            throw new java.util.ConcurrentModificationException(e);
        }
    }

    /**
     * Returns whether the provided PAYONE {@code notification} can be processed by this instance.
     * @param notification a PAYONE notification
     * @return whether the {@code notification} can be processed
     */
    protected abstract boolean canProcess(final Notification notification);

    /**
     * Generates a list of update actions which can be applied to the payment in one step.
     *
     * @param payment the payment to which the updates may apply
     * @param notification the notification to process
     * @return an immutable list of update actions which will e.g. add an interfaceInteraction to the payment
     * or apply changes to a corresponding transaction in the payment
     */
    protected ImmutableList<UpdateAction<Payment>> createPaymentUpdates(final Payment payment,
                                                                                 final Notification notification) {
        final ImmutableList.Builder<UpdateAction<Payment>> listBuilder = ImmutableList.builder();
        listBuilder.add(createNotificationAddAction(notification));
        listBuilder.add(setStatusInterfaceCode(notification));
        listBuilder.add(setStatusInterfaceText(notification));
        return listBuilder.build();
    }

    /**
     * Creates a payment update action to add the given notification as
     * {@value CustomTypeBuilder#PAYONE_INTERACTION_NOTIFICATION}.
     *
     * @param notification the PAYONE notification
     * @return the update action
     */
    protected static AddInterfaceInteraction createNotificationAddAction(final Notification notification) {
        return AddInterfaceInteraction.ofTypeKeyAndObjects(
                CustomTypeBuilder.PAYONE_INTERACTION_NOTIFICATION,
                ImmutableMap.of(
                        CustomFieldKeys.TIMESTAMP_FIELD, toZonedDateTime(notification),
                        CustomFieldKeys.SEQUENCE_NUMBER_FIELD, toSequenceNumber(notification.getSequencenumber()),
                        CustomFieldKeys.TX_ACTION_FIELD, notification.getTxaction().getTxActionCode(),
                        CustomFieldKeys.NOTIFICATION_FIELD, notification.toString()));
    }

    /**
     * Creates a payment update action to set the latest txaction to StatusInterfaceCode
     * @param notification the PAYONE notification
     * @return the update action
     */
    protected static SetStatusInterfaceCode setStatusInterfaceCode(final Notification notification) {
        return SetStatusInterfaceCode.of(notification.getTxaction().toString());
    }

    /**
     * Creates a payment update action to set the latest txaction to StatusInterfaceText
     * @param notification the PAYONE notification
     * @return the update action
     */
    protected static SetStatusInterfaceText setStatusInterfaceText(final Notification notification) {
        return SetStatusInterfaceText.of(notification.getTxaction().toString());
    }

    /**
     * Converts the notification's "txtime" to a zoned datetime.
     * @param notification the notification to get the transaction timestamp from
     * @return the transaction timestamp
     */
    protected static ZonedDateTime toZonedDateTime(final Notification notification) {
        return ZonedDateTime.of(
                LocalDateTime.ofEpochSecond(Long.valueOf(notification.getTxtime()), 0, ZoneOffset.UTC),
                ZoneId.of("UTC"));
    }

    /**
     * Checks whether {@code transactions} contains a transaction of the given {@code transactionType} with the
     * given {@code interactionId}.
     *
     * @param transactions the list of transactions
     * @param transactionType the type of transaction
     * @param interactionId the interaction ID (aka PAYONE's sequencenumber)
     * @return whether there is a {@code CHARGE} transaction with the provided {@code interactionId} in
     *         the {@code transactions}
     */
    protected static Optional<Transaction> findMatchingTransaction(final Collection<Transaction> transactions,
                                                                   final TransactionType transactionType,
                                                                   final String interactionId) {
        return transactions.stream()
                .filter(transaction -> transaction.getType().equals(transactionType)
                        && toSequenceNumber(transaction.getInteractionId()).equals(interactionId))
                .findFirst();
    }

    /**
     * Transforms the {@code sequenceNumber} into {@value #DEFAULT_SEQUENCE_NUMBER} if necessary.
     *
     * @param sequenceNumber the sequence number string
     * @return the {@code sequenceNumber} if not null, {@value #DEFAULT_SEQUENCE_NUMBER} otherwise
     */
    @Nonnull
    protected static String toSequenceNumber(final String sequenceNumber) {
        return MoreObjects.firstNonNull(sequenceNumber, DEFAULT_SEQUENCE_NUMBER);
    }
}