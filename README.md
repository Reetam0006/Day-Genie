# Day-Genie ✨
### Context-Aware Intelligent Task Planning Application
*Minor Project – I | Narula Institute of Technology | AY 2025-26*

---

## Project Structure

```
daygenie/
├── pom.xml
└── src/main/
    ├── java/com/daygenie/
    │   ├── DayGenieApplication.java          # Entry point
    │   ├── config/
    │   │   ├── JwtUtil.java                  # JWT token helper
    │   │   ├── JwtAuthFilter.java            # JWT request filter
    │   │   └── SecurityConfig.java           # Spring Security config
    │   ├── controller/
    │   │   ├── AuthController.java           # POST /api/auth/login|register
    │   │   └── TaskController.java           # CRUD + NLP + refresh endpoints
    │   ├── engine/
    │   │   └── DecisionEngine.java           # Rule-based risk analysis
    │   ├── model/
    │   │   ├── User.java                     # JPA entity
    │   │   ├── Task.java                     # JPA entity
    │   │   └── enums: Priority, TaskCategory, TaskStatus, RiskLevel
    │   ├── repository/
    │   │   ├── UserRepository.java
    │   │   └── TaskRepository.java
    │   └── service/
    │       ├── NlpParserService.java         # Rule-based NLP (date/time/location)
    │       ├── WeatherService.java           # OpenWeatherMap API
    │       ├── MapsService.java              # Google Distance Matrix API
    │       ├── TaskService.java              # Business logic + scheduled refresh
    │       └── UserService.java             # Registration + UserDetails
    └── resources/
        ├── application.properties
        └── static/
            └── index.html                    # Single-page frontend (Vanilla JS)
```

---

## How to Run

### Prerequisites
- Java 17+
- Maven 3.8+

### Steps

```bash
# 1. Clone / extract the project
cd daygenie

# 2. (Optional) Add API keys in src/main/resources/application.properties
#    daygenie.weather.api.key=YOUR_KEY   ← openweathermap.org/api
#    daygenie.maps.api.key=YOUR_KEY      ← console.cloud.google.com

# 3. Build & run
mvn spring-boot:run

# 4. Open browser
#    App:        http://localhost:8080
#    H2 Console: http://localhost:8080/h2-console  (JDBC: jdbc:h2:mem:daygenie)
```

The app runs with H2 in-memory database by default — no MySQL setup needed for dev.

---

## API Endpoints

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| POST | `/api/auth/register` | ✗ | Register new user |
| POST | `/api/auth/login` | ✗ | Login → returns JWT |
| GET  | `/api/tasks` | ✓ | All user tasks |
| GET  | `/api/tasks/today` | ✓ | Today's tasks |
| GET  | `/api/tasks/{id}` | ✓ | Single task |
| POST | `/api/tasks` | ✓ | Create structured task |
| **POST** | **`/api/tasks/nlp`** | ✓ | **Create task from natural language** |
| PUT  | `/api/tasks/{id}` | ✓ | Update task |
| DELETE | `/api/tasks/{id}` | ✓ | Delete task |
| POST | `/api/tasks/{id}/refresh` | ✓ | Re-run weather/route/decision |

### NLP endpoint example
```json
POST /api/tasks/nlp
{ "rawInput": "Go to college on Sunday at 9 AM" }
```
Returns a fully enriched Task with weather, route and risk recommendations.

---

## Switching to MySQL (Production)

1. Uncomment the MySQL section in `application.properties`
2. Comment out the H2 section
3. Create the database: `CREATE DATABASE daygenie;`
4. Update username/password

---

## Technologies Used

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.2 |
| Security | Spring Security + JWT (JJWT) |
| Database | H2 (dev) / MySQL (prod) via Spring Data JPA |
| NLP | Rule-based regex parser (no external lib) |
| Weather API | OpenWeatherMap `/forecast` |
| Maps API | Google Distance Matrix |
| Frontend | HTML5, CSS3, Vanilla JS (single file) |
| Build | Maven |

---

## Team
| Name | Roll No. |
|------|----------|
| Sarfaraj Islam | 96 |
| Reetam Chakraborty | 95 |
| Anup Keshri | 74 |
| Aniket Raj | 77 |

Supervisor: **Anirban Bhar**, Assistant Professor, Dept. of IT, NIT
