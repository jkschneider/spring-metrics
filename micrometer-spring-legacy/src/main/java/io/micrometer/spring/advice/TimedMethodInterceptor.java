package io.micrometer.spring.advice;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Builder;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

public class TimedMethodInterceptor implements MethodInterceptor {
    private final MeterRegistry registry;
    private final TimedMetricNameResolver timedMetricNameResolver;
    private final TimedTagsResolver timedTagsResolver;

    public TimedMethodInterceptor(MeterRegistry registry) {
        this(registry, invocation ->
            Tags.of("class", invocation.getMethod().getDeclaringClass().getSimpleName(),
                    "method", invocation.getMethod().getName())
        );
    }

    public TimedMethodInterceptor(MeterRegistry registry, TimedTagsResolver timedTagsResolver) {
        this(registry, (metricName, invocation) -> metricName, timedTagsResolver);
    }

    public TimedMethodInterceptor(MeterRegistry registry, TimedMetricNameResolver timedMetricNameResolver, TimedTagsResolver timedTagsResolver) {
        this.registry = registry;
        this.timedMetricNameResolver = timedMetricNameResolver;
        this.timedTagsResolver = timedTagsResolver;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        Timed methodLevelTimed = AnnotatedElementUtils.findMergedAnnotation(method, Timed.class);
        Timed classLevelTimed = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), Timed.class);
        if (methodLevelTimed == null) {
            method = ReflectionUtils.findMethod(invocation.getThis().getClass(), method.getName(), method.getParameterTypes());
            methodLevelTimed = AnnotatedElementUtils.findMergedAnnotation(method, Timed.class);
        }
        if (classLevelTimed == null && methodLevelTimed == null) {
            return invocation.proceed();
        }

        Timer.Sample sample = Timer.start(registry);
        final String metricName;
        if (methodLevelTimed != null && !methodLevelTimed.value().isEmpty()) {
            metricName = methodLevelTimed.value();
        }
        else if (classLevelTimed != null && !classLevelTimed.value().isEmpty()){
            metricName = classLevelTimed.value();
        }
        else {
            metricName = TimedAspect.DEFAULT_METRIC_NAME;
        }
        Builder builder = Timer.builder(timedMetricNameResolver.apply(metricName, invocation));
        if (methodLevelTimed != null) {
            builder.description(methodLevelTimed.description().isEmpty() ? null : methodLevelTimed.description())
                .publishPercentileHistogram(methodLevelTimed.histogram())
                .publishPercentiles(methodLevelTimed.percentiles().length == 0 ? null : methodLevelTimed.percentiles());
        }
        else {
            builder.description(classLevelTimed.description().isEmpty() ? null : classLevelTimed.description())
                .publishPercentileHistogram(classLevelTimed.histogram())
                .publishPercentiles(classLevelTimed.percentiles().length == 0 ? null : classLevelTimed.percentiles());
        }

        if (classLevelTimed != null) {
            builder.tags(classLevelTimed.extraTags());
        }
        if (methodLevelTimed != null) {
            builder.tags(methodLevelTimed.extraTags());
        }
        builder.tags(timedTagsResolver.apply(invocation));
        try {
            return invocation.proceed();
        } finally {
            sample.stop(builder.register(registry));
        }
    }
}
