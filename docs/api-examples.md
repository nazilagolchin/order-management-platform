# API examples

All examples assume `order-service` is running locally on port `8081` (see the
[README](../README.md#running-locally)). Interactive docs are always available at
`http://localhost:8081/swagger-ui.html`.

## Create an order

```bash
curl -i -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 5b1f7c9e-1c2d-4e3f-9a4b-1234567890ab" \
  -d '{
        "customerId": "11111111-1111-1111-1111-111111111111",
        "currency": "USD",
        "items": [
          { "productId": "22222222-2222-2222-2222-222222222222", "quantity": 2, "unitPrice": 19.99 },
          { "productId": "33333333-3333-3333-3333-333333333333", "quantity": 1, "unitPrice": 5.00 }
        ]
      }'
```

```http
HTTP/1.1 201 Created
Location: /api/orders/8f14e45f-ceea-167a-5a36-dedd4bea2543
X-Correlation-Id: a3c1e6e0-...

{
  "id": "8f14e45f-ceea-167a-5a36-dedd4bea2543",
  "customerId": "11111111-1111-1111-1111-111111111111",
  "status": "PENDING",
  "totalAmount": 44.98,
  "currency": "USD",
  "createdAt": "2026-06-28T18:00:00Z",
  "updatedAt": "2026-06-28T18:00:00Z",
  "version": 0,
  "items": [
    { "id": "...", "productId": "22222222-2222-2222-2222-222222222222", "quantity": 2, "unitPrice": 19.99 },
    { "id": "...", "productId": "33333333-3333-3333-3333-333333333333", "quantity": 1, "unitPrice": 5.00 }
  ]
}
```

### Replaying the same `Idempotency-Key`

Sending the exact same request again with the same `Idempotency-Key` header returns the
*original* order (same `id`), not a new one:

```bash
curl -i -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 5b1f7c9e-1c2d-4e3f-9a4b-1234567890ab" \
  -d '{ "customerId": "11111111-1111-1111-1111-111111111111", "currency": "USD", "items": [...] }'
```

Reusing that same key with a **different** payload is rejected instead of silently
creating a second order or silently returning the first one:

```http
HTTP/1.1 409 Conflict

{
  "timestamp": "2026-06-28T18:01:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Idempotency-Key '5b1f7c9e-1c2d-4e3f-9a4b-1234567890ab' was already used with a different request payload.",
  "path": "/api/orders",
  "correlationId": "a3c1e6e0-...",
  "fieldViolations": []
}
```

### Validation error

```bash
curl -i -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{ "currency": "us-dollars", "items": [] }'
```

```http
HTTP/1.1 400 Bad Request

{
  "timestamp": "2026-06-28T18:02:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for one or more fields.",
  "path": "/api/orders",
  "correlationId": "a3c1e6e0-...",
  "fieldViolations": [
    { "field": "customerId", "message": "customerId is required" },
    { "field": "currency", "message": "currency must be a 3-letter ISO 4217 code, e.g. USD" },
    { "field": "items", "message": "an order must contain at least one item" }
  ]
}
```

## Get an order

```bash
curl http://localhost:8081/api/orders/8f14e45f-ceea-167a-5a36-dedd4bea2543
```

A missing order returns `404` with the same `ApiError` shape:

```http
HTTP/1.1 404 Not Found

{
  "status": 404,
  "error": "Not Found",
  "message": "Order 8f14e45f-ceea-167a-5a36-dedd4bea2543 was not found.",
  "path": "/api/orders/8f14e45f-ceea-167a-5a36-dedd4bea2543",
  "correlationId": "a3c1e6e0-...",
  "fieldViolations": []
}
```

## List orders

```bash
# All orders, paginated
curl "http://localhost:8081/api/orders?page=0&size=20&sort=createdAt,desc"

# Filtered to one customer
curl "http://localhost:8081/api/orders?customerId=11111111-1111-1111-1111-111111111111"
```

```json
{
  "content": [ { "id": "...", "status": "PENDING", "...": "..." } ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

## Cancel an order

```bash
curl -i -X POST http://localhost:8081/api/orders/8f14e45f-ceea-167a-5a36-dedd4bea2543/cancel
```

Cancelling an already-cancelled order is a business-rule violation, not a no-op:

```http
HTTP/1.1 422 Unprocessable Entity

{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Order 8f14e45f-ceea-167a-5a36-dedd4bea2543 is already cancelled.",
  "path": "/api/orders/8f14e45f-ceea-167a-5a36-dedd4bea2543/cancel",
  "correlationId": "a3c1e6e0-...",
  "fieldViolations": []
}
```
