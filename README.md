# FitCore

FitCore is a Gym Management System with a Spring Boot backend and a React + Vite frontend.

## Project Structure

```text
FitCore/
├── gym-backend/
│   ├── src/
│   ├── pom.xml
│   └── ...
└── gym-frontend/
    ├── src/
    ├── package.json
    └── ...
```

## Backend

```bash
cd gym-backend
./mvnw spring-boot:run
```

Backend API base URL:

```text
http://localhost:8080/api
```

## Frontend

```bash
cd gym-frontend
npm install
npm run dev
```

The frontend reads `VITE_API_URL` from `gym-frontend/.env`.

## Seed Data

Backend seed data is in:

```text
gym-backend/src/main/resources/data.sql
```

Seed password for the included demo users:

```text
Fitcore@123
```
