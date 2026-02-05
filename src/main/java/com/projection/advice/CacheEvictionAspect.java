package com.projection.advice;

import com.projection.annotation.InvalidateProjectionCache;
import com.projection.cache.CacheKey;
import com.projection.cache.ProjectionCacheManager;
import com.projection.config.ProjectionProperties;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AOP aspect that handles cache eviction for @InvalidateProjectionCache annotated methods.
 * Executes after successful method completion to maintain cache consistency.
 */
@Aspect
@Order(1)
public class CacheEvictionAspect {

    private static final Logger log = LoggerFactory.getLogger(CacheEvictionAspect.class);
    private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{([^}]+)}");

    private final ProjectionCacheManager cacheManager;
    private final ProjectionProperties properties;

    public CacheEvictionAspect(ProjectionCacheManager cacheManager, ProjectionProperties properties) {
        this.cacheManager = cacheManager;
        this.properties = properties;
    }

    @AfterReturning("@annotation(invalidateCache)")
    public void evictCache(JoinPoint joinPoint, InvalidateProjectionCache invalidateCache) {
        if (!properties.getCache().isEnabled() || 
            !properties.getCache().getManualEviction().isEnabled()) {
            return;
        }

        String[] pathPatterns = invalidateCache.paths();
        if (pathPatterns.length == 0) {
            return;
        }

        Map<String, Object> pathVariables = extractPathVariables(joinPoint);

        for (String pathPattern : pathPatterns) {
            String resolvedPath = resolvePathVariables(pathPattern, pathVariables);
            evictForPath(resolvedPath, pathPattern);
        }
    }

    private Map<String, Object> extractPathVariables(JoinPoint joinPoint) {
        Map<String, Object> variables = new HashMap<>();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            PathVariable annotation = parameters[i].getAnnotation(PathVariable.class);
            if (annotation != null) {
                String name;
                if (!annotation.value().isEmpty()) {
                    name = annotation.value();
                } else {
                    name = parameters[i].getName();
                    // Detect synthetic parameter names (arg0, arg1, etc.)
                    // These occur when compiled without -parameters flag
                    if (name.matches("arg\\d+")) {
                        log.warn("Detected synthetic parameter name '{}' in method {}.{}() - " +
                                "cache eviction path variables may not resolve correctly. " +
                                "Either specify @PathVariable(\"name\") explicitly or compile with -parameters flag.",
                                name, method.getDeclaringClass().getSimpleName(), method.getName());
                    }
                }
                variables.put(name, args[i]);
            }
        }

        return variables;
    }

    private String resolvePathVariables(String pathPattern, Map<String, Object> variables) {
        Matcher matcher = PATH_VARIABLE_PATTERN.matcher(pathPattern);
        StringBuilder resolved = new StringBuilder();

        while (matcher.find()) {
            String variableName = matcher.group(1);
            Object value = variables.get(variableName);
            
            if (value != null) {
                matcher.appendReplacement(resolved, Matcher.quoteReplacement(value.toString()));
            } else {
                matcher.appendReplacement(resolved, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(resolved);

        return resolved.toString();
    }

    private void evictForPath(String resolvedPath, String originalPattern) {
        if (resolvedPath.contains("{")) {
            cacheManager.evictByPathPattern(resolvedPath);
            log.debug("Evicted cache entries matching pattern: {}", resolvedPath);
        } else {
            for (String method : new String[]{"GET", "HEAD"}) {
                CacheKey key = CacheKey.of(method, resolvedPath, null);
                cacheManager.evict(key);
            }
            log.debug("Evicted cache entry for path: {}", resolvedPath);
        }
    }
}
