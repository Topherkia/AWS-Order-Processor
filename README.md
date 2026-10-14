# AWS Order Processor

An event-driven, cloud-based order processing system built on AWS that automates the order workflow between wholesale food suppliers and their small business customers (cafes, restaurants, and catering companies). The system receives orders via API Gateway, queues them in SQS, processes them in Lambda, stores records in MariaDB (RDS), and pushes notifications through SNS .

**Key guarantee:** Every order is either processed successfully or preserved in the DLQ. No data is lost.

---

## Table of Contents

- [The Problem](#the-problem)
- [Target Users](#target-users)
- [Key Benefits](#key-benefits)
- [Architecture](#architecture)
- [AWS Services](#aws-services)
- [Database Schema (MariaDB)](#database-schema-mariadb)
- [Lambda Functions](#lambda-functions)
  - [Lambda 1: Order Processor](#lambda-1-order-processor)
  - [Lambda 2: DB Logger & Push](#lambda-2-db-logger--push)
- [Failure & Retry Logic](#failure--retry-logic)
- [Project Structure](#project-structure)
- [Setup](#setup)
- [Testing](#testing)
- [Team](#team)

## The Problem

Wholesale suppliers in the food service industry communicate with numerous small business customers daily. Restaurant managers, cafe owners, and catering operators need timely, accurate information from their suppliers — but repetitive inquiries, manual updates, and back-and-forth messages create delays, misunderstandings, and unnecessary administrative work on both sides.

## Target Users

**Primary users — Wholesale food suppliers**

- Provide food products, ingredients, and supplies to cafes, restaurants, and catering companies
- Manage communication with many small business customers simultaneously
- Need to share order updates, delivery information, and important notices efficiently
- Benefit from automated notifications that replace repetitive manual messaging

**Secondary users — Small food service businesses**

- Cafe owners, restaurant managers, and catering operators
- Have busy daily schedules and limited time for administrative tasks
- Need timely, accurate information from their suppliers to plan operations
- Benefit from receiving automated notifications without needing to check messages or follow up on routine inquiries

**Example user scenario**

A cafe owner places an order for ingredients. The system processes it automatically, logs it in the database, and sends a success notification. If the order fails repeatedly, the DLQ captures it and a failure notification is sent — so the supplier can follow up without the cafe owner needing to chase updates.

## Key Benefits

- Automates routine order communication end-to-end
- Reduces time spent on customer communication by up to **90%**
- Reliable message handling via SQS queues and DLQ for failures
- Real-time notifications through SNS for both success and failure events
- Full audit trail stored in MariaDB
- Designed to be easy to use — no added complexity to daily workflows

## Architecture

> Refer to the architecture diagram:
> [`docs/architecture.jpeg`](docs/architecture.jpeg)

**Flow:**

1. **User UI** → **API Gateway** → **SQS Main Queue**
2. **Lambda 1 (Order Processor)** polls SQS, writes to **MariaDB (RDS)**, publishes success to **SNS 1**
3. On failure, SQS retries. After `maxReceiveCount`, message → **SQS DLQ** → **SNS 2 (Failure)**
4. **Lambda 2 (DB Logger & Push)** subscribes to SNS 1 & 2, logs to `Notification` table, pushes to User UI

---

## AWS Services

| Service       | Purpose                             |
| ------------- | ----------------------------------- |
| API Gateway   | Entry point for orders              |
| SQS (Main)    | Buffers incoming orders             |
| SQS (DLQ)     | Stores orders that fail repeatedly  |
| Lambda 1      | Processes orders, writes to MariaDB |
| Lambda 2      | Logs notifications, pushes to UI    |
| MariaDB (RDS) | Stores orders, items, notifications |
| SNS 1         | Success events                      |
| SNS 2         | Failure events                      |

**Region:** `us-east-1`

---

## Database Schema (MariaDB)

> Full schema image: [`docs/db_schema_image.png`](docs/db_schema_image.png)

| Table          | Key Columns                                    |
| -------------- | ---------------------------------------------- |
| `Customers`    | id (PK), Username, Password, email             |
| `Orders`       | id (PK), created_at, user_id (FK → Customers)  |
| `Items`        | id (PK), Name, image_url, price                |
| `order_items`  | id (PK), order_id (FK), item_id (FK), quantity |
| `Notification` | id (PK), user_id (FK), message, type           |

---

## Lambda Functions

### Lambda 1: Order Processor

- write as we come up

### Lambda 2: DB Logger & Push

- write as we come up

---

## Failure & Retry Logic

- write as we come up

---

## Project Structure

```
AWS-Order-Processor/
├── docs/
│   └── db_schema_image.png
├── main/
│   ├── db_manager.py
│   ├── lambda_order_processor.py
│   └── lambda_db_logger.py
├── sql/
│   └── schema.sql
├── README.md
└── requirements.txt
```

---

## Setup

1. **MariaDB (RDS):** Launch in `us-east-1`, run `sql/schema.sql`
2. **SQS:** Create `OrderQueue` (`maxReceiveCount = 3`) + `OrderDLQ` (redrive policy)
3. **SNS:** Create `OrderSuccessTopic` + `OrderFailureTopic`; subscribe Lambda 2 to both; link DLQ → SNS 2
4. **Lambda 1:** Python 3.9+, LabRole, trigger = SQS, set env vars
5. **Lambda 2:** Python 3.9+, LabRole, trigger = SNS 1 & 2, set env vars
6. **API Gateway:** POST endpoint → SQS `OrderQueue`

> ⚠️ **Learner Lab reminder:** MariaDB RDS keeps costing money between sessions. Stop or delete it when not testing.

---

## Testing

- write as we come up

---

## Team

| Name   | Role                                |
| ------ | ----------------------------------- |
| [Name] | FrontEnd Developer (Android App UI) |
| [Name] | Backend Developer (Lambda)          |
| [Name] | Database and Lambda                 |
| [Name] | Architecture & coordination         |
| [Name] | Documentation and testing           |
