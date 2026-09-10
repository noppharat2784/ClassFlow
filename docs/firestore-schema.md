# ClassFlow — Cloud Firestore Schema Specification

This document specifies the exact Cloud Firestore schema implemented in ClassFlow v1. The database model comprises **seven root collections** and **one curriculum subcollection**.

---

## 1. `tracks` (Root Collection)

Curriculum track blueprints defining pacing, phases, milestones, and rubric templates.

- **Document ID**: Unique string identifier (e.g., `ss1`, `ss2`)
- **Fields**:
  - `trackId` (string, required): Matches document ID
  - `code` (string, required): Short display code (e.g., `"SS1"`)
  - `name` (string, required): Track title (e.g., `"Python Foundations"`)
  - `description` (string, required): Overview of track outcomes
  - `totalWeeks` (number, required): Total duration in weeks (typically `12`)
  - `active` (boolean, required): Whether the track is currently available

### Subcollection: `tracks/{trackId}/weeks`
Defines week-by-week schedule specifications.
- **Document ID**: Formatted week key (e.g., `week_01`, `week_08`)
- **Fields**:
  - `weekNumber` (number, required): Calendar week index (`1` through `12`)
  - `title` (string, required): Topic for the week
  - `phase` (string, required): Pedagogical phase (e.g., `"Fundamentals"`, `"Studio Project"`)
  - `milestone` (string, required): Expected student deliverable
  - `progressType` (string, required): `"STUDENT"` (weeks 1–7) or `"PROJECT"` (weeks 8–12)
  - `active` (boolean, required): Schedule validity flag

---

## 2. `classes` (Root Collection)

Operational course cohorts running an active or historical track.

- **Document ID**: Unique offering key (e.g., `2026_SS1_A`)
- **Fields**:
  - `classId` (string, required): Matches document ID
  - `trackId` (string, required): Reference to parent track ID
  - `name` (string, required): Display cohort name (e.g., `"Software Studio 1 — Cohort A"`)
  - `startDate` (string, required): ISO-8601 formatted start date (`"YYYY-MM-DD"`)
  - `currentWeek` (number, required): Current instructional week (`1` to `totalWeeks`)
  - `status` (string, required): Lifecycle state (`"PLANNED"`, `"ACTIVE"`, `"COMPLETED"`, `"ARCHIVED"`)

---

## 3. `students` (Root Collection)

Permanent student profile records.

- **Document ID**: Student ID string (e.g., `ST_001`)
- **Fields**:
  - `studentId` (string, required): Unique institutional identifier
  - `name` (string, required): Full legal or official student name
  - `nickname` (string, required): Common or preferred name used in daily instruction
  - `active` (boolean, required): Whether student is currently active in the academy
  - `createdAt` (timestamp, required): Record creation time
  - `updatedAt` (timestamp, required): Last modification time

---

## 4. `enrollments` (Root Collection)

Associates a Student with a specific Class offering and hosts their competency assessment.

- **Document ID**: Deterministic composite `${classId}_${studentId}` (e.g., `2026_SS1_A_ST_001`)
- **Fields**:
  - `enrollmentId` (string, required): Matches document ID
  - `classId` (string, required): Foreign key to `classes`
  - `trackId` (string, required): Foreign key to `tracks`
  - `studentId` (string, required): Foreign key to `students`
  - `status` (string, required): Enrollment lifecycle (`"ACTIVE"`, `"COMPLETED"`, `"WITHDRAWN"`, `"ARCHIVED"`)
  - `enrolledAt` (timestamp, required): Time enrollment was created
  - `assessment` (map, optional): Embedded competency assessment data
    - `overallLevel` (string, required if assessed): Performance band (`"BEGINNER"`, `"DEVELOPING"`, `"PROFICIENT"`, `"ADVANCED"`)
    - `overallScore` (number, required if assessed): Average score across all criteria (`1.0` to `4.0`)
    - `assessedAt` (timestamp, required if assessed): Assessment timestamp
    - `assessedBy` (string, required if assessed): Evaluator user identifier
    - `strengths` (string, optional): Qualitative narrative on strong competencies
    - `nextSteps` (string, optional): Actionable recommendations for continued growth
    - `criteriaScores` (map, required if assessed): Map of 15 rubric criterion IDs to integer scores (`1` through `4`)

---

## 5. `studentProgress` (Root Collection)

Weekly individual student progress and checkpoint logs.

- **Document ID**: Deterministic composite `${classId}_${weekNumber}_${studentId}` (e.g., `2026_SS1_A_3_ST_001`)
- **Fields**:
  - `progressId` (string, required): Matches document ID
  - `classId` (string, required): Foreign key to `classes`
  - `weekNumber` (number, required): Instructional week index (`1` to `12`)
  - `studentId` (string, required): Foreign key to `students`
  - `status` (string, required): Progress status (`"ON_TRACK"`, `"NEEDS_ATTENTION"`, `"BLOCKED"`, `"COMPLETED"`)
  - `blocker` (string, optional): Blocker description (mandatory if status is `"BLOCKED"`)
  - `teacherNote` (string, optional): Instructional feedback note (maximum 500 characters)
  - `metrics` (map, optional): Track-specific rubric metrics defined by `ProgressMetricSchema`
  - `updatedAt` (timestamp, required): Last modification timestamp
  - `updatedBy` (string, required): User ID of the updating educator

---

## 6. `projects` (Root Collection)

Team entities created for collaborative project phases.

- **Document ID**: Auto-generated Cloud Firestore document ID
- **Fields**:
  - `projectId` (string, required): Matches document ID
  - `classId` (string, required): Foreign key to `classes`
  - `trackId` (string, required): Foreign key to `tracks`
  - `title` (string, required): Project deliverable title
  - `teamName` (string, required): Student team name
  - `memberIds` (array of strings, required): List of enrolled `studentId` values
  - `currentWeek` (number, required): Current progress week for the project
  - `overallStatus` (string, required): Aggregate status (`"NOT_STARTED"`, `"ON_TRACK"`, `"NEEDS_ATTENTION"`, `"BLOCKED"`, `"COMPLETED"`)
  - `active` (boolean, required): Active lifecycle flag (soft archive sets `active: false`)
  - `createdAt` (timestamp, required): Project creation timestamp
  - `updatedAt` (timestamp, required): Last update timestamp

---

## 7. `projectProgress` (Root Collection)

Weekly milestone deliverable evaluations for project teams.

- **Document ID**: Deterministic composite `${projectId}_${weekNumber}` (e.g., `proj_auto123_8`)
- **Fields**:
  - `progressId` (string, required): Matches document ID
  - `projectId` (string, required): Foreign key to `projects`
  - `classId` (string, required): Foreign key to `classes`
  - `weekNumber` (number, required): Milestone week index (`8` to `12`)
  - `overallStatus` (string, required): Milestone health (`"NOT_STARTED"`, `"ON_TRACK"`, `"NEEDS_ATTENTION"`, `"BLOCKED"`, `"COMPLETED"`)
  - `blocker` (string, optional): Blocker description (mandatory if status is `"BLOCKED"`)
  - `teacherNote` (string, optional): Evaluator guidance note (maximum 500 characters)
  - `metrics` (map, optional): Milestone checklist and metrics
  - `updatedAt` (timestamp, required): Evaluation timestamp
  - `updatedBy` (string, required): User ID of evaluating teacher
