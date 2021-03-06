package com.commercetools.pspadapter.payone.mapping;

import com.commercetools.pspadapter.payone.config.PayoneConfig;
import com.commercetools.pspadapter.payone.domain.ctp.PaymentWithCartLike;
import com.commercetools.pspadapter.payone.domain.payone.model.common.AuthorizationRequest;
import com.commercetools.pspadapter.payone.domain.payone.model.common.ClearingType;
import com.commercetools.pspadapter.payone.domain.payone.model.wallet.WalletAuthorizationRequest;
import com.commercetools.pspadapter.payone.domain.payone.model.wallet.WalletPreauthorizationRequest;
import com.commercetools.pspadapter.tenant.TenantConfig;
import com.google.common.base.Preconditions;
import io.sphere.sdk.payments.Payment;
import io.sphere.sdk.payments.PaymentMethodInfo;

import javax.annotation.Nonnull;
import java.util.function.BiFunction;

/**
 * Requests factory for Wallet based payments, like <i>PayPal</i> and <i>Paydirekt</i>.
 * <p>Based on {@link PaymentMethodInfo#getMethod() Payment#paymentMethodInfo#method} value the request will be created
 * with respective {@link WalletAuthorizationRequest#clearingtype} and {@link WalletAuthorizationRequest#wallettype}</p>
 */
public class WalletRequestFactory extends PayoneRequestFactory {

    public WalletRequestFactory(@Nonnull final TenantConfig tenantConfig) {
        super(tenantConfig);
    }

    @Override
    @Nonnull
    public WalletPreauthorizationRequest createPreauthorizationRequest(@Nonnull final PaymentWithCartLike paymentWithCartLike) {
        return createRequestInternal(paymentWithCartLike, WalletPreauthorizationRequest::new);
    }

    @Override
    @Nonnull
    public WalletAuthorizationRequest createAuthorizationRequest(@Nonnull final PaymentWithCartLike paymentWithCartLike) {
        return createRequestInternal(paymentWithCartLike, WalletAuthorizationRequest::new);
    }

    @Nonnull
    private <WR extends AuthorizationRequest> WR createRequestInternal(@Nonnull final PaymentWithCartLike paymentWithCartLike,
                                                                       @Nonnull final BiFunction<? super PayoneConfig, ClearingType, WR> requestConstructor) {

        final Payment ctPayment = paymentWithCartLike.getPayment();

        Preconditions.checkArgument(ctPayment.getCustom() != null, "Missing custom fields on payment!");

        final ClearingType clearingType = ClearingType.getClearingTypeByKey(ctPayment.getPaymentMethodInfo().getMethod());
        WR request = requestConstructor.apply(getPayoneConfig(), clearingType);

        mapFormPaymentWithCartLike(request, paymentWithCartLike);

        return request;
    }
}
