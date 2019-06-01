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
package io.micrometer.core.ipc.http;

import io.micrometer.core.instrument.util.JsonUtils;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.lang.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;

/**
 * A general-purpose interface for controlling how {@link io.micrometer.core.instrument.MeterRegistry} implementations
 * perform HTTP calls for various purposes. This interface can be used to inject more advanced customization like SSL
 * verification, key loading, etc. without requiring further additions to registry configurations.
 *
 * @author Jon Schneider
 * @since 1.1.0
 */
public interface HttpSender {
    Response send(Request request) throws Throwable;

    default Request.Builder post(String uri) {
        return newRequest(uri).withMethod(Method.POST);
    }

    default Request.Builder head(String uri) {
        return newRequest(uri).withMethod(Method.HEAD);
    }

    default Request.Builder put(String uri) {
        return newRequest(uri).withMethod(Method.PUT);
    }

    default Request.Builder get(String uri) {
        return newRequest(uri).withMethod(Method.GET);
    }

    default Request.Builder delete(String uri) {
        return newRequest(uri).withMethod(Method.DELETE);
    }

    default Request.Builder options(String uri) {
        return newRequest(uri).withMethod(Method.OPTIONS);
    }

    default Request.Builder newRequest(String uri) {
        return new Request.Builder(uri, this);
    }

    class Request {
        private final URL url;
        private final byte[] entity;
        private final Method method;
        private final Map<String, String> requestHeaders;

        public Request(URL url, byte[] entity, Method method, Map<String, String> requestHeaders) {
            this.url = url;
            this.entity = entity;
            this.method = method;
            this.requestHeaders = requestHeaders;
        }

        public URL getUrl() {
            return url;
        }

        public byte[] getEntity() {
            return entity;
        }

        public Method getMethod() {
            return method;
        }

        public Map<String, String> getRequestHeaders() {
            return requestHeaders;
        }

        public static Builder build(String uri, HttpSender sender) {
            return new Builder(uri, sender);
        }

        @Override
        public String toString() {
            StringBuilder printed = new StringBuilder(method.toString()).append(" ")
                    .append(url.toString()).append("\n");
            if (entity.length == 0) {
                printed.append("<no request body>");
            } else if ("application/json".equals(requestHeaders.get("Content-Type"))) {
                printed.append(JsonUtils.prettyPrint(new String(entity)));
            } else {
                printed.append(new String(entity));
            }
            return printed.toString();
        }

        public static class Builder {
            private static final String APPLICATION_JSON = "application/json";
            private static final String TEXT_PLAIN = "text/plain";

            private final URL url;
            private final HttpSender sender;

            private byte[] entity = new byte[0];
            private Method method;
            private Map<String, String> requestHeaders = new LinkedHashMap<>();

            Builder(String uri, HttpSender sender) {
                try {
                    this.url = URI.create(uri).toURL();
                } catch (MalformedURLException ex) {
                    throw new UncheckedIOException(ex);
                }
                this.sender = sender;
            }

            /**
             * Add a header to the request.
             *
             * @param name  The name of the header.
             * @param value The value of the header.
             * @return This request builder.
             */
            public final Builder withHeader(String name, String value) {
                requestHeaders.put(name, value);
                return this;
            }

            /**
             * If user and password are non-empty, set basic authentication on the request.
             *
             * @param user     A user name, if available.
             * @param password A password, if available.
             * @return This request builder.
             */
            public final Builder withBasicAuthentication(@Nullable String user, @Nullable String password) {
                if (user != null && StringUtils.isNotBlank(user)) {
                    String encoded = Base64.getEncoder().encodeToString((user.trim() + ":" + (password == null ? "" : password.trim()))
                            .getBytes(StandardCharsets.UTF_8));
                    withHeader("Authorization", "Basic " + encoded);
                }
                return this;
            }

            /**
             * Set the request body as JSON.
             *
             * @param content The request body.
             * @return This request builder.
             */
            public final Builder withJsonContent(String content) {
                return withContent(APPLICATION_JSON, content);
            }

            /**
             * Set the request body as JSON.
             *
             * @param content The request body.
             * @return This request builder.
             */
            public final Builder withPlainText(String content) {
                return withContent(TEXT_PLAIN, content);
            }

            /**
             * Set the request body.
             *
             * @param type    The value of the "Content-Type" header to add.
             * @param content The request body.
             * @return This request builder.
             */
            public final Builder withContent(String type, String content) {
                return withContent(type, content.getBytes(StandardCharsets.UTF_8));
            }

            /**
             * Set the request body.
             *
             * @param type    The value of the "Content-Type" header to add.
             * @param content The request body.
             * @return This request builder.
             */
            public final Builder withContent(String type, byte[] content) {
                withHeader("Content-Type", type);
                entity = content;
                return this;
            }

            /**
             * Add header to accept {@code application/json} data.
             *
             * @return This request builder.
             */
            public Builder acceptJson() {
                return accept(APPLICATION_JSON);
            }

            /**
             * Add accept header.
             *
             * @param type The value of the "Accept" header to add.
             * @return This request builder.
             */
            public Builder accept(String type) {
                return withHeader("Accept", type);
            }

            /**
             * Set the request method.
             *
             * @param method An HTTP method.
             * @return This request builder.
             */
            public final Builder withMethod(Method method) {
                this.method = method;
                return this;
            }

            /**
             * Add a "Content-Encoding" header of "gzip" and compress the request body.
             *
             * @return This request builder.
             * @throws IOException If compression fails.
             */
            public final Builder compress() throws IOException {
                withHeader("Content-Encoding", "gzip");
                this.entity = gzip(entity);
                return this;
            }

            /**
             * Add a "Content-Encoding" header of "gzip" and compress the request body when the supplied
             * condition is true.
             *
             * @param when Condition that governs when to compress the request body.
             * @return This request builder.
             * @throws IOException If compression fails.
             */
            public final Builder compressWhen(Supplier<Boolean> when) throws IOException {
                if (when.get())
                    return compress();
                return this;
            }

            private static byte[] gzip(byte[] data) throws IOException {
                ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
                try (GZIPOutputStream out = new GZIPOutputStream(bos)) {
                    out.write(data);
                }
                return bos.toByteArray();
            }

            public final Builder print() {
                System.out.println(new Request(url, entity, method, requestHeaders));
                return this;
            }

            public Response send() throws Throwable {
                return sender.send(new Request(url, entity, method, requestHeaders));
            }
        }
    }

    class Response {
        public static final String NO_RESPONSE_BODY = "<no response body>";
        private final int code;
        private final String body;

        public Response(int code, @Nullable String body) {
            this.code = code;
            this.body = StringUtils.isBlank(body) ? NO_RESPONSE_BODY : body;
        }

        public int code() {
            return code;
        }

        public String body() {
            return body;
        }

        public Response onSuccess(Consumer<Response> onSuccess) {
            switch (HttpStatusClass.valueOf(code)) {
                case INFORMATIONAL:
                case SUCCESS:
                    onSuccess.accept(this);
                default:
                    break;
            }
            return this;
        }

        public Response onError(Consumer<Response> onError) {
            switch (HttpStatusClass.valueOf(code)) {
                case CLIENT_ERROR:
                case SERVER_ERROR:
                    onError.accept(this);
                default:
                    break;
            }
            return this;
        }

        public boolean isSuccessful() {
            switch (HttpStatusClass.valueOf(code)) {
                case INFORMATIONAL:
                case SUCCESS:
                    return true;
                default:
                    return false;
            }
        }
    }

    enum Method {
        GET, HEAD, POST, PUT, DELETE, OPTIONS
    }
}
