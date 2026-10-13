CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE frontline_snapshot (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    snapshot_date DATE NOT NULL UNIQUE,
    front_line    geometry(LineString, 4326) NOT NULL,
    extra_areas   geometry(MultiPolygon, 4326),
    source_map    TEXT NOT NULL,
    note          TEXT,

    CONSTRAINT frontline_snapshot_source_map_not_blank_check
        CHECK (btrim(source_map) <> ''),
    CONSTRAINT frontline_snapshot_west_to_east_check
        CHECK (ST_X(ST_StartPoint(front_line)) < ST_X(ST_EndPoint(front_line))),
    CONSTRAINT frontline_snapshot_extra_areas_valid_check
        CHECK (extra_areas IS NULL OR ST_IsValid(extra_areas))
);