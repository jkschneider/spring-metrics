/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.aop;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.lang.NonNullApi;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Aspect responsible for intercepting all methods annotated with the {@link Counted}
 * annotation and record a few counter metrics about their execution status.
 *
 * @author Ali Dehghani
 * @since 1.2.0
 * @see Counted
 */
@Aspect
@NonNullApi
public class CountedAspect {
    public final String DEFAULT_EXCEPTION_TAG_VALUE = "none";
    public final String RESULT_TAG_FAILURE_VALUE = "failure";
    public final String RESULT_TAG_SUCCESS_VALUE = "success";

    /**
     * The tag name to encapsulate the method execution status.
     */
    private static final String RESULT_TAG = "result";

    /**
     * The tag name to encapsulate the exception thrown by the intercepted method.
     */
    private static final String EXCEPTION_TAG = "exception";

    /**
     * Where we're going register metrics.
     */
    private final MeterRegistry meterRegistry;

    /**
     * A function to produce additional tags for any given join point.
     */
    private final Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint;

    /**
     * Construct a new aspect with the given {@code meterRegistry} along with a default
     * tags provider.
     *
     * @param meterRegistry Where we're going register metrics.
     */
    public CountedAspect(MeterRegistry meterRegistry) {
        this(meterRegistry, pjp ->
                Tags.of("class", pjp.getStaticPart().getSignature().getDeclaringTypeName(),
                        "method", pjp.getStaticPart().getSignature().getName()));
    }

    /**
     * Constructs a new aspect with the given {@code meterRegistry} and tags provider function.
     *
     * @param meterRegistry        Where we're going register metrics.
     * @param tagsBasedOnJoinPoint A function to generate tags given a join point.
     */
    public CountedAspect(MeterRegistry meterRegistry, Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint) {
        this.meterRegistry = meterRegistry;
        this.tagsBasedOnJoinPoint = tagsBasedOnJoinPoint;
    }

    /**
     * Intercept methods annotated with the {@link Counted} annotation and expose a few counters about
     * their execution status. By default, this aspect records both failed and successful attempts. If the
     * {@link Counted#recordFailuresOnly()} is set to {@code true}, then the aspect would record only
     * failed attempts. In case of a failure, the aspect tags the counter with the simple name of the thrown
     * exception.
     *
     * <p>When the annotated method returns a {@link CompletionStage} or any of its subclasses, the counters will be incremented
     * only when the {@link CompletionStage} is completed. If completed exceptionally a failure is recorded, otherwise if
     * {@link Counted#recordFailuresOnly()} is set to {@code false}, a success is recorded.
     *
     * @param pjp     Encapsulates some information about the intercepted area.
     * @param counted The annotation.
     * @return Whatever the intercepted method returns.
     * @throws Throwable When the intercepted method throws one.
     */
    @Around("@annotation(counted)")
    public Object interceptAndRecord(ProceedingJoinPoint pjp, Counted counted) throws Throwable {

        final Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        final boolean stopWhenCompleted = CompletionStage.class.isAssignableFrom(method.getReturnType());

        if (stopWhenCompleted) {
            try {
                return ((CompletionStage<?>) pjp.proceed()).whenComplete((result, throwable) ->
                        recordCompletionResult(pjp, counted, throwable));
            } catch (Throwable e) {
                record(pjp, counted, e.getClass().getSimpleName(), RESULT_TAG_FAILURE_VALUE);
                throw e;
            }
        }

        try {
            Object result = pjp.proceed();
            if (!counted.recordFailuresOnly()) {
                record(pjp, counted, DEFAULT_EXCEPTION_TAG_VALUE, RESULT_TAG_SUCCESS_VALUE);
            }
            return result;
        } catch (Throwable e) {
            record(pjp, counted, e.getClass().getSimpleName(), RESULT_TAG_FAILURE_VALUE);
            throw e;
        }
    }

    private void recordCompletionResult(ProceedingJoinPoint pjp, Counted counted, Throwable throwable) {

        if (throwable != null) {
            String exceptionTagValue = throwable.getCause() == null ?
                    throwable.getClass().getSimpleName() : throwable.getCause().getClass().getSimpleName();
            record(pjp, counted, exceptionTagValue, RESULT_TAG_FAILURE_VALUE);
        } else if (!counted.recordFailuresOnly()) {
            record(pjp, counted, DEFAULT_EXCEPTION_TAG_VALUE, RESULT_TAG_SUCCESS_VALUE);
        }

    }

    private void record(ProceedingJoinPoint pjp, Counted counted, String exception, String result) {
        try {
            Counter.builder(counted.value())
                    .description(counted.description().isEmpty() ? null : counted.description())
                    .tags(counted.extraTags())
                    .tag(EXCEPTION_TAG, exception)
                    .tag(RESULT_TAG, result)
                    .tags(tagsBasedOnJoinPoint.apply(pjp))
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            // ignoring on purpose
        }
    }
}
