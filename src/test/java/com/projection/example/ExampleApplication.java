package com.projection.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Example Spring Boot application demonstrating the Response Projection Framework.
 * 
 * Start the application and test with:
 * 
 * # Get all users with projection
 * curl -H "X-Response-Fields: id,name" http://localhost:8080/api/users
 * 
 * # Get single user with nested projection
 * curl -H "X-Response-Fields: id,name,profile(avatar)" http://localhost:8080/api/users/1
 * 
 * # Get orders with nested items
 * curl -H "X-Response-Fields: id,total,items(productId,quantity)" http://localhost:8080/api/users/1/orders
 * 
 * # Without projection header - returns full response
 * curl http://localhost:8080/api/users/1
 * 
 * # Request non-existent field - returns error
 * curl -H "X-Response-Fields: id,secret" http://localhost:8080/api/users/1
 */
@SpringBootApplication(scanBasePackages = "com.projection")
public class ExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}
