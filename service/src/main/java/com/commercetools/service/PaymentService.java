package com.commercetools.service;

import io.sphere.sdk.commands.UpdateAction;
import io.sphere.sdk.payments.Payment;
import io.sphere.sdk.payments.PaymentDraft;
import io.sphere.sdk.payments.PaymentMethodInfo;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * CTP service to work with {@link Payment}s
 */
public interface PaymentService {

    /**
     * Create a payment from the draft.
     * @param paymentDraft <b>non-null</b> {@link PaymentDraft} to save.
     * @return completion stage with new created {@link Payment} instance
     */
    CompletionStage<Payment> createPayment(PaymentDraft paymentDraft);

    /**
     * Try to fetch a payment with by it's {@link Payment#getInterfaceId()} and {@link PaymentMethodInfo#getPaymentInterface()}.
     *
     * @param paymentMethodInterface name of payment interface, like "PAYONE"
     * @param interfaceId the payment's {@link Payment#getInterfaceId()}
     * @return completion stage with optional found payment.
     */
    CompletionStage<Optional<Payment>> getByPaymentMethodAndInterfaceId(String paymentMethodInterface, String interfaceId);

    /**
     * Apply {@code updateActions} to the {@code payment}
     * @param payment <b>non-null</b> {@link Payment} to update
     * @param updateActions <b>non-null</b> list of {@link UpdateAction<Payment>} to apply to the {@code payment}
     * @return Completion stage with an instance of the updated payment
     */
    CompletionStage<Payment> updatePayment(Payment payment, List<UpdateAction<Payment>> updateActions);
}
