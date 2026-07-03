--@formatter:off
DECLARE
BEGIN
    EXECUTE IMMEDIATE '
        CREATE TABLE @@table_name@@ (
            bucket_start         TIMESTAMP       NOT NULL,
            bucket_duration_ms   NUMBER(19)      NOT NULL,
            dal_name             VARCHAR2(255)   NOT NULL,
            operation            VARCHAR2(16)    NOT NULL,
            instance_id          VARCHAR2(36)    NOT NULL,
            invocation_count     NUMBER(19)      NOT NULL,
            error_count          NUMBER(19)      NOT NULL,
            total_duration_nanos NUMBER(19)      NOT NULL,
            min_duration_nanos   NUMBER(19)      NOT NULL,
            max_duration_nanos   NUMBER(19)      NOT NULL,
            histogram_counts     VARCHAR2(2048)  NOT NULL,
            CONSTRAINT @@table_name@@_pk
                PRIMARY KEY (bucket_start, dal_name, operation, instance_id)
        )';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;
END;
/
DECLARE
BEGIN
    EXECUTE IMMEDIATE '
        CREATE INDEX @@table_name@@_ix1
            ON @@table_name@@ (dal_name, bucket_start)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1408 THEN RAISE; END IF;
END;
/
