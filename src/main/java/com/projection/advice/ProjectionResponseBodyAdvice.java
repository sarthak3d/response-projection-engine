package com.projection.advice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projection.annotation.Projectable;
import com.projection.cache.CacheKey;
import com.projection.cache.ProjectionCacheManager;
import com.projection.config.ProjectionProperties;
import com.projection.core.AllowlistValidator;
import com.projection.core.FilterContext;
import com.projection.core.ProjectionTree;
import com.projection.core.ProjectionTreeParser;
import com.projection.error.ProjectionErrorResponse;
import com.projection.exception.ProjectionException;
import com.projection.interceptor.ProjectionCacheInterceptor;
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

/**
 * Response body advice that handles cache misses.
 * 
 * This advice only runs when the interceptor allows the request through (cache miss).
 * Responsibilities:
 *   1. Convert response body to JsonNode
 *   2. Cache full response for future requests
 *   3. Apply projection if header present
 *   4. Return projected response
 * 
 * Cache hits are handled by ProjectionCacheInterceptor before reaching this advice.
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

        return returnType.getMethod() != null 
            && returnType.getMethod().isAnnotationPresent(Projectable.class);
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

        if (isErrorResponse(body)) {
            return body;
        }

        HttpServletRequest servletRequest = extractServletRequest(request);
        if (servletRequest == null) {
            return body;
        }

        Boolean cacheHit = (Boolean) servletRequest.getAttribute(ProjectionCacheInterceptor.CACHE_HIT_ATTRIBUTE);
        if (Boolean.TRUE.equals(cacheHit)) {
            return body;
        }

        Projectable projectable = (Projectable) servletRequest.getAttribute(ProjectionCacheInterceptor.PROJECTABLE_ATTRIBUTE);
        if (projectable == null) {
            projectable = returnType.getMethod().getAnnotation(Projectable.class);
        }

        String projectionHeader = servletRequest.getHeader(properties.getHeaderName());
        FilterContext context = FilterContext.builder(properties).build();

        try {
            JsonNode fullResponse = objectMapper.valueToTree(body);

            cacheFullResponse(servletRequest, fullResponse, projectable);

            if (projectionHeader == null || projectionHeader.isBlank()) {
                return body;
            }

            ProjectionTree projection = ProjectionTreeParser.parse(projectionHeader);

            String[] allowedFields = projectable.allowedFields();
            if (allowedFields != null && allowedFields.length > 0) {
                AllowlistValidator validator = AllowlistValidator.fromFieldSpecs(allowedFields);
                if (validator != null) {
                    validator.validate(projection);
                }
            }

            if (!projector.supports(selectedContentType.toString())) {
                log.warn("Unsupported content type for projection: {}", selectedContentType);
                return body;
            }

            JsonNode projected = projector.project(fullResponse, projection, context);
            
            log.debug("Projected response (cache miss) for {} [traceId={}]", 
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
            log.error("Security violation [traceId={}]: {}", context.getTraceId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during projection [traceId={}]", context.getTraceId(), e);
            return body;
        }
    }

    private void cacheFullResponse(HttpServletRequest request, JsonNode fullResponse, Projectable projectable) {
        if (!properties.getCache().isEnabled()) {
            return;
        }

        CacheKey cacheKey = (CacheKey) request.getAttribute(ProjectionCacheInterceptor.CACHE_KEY_ATTRIBUTE);
        if (cacheKey == null) {
            cacheKey = buildCacheKey(request, projectable);
        }

        int ttl = projectable.ttlSeconds();
        boolean isCollection = projectable.collection();
        cacheManager.put(cacheKey, fullResponse, ttl, isCollection);
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

        java.security.Principal principal = request.getUserPrincipal();
        if (principal == null) {
            log.warn("User context required for {} but no Principal found", request.getRequestURI());
            throw new IllegalStateException("User context caching requires an authenticated user");
        }

        String authenticatedId = principal.getName();

        String headerName = null;
        if (properties.getCache() != null && properties.getCache().getUserContext() != null) {
            headerName = properties.getCache().getUserContext().getHeaderName();
        }

        if (headerName != null && !headerName.isBlank()) {
            String headerValue = request.getHeader(headerName);
            if (headerValue != null && !headerValue.isBlank()) {
                if (!headerValue.equals(authenticatedId)) {
                    log.error("Security alert: User context header mismatch for {}. Header provided but does not match Principal.", 
                        request.getRequestURI());
                    throw new SecurityException("User context header does not match authenticated user");
                }
            }
        }

        return authenticatedId;
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
