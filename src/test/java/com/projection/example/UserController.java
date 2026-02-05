package com.projection.example;

import com.projection.annotation.InvalidateProjectionCache;
import com.projection.annotation.Projectable;
import com.projection.annotation.ProjectableFields;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Example controller demonstrating the Response Projection library.
 * 
 * Usage examples:
 * 
 * GET /api/users
 * X-Response-Fields: id,name
 * -> Returns only id and name fields for each user
 * 
 * GET /api/users/1
 * X-Response-Fields: id,name,profile(avatar)
 * -> Returns id, name, and nested profile.avatar
 * 
 * GET /api/users/1/orders
 * X-Response-Fields: id,total,items(productId,quantity)
 * -> Returns orders with nested item projection
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public UserController() {
        initSampleData();
    }

    @GetMapping
    @Projectable(collection = true)
    public List<User> getAllUsers() {
        return List.copyOf(users.values());
    }

    @GetMapping("/{id}")
    @Projectable(ttlSeconds = 120)
    @ProjectableFields({"id", "name", "email", "profile(avatar,bio)"})
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        User user = users.get(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    @GetMapping("/{id}/orders")
    @Projectable(collection = true, ttlSeconds = 30)
    public ResponseEntity<List<Order>> getUserOrders(@PathVariable Long id) {
        User user = users.get(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user.orders());
    }

    @PostMapping
    @InvalidateProjectionCache(paths = {"/api/users"})
    public ResponseEntity<User> createUser(@RequestBody CreateUserRequest request) {
        long id = idGenerator.getAndIncrement();
        User user = new User(
            id,
            request.name(),
            request.email(),
            new Profile(null, null, new Settings("light", true)),
            List.of()
        );
        users.put(id, user);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}")
    @InvalidateProjectionCache(paths = {"/api/users/{id}", "/api/users"})
    public ResponseEntity<User> updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request) {
        
        User existing = users.get(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        User updated = new User(
            id,
            request.name() != null ? request.name() : existing.name(),
            request.email() != null ? request.email() : existing.email(),
            existing.profile(),
            existing.orders()
        );
        users.put(id, updated);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @InvalidateProjectionCache(paths = {"/api/users/{id}", "/api/users"})
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        if (users.remove(id) == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    private void initSampleData() {
        User user1 = new User(
            1L,
            "Alice Johnson",
            "alice@example.com",
            new Profile(
                "https://example.com/avatars/alice.jpg",
                "Software Engineer passionate about clean code",
                new Settings("dark", true)
            ),
            List.of(
                new Order(101L, 149.97, "COMPLETED", List.of(
                    new OrderItem(1001L, "Mechanical Keyboard", 2, 59.99),
                    new OrderItem(1002L, "USB-C Hub", 1, 29.99)
                )),
                new Order(102L, 89.50, "PENDING", List.of(
                    new OrderItem(1003L, "Wireless Mouse", 1, 89.50)
                ))
            )
        );

        User user2 = new User(
            2L,
            "Bob Smith",
            "bob@example.com",
            new Profile(
                "https://example.com/avatars/bob.jpg",
                "DevOps specialist",
                new Settings("light", false)
            ),
            List.of(
                new Order(201L, 299.00, "COMPLETED", List.of(
                    new OrderItem(2001L, "Monitor Stand", 1, 299.00)
                ))
            )
        );

        users.put(1L, user1);
        users.put(2L, user2);
        idGenerator.set(3);
    }

    public record User(
        Long id,
        String name,
        String email,
        Profile profile,
        List<Order> orders
    ) {}

    public record Profile(
        String avatar,
        String bio,
        Settings settings
    ) {}

    public record Settings(
        String theme,
        boolean notifications
    ) {}

    public record Order(
        Long id,
        Double total,
        String status,
        List<OrderItem> items
    ) {}

    public record OrderItem(
        Long productId,
        String productName,
        Integer quantity,
        Double price
    ) {}

    public record CreateUserRequest(String name, String email) {}
    
    public record UpdateUserRequest(String name, String email) {}
}
