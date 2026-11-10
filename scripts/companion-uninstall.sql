-- OIEP optional AI companion component: remove every database object it owns.
--
-- Run this BEFORE deleting/removing the component code (see docs/COMPANION.md, "Removing the
-- component"). The triggers write the companion event log on every report insert, status
-- change and delete. If the component code is removed but the triggers are left in place, every
-- core delete/moderation statement fails because its trigger writes to a table nobody maintains
-- (and, once the CREATE TABLE statement is gone, the table no longer exists at all).
--
-- Safe to run repeatedly: every statement uses IF EXISTS.
-- The reports table and all report data are not touched.
--
-- Usage:
--   sqlite3 <OIEP_DB_PATH> < scripts/companion-uninstall.sql
-- (with the packaged Docker setup: docker compose exec app sqlite3 /data/oiep.db \
--      < scripts/companion-uninstall.sql, or copy the file into the container first)

DROP TRIGGER IF EXISTS companion_report_events_ai;
DROP TRIGGER IF EXISTS companion_report_events_au;
DROP TRIGGER IF EXISTS companion_report_events_ad;
DROP INDEX IF EXISTS companion_report_events_order;
DROP TABLE IF EXISTS companion_report_events;
