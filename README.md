# Quizora — Real-Time Online Quiz Platform & Examination System

Quizora is a real-time MCQ quiz and confidential college examination platform where faculty can conduct live quizzes and candidates join instantly using a QR code or shareable PIN link on their phones. Candidates answer questions in real time while the host monitors live response distributions through dynamically updating charts.

- **Live Production URL**: [https://live-quiz-system.vercel.app/](https://live-quiz-system.vercel.app/)
- **GitHub Repository**: [https://github.com/vibeshmourya/Live-Quiz-System](https://github.com/vibeshmourya/Live-Quiz-System)

---

## 🛠 Technology Stack

- **Frontend**: React.js with Tailwind CSS, Chart.js (`react-chartjs-2`), `qrcode.react`, `@stomp/stompjs`, `lucide-react`
- **Backend**: Java Spring Boot 3.4 (Java 21/25) with Spring Data JPA, Spring Web, Validation, Jackson
- **Real-Time Communication**: Spring WebSocket with STOMP protocol (`/topic/session/{pin}`, `/topic/session/{pin}/stats`)
- **Database**: MySQL 8.0 with InnoDB
- **APIs**: RESTful JSON endpoints with CORS and Vite proxy enabled
- **Security**: PBKDF2WithHmacSHA256 password hashing, token-based faculty authentication, server-enforced question confidentiality, and anti-cheat validation

---

## 🔐 Faculty Authentication & Examination States

### Faculty Access & Registration
- Faculty can either **Sign In** using their credentials or **Register** a new account from the Faculty Portal modal.
- Registration collects:
  - **Full Name** (e.g., Prof. Jane Doe)
  - **Faculty Email** (validated for institutional format and duplicate prevention)
  - **Password & Confirmation** (minimum 6 characters, hashed securely with PBKDF2)
- Upon registration, faculty are securely authenticated and issued an authorized dashboard session token.
- Logout is accessible directly from the navbar header.

### Default Faculty Credentials
- **Email / Username**: `faculty@quizora.edu` or `faculty`
- **Password**: `password123`

### Faculty-Controlled Answer Change Policy
Faculty can configure the answer change policy during examination creation or editing:
1. **Single Selection (`SINGLE_SELECTION`)**:
   - The candidate submits an answer once.
   - The selected option is locked immediately on submit.
   - Reselection is prevented on both client and backend (additional attempts are rejected with HTTP 400).
2. **Allow Reselection (`ALLOW_RESELECTION`)**:
   - While the question is active and the timer is running, the candidate can tap a different option.
   - The backend atomically updates the existing submission record, adjusts candidate score, and recalculates option statistics.
   - Live Chart.js graphs shift counts from the old option to the new option in real time without creating duplicate submission rows.

---

## 📁 Project Structure

```
QUIZ_APP/
├── backend/
│   ├── pom.xml
│   ├── src/main/
│   │   ├── java/com/quizlive/
│   │   │   ├── config/          # WebSocket and CORS configurations
│   │   │   ├── controller/      # REST API endpoints (Quizzes, Live Sessions, Auth)
│   │   │   ├── dto/             # Data Transfer Objects (RegisterRequest, QuizDto, etc.)
│   │   │   ├── model/           # JPA entities (Quiz, AnswerChangePolicy, Faculty, Submission, etc.)
│   │   │   ├── repository/      # Spring Data JPA repositories
│   │   │   ├── service/         # Business logic, state synchronization, reselection & scoring
│   │   │   └── util/            # PBKDF2 password hashing utility
│   │   └── resources/
│   │       └── application.properties
├── frontend/
│   ├── package.json
│   ├── vite.config.js          # Configured with proxy for /api and /ws-raw
│   ├── src/
│   │   ├── components/          # Navbar, FacultyLoginModal (Sign in & Register tabs), ExamPreviewModal
│   │   ├── pages/
│   │   │   ├── HomePage.jsx             # Quizora landing (Create / Join)
│   │   │   ├── CreateQuizPage.jsx       # Answer change policy selector, dynamic creator, draft saver
│   │   │   ├── JoinQuizPage.jsx         # Candidate PIN entry & QR direct join
│   │   │   ├── HostLivePage.jsx         # Live dashboard with Chart.js & synchronized controls
│   │   │   ├── CandidateQuizPage.jsx    # Mobile-optimized answer pad with reselection support
│   │   │   └── ResultsPage.jsx          # Final score & ranked leaderboard
│   │   ├── services/
│   │   │   ├── api.js                   # REST client with automatic Bearer token injection
│   │   │   └── websocket.js             # STOMP native WebSocket client
│   │   ├── App.jsx
│   │   └── index.css
└── README.md
```

---

## 🚀 How to Run Locally

### 1. Database Setup (MySQL)
Ensure MySQL is running on port `3306` with database `quizlive`:
```sql
CREATE DATABASE IF NOT EXISTS quizlive;
```

#### Safe Database Migrations Applied:
```sql
ALTER TABLE faculty_users ADD COLUMN email VARCHAR(150) NULL UNIQUE;
ALTER TABLE quizzes ADD COLUMN answer_change_policy VARCHAR(30) NOT NULL DEFAULT 'SINGLE_SELECTION';
```

### 2. Start Backend (Spring Boot)
In the `backend` directory:
```bash
cd backend
mvn spring-boot:run
```
The backend starts at `http://localhost:8080`.

### 3. Start Frontend (React + Vite)
In the `frontend` directory:
```bash
cd frontend
npm install
npm run dev
```
The application opens at `http://localhost:5173`.
