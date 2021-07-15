/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.benchmark.core;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@State(Scope.Benchmark)
@Fork(1)
@Measurement(iterations = 2)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ReplaceEscapeCharactersBenchmark {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ReplaceEscapeCharactersBenchmark.class.getSimpleName())
                .warmupIterations(2)
                .measurementIterations(2)
                .addProfiler(GCProfiler.class)
                .build();
        new Runner(opt).run();
    }

    private static final Pattern PATTERN_SPECIAL_CHARACTERS = Pattern.compile("([, =\"])");
    private String string;
    private Map<String, String> replaceMap = new HashMap<>();

    @Setup
    public void setup() {
        string = "jabjks.fjsjfd-jfkdsf=jhfdsf,jkkfdf\"fjdsjfe";

        replaceMap.put(",", "\\,");
        replaceMap.put(" ", "\\ ");
        replaceMap.put("=", "\\=");
        replaceMap.put("\"", "\\\"");
    }

    @Benchmark
    public String regex() {
        return PATTERN_SPECIAL_CHARACTERS.matcher(string).replaceAll("\\\\$1");
    }

    @Benchmark
    public String replaceFromMap() {
        return replaceFromMap(string, replaceMap);
    }

    public static String replaceFromMap(String string, Map<String, String> replacements) {
        StringBuilder sb = newStringBuilder(string);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            int start = sb.indexOf(key, 0);
            while (start > -1) {
                int end = start + key.length();
                int nextSearchStart = start + value.length();
                sb.replace(start, end, value);
                start = sb.indexOf(key, nextSearchStart);
            }
        }
        return sb.toString();
    }

    private static StringBuilder sb = new StringBuilder();

    private static StringBuilder newStringBuilder(String string) {
        sb.setLength(0);
        sb.append(string);
        return sb;
    }
}
