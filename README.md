# UniGuide 🎓

**Study smarter, not just harder.**

UniGuide is a web application designed to help students navigate their academic journey through a centralized and accessible platform.

The project focuses on building a modern web application with a Java-based backend, database integration and user authentication.

## 🚀 Tech Stack

### Backend
- **Java 21** – Main programming language.
- **Spring Boot 3.3.4** – Backend framework.
- **Spring JDBC (JdbcTemplate)** – Database interaction.
- **JWT (JSON Web Tokens)** – Authentication and authorization.
- **jBCrypt** – Secure password hashing.

### Database
- **MySQL** – Relational database for persistent data storage.

### Frontend
- HTML5
- CSS3
- JavaScript

## ✨ Features

- User authentication and account management.
- Secure password hashing using BCrypt.
- JWT-based authentication.
- REST API architecture.
- Database integration using MySQL and JdbcTemplate.
- Password reset token functionality.
- Responsive web interface.

## 🏗️ Project Structure

```text
UniGuide/
│
├── src/
│   └── main/
│       ├── java/
│       │   └── ...
│       └── resources/
│           ├── static/
│           └── application.properties
│
├── .mvn/
├── mvnw
├── mvnw.cmd
├── pom.xml
└── README.md
```

## ⚙️ Installation and Setup

### Prerequisites

Make sure you have installed:
- Java JDK 21
- MySQL Server
- Git
- Maven (optional, Maven Wrapper is included)

### 1. Clone the repository

```bash
git clone https://github.com/huzaalex/UniGuide.git
cd UniGuide
```

### 2. Configure the database

Create a MySQL database:

```sql
CREATE DATABASE uniguide;
```

Configure your database connection in `application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/uniguide
spring.datasource.username=YOUR_USERNAME
spring.datasource.password=YOUR_PASSWORD
```

### 3. Run the application

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

On Linux/macOS:

```bash
./mvnw spring-boot:run
```

The backend will start on:

```text
http://localhost:8080
```

## 🔐 Security

UniGuide implements several security-related mechanisms:

- Password hashing with BCrypt.
- JWT-based authentication.
- Token-based password reset functionality.
- Separation between application logic and database operations.

## 🛠️ Development

This project is actively developed as a learning and practical software engineering project.

The main objectives are to improve knowledge of:
- Backend development with Java and Spring Boot.
- RESTful API design.
- Relational database management.
- Authentication and application security.
- Full-stack web development.
- Git and collaborative software development.

## 👨‍💻 Author

**Huza Alex**

GitHub: [@huzaalex](https://github.com/huzaalex)

---

*UniGuide — Study smarter, not just harder.*