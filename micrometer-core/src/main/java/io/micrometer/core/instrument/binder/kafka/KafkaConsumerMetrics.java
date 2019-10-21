/**
 * Copyright 2018 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.binder.kafka;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import io.micrometer.core.lang.Nullable;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.ToDoubleFunction;

import static java.util.Collections.emptyList;

/**
 * Kafka consumer metrics collected from metrics exposed by Kafka consumers via the MBeanServer. Metrics are exposed
 * at each consumer thread.
 * <p>
 * Metric names here are based on the naming scheme as it was last changed in Kafka version 0.11.0. Metrics for earlier
 * versions of Kafka will not report correctly.
 *
 * @author Wardha Perinkadakattu
 * @author Jon Schneider
 * @author Johnny Lim
 * @see <a href="https://docs.confluent.io/current/kafka/monitoring.html">Kakfa monitoring documentation</a>
 * @since 1.1.0
 * @deprecated since 1.X.0 in favor of {@link KafkaConsumerApiMetrics}
 */
@Deprecated
@Incubating(since = "1.1.0")
@NonNullApi
@NonNullFields
public class KafkaConsumerMetrics implements MeterBinder, AutoCloseable {
    private static final String JMX_DOMAIN = "kafka.consumer";
    private static final String METRIC_NAME_PREFIX = "kafka.consumer.";

    private final MBeanServer mBeanServer;

    private final Iterable<Tag> tags;

    @Nullable
    private Integer kafkaMajorVersion;

    private final List<Runnable> notificationListenerCleanUpRunnables = new CopyOnWriteArrayList<>();

    public KafkaConsumerMetrics() {
        this(emptyList());
    }

    public KafkaConsumerMetrics(Iterable<Tag> tags) {
        this(getMBeanServer(), tags);
    }

    public KafkaConsumerMetrics(MBeanServer mBeanServer, Iterable<Tag> tags) {
        this.mBeanServer = mBeanServer;
        this.tags = tags;
    }

    private static MBeanServer getMBeanServer() {
        List<MBeanServer> mBeanServers = MBeanServerFactory.findMBeanServer(null);
        if (!mBeanServers.isEmpty()) {
            return mBeanServers.get(0);
        }
        return ManagementFactory.getPlatformMBeanServer();
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registerMetricsEventually("consumer-fetch-manager-metrics", (o, tags) -> {
            // metrics reported per consumer, topic and partition
            if (tags.stream().anyMatch(t -> t.getKey().equals("topic")) && tags.stream().anyMatch(t -> t.getKey().equals("partition"))) {
                registerGaugeForObject(registry, o, "records-lag", tags, "The latest lag of the partition", "records");
                registerGaugeForObject(registry, o, "records-lag-avg", tags, "The average lag of the partition", "records");
                registerGaugeForObject(registry, o, "records-lag-max", tags, "The maximum lag in terms of number of records for any partition in this window. An increasing value over time is your best indication that the consumer group is not keeping up with the producers.", "records");
                if (kafkaMajorVersion(tags) >= 2) {
                    // KAFKA-6184
                    registerGaugeForObject(registry, o, "records-lead", tags, "The latest lead of the partition.", "records");
                    registerGaugeForObject(registry, o, "records-lead-min", tags, "The min lead of the partition. The lag between the consumer offset and the start offset of the log. If this gets close to zero, it's an indication that the consumer may lose data soon.", "records");
                    registerGaugeForObject(registry, o, "records-lead-avg", tags, "The average lead of the partition.", "records");
                }
            // metrics reported per consumer and topic
            }  else if (tags.stream().anyMatch(t -> t.getKey().equals("topic"))) {
                registerGaugeForObject(registry, o, "fetch-size-avg", tags, "The average number of bytes fetched per request.", BaseUnits.BYTES);
                registerGaugeForObject(registry, o, "fetch-size-max", tags, "The maximum number of bytes fetched per request.", BaseUnits.BYTES);
                registerGaugeForObject(registry, o, "records-per-request-avg", tags, "The average number of records in each request.", "records");
                registerFunctionCounterForObject(registry, o, "bytes-consumed-total", tags, "The total number of bytes consumed.", BaseUnits.BYTES);
                registerFunctionCounterForObject(registry, o, "records-consumed-total", tags, "The total number of records consumed.", "records");
            // metrics reported just per consumer
            }  else {
                registerFunctionCounterForObject(registry, o, "fetch-total", tags, "The number of fetch requests.", "requests");
                registerTimeGaugeForObject(registry, o, "fetch-latency-avg", tags, "The average time taken for a fetch request.");
                registerTimeGaugeForObject(registry, o, "fetch-latency-max", tags, "The max time taken for a fetch request.");
                registerTimeGaugeForObject(registry, o, "fetch-throttle-time-avg", tags, "The average throttle time. When quotas are enabled, the broker may delay fetch requests in order to throttle a consumer which has exceeded its limit. This metric indicates how throttling time has been added to fetch requests on average.");
                registerTimeGaugeForObject(registry, o, "fetch-throttle-time-max", tags, "The maximum throttle time.");
            }
        });

        registerMetricsEventually("consumer-coordinator-metrics", (o, tags) -> {
            registerGaugeForObject(registry, o, "assigned-partitions", tags, "The number of partitions currently assigned to this consumer.", "partitions");
            registerGaugeForObject(registry, o, "commit-rate", tags, "The number of commit calls per second.", "commits");
            registerGaugeForObject(registry, o, "join-rate", tags, "The number of group joins per second. Group joining is the first phase of the rebalance protocol. A large value indicates that the consumer group is unstable and will likely be coupled with increased lag.", "joins");
            registerGaugeForObject(registry, o, "sync-rate", tags, "The number of group syncs per second. Group synchronization is the second and last phase of the rebalance protocol. A large value indicates group instability.", "syncs");
            registerGaugeForObject(registry, o, "heartbeat-rate", tags, "The average number of heartbeats per second. After a rebalance, the consumer sends heartbeats to the coordinator to keep itself active in the group. You may see a lower rate than configured if the processing loop is taking more time to handle message batches. Usually this is OK as long as you see no increase in the join rate.", "heartbeats");

            registerTimeGaugeForObject(registry, o, "commit-latency-avg", tags, "The average time taken for a commit request.");
            registerTimeGaugeForObject(registry, o, "commit-latency-max", tags, "The max time taken for a commit request.");
            registerTimeGaugeForObject(registry, o, "join-time-avg", tags, "The average time taken for a group rejoin. This value can get as high as the configured session timeout for the consumer, but should usually be lower.");
            registerTimeGaugeForObject(registry, o, "join-time-max", tags, "The max time taken for a group rejoin. This value should not get much higher than the configured session timeout for the consumer.");
            registerTimeGaugeForObject(registry, o, "sync-time-avg", tags, "The average time taken for a group sync.");
            registerTimeGaugeForObject(registry, o, "sync-time-max", tags, "The max time taken for a group sync.");
            registerTimeGaugeForObject(registry, o, "heartbeat-response-time-max", tags, "The max time taken to receive a response to a heartbeat request.");
            registerTimeGaugeForObject(registry, o, "last-heartbeat-seconds-ago", "last-heartbeat", tags,
                    "The time since the last controller heartbeat.", TimeUnit.SECONDS);
        });

        registerMetricsEventually("consumer-metrics", (o, tags) -> {
            registerGaugeForObject(registry, o, "connection-count", tags, "The current number of active connections.", "connections");
            registerGaugeForObject(registry, o, "connection-creation-total", tags, "New connections established.", "connections");
            registerGaugeForObject(registry, o, "connection-close-total", tags, "Connections closed.", "connections");
            registerGaugeForObject(registry, o, "io-ratio", tags, "The fraction of time the I/O thread spent doing I/O.", null);
            registerGaugeForObject(registry, o, "io-wait-ratio", tags, "The fraction of time the I/O thread spent waiting.", null);
            registerGaugeForObject(registry, o, "select-total", tags, "Number of times the I/O layer checked for new I/O to perform.", null);

            registerTimeGaugeForObject(registry, o, "io-time-ns-avg", "io-time-avg", tags,
                    "The average length of time for I/O per select call.", TimeUnit.NANOSECONDS);
            registerTimeGaugeForObject(registry, o, "io-wait-time-ns-avg", "io-wait-time-avg", tags,
                    "The average length of time the I/O thread spent waiting for a socket to be ready for reads or writes.",
                    TimeUnit.NANOSECONDS);

            if (kafkaMajorVersion(tags) >= 2) {
                registerGaugeForObject(registry, o, "successful-authentication-total", "authentication-attempts",
                        Tags.concat(tags, "result", "successful"), "The number of successful authentication attempts.", null);
                registerGaugeForObject(registry, o, "failed-authentication-total", "authentication-attempts",
                        Tags.concat(tags, "result", "failed"), "The number of failed authentication attempts.", null);

                registerGaugeForObject(registry, o, "network-io-total", tags, "", BaseUnits.BYTES);
                registerGaugeForObject(registry, o, "outgoing-byte-total", tags, "", BaseUnits.BYTES);
                registerGaugeForObject(registry, o, "request-total", tags, "", "requests");
                registerGaugeForObject(registry, o, "response-total", tags, "", "responses");

                registerTimeGaugeForObject(registry, o, "io-waittime-total", "io-wait-time-total", tags,
                        "Time spent on the I/O thread waiting for a socket to be ready for reads or writes.",
                        TimeUnit.NANOSECONDS);
                registerTimeGaugeForObject(registry, o, "iotime-total", "io-time-total", tags,
                        "Time spent in I/O during select calls.", TimeUnit.NANOSECONDS);
            }
        });
    }

    private void registerGaugeForObject(MeterRegistry registry, ObjectName o, String jmxMetricName, String meterName, Tags allTags, String description, @Nullable String baseUnit) {
        final AtomicReference<Gauge> gauge = new AtomicReference<>();
        gauge.set(Gauge
                .builder(METRIC_NAME_PREFIX + meterName, mBeanServer,
                        getJmxAttribute(registry, gauge, o, jmxMetricName))
                .description(description)
                .baseUnit(baseUnit)
                .tags(allTags)
                .register(registry));
    }

    private void registerGaugeForObject(MeterRegistry registry, ObjectName o, String jmxMetricName, Tags allTags, String description, @Nullable String baseUnit) {
        registerGaugeForObject(registry, o, jmxMetricName, sanitize(jmxMetricName), allTags, description, baseUnit);
    }

    private void registerFunctionCounterForObject(MeterRegistry registry, ObjectName o, String jmxMetricName, Tags allTags, String description, @Nullable String baseUnit) {
        final AtomicReference<FunctionCounter> counter = new AtomicReference<>();
        counter.set(FunctionCounter
                .builder(METRIC_NAME_PREFIX + sanitize(jmxMetricName), mBeanServer,
                        getJmxAttribute(registry, counter, o, jmxMetricName))
                .description(description)
                .baseUnit(baseUnit)
                .tags(allTags)
                .register(registry));
    }

    private void registerTimeGaugeForObject(MeterRegistry registry, ObjectName o, String jmxMetricName,
            String meterName, Tags allTags, String description, TimeUnit timeUnit) {
        final AtomicReference<TimeGauge> timeGauge = new AtomicReference<>();
        timeGauge.set(TimeGauge.builder(METRIC_NAME_PREFIX + meterName, mBeanServer, timeUnit,
                getJmxAttribute(registry, timeGauge, o, jmxMetricName))
                .description(description)
                .tags(allTags)
                .register(registry));
    }

    private void registerTimeGaugeForObject(MeterRegistry registry, ObjectName o, String jmxMetricName,
            String meterName, Tags allTags, String description) {
        registerTimeGaugeForObject(registry, o, jmxMetricName, meterName, allTags, description, TimeUnit.MILLISECONDS);
    }

    private ToDoubleFunction<MBeanServer> getJmxAttribute(MeterRegistry registry, AtomicReference<? extends Meter> meter,
                                                          ObjectName o, String jmxMetricName) {
        return s -> safeDouble(jmxMetricName, () -> {
            if (!s.isRegistered(o)) {
                registry.remove(meter.get());
            }
            return s.getAttribute(o, jmxMetricName);
        });
    }

    private void registerTimeGaugeForObject(MeterRegistry registry, ObjectName o, String jmxMetricName, Tags allTags, String description) {
        registerTimeGaugeForObject(registry, o, jmxMetricName, sanitize(jmxMetricName), allTags, description);
    }

    int kafkaMajorVersion(Tags tags) {
        if (kafkaMajorVersion == null || kafkaMajorVersion == -1) {
            kafkaMajorVersion = tags.stream().filter(t -> "client.id".equals(t.getKey())).findAny()
                    .map(clientId -> {
                        try {
                            String version = (String) mBeanServer.getAttribute(new ObjectName(JMX_DOMAIN + ":type=app-info,client-id=" + clientId.getValue()), "version");
                            return Integer.parseInt(version.substring(0, version.indexOf('.')));
                        } catch (Throwable e) {
                            // this can happen during application bootstrapping
                            // in this case, JMX bean is not available yet and version cannot be determined yet
                            return -1;
                        }
                    })
                    .orElse(-1);
        }
        return kafkaMajorVersion;
    }

    private void registerMetricsEventually(String type, BiConsumer<ObjectName, Tags> perObject) {
        try {
            Set<ObjectName> objs = mBeanServer.queryNames(new ObjectName(JMX_DOMAIN + ":type=" + type + ",*"), null);
            if (!objs.isEmpty()) {
                for (ObjectName o : objs) {
                    perObject.accept(o, Tags.concat(tags, nameTag(o)));
                }
                return;
            }
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException("Error registering Kafka JMX based metrics", e);
        }

        registerNotificationListener(type, perObject);
    }

    /**
     * This notification listener should remain indefinitely since new Kafka consumers can be added at any time.
     *
     * @param type      The Kafka JMX type to listen for.
     * @param perObject Metric registration handler when a new MBean is created.
     */
    private void registerNotificationListener(String type, BiConsumer<ObjectName, Tags> perObject) {
        NotificationListener notificationListener = (notification, handback) -> {
            MBeanServerNotification mbs = (MBeanServerNotification) notification;
            ObjectName o = mbs.getMBeanName();
            perObject.accept(o, Tags.concat(tags, nameTag(o)));
        };

        NotificationFilter filter = (NotificationFilter) notification -> {
            if (!MBeanServerNotification.REGISTRATION_NOTIFICATION.equals(notification.getType()))
                return false;
            ObjectName obj = ((MBeanServerNotification) notification).getMBeanName();
            return obj.getDomain().equals(JMX_DOMAIN) && obj.getKeyProperty("type").equals(type);
        };

        try {
            mBeanServer.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, notificationListener, filter, null);
            notificationListenerCleanUpRunnables.add(() -> {
                try {
                    mBeanServer.removeNotificationListener(MBeanServerDelegate.DELEGATE_NAME, notificationListener);
                } catch (InstanceNotFoundException | ListenerNotFoundException ignored) {
                }
            });
        } catch (InstanceNotFoundException e) {
            throw new RuntimeException("Error registering Kafka MBean listener", e);
        }
    }

    private double safeDouble(String jmxMetricName, Callable<Object> callable) {
        try {
            return Double.parseDouble(callable.call().toString());
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    private Iterable<Tag> nameTag(ObjectName name) {
        Tags tags = Tags.empty();

        String clientId = name.getKeyProperty("client-id");
        if (clientId != null) {
            tags = Tags.concat(tags, "client.id", clientId);
        }

        String topic = name.getKeyProperty("topic");
        if (topic != null) {
            tags = Tags.concat(tags, "topic", topic);
        }

        String partition = name.getKeyProperty("partition");
        if (partition != null) {
            tags = Tags.concat(tags, "partition", partition);
        }

        return tags;
    }

    private static String sanitize(String value) {
        return value.replaceAll("-", ".");
    }

    @Override
    public void close() {
        notificationListenerCleanUpRunnables.forEach(Runnable::run);
    }
}
