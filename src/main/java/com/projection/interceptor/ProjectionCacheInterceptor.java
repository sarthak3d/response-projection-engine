package com.projection.interceptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projection.annotation.Projectable;
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
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;

/**
 * Interceptor that handles cache hits before the controller executes.
 * 
 * On cache hit:
 *   - Retrieves full response from cache
 *   - Applies projection if header present
 *   - Writes response directly, bypassing controller entirely
 * 
 * On cache miss:
 *   - Stores cache key in request attribute for ResponseBodyAdvice
 *   - Proceeds to controller normally
 */
public class ProjectionCacheInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ProjectionCacheInterceptor.class);

    public static final String CACHE_KEY_ATTRIBUTE = "projection.cacheKey";
    public static final String PROJECTABLE_ATTRIBUTE = "projection.projectable";
    public static final String CACHE_HIT_ATTRIBUTE = "projection.cacheHit";

    private final ProjectionProperties properties;
    private final ObjectMapper objectMapper;
    private final ResponseProjector projector;
    private final ProjectionCacheManager cacheManager;

    public ProjectionCacheInterceptor(
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
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) 
            throws Exception {
        
        if (!properties.isEnabled()) {
            return true;
        }

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        Projectable projectable = handlerMethod.getMethodAnnotation(Projectable.class);
        if (projectable == null) {
            return true;
        }

        if (!properties.getCache().isEnabled()) {
            request.setAttribute(PROJECTABLE_ATTRIBUTE, projectable);
            return true;
        }

        CacheKey cacheKey = buildCacheKey(request, projectable);
        Optional<CachedResponse> cached = cacheManager.get(cacheKey);

        if (cached.isPresent()) {
            return handleCacheHit(request, response, cached.get(), projectable);
        }

        request.setAttribute(CACHE_KEY_ATTRIBUTE, cacheKey);
        request.setAttribute(PROJECTABLE_ATTRIBUTE, projectable);
        return true;
    }

    private boolean handleCacheHit(
            HttpServletRequest request,
            HttpServletResponse response,
            CachedResponse cachedResponse,
            Projectable projectable) throws IOException {

        FilterContext context = FilterContext.builder(properties).build();
        String projectionHeader = request.getHeader(properties.getHeaderName());

        try {
            JsonNode fullResponse = cachedResponse.getFullResponse();
            JsonNode result;

            if (projectionHeader != null && !projectionHeader.isBlank()) {
                ProjectionTree projection = ProjectionTreeParser.parse(projectionHeader);

                String[] allowedFields = projectable.allowedFields();
                if (allowedFields != null && allowedFields.length > 0) {
                    AllowlistValidator validator = AllowlistValidator.fromFieldSpecs(allowedFields);
                    if (validator != null) {
                        validator.validate(projection);
                    }
                }

                result = projector.project(fullResponse, projection, context);
                log.debug("Cache hit with projection for {} [traceId={}]", 
                    request.getRequestURI(), context.getTraceId());
            } else {
                result = fullResponse;
                log.debug("Cache hit (no projection) for {} [traceId={}]", 
                    request.getRequestURI(), context.getTraceId());
            }

            writeJsonResponse(response, result, HttpStatus.OK);
            request.setAttribute(CACHE_HIT_ATTRIBUTE, true);
            return false;

        } catch (ProjectionException e) {
            log.warn("Projection failed on cache hit [traceId={}]: {}", context.getTraceId(), e.getMessage());
            writeErrorResponse(response, e, context.getTraceId());
            return false;

        } catch (IllegalStateException e) {
            log.error("Security violation on cache hit [traceId={}]: {}", context.getTraceId(), e.getMessage());
            throw e;
        }
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

    private void writeJsonResponse(HttpServletResponse response, JsonNode body, HttpStatus status) 
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private void writeErrorResponse(HttpServletResponse response, ProjectionException e, String traceId) 
            throws IOException {
        ProjectionErrorResponse errorResponse = ProjectionErrorResponse.of(
            e.getErrorCode(),
            e.getMessage(),
            e.getPath(),
            traceId
        );
        response.setStatus(e.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
