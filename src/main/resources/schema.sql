-- OIEP schema (SQLite). Idempotent: safe to run on every boot.
-- question_types stores a comma separated list of normalized tag keys.
CREATE TABLE IF NOT EXISTS reports (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    company        TEXT    NOT NULL,
    role           TEXT    NOT NULL,
    round          TEXT    NOT NULL,
    work_mode      TEXT    NOT NULL DEFAULT '',
    location       TEXT    NOT NULL DEFAULT '',
    outcome        TEXT    NOT NULL,
    heard_back     TEXT    NOT NULL DEFAULT '',
    question_types TEXT    NOT NULL DEFAULT '',
    salary         TEXT    NOT NULL DEFAULT '',
    vibe           TEXT    NOT NULL DEFAULT '',
    story          TEXT    NOT NULL,
    helpful        INTEGER NOT NULL DEFAULT 0,
    created_at     TEXT    NOT NULL,
    status         TEXT    NOT NULL DEFAULT 'published'
);

CREATE INDEX IF NOT EXISTS idx_reports_created_at ON reports (created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_reports_helpful ON reports (helpful DESC, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reports_status ON reports (status, created_at DESC, id DESC);
