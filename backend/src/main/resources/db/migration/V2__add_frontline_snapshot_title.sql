DELETE FROM frontline_snapshot;

ALTER TABLE frontline_snapshot
    ADD COLUMN title TEXT NOT NULL,
    ADD CONSTRAINT frontline_snapshot_title_not_blank_check
        CHECK (btrim(title) <> '');
