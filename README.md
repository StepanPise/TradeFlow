# TradeFlow

An event-driven order and inventory management system built with Java, Spring Boot, and Kafka. It uses transactional outbox messaging and idempotent processing to coordinate orders and inventory reservations across independent services, each with its own PostgreSQL database.

## Getting Started

### Prerequisites

* Docker Desktop
* Java 21
* Maven

### Run the Application

Docker Compose runs the databases and Kafka. The two Spring Boot services run locally.

1. Start the infrastructure from the project root:

```bash
docker compose up -d
```

2. Start Inventory Service:

```bash
mvn -f inventory-service/pom.xml spring-boot:run
```

3. In a separate terminal, start Order Service:

```bash
mvn -f order-service/pom.xml spring-boot:run
```

Flyway automatically initializes the database schemas and sample products. Local connection settings are provided in each service's `application.properties`.

## API Documentation

Explore the endpoints and send requests through Swagger UI:

* **Order Service:** http://localhost:8081/swagger-ui.html
* **Inventory Service:** http://localhost:8082/swagger-ui.html

To try the application, retrieve the available products, create an order using their IDs, and check the resulting order status.

## Order Flow

```text
Order created (PENDING)
        │
        ▼
Order outbox → Kafka → Inventory Service
                              │
                     Reserve all items
                     or reject the order
                              │
                              ▼
                  Inventory outbox → Kafka
                                       │
                                       ▼
                                 Order Service
                                       │
                                       ▼
                              CONFIRMED / REJECTED
```

Processing is asynchronous, so a newly created order may remain `PENDING` briefly. If any requested product is unavailable or has insufficient inventory, the reservation is rejected without deducting any quantities.

## Main Features

* **Event-Driven Order Processing** — Asynchronous communication between Order and Inventory services through Kafka.
* **Transactional Outbox** — Each service commits business changes and outgoing events within the same database transaction.
* **Idempotent Processing** — Duplicate events do not cause repeated inventory deductions or order status changes.
* **Atomic Inventory Reservations** — All requested items are reserved together, with database locking to prevent overselling.

## Technologies

* **Backend:** Java 21, Spring Boot, REST API
* **Messaging:** Apache Kafka, Spring for Apache Kafka
* **Data Layer:** PostgreSQL, Spring Data JPA, Hibernate, Spring JDBC, Flyway
* **API Documentation:** OpenAPI, Swagger UI, springdoc
* **Build & Infrastructure:** Maven, Docker Compose
* **Testing & CI:** JUnit 5, Mockito, GitHub Actions

## Project Status

The core order reservation flow is implemented. Development continues with a focus on failure handling, end-to-end testing, and observability. Future functionality includes order cancellation and inventory release.
