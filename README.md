# MyPan

English | [中文](#中文简介)

MyPan is a backend-focused cloud drive system. It implements a complete file lifecycle around upload, storage, preview, sharing, recycle bin management, and administration, with an emphasis on backend engineering rather than UI.

## Overview

This project simulates the core backend workflows of a production-style personal cloud drive:

- user authentication and session management
- large-file upload with resumable chunks
- instant upload based on file MD5
- local storage and MinIO object storage switching
- media post-processing for image, audio, and video files
- share links with extraction codes and access control
- recycle bin cleanup and scheduled maintenance
- admin-side user, file, and quota management

The repository is full-stack, but the main implementation focus is on the backend side. The frontend exists only as a thin client for calling APIs and demonstrating the end-to-end flow.

## Backend Highlights

### 1. Resumable chunk upload and instant upload

- Supports chunked upload for large files
- Supports resumable upload by querying already uploaded chunks
- Supports upload cancellation and cleanup
- Uses file MD5 for instant upload when the same physical file already exists
- Uses Redis to track temporary upload state and progress

### 2. Storage abstraction for local and object storage

- `STORAGE_TYPE=local` stores files on the local filesystem
- `STORAGE_TYPE=minio` uses MinIO multipart upload and object storage APIs
- Business logic is separated from storage implementation through service abstractions
- The design is extendable to OSS / COS / S3-like providers

### 3. Media processing pipeline

- Generates thumbnails for image files
- Extracts audio cover art and generates audio thumbnails
- Generates video cover images
- Transcodes videos into HLS segments for streaming playback
- Runs media processing asynchronously after upload completion
- Uses SSE to notify the client of transcode progress updates

### 4. Authentication, authorization, and access control

- Uses JWT for authentication
- Uses Redis to maintain online session state and sliding expiration
- Uses Spring Security for authorization
- Separates normal authenticated access from share-link-based access
- Supports admin role with protected management endpoints

### 5. Operational and cleanup logic

- Scheduled cleanup for expired shares
- Scheduled cleanup for recycle bin entries older than retention threshold
- Scheduled cleanup for failed or stuck transcoding tasks
- Scheduled cleanup for orphan temporary files and orphan database records
- Periodic recalculation of user used space

### 6. Admin capabilities

- user list and status management
- file list and file deletion management
- per-user storage quota adjustment
- system settings management

## Tech Stack

### Backend

- Java 17
- Spring Boot 3
- Spring Web
- Spring Data JPA
- Spring Security
- Spring Validation
- MySQL
- Redis
- Redisson
- JWT
- QueryDSL
- MapStruct
- MinIO
- Lombok

### Frontend

- Vue 3
- Vite
- Element Plus

The frontend is not the focus of this project and is only used to demonstrate backend APIs and business flows.

## Architecture

```text
Client
  -> HTTP API / SSE
Spring Boot Backend
  -> MySQL      (metadata, users, shares, quotas)
  -> Redis      (captcha, session, upload state, cache)
  -> Local FS   or MinIO (file storage)
  -> Async media processing (thumbnail, cover, HLS)
```

Key backend modules:

- `web/controller`: account, file, recycle, share, web share, admin, SSE
- `service/file/upload`: chunk upload, resume, abort, instant upload
- `service/file/transcode`: media processing and derived file generation
- `service/file/storage`: storage abstraction for local and MinIO
- `infra/security`: JWT, session validation, security configuration
- `infra/redis`: upload state, captcha, cache, system settings
- `schedule`: cleanup jobs and maintenance tasks

## Backend Features in Detail

### File management

- create folders
- rename files and folders
- move files across folders
- folder breadcrumb navigation
- category-based file listing
- download single or multiple files

### Preview and delivery

- image preview and thumbnail delivery
- video streaming from HLS or stored file resources
- audio metadata reading
- document preview support through the client side

### Sharing

- create share links
- custom extraction codes
- cookie + Redis based share access validation
- save shared files to personal space
- shared file preview and download

### Account system

- captcha generation
- email verification code sending
- registration and login
- password reset and password update
- avatar upload
- QQ login integration

## Local Run

### Prerequisites

- JDK 17
- Maven 3.9+ or the bundled Maven Wrapper
- MySQL 8+
- Redis 6+
- Node.js 18+ for the demo frontend
- optional: MinIO
- optional: FFmpeg for complete video transcoding and HLS generation

### 1. Import database schema

Import:

```sql
source mypan-server/mypan.sql;
```

Recommended database name:

```text
mypan
```

### 2. Configure backend environment variables

Use:

```text
mypan-server/.env.example
```

Minimal recommended configuration:

```env
DB_URL=jdbc:mysql://127.0.0.1:3306/mypan?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8&allowMultiQueries=true
DB_USERNAME=root
DB_PASSWORD=your_password

REDIS_HOST=127.0.0.1
REDIS_PORT=6379

JWT_SECRET=replace_with_your_secret

APP_PROJECT_FOLDER=d:/mypan
STORAGE_TYPE=local
```

Optional integrations:

- mail configuration for registration and password reset
- QQ Open Platform configuration for QQ login
- MinIO configuration when `STORAGE_TYPE=minio`

### 3. Start backend

From `mypan-server`:

```bash
./mvnw spring-boot:run
```

On Windows:

```bash
mvnw.cmd spring-boot:run
```

Default backend base URL:

```text
http://localhost:7090/api
```

### 4. Start frontend demo client

From `mypan-front`:

```bash
npm install
npm run dev
```

Default frontend URL:

```text
http://localhost:1024
```

The frontend proxies `/api` to the backend during local development.

## Project Structure

```text
MyPan
├─ mypan-server  # backend service
└─ mypan-front   # minimal demo client
```

## Future Improvements

- Docker / Docker Compose deployment
- unit and integration tests
- rate limiting and audit logs
- object storage provider expansion
- monitoring and metrics
- message notifications and share expiration reminders

## 中文简介

MyPan 是一个以后端实现为核心的网盘系统项目。项目重点不在前端界面，而在后端能力建设，包括：

- 分片上传、断点续传、秒传
- 本地存储与 MinIO 对象存储切换
- 图片缩略图、音频封面、视频封面与 HLS 转码
- JWT + Redis 登录态管理
- 分享链接与提取码访问控制
- 回收站、定时清理任务、管理员后台

后端技术栈主要为 Java 17、Spring Boot 3、Spring Security、JPA、MySQL、Redis、Redisson、QueryDSL、MapStruct、MinIO。
