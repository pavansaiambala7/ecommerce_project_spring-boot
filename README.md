# E-commerce Spring Boot Application

A robust, production-oriented Java e-commerce web application built with Spring Boot, JSP, Spring Security, and Hibernate. 

This project follows a layered MVC architecture and supports role-based access for admin and customer workflows, designed to provide a seamless shopping experience and easy product management.

## 🚀 Key Features

- **Server-Rendered UI**: Dynamic and responsive views using JSP and JSTL.
- **Secure Authentication**: Spring Security handles role-based authorization (Admin vs. User).
- **Custom Persistence**: Custom Hibernate SessionFactory configuration for advanced database management.
- **Admin Dashboard**: Modules for managing products, categories, and viewing registered customers.
- **Customer Portal**: Modules for user registration, login, profile management, and product browsing.
- **Database Integrated**: MySQL-backed persistence with clean DAO and service layers.

## 🛠️ Tech Stack

- **Java 11**
- **Spring Boot 2.6.4**
- **Spring MVC**
- **Spring Security**
- **Hibernate ORM**
- **JSP + Tomcat Jasper**
- **MySQL 8**
- **Maven**

## 📂 Project Structure

```text
src/main/java/com/jtspringproject/JtSpringProject/
  configuration/     # Security config
  controller/        # MVC controllers
  dao/               # Data access layer
  models/            # Entities
  services/          # Business layer
  repository/        # Spring Data repository
src/main/resources/
  application.properties
src/main/webapp/views/
  *.jsp
```

## ⚙️ Getting Started

### Prerequisites
- Java 11+
- Maven 3.8+
- MySQL Server

### 1) Database Configuration
Update `src/main/resources/application.properties` with your local MySQL credentials:

```properties
db.driver=com.mysql.cj.jdbc.Driver
db.url=jdbc:mysql://localhost:3306/ecommjava?createDatabaseIfNotExist=true
db.username=root
db.password=your_password

hibernate.dialect=org.hibernate.dialect.MySQL5Dialect
hibernate.show_sql=true
hibernate.hbm2ddl.auto=update
entitymanager.packagesToScan=com
```

### 2) Seed Initial Data (Optional)
Run the `basedata.sql` file in your MySQL database to populate initial categories, products, and a default admin user.

### 3) Run the Application
Open your terminal and build the project:
```bash
mvn clean package
mvn spring-boot:run
```

Once running, navigate to: `http://localhost:8080/`

## 🔒 Security & Roles
- **Admin Routes**: All routes under `/admin/**` require the `ADMIN` role.
- **User Routes**: Require the `USER` role.
- **CSRF Protection**: Enabled for all form submissions to ensure data integrity.

---
*Built with ❤️ focusing on clean architecture and scalable Java backend design.*
