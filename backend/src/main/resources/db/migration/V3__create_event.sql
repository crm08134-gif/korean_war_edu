CREATE TABLE source_doc (
                          id      TEXT PRIMARY KEY,
                          title   TEXT NOT NULL,
                          author  TEXT,
                          url     TEXT,
                          license TEXT NOT NULL
);

CREATE TABLE event (
                     id              TEXT PRIMARY KEY,
                     title           TEXT NOT NULL,
                     event_date      DATE NOT NULL,
                     date_precision  TEXT NOT NULL,
                     place_name      TEXT NOT NULL,
                     geom            geometry(Point, 4326) NOT NULL,
                     category        TEXT NOT NULL,
                     hook            TEXT NOT NULL,
                     body            TEXT NOT NULL,
                     confidence_tier TEXT NOT NULL,
                     status          TEXT NOT NULL,

                     CONSTRAINT event_date_in_war_check
                       CHECK (event_date BETWEEN DATE '1950-06-25' AND DATE '1953-07-27'),
                     CONSTRAINT event_geom_in_korea_check
                       CHECK (ST_X(geom) BETWEEN 124 AND 132 AND ST_Y(geom) BETWEEN 33 AND 43.5),
                     CONSTRAINT event_date_precision_check
                       CHECK (date_precision IN ('day', 'month')),
                     CONSTRAINT event_confidence_tier_check
                       CHECK (confidence_tier IN ('confirmed', 'consensus', 'disputed', 'myth')),
                     CONSTRAINT event_status_check
                       CHECK (status IN ('draft', 'reviewed'))
);

CREATE TABLE event_claim (
                           id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                           event_id TEXT NOT NULL REFERENCES event (id) ON DELETE CASCADE,
                           text     TEXT NOT NULL
);

CREATE TABLE claim_evidence (
                              id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                              claim_id  BIGINT NOT NULL REFERENCES event_claim (id) ON DELETE CASCADE,
                              source_id TEXT NOT NULL REFERENCES source_doc (id),
                              locator   TEXT NOT NULL,
                              quote     TEXT
);
