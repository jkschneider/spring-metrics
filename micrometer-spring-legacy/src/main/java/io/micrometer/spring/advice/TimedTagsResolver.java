package io.micrometer.spring.advice;

import io.micrometer.core.instrument.Tag;
import org.aopalliance.intercept.MethodInvocation;

import java.util.function.Function;

public interface TimedTagsResolver extends Function<MethodInvocation, Iterable<Tag>> {
}
