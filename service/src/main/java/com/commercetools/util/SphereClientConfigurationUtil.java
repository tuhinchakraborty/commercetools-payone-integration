package com.commercetools.util;

import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.client.QueueSphereClientDecorator;
import io.sphere.sdk.client.RetrySphereClientDecorator;
import io.sphere.sdk.client.SphereClient;
import io.sphere.sdk.client.SphereClientConfig;
import io.sphere.sdk.client.SphereClientFactory;
import io.sphere.sdk.retry.RetryAction;
import io.sphere.sdk.retry.RetryPredicate;
import io.sphere.sdk.retry.RetryRule;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static io.sphere.sdk.http.HttpStatusCode.BAD_GATEWAY_502;
import static io.sphere.sdk.http.HttpStatusCode.GATEWAY_TIMEOUT_504;
import static io.sphere.sdk.http.HttpStatusCode.SERVICE_UNAVAILABLE_503;

public final class SphereClientConfigurationUtil {
    private static final long DEFAULT_TIMEOUT = 10;
    private static final TimeUnit DEFAULT_TIMEOUT_TIME_UNIT = TimeUnit.SECONDS;
    protected static final int RETRIES_LIMIT = 5;
    private static final int MAX_PARALLEL_REQUESTS = 30;
    private static final long DEFAULT_RETRY_INTERVAL_IN_SECOND = 10;
    private static final int MAX_RETRY_RULES = 2;

    /**
     * Creates a {@link BlockingSphereClient} with a custom {@code timeout} with a custom {@link
     * TimeUnit} as waiting time limit for blocking SphereClient to complete CTP request .
     *
     * @param clientConfig the client configuration for the client.
     * @return the instantiated {@link BlockingSphereClient}.
     */
    public static BlockingSphereClient createClient(@Nonnull final SphereClientConfig clientConfig) {
        final SphereClient underlyingClient = SphereClientFactory.of().createClient(clientConfig);
        return decorateSphereClient(underlyingClient);
    }

    /**
     * Creates a {@link BlockingSphereClient} with a custom {@code timeout} with a custom {@link
     * TimeUnit} as waiting time limit for blocking SphereClient to complete CTP request .
     *
     * @param sphereClient the HTTP underlying client.
     * @return the instantiated {@link BlockingSphereClient}.
     */
    protected static BlockingSphereClient decorateSphereClient(@Nonnull final SphereClient sphereClient) {
        final SphereClient retryClient = withRetry(sphereClient);
        final SphereClient limitedClient = withLimitedParallelRequests(retryClient);
        return withBlocking(limitedClient);
    }

    private static SphereClient withRetry(@Nonnull final SphereClient sphereClient) {
        final RetryAction scheduledRetry =
                RetryAction.ofScheduledRetry(RETRIES_LIMIT, context -> calculateVariableDelay());

        final RetryAction immediateRetry =
                RetryAction.ofImmediateRetries(RETRIES_LIMIT);

        final RetryPredicate httpErrorMatcher =
                RetryPredicate.ofMatchingErrors(io.sphere.sdk.http.HttpException.class);

        final RetryPredicate http5xxMatcher =
                RetryPredicate.ofMatchingStatusCodes(BAD_GATEWAY_502, SERVICE_UNAVAILABLE_503, GATEWAY_TIMEOUT_504);

        final List<RetryRule> retryRules = new ArrayList<>(MAX_RETRY_RULES);
        retryRules.add(RetryRule.of(http5xxMatcher, scheduledRetry));
        retryRules.add(RetryRule.of(httpErrorMatcher, immediateRetry));

        return RetrySphereClientDecorator.of(sphereClient, retryRules);
    }

    private static BlockingSphereClient withBlocking(@Nonnull final SphereClient sphereClient) {
        return BlockingSphereClient.of(sphereClient, DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_TIME_UNIT);
    }

    /**
     * Computes a variable delay in seconds.
     *
     * @return a computed variable delay in seconds, which is a random component in addition to a default interval.
     */
    private static Duration calculateVariableDelay() {
        final long randomNumberInRange = getRandomNumberInRange(1, DEFAULT_RETRY_INTERVAL_IN_SECOND);
        return Duration.ofSeconds(DEFAULT_RETRY_INTERVAL_IN_SECOND + randomNumberInRange);
    }

    private static long getRandomNumberInRange(final long min, final long max) {
        return new Random().longs(min, (max + 1)).limit(1).findFirst().getAsLong();
    }

    private static SphereClient withLimitedParallelRequests(@Nonnull final SphereClient sphereClient) {
        return QueueSphereClientDecorator.of(sphereClient, MAX_PARALLEL_REQUESTS);
    }

    private SphereClientConfigurationUtil() {}
}