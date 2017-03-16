package com.commercetools.pspadapter.payone.mapping;

import com.commercetools.pspadapter.payone.config.PayoneConfig;
import com.commercetools.pspadapter.payone.domain.ctp.PaymentWithCartLike;
import com.commercetools.pspadapter.payone.domain.payone.model.common.ClearingType;
import com.commercetools.pspadapter.payone.domain.payone.model.wallet.WalletAuthorizationRequest;
import com.commercetools.pspadapter.payone.domain.payone.model.wallet.WalletPreauthorizationRequest;
import com.google.common.base.Preconditions;
import io.sphere.sdk.carts.CartLike;
import io.sphere.sdk.payments.Payment;
import org.javamoney.moneta.function.MonetaryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * @author fhaertig
 * @since 20.01.16
 */
public class PaypalRequestFactory extends PayoneRequestFactory {

    private static final Logger LOG = LoggerFactory.getLogger(PaypalRequestFactory.class);

    public PaypalRequestFactory(final PayoneConfig config) {
        super(config);
    }

    @Override
    public WalletPreauthorizationRequest createPreauthorizationRequest(final PaymentWithCartLike paymentWithCartLike) {

        final Payment ctPayment = paymentWithCartLike.getPayment();
        final CartLike ctCartLike = paymentWithCartLike.getCartLike();

        Preconditions.checkArgument(ctPayment.getCustom() != null, "Missing custom fields on payment!");

        final String clearingSubType = ClearingType.getClearingTypeByKey(ctPayment.getPaymentMethodInfo().getMethod()).getSubType();
        WalletPreauthorizationRequest request = new WalletPreauthorizationRequest(getPayoneConfig(), clearingSubType);

        request.setReference(paymentWithCartLike.getReference());

        Optional.ofNullable(ctPayment.getAmountPlanned())
                .ifPresent(amount -> {
                    request.setCurrency(amount.getCurrency().getCurrencyCode());
                    request.setAmount(MonetaryUtil
                            .minorUnits()
                            .queryFrom(amount)
                            .intValue());
                });

        mapFormPaymentWithCartLike(request, paymentWithCartLike, LOG);

        return request;
    }

    @Override
    public WalletAuthorizationRequest createAuthorizationRequest(final PaymentWithCartLike paymentWithCartLike) {

        final Payment ctPayment = paymentWithCartLike.getPayment();
        final CartLike ctCartLike = paymentWithCartLike.getCartLike();

        Preconditions.checkArgument(ctPayment.getCustom() != null, "Missing custom fields on payment!");

        final String clearingSubType = ClearingType.getClearingTypeByKey(ctPayment.getPaymentMethodInfo().getMethod()).getSubType();
        WalletAuthorizationRequest request = new WalletAuthorizationRequest(getPayoneConfig(), clearingSubType);

        request.setReference(paymentWithCartLike.getReference());

        Optional.ofNullable(ctPayment.getAmountPlanned())
                .ifPresent(amount -> {
                    request.setCurrency(amount.getCurrency().getCurrencyCode());
                    request.setAmount(MonetaryUtil
                            .minorUnits()
                            .queryFrom(amount)
                            .intValue());
                });

        mapFormPaymentWithCartLike(request, paymentWithCartLike, LOG);

        return request;
    }
}