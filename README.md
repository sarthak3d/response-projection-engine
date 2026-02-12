<p align="center"><img src="resources/icon/logo.png" alt="Response Projection Engine Logo" width="300"></p>
<h1 align="center">Response Projection Engine</h1>

<p align="center">
  <strong>A dynamic response projection layer for Web applications designed to enforce data encapsulation, eliminating accidental exposure while streamlining DTO management.</strong>
</p>

<p align="center">
  <a href="#quick-start">Quick Start</a> •
  <a href="#annotations">Annotations</a> •
  <a href="#configuration">Configuration</a> •
  <a href="resources/CHANGELOG.md">Change Log</a> •
  <a href="#license">License</a>
</p>

---

## Overview

This library allows frontends to request only the fields they need via HTTP headers, without changing your REST API contracts. The backend returns full objects, and the library filters them based on the client's projection specification.

**What it is:**
- A response filter
- A projection engine  
- A safety layer
- A DTO reducer (response-side)

**What it is NOT:**
- A backend performance optimizer
- A DB/CPU optimization tool
- GraphQL replacement
- Gateway or proxy

## Requirements

- **Java**: 17 or higher
- **Spring Boot**: 3.2.0 is recommended (compatible with 3.x)
- **Maven**: 3.6+ (for building from source)

## Quick Start

### 1. Add Dependency

#### Maven

```xml
<dependency>
    <groupId>io.github.sarthak3d</groupId>
    <artifactId>response-projection-engine</artifactId>
    <version>2.0.0</version>
</dependency>
```

#### Gradle

```java
implementation("io.github.sarthak3d:response-projection-engine:2.0.0")
```

### 2. Annotate Endpoints

```java
@GetMapping("/{id}")
@Projectable
public User getUserById(@PathVariable Long id) {
    return userService.findById(id);
}
```

### 3. Request with Projection Header

```bash
X-Response-Fields: id,name,profile(avatar)
```

### 4. All Done

## Projection DSL

The projection language is intentionally minimal and safe:

```
projection := field (',' field)*
field      := name | name '(' projection ')'
name       := [a-zA-Z_][a-zA-Z0-9_]*
```

**Examples:**
```
id,name
id,name,profile(avatar,bio)
orders(id,total,items(productId))
```

**Not supported:**
- Wildcards
- Aliases
- Filters
- Renaming
- Functions

### Header Usage Rules

When writing projections in the `X-Response-Fields` header, follow these rules:

**Syntax Rules:**
1. **Field names** must start with a letter or underscore, followed by alphanumeric characters or underscores
2. **Separate fields** with commas (no spaces required, but allowed)
3. **Nest fields** using parentheses: `parent(child1,child2)`
4. **Multi-level nesting** is supported: `level1(level2(level3))`
5. **No trailing commas** — `id,name,` is invalid

For more examples, see [Example](resources/Example.md)

**Common Mistakes:**
| Invalid | Why | Correct |
|---------|-----|---------|
| `*` | Wildcards not supported | List fields explicitly |
| `id,` | Trailing comma | `id` |
| `123field` | Name starts with number | `field123` |
| `profile.avatar` | Dot notation not supported | `profile(avatar)` |
| `id as userId` | Aliases not supported | `id` |

**Best Practices:**
- Request only the fields you need to minimize payload size
- Use nested projections for related objects instead of requesting full objects
- Keep nesting depth within configured limits (default: 5)

### Empty or Missing Header

When the `X-Response-Fields` header is **empty or not provided**, the library returns the **full response** without any filtering:

| Scenario | Behavior |
|----------|----------|
| Header missing | Full response returned (no projection) |
| Header empty (`X-Response-Fields: `) | Full response returned (no projection) |
| Header whitespace only (`X-Response-Fields:   `) | Full response returned (no projection) |
| Header with valid fields | Only requested fields returned |

**Why this design?**
- **Backwards compatibility**: Existing clients without the header continue to work
- **Opt-in filtering**: Projection is explicitly requested, not assumed
- **Graceful degradation**: If a client forgets the header, they get all data (safe default)

## Annotations

### @Projectable

Marks an endpoint as eligible for projection and caching. Without this annotation, the library ignores the endpoint entirely.

```java
@GetMapping
@Projectable(collection = true, ttlSeconds = 30)
public List<User> getAllUsers() {
    return userService.findAll();
}
```

**Attributes:**

| Attribute | Type | Description | Default |
|-----------|------|-------------|---------|
| `ttlSeconds` | `int` | Cache time-to-live in seconds. When set to `-1`, uses the global configuration value from `application.properties`. | `-1` |
| `collection` | `boolean` | Set to `true` when the endpoint returns a list/array. Collections use a separate, typically shorter TTL (`response.projection.cache.collection-ttl-seconds`). | `false` |
| `userContext` | `boolean` | Enables per-user cache isolation. When `true`, the cache key includes the user's identity (from header or Principal), preventing data leakage across users. | `false` |
| `allowedFields` | `String[]` | Array of allowed field paths that clients can request. When specified, only these fields are projectable. Acts as an allowlist for sensitive data. | `{}` (All fields allowed) |

**What the attributes control:**
- **ttlSeconds**: How long the full response is cached before being re-fetched. Use `-1` to inherit from config, or set a specific value to override.
- **collection**: Collections (lists) often change more frequently than single items, so they use a shorter cache TTL by default (10s vs 60s).
- **userContext**: Prevents data leakage for user-specific endpoints. See examples below.
- **allowedFields**: Restricts projectable fields (allowlist). If a client requests a field not in this list, they get a 400 error. Defaults to allowing all fields.

**Per-User Cache Isolation (`userContext`):**

```java
// Shared cache (default) - same response cached for all users
@GetMapping("/weather")
@Projectable
public Weather getWeather() { ... }

// Per-user cache - separate cache entry per user
@GetMapping("/me")
@Projectable(userContext = true)
public User getCurrentUser() { ... }

// Per-user cache with collection
@GetMapping("/my-orders")
@Projectable(userContext = true, collection = true)
public List<Order> getMyOrders() { ... }

// Secure endpoint with field allowlist
@GetMapping("/{id}")
@Projectable(allowedFields = {"id", "name", "email", "profile(avatar,bio)"})
public User getUserById(@PathVariable Long id) { ... }
```

User identity is strictly derived from the **Authenticated Principal** (`request.getUserPrincipal().getName()`).

**Security Rules:**
1. **Authentication Required**: Endpoints with `userContext = true` **must** be accessed by an authenticated user. If no Principal is found, the request fails.
2. **Header Validation**: If `response.projection.cache.user-context.header-name` is configured (e.g., `X-User-Id`), the client **may** send this header, but its value **must match** the authenticated Principal's name. A mismatch results in a security exception.

This ensures that a malicious client cannot spoof another user's identity to poison the cache.

| Endpoint Type | `userContext` | Cache Key Example |
|---------------|---------------|-------------------|
| Public data (`/weather`) | `false` | `GET:/weather` |
| User-specific (`/me`) | `true` | `GET:/me@user123` |
| URL with userId (`/users/{id}`) | `false` | `GET:/users/5` (already unique) |

**Use allowedFields when:**
- The response contains sensitive fields that should never be exposed (e.g., `passwordHash`, `ssn`)
- You want explicit control over what clients can request
- The endpoint returns data with varying sensitivity levels

### @InvalidateProjectionCache

Declares which cache entries should be evicted after a successful write operation. Applied to POST, PUT, DELETE, or PATCH methods that modify data.

```java
@PutMapping("/{id}")
@InvalidateProjectionCache(paths = {"/users/{id}", "/users"})
public User updateUser(@PathVariable Long id, @RequestBody UpdateRequest request) {
    return userService.update(id, request);
}
```

**The `paths` attribute:**

The `paths` attribute is an array of URI patterns that specify which cache entries to evict:

| Format | Description | Example |
|--------|-------------|---------|
| Fixed path | Evicts the exact cache entry | `"/users"` |
| Path with variable | Variable is resolved from method parameters | `"/users/{id}"` |
| Multiple paths | Evicts multiple cache entries | `{"/users/{id}", "/users"}` |

**How path variable resolution works:**

```java
@DeleteMapping("/{userId}/orders/{orderId}")
@InvalidateProjectionCache(paths = {
    "/users/{userId}/orders/{orderId}",  // Evicts the specific order
    "/users/{userId}/orders"              // Evicts the orders list
})
public void deleteOrder(
    @PathVariable Long userId,    // Resolves {userId}
    @PathVariable Long orderId    // Resolves {orderId}
) {
    orderService.delete(orderId);
}
```

The library:
1. Extracts `@PathVariable` annotations from method parameters
2. Matches variable names to placeholders in the path (e.g., `{userId}` matches parameter `userId`)
3. Replaces placeholders with actual values at runtime
4. Evicts the resolved cache keys

**Eviction timing:**
- Cache is evicted **after** the method returns successfully
- If the method throws an exception, cache is not evicted
- Maintaining Data consistency and is Transaction safety

## Strict Behavior

The library is strict by design - no partial success is allowed:

| Condition | Result |
|-----------|--------|
| Requested field missing | 400 error |
| Parent field missing | 400 error |
| Max depth exceeded | 400 error |
| Cycle detected | 500 error |
| Requesting disallowed field | 400 error |

Error response format:
```json
{
    "error": {
        "code": "MISSING_FIELD",
        "message": "Requested field does not exist in response: profile.avatar",
        "path": "profile.avatar",
        "traceId": "abc123"
    }
}
```

Example: Disallowed field error
```json
{
    "error": {
        "code": "FIELD_NOT_ALLOWED",
        "message": "Field is not allowed for projection: profile.settings",
        "path": "profile.settings",
        "traceId": "xyz789"
    }
}
```

## Caching

Full backend responses are cached (never projected variants):

- **TTL-based expiration**: Configurable default and collection TTLs
- **ETag/Last-Modified**: HTTP conditional request support
- **Manual eviction**: Via `@InvalidateProjectionCache` annotation
- **User context isolation**: Optional per-user cache keys to prevent data leakage

Cache key format: `METHOD:/path?sortedQueryParams[@userContext]`

## Configuration

```properties
# Master switch
response.projection.enabled=true

# Header name for projection requests
response.projection.header-name=X-Response-Fields

# Depth and cycle limits
response.projection.max-depth=5
response.projection.cycle-detection.enabled=true

# Threshold for array memoization optimization
# Arrays with size >= this value use pre-compiled field instructions.
response.projection.memoization-threshold=10

# Caching
response.projection.cache.enabled=true
response.projection.cache.default-ttl-seconds=60      # TTL for single items (/users/{id})
response.projection.cache.collection-ttl-seconds=10   # Shorter TTL for collections (change often)
response.projection.cache.conditional.enabled=true
response.projection.cache.manual-eviction.enabled=true

# User context header for per-user cache isolation
# Used when @Projectable(userContext = true) is set
response.projection.cache.user-context.header-name=X-User-Id

# Trace IDs in error responses
response.projection.trace-id.enabled=true

# HTTP status codes for errors
response.projection.error.missing-field.status=400
response.projection.error.max-depth.status=400
response.projection.error.cycle.status=500
```

## API Contract

REST APIs remain unchanged:
- Same endpoints
- Same HTTP verbs
- Same URLs
- Same response structure (before filtering)

Frontend expresses intent via the projection header only.

## Error Handling

Backend errors always pass through untouched. The projection library only processes successful (2xx) responses.

## Building

```bash
mvn clean install
```

## Running Tests

```bash
mvn test
```

## Running Example Application

The example application is located in `src/test/java/com/projection/example/`.

```bash
mvn spring-boot:run -Dspring-boot.run.main-class=com.projection.example.ExampleApplication
```

## License

MIT
