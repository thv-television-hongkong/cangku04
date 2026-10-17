-- The app has full privileges only on its own database (created by the MySQL image).
-- SHOW_ROUTINE is required by mysqldump --routines with MySQL 8.0.20+.
GRANT SHOW_ROUTINE ON *.* TO 'campus'@'%';
