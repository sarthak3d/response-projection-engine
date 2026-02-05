**Header Examples:**

#### Basic Examples
```bash
# Single field
X-Response-Fields: id

# Multiple fields
X-Response-Fields: id,name,email

# With spaces (optional, ignored)
X-Response-Fields: id, name, email
```

#### **Nested Object Fields**

```bash
# Object inside object
X-Response-Fields: id,profile(avatar,bio)
```

**Response:**
```json
{
  "id": 1,
  "profile": {
    "avatar": "url",
    "bio": "text"
  }
}
```

#### **Array Fields**

```bash
# Array of primitives (e.g., tags: ["java", "spring"])
X-Response-Fields: id,tags

# Array of objects - project specific fields from each item
X-Response-Fields: id,orders(id,total)
```


**Response:**
```json
{
  "id": 1,
  "orders": [
    { "id": 101, "total": 99.99 },
    { "id": 102, "total": 49.99 }
  ]
}
```

#### **Nested Arrays (Array inside Array)**

```bash
# Orders containing items, each item has variants
X-Response-Fields: orders(id,items(productId,variants(size,color)))
```


**Response:**
```json
{
  "orders": [{
    "id": 1,
    "items": [{
      "productId": "A1",
      "variants": [
        { "size": "M", "color": "red" }
      ]
    }]
  }]
}
```

#### **Array Inside Object**

```bash
# User has a profile object containing a skills array
X-Response-Fields: id,profile(name,skills)
```


**Response:**
```json
{
  "id": 1,
  "profile": {
    "name": "John",
    "skills": ["java", "python"]
  }
}
```

```bash
# User has settings object with notifications array containing objects
X-Response-Fields: id,settings(theme,notifications(type,enabled))
```

**Response:**
```json
{
  "id": 1,
  "settings": {
    "theme": "dark",
    "notifications": [
      { "type": "email", "enabled": true }
    ]
  }
}
```

#### **Object Inside Array**

```bash
# Array of orders, each order has a nested shipping object
X-Response-Fields: orders(id,shipping(address,city))
```


**Response:**
```json
{
  "orders": [{
    "id": 1,
    "shipping": {
      "address": "123 Main St",
      "city": "NYC"
    }
  }]
}
```

```bash
# Array of products with nested manufacturer object
X-Response-Fields: products(name,manufacturer(name,country))
```

**Response:**
```json
{
  "products": [{
    "name": "Widget",
    "manufacturer": {
      "name": "Acme",
      "country": "USA"
    }
  }]
}
```

#### Complex Real-World Examples
```bash
# E-commerce order with deep nesting
X-Response-Fields: id,customer(name,email),items(product(name,price),quantity),shipping(address(street,city,zip))

# Blog post with author and comments
X-Response-Fields: id,title,author(name,avatar),comments(id,text,user(name))

# Organization with departments and employees
X-Response-Fields: name,departments(name,manager(name),employees(id,name,role))

# API response with metadata and paginated data
X-Response-Fields: metadata(total,page),data(id,name,tags)
```

#### Mixed Depth Examples
```bash
# Level 1 only
X-Response-Fields: id,name

# Level 2 (object in object)
X-Response-Fields: id,profile(bio)

# Level 3 (array in object in root)
X-Response-Fields: id,profile(skills(name,level))

# Level 4 (object in array in object in root)
X-Response-Fields: id,profile(certifications(issuer(name,website)))

# Level 5 (maximum default depth)
X-Response-Fields: company(departments(teams(members(contact(email)))))
```
