# FitCore

FitCore is a full-stack gym management system built as a monorepo. It includes a Spring Boot REST API for memberships, workouts, nutrition, trainers, attendance, progress tracking, and admin workflows, plus a React + Vite dashboard frontend.

## Live Deployments

```text
Frontend: https://fit-core-git-master-roshan17431s-projects.vercel.app
Backend:  https://fitcore-posv.onrender.com
Health:   https://fitcore-posv.onrender.com/api/health
```

## Tech Stack

**Backend**
- Java 17
- Spring Boot 4.1
- Spring Security + JWT
- Spring Data JPA
- PostgreSQL
- Swagger/OpenAPI
- Docker
- Optional AWS S3 image upload support

**Frontend**
- React 19
- Vite 8
- React Router
- Tailwind CSS 4
- Axios
- Recharts
- Lucide React icons

## Project Structure

```text
FitCore/
├── gym-backend/
│   ├── src/main/java/com/fitcore/gym/
│   │   ├── config/
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── exception/
│   │   ├── repository/
│   │   ├── security/
│   │   └── service/
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   └── data.sql
│   ├── Dockerfile
│   ├── pom.xml
│   └── .env.example
├── gym-frontend/
│   ├── src/
│   │   ├── components/
│   │   ├── context/
│   │   ├── layouts/
│   │   ├── pages/
│   │   ├── routes/
│   │   └── services/
│   ├── package.json
│   ├── vercel.json
│   └── .env.example
├── render.yaml
└── README.md
```

## Features

- JWT-based authentication with login, registration, and forgot password flow
- Protected user dashboard with membership, workout, nutrition, progress, trainer, and attendance modules
- Admin dashboard for users, trainers, memberships, attendance, revenue, and growth analytics
- Membership plan browsing, active membership view, and history tracking
- Workout library, plans, daily tracker, and workout history
- Nutrition dashboard, diet plans, food logging, and calorie tracking
- Progress tracking for weight, measurements, BMI, and analytics
- Trainer list, profiles, session booking, and session history
- Attendance check-in/check-out, history, and statistics
- Responsive SaaS-style UI with dark/light mode
- Seed data in `gym-backend/src/main/resources/data.sql`

## Prerequisites

- Java 17
- Node.js 22.x
- npm
- PostgreSQL database, such as Neon
- Docker, only required for container deployment

## Backend Setup

```bash
cd gym-backend
cp .env.example .env
```

Update `gym-backend/.env`:

```text
DATABASE_URL=jdbc:postgresql://your-neon-host/your-database?sslmode=require
DATABASE_USERNAME=your-database-username
DATABASE_PASSWORD=your-database-password
JWT_SECRET=replace-with-at-least-32-random-characters
JWT_EXPIRATION_MS=86400000
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
JPA_DDL_AUTO=update
SQL_INIT_MODE=always
AWS_S3_ENABLED=false
```

Run the API:

```bash
./mvnw spring-boot:run
```

The backend runs at:

```text
http://localhost:8080
```

Common endpoints:

```text
GET  /api/health
POST /api/auth/register
POST /api/auth/login
```

Swagger UI is available at:

```text
http://localhost:8080/swagger-ui.html
```

## Frontend Setup

```bash
cd gym-frontend
cp .env.example .env
npm install
```

For local development, set:

```text
VITE_API_BASE_URL=http://localhost:8080
```

Run the frontend:

```bash
npm run dev
```

The frontend runs at:

```text
http://localhost:5173
```

The frontend API client always calls:

```text
${VITE_API_BASE_URL}/api/...
```

Do not include `/api` in `VITE_API_BASE_URL`.

## Useful Commands

Backend:

```bash
cd gym-backend
./mvnw spring-boot:run
./mvnw -DskipTests package
```

Frontend:

```bash
cd gym-frontend
npm run dev
npm run build
npm run preview
npm run lint
```

## Environment Variables

### Backend

| Variable | Required | Description |
| --- | --- | --- |
| `DATABASE_URL` | Yes | JDBC PostgreSQL URL |
| `DATABASE_USERNAME` | Yes | Database username |
| `DATABASE_PASSWORD` | Yes | Database password |
| `JWT_SECRET` | Yes | JWT signing secret, at least 32 characters |
| `JWT_EXPIRATION_MS` | No | Token lifetime in milliseconds |
| `CORS_ALLOWED_ORIGINS` | Yes | Comma-separated frontend origins |
| `JPA_DDL_AUTO` | No | Hibernate schema mode, usually `update` |
| `SQL_INIT_MODE` | No | Seed data mode, usually `always` or `never` |
| `AWS_S3_ENABLED` | No | Enables S3 image upload support |
| `AWS_ACCESS_KEY_ID` | If S3 enabled | AWS access key |
| `AWS_SECRET_ACCESS_KEY` | If S3 enabled | AWS secret key |
| `AWS_REGION` | If S3 enabled | AWS region |
| `AWS_BUCKET_NAME` | If S3 enabled | S3 bucket name |

### Frontend

| Variable | Required | Description |
| --- | --- | --- |
| `VITE_API_BASE_URL` | Yes | Backend origin without `/api`, for example `https://fitcore-posv.onrender.com` |

## Deployment

### Backend on Render

The backend is Docker-ready:

```text
gym-backend/Dockerfile
render.yaml
```

Render settings:

```text
Runtime: Docker
Docker Context: ./gym-backend
Dockerfile Path: ./gym-backend/Dockerfile
Health Check Path: /api/health
```

Set these Render environment variables:

```text
DATABASE_URL=jdbc:postgresql://your-neon-host/your-database?sslmode=require
DATABASE_USERNAME=your-database-username
DATABASE_PASSWORD=your-database-password
JWT_SECRET=replace-with-a-long-random-secret
JWT_EXPIRATION_MS=86400000
CORS_ALLOWED_ORIGINS=https://your-vercel-domain.vercel.app,http://localhost:5173,http://127.0.0.1:5173
JPA_DDL_AUTO=update
SQL_INIT_MODE=always
AWS_S3_ENABLED=false
```

### Frontend on Vercel

Vercel settings:

```text
Root Directory: gym-frontend
Build Command: npm run build
Output Directory: dist
```

Set this Vercel environment variable:

```text
VITE_API_BASE_URL=https://fitcore-posv.onrender.com
```

The included `gym-frontend/vercel.json` rewrites all routes to `index.html` so React Router works in production.

## Authentication Flow

1. The frontend submits credentials to `/api/auth/login` or `/api/auth/register`.
2. The backend returns a JWT and user profile.
3. The frontend stores the token in browser storage.
4. Axios attaches the token to protected requests with `Authorization: Bearer <token>`.
5. Spring Security validates the JWT before protected endpoints are reached.

Public backend routes include:

```text
/api/auth/**
/api/health
/swagger-ui/**
/v3/api-docs/**
```

## Troubleshooting

### Registration or login never reaches Render

Check Vercel has:

```text
VITE_API_BASE_URL=https://fitcore-posv.onrender.com
```

The frontend should log API requests in the browser console with URLs shaped like:

```text
https://fitcore-posv.onrender.com/api/auth/register
```

### Browser shows CORS or `ERR_NETWORK`

Add the deployed frontend origin to Render:

```text
CORS_ALLOWED_ORIGINS=https://your-vercel-domain.vercel.app,http://localhost:5173,http://127.0.0.1:5173
```

Redeploy the backend after changing Render environment variables.

### `POST /api/auth/register` returns 400

Check the request body. Registration password validation requires at least:

- 8 characters
- 1 uppercase letter
- 1 lowercase letter
- 1 number
- 1 special character

### Render starts but database operations fail

Verify:

- `DATABASE_URL` uses the JDBC format
- Neon/PostgreSQL credentials are correct
- `DATABASE_USERNAME` and `DATABASE_PASSWORD` match the database role

## Notes

- Do not commit real `.env` files, database credentials, JWT secrets, or AWS credentials.
- Keep `VITE_API_BASE_URL` as the backend origin only. Do not append `/api`.
- Seed data lives in `gym-backend/src/main/resources/data.sql`.
