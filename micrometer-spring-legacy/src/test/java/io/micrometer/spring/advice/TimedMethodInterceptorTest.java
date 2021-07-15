package io.micrometer.spring.advice;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;

import static org.assertj.core.api.Assertions.assertThat;

class TimedMethodInterceptorTest {

    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    @AfterEach
    public void after() throws Exception {
        context.close();
    }

    @Test
    public void testExplicitMetricName() {
        context.register(DefaultTimedMethodInterceptorConfig.class);
        context.refresh();

        TimedService service = context.getBean(TimedService.class);
        MeterRegistry registry = context.getBean(MeterRegistry.class);

        service.timeWithExplicitValue();
        assertThat(registry.get("something")
            .tag("class", "TimedServiceImpl")
            .tag("method", "timeWithExplicitValue")
            .tag("extra", "tag")
            .timer().count()).isEqualTo(1);
    }

    @Test
    public void testDefaultMetricName() {
        context.register(DefaultTimedMethodInterceptorConfig.class);
        context.refresh();

        TimedService service = context.getBean(TimedService.class);
        MeterRegistry registry = context.getBean(MeterRegistry.class);

        service.timeWithoutValue();
        assertThat(registry.get(TimedAspect.DEFAULT_METRIC_NAME)
            .tag("class", "TimedServiceImpl")
            .tag("method", "timeWithoutValue")
            .tag("extra", "tag")
            .timer().count()).isEqualTo(1);
    }

    @Test
    public void testInterfaceMethodTimed() {
        context.register(DefaultTimedMethodInterceptorConfig.class);
        context.refresh();

        TimedService service = context.getBean(TimedService.class);
        MeterRegistry registry = context.getBean(MeterRegistry.class);

        service.timeOnInterface();
        assertThat(registry.get(TimedAspect.DEFAULT_METRIC_NAME)
            .tag("class", "TimedServiceImpl")
            .tag("method", "timeOnInterface")
            .tag("extra", "tag")
            .timer().count()).isEqualTo(1);
    }

    @Test
    public void testCustomNameAndTagResolvers() {
        context.register(CustomizedTimedMethodInterceptorConfig.class);
        context.refresh();

        TimedService service = context.getBean(TimedService.class);
        MeterRegistry registry = context.getBean(MeterRegistry.class);

        service.timeWithoutValue();
        assertThat(registry.get("method.invoke")
            .tag("test", "tag")
            .tag("extra", "tag")
            .timer().count()).isEqualTo(1);
    }

    @Test
    public void testClassTimed() {
        context.register(DefaultTimedMethodInterceptorConfig.class);
        context.refresh();

        AnnotatedTimedService service = context.getBean(AnnotatedTimedService.class);
        MeterRegistry registry = context.getBean(MeterRegistry.class);

        service.timeWithoutValue();
        assertThat(registry.get("class.invoke")
            .tag("class", "AnnotatedTimedServiceImpl")
            .tag("method", "timeWithoutValue")
            .tag("extra", "tag")
            .timer().count()).isEqualTo(1);
    }

    @Test
    public void testMethodLevelMetricName() {
        context.register(DefaultTimedMethodInterceptorConfig.class);
        context.refresh();

        AnnotatedTimedService service = context.getBean(AnnotatedTimedService.class);
        MeterRegistry registry = context.getBean(MeterRegistry.class);

        service.timeWithMethodLevelName();
        assertThat(registry.get("my.method")
            .tag("class", "AnnotatedTimedServiceImpl")
            .tag("method", "timeWithMethodLevelName")
            .tag("extra", "tag")
            .timer().count()).isEqualTo(1);
    }

    @Test
    public void testMergedClassAndMethodTags() {
        context.register(DefaultTimedMethodInterceptorConfig.class);
        context.refresh();

        AnnotatedTimedService service = context.getBean(AnnotatedTimedService.class);
        MeterRegistry registry = context.getBean(MeterRegistry.class);

        service.timeWithMergedTags();
        assertThat(registry.get("class.invoke")
            .tag("class", "AnnotatedTimedServiceImpl")
            .tag("method", "timeWithMergedTags")
            .tag("extra", "tag")
            .tag("extra2", "tag")
            .timer().count()).isEqualTo(1);
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import({ TimedServiceImpl.class, AnnotatedTimedServiceImpl.class })
    static class DefaultTimedMethodInterceptorConfig {
        @Bean
        public SimpleMeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        public TimedMethodInterceptor timedMethodInterceptor(MeterRegistry meterRegistry) {
            return new TimedMethodInterceptor(meterRegistry);
        }

        @Bean
        public TimedAnnotationAdvisor timedAnnotationAdvisor(TimedMethodInterceptor timedMethodInterceptor) {
            return new TimedAnnotationAdvisor(timedMethodInterceptor);
        }
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import({ TimedServiceImpl.class, AnnotatedTimedServiceImpl.class })
    static class CustomizedTimedMethodInterceptorConfig {
        @Bean
        public SimpleMeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        public TimedMethodInterceptor timedMethodInterceptor(MeterRegistry meterRegistry) {
            return new TimedMethodInterceptor(meterRegistry, (metricName, invocation) -> "method.invoke", invocation -> Tags.of("test", "tag"));
        }

        @Bean
        public TimedAnnotationAdvisor timedAnnotationAdvisor(TimedMethodInterceptor timedMethodInterceptor) {
            return new TimedAnnotationAdvisor(timedMethodInterceptor);
        }
    }

    interface TimedService {
        String timeWithExplicitValue();

        String timeWithoutValue();

        @Timed(extraTags = { "extra", "tag" })
        String timeOnInterface();
    }

    @Service
    static class TimedServiceImpl implements TimedService {
        @Timed(value = "something", extraTags = { "extra", "tag" })
        @Override
        public String timeWithExplicitValue() {
            return "I'm";
        }

        @Timed(extraTags = { "extra", "tag" })
        @Override
        public String timeWithoutValue() {
            return "sorry";
        }

        @Override
        public String timeOnInterface() {
            return "Dave,";
        }
    }

    @Timed(value = "class.invoke", description = "class description", extraTags = { "extra", "tag" })
    interface AnnotatedTimedService {

        String timeWithoutValue();

        String timeWithMethodLevelName();

        String timeWithMergedTags();
    }

    @Service
    static class AnnotatedTimedServiceImpl implements AnnotatedTimedService {

        @Override
        public String timeWithoutValue() {
            return "I can't";
        }

        @Override
        @Timed("my.method")
        public String timeWithMethodLevelName() {
            return "do";
        }

        @Override
        @Timed(extraTags = { "extra2", "tag" })
        public String timeWithMergedTags() {
            return "that";
        }
    }
}