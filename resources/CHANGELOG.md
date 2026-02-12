# Changelog

All notable changes to the Response Projection Engine will be documented in this file.

## 2.0.0 [2026-02-13]

### Big Changes
- **Removed `@ProjectableFields`**: This annotation is removed and merged into `@Projectable`. Use `allowedFields` attribute of `@Projectable` now.
- **New Caching Architecture**: Complete redesign of the caching layer with interceptor-based approach.
  - Cache hits now bypass controller execution entirely, saving DB calls, business logic, and serialization.
  - `ProjectionCacheInterceptor` handles cache hits before the controller runs.
  - `ProjectionResponseBodyAdvice` now only handles cache misses.


### Bug Fixes
- **Strict ascend() Validation**: `FilterContext.ascend()` now throws `IllegalStateException` when called without a matching `descend()`, making traversal mismatches explicit for easier debugging.

### Performance
- **Controller Bypass on Cache Hit**: When a cached response exists, the controller, database queries, and business logic are completely skipped.
- **Entry Iteration Optimization**: Replaced double Map lookups in projection algorithm with direct entry iteration.
- **Early Short-Circuit**: Requests without projection header now return immediately without any processing overhead.

### Benchmarks
- 10-20% reduction in average latency compared to previous versions.
- Reduced GC pressure for large collections. 10% reduction in peak heap usage for large datasets.


## 1.2.0 [2026-02-05]

### New Features
- **Per-User Cache Isolation**: Added support for user-specific caching via the `userContext` attribute in `@Projectable`.
  - Can now isolate cached responses based on a specific header (e.g., `X-User-Id`) or the authenticated Principal.
  - Prevents data leakage between users for personalized endpoints.
- **Enhanced Caching Logic**:
  - Improved `CacheKey` generation to include user context when applicable.
  - Added support for conditional requests (ETags/Last-Modified) in the cache manager.

### Bug Fixes & Improvements
- Fixed edge cases in `ProjectionResponseBodyAdvice` where missing headers could cause issues.
- Improved error handling in `FilterContext` to prevent potential NPEs during deep nesting traversal.
- Hardened `ProjectionCacheManager` against potential concurrency issues.
- Updated `README.md` with comprehensive documentation on the new Caching and User Context features.

### Technical Details
- Refactored `ProjectionProperties` to better structure cache configuration.
- Upgraded `CacheKey` to support an optional `userContext` string.

## 1.0.0 [2026-01-30]
### Added
- **Core Engine**: First release of the Response Projection Engine for Spring Boot.
- **Annotations**: 
  - `@Projectable`: Marks classes eligible for projection.
  - `@ProjectableFields`: Defines allowed fields (allowlist) for response filtering.
  - `@InvalidateProjectionCache`: Annotations to handle cache invalidation on updates.
- **Dynamic Filtering**: Support for `X-Response-Fields` HTTP header to dynamically select fields in the JSON response.
- **Nested Projections**: Full support for nested field selection and array handling.
- **Caching**: Caffeine-based caching implementation for high-performance field resolution.
- **Documentation**: README with JSON visualizations and usage guidelines.
