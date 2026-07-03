--@formatter:off
CREATE TABLE IF NOT EXISTS @@table_name@@ (
    bucket_start         TIMESTAMP     NOT NULL,
    bucket_duration_ms   BIGINT        NOT NULL,
    dal_name             VARCHAR(255)  NOT NULL,
    operation            VARCHAR(16)   NOT NULL,
    instance_id          VARCHAR(36)   NOT NULL,
    invocation_count     BIGINT        NOT NULL,
    error_count          BIGINT        NOT NULL,
    total_duration_nanos BIGINT        NOT NULL,
    min_duration_nanos   BIGINT        NOT NULL,
    max_duration_nanos   BIGINT        NOT NULL,
    histogram_counts     VARCHAR(2048) NOT NULL,
    CONSTRAINT @@table_name@@_pk
        PRIMARY KEY (bucket_start, dal_name, operation, instance_id)
);

CREATE INDEX IF NOT EXISTS @@table_name@@_ix1
    ON @@table_name@@ (dal_name, bucket_start);
