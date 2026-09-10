# ClassFlow — System Architecture & Domain Model

## Overview

ClassFlow is a specialized teaching operations tool built for **VU Tech Academy** educators. It enables studio leads and instructors to manage curriculum tracks, track student learning journeys across multiple offerings, conduct competency assessments, guide student projects, and triage operational risks via a priority intervention center.

---

## Domain Hierarchy & Conceptual Model

ClassFlow organizes studio teaching operations into clear hierarchical domains:

```text
TRACK (Curriculum Blueprint)
  └── CLASS (Operational Offering)
        ├── ENROLLMENT (Student Association & Status)
        │     ├── STUDENT PROGRESS (Weekly Status & Dynamic Metrics)
        │     └── ASSESSMENT (Embedded Rubric Evaluation)
        │
        └── PROJECT (Team Entity)
              └── PROJECT PROGRESS (Weekly Deliverables & Status Sync)

HOME (Operational Dashboard)
  └── ATTENTION DISCOVERY
        └── TARGETED STUDENT / PROJECT CONTEXT
```

---

## Core Structural Invariants

### 1. Track vs. Class
- **Track** (`tracks`): Represents the immutable curriculum blueprint (e.g., `ss1` - Python Foundations, `ss2` - Smart Systems). It defines the standard 10-week schedule (`W01`–`W10`), progression phases, dynamic metric schemas, and rubric criteria.
- **Class** (`classes`): Represents an operational course offering (e.g., `2026_SS1_A`). A class instantiates a specific track, possesses independent lifecycle states (`PLANNED`, `ACTIVE`, `COMPLETED`, `ARCHIVED`), and maintains an independent `currentWeek` pointer (`1` to `10`).

### 2. Student Identity vs. Enrollment
- **Student** (`students`): Represents permanent individual identity (`studentId`, `name`, `nickname`, `active`). A student exists independently of classes.
- **Enrollment** (`enrollments`): Represents the contractual association between a Student and a Class. A single student may hold multiple historical enrollments across different terms and tracks.
- **Deterministic Keying**: Enrollment documents use the deterministic ID format `{classId}_{studentId}`, preventing duplicate logical records in the same class.

### 3. Student Progress vs. Project Progress
- **StudentProgress** (`studentProgress`): Evaluates weekly individual student progress using the dynamic metric schema for that Track/Week. Document IDs follow `{classId}_{studentId}_{weekId}` (e.g., `2026_SS1_A_ST_002_W05`). Status options include `NOT_STARTED`, `ON_TRACK`, `NEEDS_ATTENTION`, `BLOCKED`, and `COMPLETED`.
- **ProjectProgress** (`projectProgress`): Evaluates weekly team deliverable progress using the dynamic metric schema for that Track/Week. Document IDs follow `{projectId}_{weekId}` (e.g., `proj_auto123_W09`).

### 4. Competency Assessment
- The **Assessment** record evaluates 15 criteria across 5 engineering domains for the SS1 curriculum.
- In accordance with the single-source-of-truth principle, the assessment is embedded directly within the parent `Enrollment` document (`enrollments/{classId}_{studentId}.assessment`), ensuring transactional co-location with the enrollment record.
- Persisted assessment attributes include `rubricId` (`SS1_COMPETENCY_V1`), `criteria`, `strength`, `nextStep`, and `updatedAt`. Overall competence score, band level (`STRONG`, `READY`, `DEVELOPING`, `NEED SUPPORT`), and domain radar calculations remain dynamic runtime calculations.

---

## Key Engineering Decisions & Operational Rules

### 1. Transaction-Guarded Writes
Invariant-sensitive write paths use Cloud Firestore transactions (`runTransaction`), including class current-week changes, Enrollment status changes, Student Progress saves, Assessment saves, and Project Progress current-week synchronization:
- **Class Week Advancement**: Validates track bounds before updating `currentWeek`.
- **Enrollment Status Changes**: Enforces valid lifecycle transitions (`ACTIVE`, `COMPLETED`, `WITHDRAWN`, `ARCHIVED`).
- **Student Progress**: Ensures active enrollment exists and validates that `blocker` descriptions accompany any `BLOCKED` status.
- **Assessment Scoring**: Enforces valid rubric criterion scoring (integers 1–4 or `NOT_OBSERVED`).

### 2. Zero-Cascade Lifecycle Protection
- Archiving a Student (`student.active = false`) never cascades destructive changes to historical enrollments, past progress, or existing project memberships.
- Archiving or completing a Class leaves historical records intact for reporting and portfolio auditing.

### 3. UI-Only `NOT_RECORDED` Semantics
- If an active student does not yet have a `studentProgress` record for the class's current week, the application presents an operational status of `NOT_RECORDED`.
- `NOT_RECORDED` is purely a UI presentation state. It is **never persisted** to Firestore, keeping database collections clean and free of junk status records.

### 4. Enrollment Write Authority
- Progress updates and competency assessments require the student's enrollment to be `ACTIVE`.
- Inactive enrollments (`COMPLETED`, `WITHDRAWN`, `ARCHIVED`) are strictly read-only in the UI and rejected by repository transactions.

### 5. Project Current-Week Status Synchronization
- Projects maintain an `overallStatus` on the root `Project` document.
- When saving a `ProjectProgress` record, if the record corresponds to the project's `currentWeek`, the repository transaction atomically updates the root `project.overallStatus`. Past-week retrospectives do not overwrite current project status.

### 6. Read-Derived Operational Dashboard
- The Home Dashboard does not query a synthetic "dashboard" collection.
- It derives the **Priority Intervention Feed** and class summaries dynamically from bounded queries scoped to active classes and current-week operational records, with automatic refresh on screen resume (`onResume`).

### 7. Stale Async Generation Protection
- Detail screens (such as `StudentDetailActivity` and `ProjectDetailActivity`) enforce an atomic request sequence token or lifecycle checks.
- If a user rapidly navigates or switches contexts, late-arriving asynchronous Firestore callbacks are discarded, preventing stale data from overwriting newer user selections.
