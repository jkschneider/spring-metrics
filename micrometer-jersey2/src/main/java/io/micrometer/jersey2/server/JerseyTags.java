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
package io.micrometer.jersey2.server;

import java.util.List;
import java.util.regex.Pattern;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.http.Outcome;
import io.micrometer.core.instrument.util.StringUtils;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.uri.UriTemplate;

/**
 * Factory methods for {@link Tag Tags} associated with a request-response exchange that
 * is handled by Jersey 2.
 *
 * @author Michael Weirauch
 * @author Johnny Lim
 * @since 1.1.0
 */
public final class JerseyTags {

    private static final Tag URI_NOT_FOUND = Tag.of("uri", "NOT_FOUND");

    private static final Tag URI_REDIRECTION = Tag.of("uri", "REDIRECTION");

    private static final Tag URI_ROOT = Tag.of("uri", "root");

    private static final Tag EXCEPTION_NONE = Tag.of("exception", "None");

    private static final Tag STATUS_SERVER_ERROR = Tag.of("status", String.valueOf(Status.INTERNAL_SERVER_ERROR.getStatusCode()));

    private static final Tag METHOD_UNKNOWN = Tag.of("method", "UNKNOWN");

    private static final Pattern TRAILING_SLASH_PATTERN = Pattern.compile("/$");

    private static final Pattern MULTIPLE_SLASH_PATTERN = Pattern.compile("//+");

    private JerseyTags() {
    }

    /**
     * Creates a {@code method} tag based on the {@link ContainerRequest#getMethod()
     * method} of the given {@code request}.
     * @param request the container request
     * @return the method tag whose value is a capitalized method (e.g. GET).
     */
    public static Tag method(ContainerRequest request) {
        return (request != null) ? Tag.of("method", request.getMethod()) : METHOD_UNKNOWN;
    }

    /**
     * Creates a {@code status} tag based on the status of the given {@code response}.
     * @param response the container response
     * @return the status tag derived from the status of the response
     */
    public static Tag status(ContainerResponse response) {
        /* In case there is no response we are dealing with an unmapped exception. */
        return (response != null)
                ? Tag.of("status", Integer.toString(response.getStatus()))
                : STATUS_SERVER_ERROR;
    }

    /**
     * Creates a {@code uri} tag based on the URI of the given {@code event}. Uses the
     * {@link ExtendedUriInfo#getMatchedTemplates()} if
     * available. {@code REDIRECTION} for 3xx responses, {@code NOT_FOUND} for 404 responses.
     * @param event the request event
     * @return the uri tag derived from the request event
     */
    public static Tag uri(RequestEvent event) {
        ContainerResponse response = event.getContainerResponse();
        if (response != null) {
            int status = response.getStatus();
            if (isRedirection(status)) {
                return URI_REDIRECTION;
            }
            if (status == 404 && event.getUriInfo().getMatchedResourceMethod() == null) {
                return URI_NOT_FOUND;
            }
        }
        String matchingPattern = getMatchingPattern(event);
        if (matchingPattern.equals("/")) {
            return URI_ROOT;
        }
        return Tag.of("uri", matchingPattern);
    }

    private static boolean isRedirection(int status) {
        return Response.Status.Family.familyOf(status) == Response.Status.Family.REDIRECTION;
    }

    private static String getMatchingPattern(RequestEvent event) {
        ExtendedUriInfo uriInfo = event.getUriInfo();
        List<UriTemplate> templates = uriInfo.getMatchedTemplates();

        StringBuilder sb = new StringBuilder();
        sb.append(uriInfo.getBaseUri().getPath());
        for (int i = templates.size() - 1; i >= 0; i--) {
            sb.append(templates.get(i).getTemplate());
        }
        String multipleSlashCleaned = MULTIPLE_SLASH_PATTERN.matcher(sb.toString()).replaceAll("/");
        if (multipleSlashCleaned.equals("/")) {
            return multipleSlashCleaned;
        }
        return TRAILING_SLASH_PATTERN.matcher(multipleSlashCleaned).replaceAll("");
    }

    /**
     * Creates a {@code exception} tag based on the {@link Class#getSimpleName() simple
     * name} of the class of the given {@code exception}.
     * @param event the request event
     * @return the exception tag derived from the exception
     */
    public static Tag exception(RequestEvent event) {
        Throwable exception = event.getException();
        if (exception == null) {
            return EXCEPTION_NONE;
        }
        ContainerResponse response = event.getContainerResponse();
        if (response != null) {
            int status = response.getStatus();
            if (status == 404 || isRedirection(status)) {
                return EXCEPTION_NONE;
            }
        }
        if (exception.getCause() != null) {
            exception = exception.getCause();
        }
        String simpleName = exception.getClass().getSimpleName();
        return Tag.of("exception", StringUtils.isNotEmpty(simpleName) ? simpleName
                : exception.getClass().getName());
    }

    /**
     * Creates an {@code outcome} tag based on the status of the given {@code response}.
     * @param response the container response
     * @return the outcome tag derived from the status of the response
     */
    public static Tag outcome(ContainerResponse response) {
        if (response != null) {
            return Outcome.forStatus(response.getStatus()).asTag();
        }
        /* In case there is no response we are dealing with an unmapped exception. */
        return Outcome.SERVER_ERROR.asTag();
    }

}
