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

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.annotation.TimedSet;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.lang.NonNullApi;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * <p>
 * AspectJ aspect for intercepting types or methods annotated with {@link Timed @Timed}.<br>
 * The aspect supports programmatic customizations through constructor-injectable custom logic.
 * </p>
 * <p>
 * You might want to add tags programmatically to the {@link Timer}.<br>
 * In this case, the tags provider function (<code>Function&lt;ProceedingJoinPoint, Iterable&lt;Tag&gt;&gt;</code>) can help.
 * It receives a {@link ProceedingJoinPoint} and returns the {@link Tag}s that will be attached to the {@link Timer}.
 * </p>
 * <p>
 * You might also want to skip the {@link Timer} creation programmatically.<br>
 * One use-case can be having another component in your application that already processes the {@link Timed @Timed} annotation
 * in some cases so that {@code TimedAspect} should not intercept these methods. E.g.: Spring Boot does this for its controllers.
 * By using the skip predicate (<code>Predicate&lt;ProceedingJoinPoint&gt;</code>)
 * you can tell the {@code TimedAspect} when not to create a {@link Timer}.
 *
 * Here's an example to disable {@link Timer} creation for Spring controllers:
 *</p>
 * <pre>
 * &#064;Bean
 * public TimedAspect timedAspect(MeterRegistry meterRegistry) {
 *     return new TimedAspect(meterRegistry, this::skipControllers);
 * }
 *
 * private boolean skipControllers(ProceedingJoinPoint pjp) {
 *     Class&lt;?&gt; targetClass = pjp.getTarget().getClass();
 *     return targetClass.isAnnotationPresent(RestController.class) || targetClass.isAnnotationPresent(Controller.class);
 * }
 * </pre>
 *
 * @author David J. M. Karlsen
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Nejc Korasa
 * @author Jonatan Ivanov
 * @since 1.0.0
 */
@Aspect
@NonNullApi
@Incubating(since = "1.0.0")
public class TimedAspect {
    private static final Predicate<ProceedingJoinPoint> DONT_SKIP_ANYTHING = pjp -> false;
    public static final String DEFAULT_METRIC_NAME = "method.timed";
    public static final String DEFAULT_EXCEPTION_TAG_VALUE = "none";

    /**
     * Tag key for an exception.
     *
     * @since 1.1.0
     */
    public static final String EXCEPTION_TAG = "exception";

    private final MeterRegistry registry;
    private final Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint;
    private final Predicate<ProceedingJoinPoint> shouldSkip;

    /**
     * Creates a {@code TimedAspect} instance with {@link Metrics#globalRegistry}.
     *
     * @since 1.2.0
     */
    public TimedAspect() {
        this(Metrics.globalRegistry);
    }

    /**
     * Creates a {@code TimedAspect} instance with the given {@code registry}.
     *
     * @param registry Where we're going to register metrics.
     */
    public TimedAspect(MeterRegistry registry) {
        this(registry, DONT_SKIP_ANYTHING);
    }

    /**
     * Creates a {@code TimedAspect} instance with the given {@code registry} and tags provider function.
     *
     * @param registry Where we're going to register metrics.
     * @param tagsBasedOnJoinPoint A function to generate tags given a join point.
     */
    public TimedAspect(MeterRegistry registry, Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint) {
        this(registry, tagsBasedOnJoinPoint, DONT_SKIP_ANYTHING);
    }

    /**
     * Creates a {@code TimedAspect} instance with the given {@code registry} and skip predicate.
     *
     * @param registry Where we're going to register metrics.
     * @param shouldSkip A predicate to decide if creating the timer should be skipped or not.
     * @since 1.7.0
     */
    public TimedAspect(MeterRegistry registry, Predicate<ProceedingJoinPoint> shouldSkip) {
        this(
                registry,
                pjp -> Tags.of("class", pjp.getStaticPart().getSignature().getDeclaringTypeName(),
                        "method", pjp.getStaticPart().getSignature().getName()),
                shouldSkip
        );
    }

    /**
     * Creates a {@code TimedAspect} instance with the given {@code registry}, tags provider function and skip predicate.
     *
     * @param registry Where we're going to register metrics.
     * @param tagsBasedOnJoinPoint A function to generate tags given a join point.
     * @param shouldSkip A predicate to decide if creating the timer should be skipped or not.
     * @since 1.7.0
     */
    public TimedAspect(MeterRegistry registry, Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint, Predicate<ProceedingJoinPoint> shouldSkip) {
        this.registry = registry;
        this.tagsBasedOnJoinPoint = tagsBasedOnJoinPoint;
        this.shouldSkip = shouldSkip;
    }

    @Around("execution (@io.micrometer.core.annotation.Timed * *.*(..))")
    public Object timedMethod(ProceedingJoinPoint pjp) throws Throwable {
        if (shouldSkip.test(pjp)) {
            return pjp.proceed();
        }

        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Timed timed = method.getAnnotation(Timed.class);
        if (timed == null) {
            method = pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
            timed = method.getAnnotation(Timed.class);
        }

        return processTimers(pjp, method, timed);
    }
    
    @Around("execution (@io.micrometer.core.annotation.TimedSet * *.*(..))")
    public Object timedSetMethod(ProceedingJoinPoint pjp) throws Throwable {
        if (shouldSkip.test(pjp)) {
            return pjp.proceed();
        }

        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        TimedSet timedSet = method.getAnnotation(TimedSet.class);
        if (timedSet == null) {
            method = pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
            timedSet = method.getAnnotation(TimedSet.class);
        }
        return processTimers(pjp, method, timedSet.value());
    }
    
    public Object processTimers(ProceedingJoinPoint pjp, Method method, Timed ...timers) throws Throwable {
        final boolean stopWhenCompleted = CompletionStage.class.isAssignableFrom(method.getReturnType());
        List<SampledMetric> sampledMetrics = createSampledMetrics(pjp, timers);
        if (stopWhenCompleted) {
            return processCompletableJoinPoint(pjp, sampledMetrics);
        }
        return processSimpleJoinPoint(pjp, sampledMetrics);
    }

    
    
    private List<SampledMetric> createSampledMetrics(ProceedingJoinPoint pjp, Timed... timers) {
        List<SampledMetric> sampledMetrics = new ArrayList<>(timers.length);
        for (Timed timed: timers) {
            final String metricName = timed.value().isEmpty() ? DEFAULT_METRIC_NAME : timed.value();
            if (!timed.longTask()) {
                SampledMetric sampledMetric = new ShortSampledMetric(pjp, timed, metricName, Timer.start(registry));
                sampledMetrics.add(sampledMetric);
            } else {
                LongSampledMetric sampledMetric = new LongSampledMetric(buildLongTaskTimer(pjp, timed, metricName).map(LongTaskTimer::start));
                sampledMetrics.add(sampledMetric);
            }
        }
        return sampledMetrics;
    }
    
    private Object processCompletableJoinPoint(ProceedingJoinPoint pjp, List<SampledMetric> sampledMetrics)
            throws Throwable, Exception {
        CompletionStage<?> completionStage;
        try {
            completionStage = (CompletionStage<?>) pjp.proceed();
            for (SampledMetric sampledMetric: sampledMetrics) {
               completionStage = completionStage.whenComplete((result, throwable) ->
               sampledMetric.stop(getExceptionTag(throwable)));
            }
        } catch (Exception ex) {
            String exceptionClassName = ex.getClass().getSimpleName();
            for (SampledMetric sampledMetric: sampledMetrics) {
                sampledMetric.stop(exceptionClassName);
            }
            throw ex;
        }
        return completionStage;
    }

    private Object processSimpleJoinPoint(ProceedingJoinPoint pjp, List<SampledMetric> sampledMetrics)
            throws Throwable, Exception {
        String exceptionClass = DEFAULT_EXCEPTION_TAG_VALUE;
        try {
            return pjp.proceed();
        } catch (Exception ex) {
            exceptionClass = ex.getClass().getSimpleName();
            throw ex;
        } finally {
            for (SampledMetric sampledMetric: sampledMetrics) {
                sampledMetric.stop(exceptionClass);
            }
        }
    }

    private String getExceptionTag(Throwable throwable) {

        if (throwable == null) {
            return DEFAULT_EXCEPTION_TAG_VALUE;
        }

        if (throwable.getCause() == null) {
            return throwable.getClass().getSimpleName();
        }

        return throwable.getCause().getClass().getSimpleName();
    }

    /**
     * Secure long task timer creation - it should not disrupt the application flow in case of exception
     */
    private Optional<LongTaskTimer> buildLongTaskTimer(ProceedingJoinPoint pjp, Timed timed, String metricName) {
        try {
            return Optional.of(LongTaskTimer.builder(metricName)
                                       .description(timed.description().isEmpty() ? null : timed.description())
                                       .tags(timed.extraTags())
                                       .tags(tagsBasedOnJoinPoint.apply(pjp))
                                       .register(registry));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    private abstract static class SampledMetric {
        abstract void stop(String exceptionClass);
    }
    
    private class ShortSampledMetric extends SampledMetric {
        final ProceedingJoinPoint pjp;
        final Timed timed; 
        final Timer.Sample sample;
        final String metricName;
        
        public ShortSampledMetric(ProceedingJoinPoint pjp, Timed timed, String metricName, Timer.Sample sample) {
            this.pjp = pjp;
            this.timed = timed;
            this.sample = sample;
            this.metricName = metricName;
        }
        
        @Override
        void stop(String exceptionClass) {
            try {
                sample.stop(Timer.builder(metricName)
                        .description(timed.description().isEmpty() ? null : timed.description())
                        .tags(timed.extraTags())
                        .tags(EXCEPTION_TAG, exceptionClass)
                        .tags(tagsBasedOnJoinPoint.apply(pjp))
                        .publishPercentileHistogram(timed.histogram())
                        .publishPercentiles(timed.percentiles().length == 0 ? null : timed.percentiles())
                        .register(registry));
            } catch (Exception e) {
                // ignoring on purpose
            }
        }
    }
    
    private static class LongSampledMetric extends SampledMetric {
        final Optional<LongTaskTimer.Sample> sample;
        
        public LongSampledMetric(Optional<LongTaskTimer.Sample> sample) {
            this.sample = sample;
        }
        
        @Override
        void stop(String exceptionClass) {
            this.sample.ifPresent(this::stopTimer);
        }
        
        private void stopTimer(LongTaskTimer.Sample sample) {
            try {
                sample.stop();
            } catch (Exception e) {
                // ignoring on purpose
            }
        }
    }
}
