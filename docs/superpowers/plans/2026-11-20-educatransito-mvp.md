# EDUCATRÂNSITO MVP Implementation Plan

> Implementation is incremental; every feature must have tests before production code.

**Goal:** Deliver a secure, offline Android field tool to register and retrieve educational traffic guidance records.

**Architecture:** Native Kotlin/Compose application with Room/SQLite persistence. Protocol allocation is serialized in database transactions; personal data is minimized and changes are auditable.

**Tech Stack:** Kotlin, Android Gradle Plugin, Jetpack Compose, Room, AndroidX Security, BiometricPrompt, Android Print/PDF APIs, ZXing.

**Spec:** docs/superpowers/specs/2026-11-20-educatransito-design.md

## Global constraints
- Android minimum API 26.
- Offline-first; no internet permission.
- Protocol format EDU-AAAA-NNNNNN; sequence does not reset daily.
- Vehicle history does not prove the current driver was previously oriented.
- Signature confirms receipt only, not confession.
- No ticketing, face recognition, maps or external integrations.

## Implementation tasks
1. Android shell and domain primitives.
2. Room persistence and atomic protocol creation.
3. Field workflow, validation and audit history.
4. PDF receipts, three per A4 sheet, QR code and print.
5. PIN/biometric access and encrypted backup/restore.
6. Reports and end-to-end offline verification.

## Current status
Shell, domain formatter, Room entities/DAO and draft screens exist. Registration, functional search, PDFs, authentication, reports and backup remain incomplete.
