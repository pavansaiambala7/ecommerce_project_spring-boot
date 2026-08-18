# AI-Powered E-Commerce Platform & Microservices

A production-grade Java e-commerce platform built with **Java 17**, **Spring Boot 3.2**, **PostgreSQL + pgvector**, **LangChain4j**, and **Google Gemini AI**.

---

## 🚀 Key AI & Microservice Features

- **RAG Product Search**: Gemini 768-dim embeddings stored in **pgvector** with HNSW cosine similarity search.
- **AI Customer Support Assistant**: LangChain4j conversational AI agent with chat memory, RAG context augmentation, and order/product lookup tools.
- **4 RESTful Microservice Domains**: Product, User, Order, and Payment services with clean API contracts (`/api/products`, `/api/users`, `/api/orders`, `/api/payments`).
- **PostgreSQL & Flyway Migrations**: Production schema with vector columns, Flyway migration scripts (`V1`, `V2`, `V3`), and optimized performance indexes.
- **Automated Testing Suite**: Full unit and integration test coverage using JUnit 5, Mockito, `@WebMvcTest`, and JaCoCo reports.
- **CI/CD & Docker**: GitHub Actions pipeline + multi-stage `Dockerfile` and `docker-compose.yml`.

---

## 🛠️ Tech Stack

- **Backend**: Java 17, Spring Boot 3.2.5, Spring Data JPA, Spring Security 6
- **Database**: PostgreSQL 16, pgvector extension, Flyway
- **AI / LLM**: Google Gemini AI (`embedding-001`, `gemini-pro`), LangChain4j 0.35.0
- **Testing**: JUnit 5, Mockito, JaCoCo
- **DevOps**: Docker, Docker Compose, GitHub Actions

---

## ⚙️ Running Locally

### Using Docker Compose (Recommended)
```bash
export GEMINI_API_KEY="your-gemini-api-key"
docker-compose up --build
```

---

## 📡 REST API Summary

| Endpoint | Method | Description |
|---|---|---|
| `/api/products` | GET, POST, PUT, DELETE | Product CRUD with pagination |
| `/api/search?q=...` | GET | AI RAG product search |
| `/api/search/reindex` | POST | Reindex vector embeddings |
| `/api/chat` | POST | AI Customer support chat |
| `/api/orders` | GET, POST, PATCH | Order creation & management |
| `/api/payments` | POST | Payment processing & refund |

---

## 🐙 Git Branching & Commit Log

```bash
git log --oneline
```
Contains all 24 atomic commits covering foundation, database, microservices, RAG, AI chat, optimization, test suite, and CI/CD setup.
