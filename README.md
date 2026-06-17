# Day-Genie ✨
### Context-Aware Intelligent Task Planning Application
*Minor Project – II | Narula Institute of Technology | AY 2025-26*

---

## Project Structure

```
daygenie/
├── README.md
├── .gitignore
├── pom.xml
│
└── src/main/
    ├── java/com/daygenie/
    │   ├── DayGenieApplication.java          # Entry point
    │   │
    │   ├── config/
    │   │   ├── JwtUtil.java                  # JWT token helper
    │   │   ├── JwtAuthFilter.java            # JWT request filter
    │   │   └── SecurityConfig.java           # Spring Security + CORS config
    │   │
    │   ├── controller/
    │   │   ├── AuthController.java           # POST /api/auth/login|register
    │   │   └── TaskController.java           # CRUD + NLP + complete + refresh
    │   │
    │   ├── dto/
    │   │   └── Dtos.java                     # Request/Response DTOs
    │   │
    │   ├── engine/
    │   │   └── DecisionEngine.java           # Rule-based risk analysis
    │   │
    │   ├── model/
    │   │   ├── User.java                     # JPA entity
    │   │   ├── Task.java                     # JPA entity
    │   │   ├── Priority.java                 # HIGH, MEDIUM, LOW
    │   │   ├── TaskCategory.java             # STUDY, MEETING, TRAVEL, etc.
    │   │   ├── TaskStatus.java               # PENDING, COMPLETED, etc.
    │   │   └── RiskLevel.java                # LOW, MEDIUM, HIGH, UNKNOWN
    │   │
    │   ├── repository/
    │   │   ├── UserRepository.java
    │   │   └── TaskRepository.java
    │   │
    │   └── service/
    │       ├── NlpParserService.java         # Rule-based NLP parser
    │       ├── WeatherService.java           # Weather API integration
    │       ├── MapsService.java              # Maps/Distance API integration
    │       ├── TaskService.java              # Business logic
    │       └── UserService.java              # Registration + UserDetails
    │
    └── resources/
        ├── application.properties
        ├── application.properties.example    # Safe template for GitHub
        │
        └── static/
            ├── index.html                    # Login / Register page
            ├── dashboard.html                # Main application page
            │
            ├── css/
            │   └── style.css                 # Global styling
            │
            ├── js/
            │   ├── model.js                  # API calls & state
            │   ├── view.js                   # UI rendering
            │   └── controller.js             # Event handling
            │
            ├── images/
            │   └── favicon.png               # Browser tab icon  
            │
            └── fonts/
                └── Waterlily.ttf             # Custom Day-Genie font
```

---

## How to Run

### Prerequisites
- Java 17+
- Maven 3.8+

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/Reetam0006/Day-Genie.git
cd Day-Genie/daygenie

# 2. (Optional) Add API keys in src/main/resources/application.properties
#    daygenie.weather.api.key=YOUR_KEY   ← openweathermap.org/api
#    openrouteservice.api.key=YOUR_KEY      ← api.openrouteservice.org/

# 3. Build & run
mvn spring-boot:run

# 4. Open browser
#    App:        http://localhost:8080
#    PostgreSQL: YOUR_POSTGRESQL_URL  (JDBC: org.postgresql.Driver)
```

The schema is auto-managed by Hibernate on startup — no manual migrations needed.

---

## API Endpoints

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| POST | `/api/auth/register` | ✗ | Register new user |
| POST | `/api/auth/login` | ✗ | Login → returns JWT |
| GET  | `/api/tasks` | ✓ | All active tasks |
| GET  | `/api/tasks/today` | ✓ | Today's tasks |
| GET  | `/api/tasks/{id}` | ✓ | Single task |
| POST | `/api/tasks` | ✓ | Create structured task |
| **POST** | **`/api/tasks/nlp`** | ✓ | **Create task from natural language** |
| PUT  | `/api/tasks/{id}` | ✓ | Update task |
| PATCH | `/api/tasks/{id}/complete` | ✓ | Mark task as done |
| DELETE | `/api/tasks/{id}` | ✓ | Delete task |
| POST | `/api/tasks/{id}/refresh` | ✓ | Re-run weather/route/decision |

### NLP endpoint example
```json
POST /api/tasks/nlp
{ "rawInput": "Go to college on Sunday at 9 AM" }
```
Returns a fully enriched Task with weather, route and risk recommendations.

---

## Database Setup (PostgreSQL)

1. Install PostgreSQL and create the database:
```sql
   CREATE DATABASE daygenie;
```
2. Update `application.properties` with your credentials:
```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/daygenie
   spring.datasource.username=your_username
   spring.datasource.password=your_password
```
---

## Technologies Used

| Layer | Technology                                          |
|-------|-----------------------------------------------------|
| Backend | Java 17, Spring Boot 3.2                            |
| Security | Spring Security + JWT (JJWT)                        |
| Database | Supabase postgreSQL via Spring Data JPA             |
| NLP | Rule-based regex and natty parser (no external lib) |
| Weather API | OpenWeatherMap `/forecast`                          |
| Maps API | Open Route Service                                  |
| Frontend | HTML5, CSS3, Vanilla JS (MVC — 3 separate files)    |
| Build | Maven                                               |

---

## Team
| Name | Roll No. |
|------|----------|
| Sarfaraj Islam | 96 |
| Reetam Chakraborty | 95 |
| Anup Keshri | 74 |
| Aniket Raj | 77 |

Supervisor: **Anirban Bhar**, Assistant Professor, Dept. of IT, NIT