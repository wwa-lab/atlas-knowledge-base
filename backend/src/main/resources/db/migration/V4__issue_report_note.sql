-- Reporter-authored context is stored separately from allow-listed diagnostics.
-- It is bounded by the application and is never copied to ordinary audit details.
ALTER TABLE issue_report ADD report_note CLOB;
