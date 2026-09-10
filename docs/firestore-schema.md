# ClassFlow — Cloud Firestore Schema Specification

This document specifies the exact Cloud Firestore schema implemented in ClassFlow v1. The database model comprises **seven root collections** and **one curriculum subcollection**.

---

## 1. `tracks` (Root Collection)

Curriculum track blueprints defining pacing, phases, and rubric templates.

- **Document ID**: Unique string identifier (e.g., `ss1`, `ss2`)
- **Model Identity**: `trackId` is annotated with `@Exclude` and maps to the document ID.
- **Persisted Fields**:
  - `code` (string, required): Short display code (e.g., `"SS1"`)
  - `name` (string, required): Track title (e.g., `"Python Foundations"`)
  - `description` (string, required): Overview of track outcomes
  - `totalWeeks` (number, required): Fixed at `10` weeks for all current tracks
  - `active` (boolean, required): Whether the track is currently available for new classes

### Subcollection: `tracks/{trackId}/weeks`
Defines week-by-week schedule specifications for the 10-week curriculum.
- **Document ID**: `W01` through `W10`
- **Model Identity**: `weekId` is annotated with `@Exclude` and maps to the document ID.
- **Persisted Fields**:
  - `weekNumber` (number, required): Calendar week index (`1` through `10`)
  - `title` (string, required): Topic for the week
  - `summary` (string, required): Pedagogical summary and concepts covered
  - `phase` (string, required): Pedagogical phase
  - `progressType` (string, required): Operational progress tracking category

#### Track Pacing Reference:
- **SS1 (Python Foundations)**:
  - `W01`–`W08`: Phase `FOUNDATION` / Progress Type `LEARNING`
  - `W09`: Phase `CAPSTONE` / Progress Type `PROJECT`
  - `W10`: Phase `DEMO` / Progress Type `DEMO`
- **SS2 (Smart Systems)**:
  - `W01`–`W04`: Phase `DATA_DECISION` / Progress Type `INTEGRATION`
  - `W05`: Phase `CONNECTED_DEVICE` / Progress Type `INTEGRATION`
  - `W06`–`W08`: Phase `APP_CLOUD` / Progress Type `INTEGRATION`
  - `W09`: Phase `SMART_SYSTEM_INTEGRATION` / Progress Type `PROJECT`
  - `W10`: Phase `DEMO` / Progress Type `DEMO`

---

## 2. `classes` (Root Collection)

Operational course cohorts running an active or historical track.

- **Document ID**: Unique offering key (e.g., `2026_SS1_A`)
- **Persisted Fields**:
  - `classId` (string, required): Matches document ID
  - `name` (string, required): Cohort display name (e.g., `"Software Studio 1 — Cohort A"`)
  - `trackId` (string, required): Reference to parent track ID (e.g., `"ss1"`)
  - `currentWeek` (number, required): Current instructional week (`1` to `10`)
  - `status` (string, required): Lifecycle state (`"PLANNED"`, `"ACTIVE"`, `"COMPLETED"`, `"ARCHIVED"`)
  - `startDate` (timestamp, required): Cohort start date
  - `createdAt` (timestamp, required): Record creation timestamp

---

## 3. `students` (Root Collection)

Permanent student profile records.

- **Document ID**: Institutional student ID string (e.g., `ST_001`)
- **Persisted Fields**:
  - `studentId` (string, required): Matches document ID
  - `name` (string, required): Full official student name
  - `nickname` (string, required): Preferred nickname used during studio instruction
  - `active` (boolean, required): Whether student is currently active in the academy
  - `createdAt` (timestamp, required): Record creation timestamp
  - `updatedAt` (timestamp, required): Last modification timestamp

---

## 4. `enrollments` (Root Collection)

Associates a Student with a specific Class offering and hosts their competency assessment.

- **Document ID**: Deterministic composite `{classId}_{studentId}` (e.g., `2026_SS1_A_ST_001`)
- **Model Identity**: `enrollmentId` is annotated with `@Exclude` and maps to the document ID.
- **Persisted Fields**:
  - `classId` (string, required): Foreign key to `classes`
  - `trackId` (string, required): Foreign key to `tracks`
  - `studentId` (string, required): Foreign key to `students`
  - `status` (string, required): Enrollment lifecycle (`"ACTIVE"`, `"COMPLETED"`, `"WITHDRAWN"`, `"ARCHIVED"`)
  - `enrolledAt` (timestamp, required): Enrollment creation timestamp
  - `assessment` (map, optional): Embedded rubric competency assessment data
    - `rubricId` (string, required if assessed): Evaluated rubric identifier (`"SS1_COMPETENCY_V1"`)
    - `criteria` (map, required if assessed): Map of 15 criterion IDs to integer scores (`1` through `4`) or `"NOT_OBSERVED"`
    - `strength` (string, optional): Qualitative narrative on strong competencies
    - `nextStep` (string, optional): Qualitative guidance on priority areas for growth
    - `updatedAt` (timestamp, optional): Assessment timestamp

> **Runtime-Only Fields**: Overall score and performance level are calculated dynamically at runtime by `AssessmentCalculator` and are not persisted.

---

## 5. `studentProgress` (Root Collection)

Weekly individual student progress checkpoints.

- **Document ID**: Deterministic composite `{classId}_{studentId}_{weekId}` (e.g., `2026_SS1_A_ST_002_W05`)
- **Model Identity**: `progressId` is annotated with `@Exclude` and maps to the document ID.
- **Persisted Fields**:
  - `classId` (string, required): Foreign key to `classes`
  - `trackId` (string, required): Foreign key to `tracks`
  - `studentId` (string, required): Foreign key to `students`
  - `weekId` (string, required): Pacing week identifier (e.g., `"W05"`)
  - `weekNumber` (number, required): Calendar week index (`1` to `10`)
  - `metrics` (map, optional): Key-value map of track-specific metrics defined by `ProgressMetricSchema`
  - `overallStatus` (string, required): Status (`"NOT_STARTED"`, `"ON_TRACK"`, `"NEEDS_ATTENTION"`, `"BLOCKED"`, `"COMPLETED"`)
  - `blocker` (string, optional): Description of blocker (mandatory if `overallStatus` is `"BLOCKED"`)
  - `teacherNote` (string, optional): Instructional feedback note (maximum 500 characters)
  - `updatedAt` (timestamp, required): Last modification timestamp

---

## 6. `projects` (Root Collection)

Team entities created for collaborative studio project phases.

- **Document ID**: Cloud Firestore auto-generated document ID
- **Model Identity**: `projectId` is annotated with `@Exclude` and maps to the document ID.
- **Persisted Fields**:
  - `classId` (string, required): Foreign key to `classes`
  - `trackId` (string, required): Foreign key to `tracks`
  - `title` (string, required): Project deliverable title
  - `teamName` (string, optional): Student team name (nullable)
  - `memberIds` (array of strings, required): List of enrolled `studentId` values
  - `currentWeek` (number, required): Current progress week for the project
  - `overallStatus` (string, required): Aggregate health (`"NOT_STARTED"`, `"ON_TRACK"`, `"NEEDS_ATTENTION"`, `"BLOCKED"`, `"COMPLETED"`)
  - `active` (boolean, required): Active lifecycle flag (soft archive sets `active: false`)
  - `createdAt` (timestamp, required): Project creation timestamp
  - `updatedAt` (timestamp, required): Last update timestamp

---

## 7. `projectProgress` (Root Collection)

Weekly milestone deliverable evaluations for project teams.

- **Document ID**: Deterministic composite `{projectId}_{weekId}` (e.g., `proj_auto123_W09`)
- **Model Identity**: `progressId` is annotated with `@Exclude` and maps to the document ID.
- **Persisted Fields**:
  - `projectId` (string, required): Foreign key to `projects`
  - `classId` (string, required): Foreign key to `classes`
  - `trackId` (string, required): Foreign key to `tracks`
  - `weekId` (string, required): Milestone week identifier (e.g., `"W09"`)
  - `weekNumber` (number, required): Milestone week index (`1` to `10`)
  - `metrics` (map, optional): Milestone checklist map
  - `overallStatus` (string, required): Milestone health (`"NOT_STARTED"`, `"ON_TRACK"`, `"NEEDS_ATTENTION"`, `"BLOCKED"`, `"COMPLETED"`)
  - `blocker` (string, optional): Description of blocker (mandatory if `overallStatus` is `"BLOCKED"`)
  - `teacherNote` (string, optional): Evaluator guidance note (maximum 500 characters)
  - `updatedAt` (timestamp, required): Evaluation timestamp
