-- 40. Run info (#600)

CREATE TABLE RUN_INFO (
  ID             INTEGER      NOT NULL AUTO_INCREMENT,
  BUILD          INTEGER      NULL,
  VALIDATION_RUN INTEGER      NULL,
  SOURCE_TYPE    VARCHAR(20)  NULL,
  SOURCE_URI     VARCHAR(500) NULL,
  TRIGGER_TYPE   VARCHAR(20)  NULL,
  TRIGGER_DATA   VARCHAR(120) NULL,
  RUN_TIME       INTEGER      NULL,
  CREATION       VARCHAR(24)  NOT NULL,
  CREATOR        VARCHAR(40)  NOT NULL,
  CONSTRAINT RUN_INFO_PK PRIMARY KEY (ID),
  CONSTRAINT RUN_INFO_FK_BUILD FOREIGN KEY (BUILD) REFERENCES BUILDS (ID) ON DELETE CASCADE,
  CONSTRAINT RUN_INFO_FK_VALIDATION_RUN FOREIGN KEY (VALIDATION_RUN) REFERENCES VALIDATION_RUNS (ID) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS RUN_INFO_IX_SOURCE_TYPE
  ON RUN_INFO (SOURCE_TYPE);
CREATE INDEX IF NOT EXISTS RUN_INFO_IX_SOURCE
  ON RUN_INFO (SOURCE_TYPE, SOURCE_URI);
CREATE INDEX IF NOT EXISTS RUN_INFO_IX_TRIGGER_TYPE
  ON RUN_INFO (TRIGGER_TYPE);
CREATE INDEX IF NOT EXISTS RUN_INFO_IX_TRIGGER
  ON RUN_INFO (TRIGGER_TYPE, TRIGGER_DATA);