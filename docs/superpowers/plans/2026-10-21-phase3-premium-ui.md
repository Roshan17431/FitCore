# FitCore Phase 3 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. No commits or publishing are authorized.

**Goal:** Polish the existing FitCore interface while preserving Phase 1 security and Phase 2 workflows.

**Architecture:** Retain routes, service mappings and event handlers. Introduce semantic Tailwind 4 tokens and accessible shared presentation components. Use local assets and synthetic fixtures.

**Tech Stack:** React 19, Vite 8, Tailwind 4, Playwright CLI, Spring Boot/PostgreSQL regression suite.

**Spec:** User attachment `015a2602-5354-4a27-803c-8b237030b39d/Pasted text.txt`; approved emerald/graphite/soft-white direction.

## Global Constraints

- No production access or writes; no commit, merge, push, restart or deployment.
- Preserve authentication, membership calculations, booking and payment behavior; no schema/backend changes or S3.
- Preserve existing local work and private remediation decisions using recorded SHA-256 hashes.
- Test 390/768/1440px, both themes, synthetic MEMBER/TRAINER/ADMIN.

## Review Focus

- Stored theme differs from OS preference: selected theme must win without first-paint flash.
- Keyboard-only navigation: hidden sidebar unreachable, dialogs trap/restore focus and support Escape.
- Missing/broken/stock trainer photos: initials must replace misleading images.
- Wrong exercise mappings: original diagrams or explicitly labelled visual fallback.
- Long labels/tables/charts at 390px: wrap text and constrain scroll to intended regions.

## Tasks

- [x] Capture before screenshots and failing theme/modal/media checks with local fixture API.
- [x] Record approved representative auth design on Superdesign, then implement shared tokens, pre-paint theme initialization and selector. Verify theme persistence, system changes and contrast.
- [x] Implement shared accessible dialog/focus management, forms, tables, buttons, tabs, charts and shell. Verify Escape, Tab wrap, focus restoration, mobile navigation, nested active routes and search.
- [x] Polish auth pages with original branded art; preserve handlers and truthful password-recovery guidance. Test errors/loading and links.
- [x] Implement trainer initials and exercise visuals/fallbacks; preserve API structures. Check image failure behavior and no remote stock downloads.
- [x] Apply shared styling consistently to all existing page branches and charts without changing business code.
- [x] Run lint, production build, 7 API contract tests, Phase 2 fixture and real API flows, backend PostgreSQL tests, and full Phase 3 visual matrix.
- [x] Inspect after screenshots, console and requests; verify preservation fingerprints and diff; document results/deferred reset workflow. Stop with uncommitted local changes.

## Execution notes

- Used the approved direction; no additional variants or publication.
- Ruling: use labelled exercise media fallbacks because existing photographs are unverified; technique art is deferred instead of invented.
- Verified six initial browser failures before implementation; later contrast and breakpoint failures were fixed and rechecked.
- Full local verification is summarized in `docs/phase3-ui-review-2026-10-22.md`. One five-second page-load timeout was rechecked at fifteen seconds; all affected routes passed.
- Independent reviewer hit an account limit after identifying a contrast issue; that finding was reproduced and corrected, then local diff review and tests completed.
- All pre-existing and private-file hashes match. No commit, push, merge, deploy, production restart or production data operation occurred.

## Subsequent source-publication approval — 22 October

The owner authorized up to 12 focused commits and a push to GitHub `main` after implementation verification. This supersedes the implementation-only restriction on commits/pushes above. Publication must preserve unrelated local files, private review decisions, backend security/business logic and existing deployment guards; `master`, production services and production data remain unchanged. Generated design context and local screenshot/log artifacts are excluded.
