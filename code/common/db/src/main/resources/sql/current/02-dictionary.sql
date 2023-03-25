
CREATE TABLE IF NOT EXISTS REF_DICTIONARY (
    TYPE VARCHAR(16),
    WORD VARCHAR(255),
    DEFINITION VARCHAR(255)
)
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS REF_WIKI_ARTICLE (
    NAME VARCHAR(255) PRIMARY KEY,
    REF_NAME VARCHAR(255) COMMENT "If this is a redirect, it redirects to this REF_WIKI_ARTICLE.NAME",
    ENTRY LONGBLOB
)
ROW_FORMAT=DYNAMIC
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS REF_DICTIONARY_WORD ON REF_DICTIONARY (WORD);
