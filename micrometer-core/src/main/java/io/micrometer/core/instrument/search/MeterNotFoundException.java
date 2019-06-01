/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.search;

import io.micrometer.core.instrument.*;
import io.micrometer.core.lang.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * @author Jon Schneider. I promise, this was not a fun class to write. Be glad it wasn't you :)
 */
@SuppressWarnings("serial")
public class MeterNotFoundException extends RuntimeException {
    private static final String OK = "OK:";
    private static final String NOT_OK = "FAIL:";

    private MeterNotFoundException(@Nullable String nameDetail, @Nullable String typeDetail, @Nullable List<String> tagDetail) {
        super("Unable to find a meter that matches all the requirements at once. Here's what was found:"
                + (nameDetail == null ? "" : "\n   " + nameDetail)
                + (typeDetail == null ? "" : "\n   " + typeDetail)
                + (tagDetail == null ? "" : "\n   " + tagDetail.stream().collect(joining("\n   "))));
    }

    static MeterNotFoundException forSearch(RequiredSearch search, Class<? extends Meter> requiredType) {
        return new FromRequiredSearch(search, requiredType).build();
    }

    private static class FromRequiredSearch {
        private final RequiredSearch search;
        private final Class<? extends Meter> requiredMeterType;

        private FromRequiredSearch(RequiredSearch search, Class<? extends Meter> requiredMeterType) {
            this.search = search;
            this.requiredMeterType = requiredMeterType;
        }

        @Nullable
        private String nameDetail() {
            if (search.nameMatches == null)
                return null;

            Collection<String> matchingName = Search.in(search.registry)
                    .name(search.nameMatches)
                    .meters()
                    .stream()
                    .map(m -> m.getId().getName())
                    .distinct()
                    .sorted()
                    .collect(toList());

            if (!matchingName.isEmpty()) {
                if (matchingName.size() == 1) {
                    return OK + " A meter with name '" + matchingName.iterator().next() + "' was found.";
                } else {
                    return OK + " Meters with names ["
                            + matchingName.stream().map(name -> "'" + name + "'").collect(joining(", "))
                            + "] were found.";
                }
            }

            if (search.exactNameMatch != null) {
                return NOT_OK + " No meter with name '" + search.exactNameMatch + "' was found.";
            }
            return NOT_OK + " No meter that matches the name predicate was found.";
        }

        @Nullable
        private List<String> tagDetail() {
            if (search.requiredTagKeys.isEmpty() && search.requiredTags.isEmpty())
                return null;

            List<String> details = new ArrayList<>();

            for (String requiredKey : search.requiredTagKeys) {
                Collection<String> matchingRequiredKey = Search.in(search.registry).name(search.nameMatches).tagKeys(requiredKey)
                        .meters()
                        .stream()
                        .filter(requiredMeterType::isInstance)
                        .map(m -> m.getId().getName())
                        .distinct()
                        .sorted()
                        .collect(toList());

                String requiredTagDetail = "the required tag '" + requiredKey + "'.";
                if (matchingRequiredKey.isEmpty()) {
                    details.add(NOT_OK + " No meters have " + requiredTagDetail);
                } else if (matchingRequiredKey.size() == 1) {
                    details.add(OK + " A meter with name '" + matchingRequiredKey.iterator().next() + "' has " + requiredTagDetail);
                } else {
                    details.add(OK + " Meters with names ["
                            + matchingRequiredKey.stream().map(name -> "'" + name + "'").collect(joining(", "))
                            + "] have " + requiredTagDetail);
                }
            }

            for (Tag requiredTag : search.requiredTags) {
                Collection<String> matchingRequiredTag = Search.in(search.registry).name(search.nameMatches).tag(requiredTag.getKey(), requiredTag.getValue())
                        .meters()
                        .stream()
                        .filter(requiredMeterType::isInstance)
                        .map(m -> m.getId().getName())
                        .distinct()
                        .sorted()
                        .collect(toList());

                String requiredTagDetail = "a tag '" + requiredTag.getKey() + "' with value '" + requiredTag.getValue() + "'.";
                if (matchingRequiredTag.isEmpty()) {
                    Collection<String> nonMatchingValues = Search.in(search.registry).name(search.nameMatches).tagKeys(requiredTag.getKey())
                            .meters()
                            .stream()
                            .filter(requiredMeterType::isInstance)
                            .map(m -> m.getId().getTag(requiredTag.getKey()))
                            .distinct()
                            .sorted()
                            .collect(toList());

                    if (nonMatchingValues.isEmpty()) {
                        details.add(NOT_OK + " No meters have the required tag '" + requiredTag.getKey() + "'.");
                    } else if (nonMatchingValues.size() == 1) {
                        details.add(NOT_OK + " No meters have " + requiredTagDetail + " The only value found was '"
                                + nonMatchingValues.iterator().next() + "'.");
                    } else {
                        details.add(NOT_OK + " No meters have " + requiredTagDetail + " Tag values found were ["
                                + nonMatchingValues.stream().map(v -> "'" + v + "'").collect(joining(", "))
                                + "].");
                    }
                } else if (matchingRequiredTag.size() == 1) {
                    details.add(OK + " A meter with name '" + matchingRequiredTag.iterator().next() + "' has " + requiredTagDetail);
                } else {
                    details.add(OK + " Meters with names ["
                            + matchingRequiredTag.stream().map(name -> "'" + name + "'").collect(joining(", "))
                            + "] have " + requiredTagDetail);
                }
            }

            return details;
        }

        /**
         * This makes the (I think fairly safe) assumption that there aren't meters of two or more types that vary only by tag such
         * that none of the types match the search criteria.
         *
         * @return Detail on why the type requirement was not met.
         */
        @Nullable
        private String typeDetail() {
            if (requiredMeterType.equals(Meter.class))
                return null;

            if (search.nameMatches != null) {
                Collection<Meter> matchesName = Search.in(search.registry)
                        .name(search.nameMatches)
                        .meters();

                if (!matchesName.isEmpty()) {
                    Collection<String> nonMatchingTypes = matchesName.stream()
                            .filter(m -> !requiredMeterType.isInstance(m))
                            .map(m -> meterTypeName(m.getClass()))
                            .distinct()
                            .sorted()
                            .collect(toList());

                    if (nonMatchingTypes.size() == 1) {
                        return NOT_OK + " Expected to find a " + meterTypeName(requiredMeterType) + ". The only type found was a " +
                                nonMatchingTypes.iterator().next() + ".";
                    } else if (nonMatchingTypes.size() > 1) {
                        return NOT_OK + " Expected to find a " + meterTypeName(requiredMeterType) + ". Types found were [" +
                                nonMatchingTypes.stream().collect(joining(", ")) + "].";
                    }
                }
            }

            long count = Search.in(search.registry)
                    .meters()
                    .stream()
                    .filter(requiredMeterType::isInstance)
                    .count();

            if (count == 0) {
                return NOT_OK + " No meters with type " + meterTypeName(requiredMeterType) + " were found.";
            } else if (count == 1) {
                return OK + " A meter with type " + meterTypeName(requiredMeterType) + " was found.";
            } else {
                return OK + " Meters with type " + meterTypeName(requiredMeterType) + " were found.";
            }
        }

        private String meterTypeName(Class<?> meterType) {
            if (Counter.class.isAssignableFrom(meterType))
                return "counter";
            else if (Gauge.class.isAssignableFrom(meterType))
                return "gauge";
            else if (LongTaskTimer.class.isAssignableFrom(meterType))
                return "long task timer";
            else if (Timer.class.isAssignableFrom(meterType))
                return "timer";
            else if (FunctionTimer.class.isAssignableFrom(meterType))
                return "function timer";
            else if (FunctionCounter.class.isAssignableFrom(meterType))
                return "function counter";
            else if (TimeGauge.class.isAssignableFrom(meterType))
                return "time gauge";
            else if (DistributionSummary.class.isAssignableFrom(meterType))
                return "distribution summary";
            else
                return meterType.getSimpleName();
        }

        private MeterNotFoundException build() {
            return new MeterNotFoundException(nameDetail(), typeDetail(), tagDetail());
        }
    }
}
