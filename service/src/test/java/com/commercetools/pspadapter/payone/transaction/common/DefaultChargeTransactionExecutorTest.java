package com.commercetools.pspadapter.payone.transaction.common;

import com.commercetools.pspadapter.payone.domain.payone.exceptions.PayoneException;
import com.commercetools.pspadapter.payone.domain.payone.model.common.AuthorizationRequest;
import com.commercetools.pspadapter.payone.domain.payone.model.common.PayoneResponseFields;
import com.commercetools.pspadapter.payone.domain.payone.model.common.ResponseErrorCode;
import com.commercetools.pspadapter.payone.transaction.BaseTransactionBaseExecutorTest;
import com.google.common.collect.ImmutableMap;
import io.sphere.sdk.payments.TransactionState;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static com.commercetools.pspadapter.payone.domain.ctp.CustomTypeBuilder.PAYONE_INTERACTION_REDIRECT;
import static com.commercetools.pspadapter.payone.domain.ctp.CustomTypeBuilder.PAYONE_INTERACTION_RESPONSE;
import static com.commercetools.pspadapter.payone.domain.payone.model.common.ResponseStatus.*;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DefaultChargeTransactionExecutorTest extends BaseTransactionBaseExecutorTest {

    private DefaultChargeTransactionExecutor executor;

    @Mock
    protected AuthorizationRequest authorizationRequest;

    @Override
    @Before
    public void setUp() {
        super.setUp();

        executor = new DefaultChargeTransactionExecutor(typeCache, requestFactory, payonePostService, client);

        when(authorizationRequest.toStringMap(anyBoolean())).thenReturn(ImmutableMap.of("testRequestKey1", "testRequestValue2",
                "testRequestKey2", "testRequestValue2"));
        when(requestFactory.createAuthorizationRequest(paymentWithCartLike)).thenReturn(authorizationRequest);
    }

    @Test
    public void attemptExecution_withRedirectResponse_createsUpdateActions() throws Exception {
        when(payonePostService.executePost(authorizationRequest)).thenReturn(ImmutableMap.of(
                PayoneResponseFields.STATUS, REDIRECT.getStateCode(),
                PayoneResponseFields.REDIRECT, "http://mock-redirect.url",
                PayoneResponseFields.TXID, "responseTxid"
        ));
        executor.attemptExecution(paymentWithCartLike, transaction);

        assertRequestInterfaceInteraction(2);
        assertRedirectActions(TransactionState.PENDING);
        assertSetInterfaceIdActions();
        assertRedirectAddInterfaceInteractionAction(PAYONE_INTERACTION_REDIRECT,
                "http://mock-redirect.url", REDIRECT.getStateCode(), "responseTxid");
    }

    @Test
    public void attemptExecution_withApprovedResponse_createsUpdateActions() throws Exception {
        when(payonePostService.executePost(authorizationRequest)).thenReturn(ImmutableMap.of(
                PayoneResponseFields.STATUS, APPROVED.getStateCode(),
                PayoneResponseFields.TXID, "responseTxid"
        ));
        executor.attemptExecution(paymentWithCartLike, transaction);

        assertRequestInterfaceInteraction(2);
        assertChangeTransactionStateActions(TransactionState.SUCCESS);
        assertSetInterfaceIdActions();
        assertCommonAddInterfaceInteractionAction(PAYONE_INTERACTION_RESPONSE, "responseTxid", APPROVED.getStateCode());
    }

    @Test
    public void attemptExecution_withErrorResponse_createsUpdateActions() throws Exception {
        when(payonePostService.executePost(authorizationRequest)).thenReturn(ImmutableMap.of(
                PayoneResponseFields.STATUS, ERROR.getStateCode(),
                PayoneResponseFields.TXID, "responseTxid"
        ));
        executor.attemptExecution(paymentWithCartLike, transaction);

        assertRequestInterfaceInteraction(2);
        assertChangeTransactionStateActions(TransactionState.FAILURE);
        assertCommonAddInterfaceInteractionAction(PAYONE_INTERACTION_RESPONSE, "responseTxid", ERROR.getStateCode());
    }

    @Test
    public void attemptExecution_withPendingResponse_createsUpdateActions() throws Exception {
        when(payonePostService.executePost(authorizationRequest)).thenReturn(ImmutableMap.of(
                PayoneResponseFields.STATUS, PENDING.getStateCode(),
                PayoneResponseFields.TXID, "responseTxid"
        ));
        executor.attemptExecution(paymentWithCartLike, transaction);

        assertRequestInterfaceInteraction(2);
        assertChangeTransactionStateActions(TransactionState.PENDING);
        assertSetInterfaceIdActions();
        assertCommonAddInterfaceInteractionAction(PAYONE_INTERACTION_RESPONSE, "responseTxid", PENDING.getStateCode());
    }

    @Test
    public void attemptExecution_withPayoneException_createsUpdateActions() throws Exception {
        when(payonePostService.executePost(authorizationRequest)).thenThrow(new PayoneException("payone exception message"));

        executor.attemptExecution(paymentWithCartLike, transaction);

        assertRequestInterfaceInteraction(2);
        assertChangeTransactionStateActions(TransactionState.FAILURE);
        assertCommonAddInterfaceInteractionAction(PAYONE_INTERACTION_RESPONSE,
                // opposite to other branches we don't expect to have txid here
                "payone exception message", ERROR.getStateCode(), ResponseErrorCode.TRANSACTION_EXCEPTION.getErrorCode());
    }

    // TODO: https://github.com/commercetools/commercetools-payone-integration/issues/199
    @Test
    public void attemptExecution_withUnexpectedResponseStatus_throwsException() throws Exception {
        when(payonePostService.executePost(authorizationRequest)).thenReturn(ImmutableMap.of(
                PayoneResponseFields.STATUS, "OH-NO-DAVID-BLAINE",
                PayoneResponseFields.TXID, "responseTxid"
        ));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> executor.attemptExecution(paymentWithCartLike, transaction))
                .withMessageContaining("Unknown PayOne status");

        assertRequestInterfaceInteraction(1);
    }
}