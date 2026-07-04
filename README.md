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

### Render Deployment

The backend is Docker-ready for Render:

```text
gym-backend/Dockerfile
gym-backend/.dockerignore
render.yaml
```

Create a Render Web Service from this repository using the Docker runtime, or use the included `render.yaml` blueprint. The service health check path is:

```text
/api/health
```

Required Render environment variables:

```text
DATABASE_URL=jdbc:postgresql://your-neon-host/your-database?sslmode=require
DATABASE_USERNAME=your-database-username
DATABASE_PASSWORD=your-database-password
JWT_SECRET=replace-with-a-long-random-secret
JWT_EXPIRATION_MS=86400000
CORS_ALLOWED_ORIGINS=https://fit-core-git-master-roshan17431s-projects.vercel.app,https://fit-core-one-beta.vercel.app
JPA_DDL_AUTO=update
SQL_INIT_MODE=always
AWS_S3_ENABLED=false
```

Only set the AWS variables if image upload is enabled:

```text
AWS_S3_ENABLED=true
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
AWS_REGION=your-region
AWS_BUCKET_NAME=your-bucket
```

## Frontend

```bash
cd gym-frontend
npm install
npm run dev
```

The frontend reads `VITE_API_BASE_URL` from `gym-frontend/.env`.

### Vercel Deployment

Deploy the frontend as a Vercel project with:

```text
Root Directory: gym-frontend
Build Command: npm run build
Output Directory: dist
```

The frontend includes `gym-frontend/vercel.json` for Vite SPA routing. Set this Vercel environment variable:

```text
VITE_API_BASE_URL=https://fitcore-posv.onrender.com
```

## Seed Data

Backend seed data is in:

```text
gym-backend/src/main/resources/data.sql
```

Do not commit real `.env` files, JWT secrets, database passwords, or provider credentials.
