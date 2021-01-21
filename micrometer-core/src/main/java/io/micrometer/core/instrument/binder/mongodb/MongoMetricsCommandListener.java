/**
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.core.instrument.binder.mongodb;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mongodb.MongoClient;
import com.mongodb.event.CommandEvent;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

/**
 * {@link CommandListener} for collecting command metrics from {@link MongoClient}.
 *
 * @author Christophe Bornet
 * @since 1.2.0
 */
@NonNullApi
@NonNullFields
@Incubating(since = "1.2.0")
public class MongoMetricsCommandListener implements CommandListener {

    /**
     * The key under which the collection to which a MongoDB "get more" command pertains is stored.
     */
    private static final String GET_MORE_COMMAND_COLLECTION_KEY = "collection";
    private static final String UNKNOWN_COLLECTION = "unknown";
    private static final Timer.Builder TIMER_BUILDER =
            Timer.builder("mongodb.driver.commands").description("Timer of MongoDB commands");

    private final MeterRegistry registry;
    private final Cache<Integer, String> requestIdToCollectionName =
            Caffeine.newBuilder()
                    .expireAfterWrite(Duration.ofMinutes(10))
                    .maximumSize(1000)
                    .build();

    public MongoMetricsCommandListener(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void commandStarted(CommandStartedEvent commandStartedEvent) {
        registerRequest(commandStartedEvent);
    }

    @Override
    public void commandSucceeded(CommandSucceededEvent event) {
        timeCommand(
                event,
                "SUCCESS",
                event.getElapsedTime(TimeUnit.NANOSECONDS),
                deregisterRequest(event));
    }

    @Override
    public void commandFailed(CommandFailedEvent event) {
        timeCommand(event, "FAILED", event.getElapsedTime(TimeUnit.NANOSECONDS), deregisterRequest(event));
    }

    /**
     * Registers the tracking information associated with the given request ID, if any.
     *
     * @return The collection associated with the given request, or {@link #UNKNOWN_COLLECTION} if
     *     unknown.
     */
    private String registerRequest(CommandStartedEvent event) {
        String collection = getCollection(event);
        requestIdToCollectionName.put(event.getRequestId(), collection);
        return collection;
    }

    private static String getCollection(CommandStartedEvent event) {
        return getCollectionFromCommand(event, event.getCommand())
                .filter(BsonValue::isString)
                .map(BsonValue::asString)
                .map(BsonString::getValue)
                .orElse(UNKNOWN_COLLECTION);
    }

    private static Optional<BsonValue> getCollectionFromCommand(
            CommandStartedEvent event, BsonDocument document) {
        Optional<BsonValue> collectionFromCommandName =
                Optional.ofNullable(document.get(event.getCommandName()))
                        .filter(BsonValue::isString);
        Optional<BsonValue> collectionFromGetMoreCommand =
                Optional.ofNullable(document.get(GET_MORE_COMMAND_COLLECTION_KEY));
        return collectionFromCommandName.isPresent()
                ? collectionFromCommandName
                : collectionFromGetMoreCommand;
    }

    /**
     * Removes the tracking information associated with the given request ID, if any.
     *
     * @return The collection associated with the given request, or {@link #UNKNOWN_COLLECTION} if
     *     unknown.
     */
    private String deregisterRequest(CommandEvent event) {
        return Optional.ofNullable(requestIdToCollectionName.asMap().remove(event.getRequestId()))
                .orElse(UNKNOWN_COLLECTION);
    }

    private void timeCommand(
            CommandEvent event, String status, long elapsedTimeInNanoseconds, String collection) {
        TIMER_BUILDER
                .tag("collection", collection)
                .tag("command", event.getCommandName())
                .tag("cluster.id", event.getConnectionDescription().getConnectionId().getServerId().getClusterId().getValue())
                .tag("server.address", event.getConnectionDescription().getServerAddress().toString())
                .tag("status", status)
                .register(registry)
                .record(elapsedTimeInNanoseconds, TimeUnit.NANOSECONDS);
    }

}

