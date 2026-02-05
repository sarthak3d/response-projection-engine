package com.projection.advice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projection.annotation.Projectable;
import com.projection.annotation.ProjectableFields;
import com.projection.cache.CacheKey;
import com.projection.cache.CachedResponse;
import com.projection.cache.ProjectionCacheManager;
import com.projection.config.ProjectionProperties;
import com.projection.core.AllowlistValidator;
import com.projection.core.FilterContext;
import com.projection.core.ProjectionTree;
import com.projection.core.ProjectionTreeParser;
import com.projection.error.ProjectionErrorResponse;
import com.projection.exception.ProjectionException;
import com.projection.projector.ResponseProjector;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Core interceptor that applies projection to responses from @Projectable endpoints.
 * 
 * Flow:
 * 1. Check if method has @Projectable annotation
 * 2. Check cache for existing full response
 * 3. Parse projection header
 * 4. Validate against @ProjectableFields if present
 * 5. Apply projection to response
 * 6. Cache full response for future requests
 */
@ControllerAdvice
public class ProjectionResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(ProjectionResponseBodyAdvice.class);

    private final ProjectionProperties properties;
    private final ObjectMapper objectMapper;
    private final ResponseProjector projector;
    private final ProjectionCacheManager cacheManager;

    public ProjectionResponseBodyAdvice(
            ProjectionProperties properties,
            ObjectMapper objectMapper,
            ResponseProjector projector,
            ProjectionCacheManager cacheManager) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.projector = projector;
        this.cacheManager = cacheManager;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (!properties.isEnabled()) {
            return false;
        }

        Method method = returnType.getMethod();
        if (method == null) {
            return false;
        }

        return method.isAnnotationPresent(Projectable.class);
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        if (body == null) {
            return null;
        }

        if (body instanceof ResponseEntity<?>) {
            ResponseEntity<?> responseEntity = (ResponseEntity<?>) body;
            if (!responseEntity.getStatusCode().is2xxSuccessful()) {
                return body;
            }
        }

        if (isErrorResponse(body)) {
            return body;
        }

        HttpServletRequest servletRequest = extractServletRequest(request);
        if (servletRequest == null) {
            return body;
        }

        Method method = returnType.getMethod();
        Projectable projectable = method.getAnnotation(Projectable.class);
        ProjectableFields projectableFields = method.getAnnotation(ProjectableFields.class);

        String projectionHeader = servletRequest.getHeader(properties.getHeaderName());

        FilterContext context = FilterContext.builder(properties).build();

        try {
            CacheKey cacheKey = buildCacheKey(servletRequest, projectable);
            JsonNode fullResponse = getOrCacheFullResponse(body, cacheKey, projectable);

            if (projectionHeader == null || projectionHeader.isBlank()) {
                return body;
            }

            ProjectionTree projection = ProjectionTreeParser.parse(projectionHeader);

            if (projectableFields != null) {
                AllowlistValidator validator = AllowlistValidator.fromFieldSpecs(projectableFields.value());
                if (validator != null) {
                    validator.validate(projection);
                }
            }

            if (!projector.supports(selectedContentType.toString())) {
                log.warn("Unsupported content type for projection: {}", selectedContentType);
                return body;
            }

            JsonNode projected = projector.project(fullResponse, projection, context);
            
            log.debug("Projected response for {} [traceId={}]", 
                servletRequest.getRequestURI(), context.getTraceId());

            return projected;

        } catch (ProjectionException e) {
            log.warn("Projection failed [traceId={}]: {}", context.getTraceId(), e.getMessage());
            setResponseStatus(response, e.getHttpStatus());
            return ProjectionErrorResponse.of(
                e.getErrorCode(),
                e.getMessage(),
                e.getPath(),
                context.getTraceId()
            );
        } catch (IllegalStateException e) {
            // Re-throw to prevent data leakage (handled by global error handler or results in 500)
            log.error("Security violation [traceId={}]: {}", context.getTraceId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during projection [traceId={}]", context.getTraceId(), e);
            return body;
        }
    }

    private JsonNode getOrCacheFullResponse(Object body, CacheKey cacheKey, Projectable projectable) {
        Optional<CachedResponse> cached = cacheManager.get(cacheKey);
        if (cached.isPresent()) {
            return cached.get().getFullResponse();
        }

        JsonNode fullResponse = objectMapper.valueToTree(body);

        int ttl = projectable.ttlSeconds();
        boolean isCollection = projectable.collection();
        cacheManager.put(cacheKey, fullResponse, ttl, isCollection);

        return fullResponse;
    }

    private CacheKey buildCacheKey(HttpServletRequest request, Projectable projectable) {
        String userContext = extractUserContext(request, projectable);
        return CacheKey.of(
            request.getMethod(),
            request.getRequestURI(),
            request.getQueryString(),
            userContext
        );
    }

    private String extractUserContext(HttpServletRequest request, Projectable projectable) {
        if (!projectable.userContext()) {
            return null;
        }

        String headerName = null;
        if (properties.getCache() != null && properties.getCache().getUserContext() != null) {
            headerName = properties.getCache().getUserContext().getHeaderName();
        }

        if (headerName != null && !headerName.isBlank()) {
            String headerValue = request.getHeader(headerName);
            if (headerValue != null && !headerValue.isBlank()) {
                return headerValue;
            }
        }

        if (request.getUserPrincipal() != null) {
            return request.getUserPrincipal().getName();
        }

        String logHeaderName = (headerName != null) ? headerName : "<missing-header-config>";
        throw new IllegalStateException(String.format(
            "User context caching is enabled for %s but no user identifier found. " +
            "Expected header '%s' or authenticated Principal. Aborting to prevent data leakage.",
            request.getRequestURI(), logHeaderName));
    }

    private HttpServletRequest extractServletRequest(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest();
        }
        return null;
    }

    private void setResponseStatus(ServerHttpResponse response, int status) {
        if (response instanceof ServletServerHttpResponse servletResponse) {
            servletResponse.getServletResponse().setStatus(status);
        } else {
            response.setStatusCode(HttpStatus.valueOf(status));
        }
    }

    private boolean isErrorResponse(Object body) {
        if (body instanceof ResponseEntity<?> responseEntity) {
            return !responseEntity.getStatusCode().is2xxSuccessful();
        }
        return false;
    }
}
