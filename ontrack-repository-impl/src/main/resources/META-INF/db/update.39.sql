-- 39. Entity data store (#518)

CREATE TABLE ENTITY_DATA_STORE (
  ID               INTEGER        NOT NULL AUTO_INCREMENT,
  PROJECT          INTEGER        NULL,
  BRANCH           INTEGER        NULL,
  PROMOTION_LEVEL  INTEGER        NULL,
  VALIDATION_STAMP INTEGER        NULL,
  BUILD            INTEGER        NULL,
  PROMOTION_RUN    INTEGER        NULL,
  VALIDATION_RUN   INTEGER        NULL,
  CREATION         VARCHAR(24)    NOT NULL,
  CREATOR          VARCHAR(40)    NOT NULL,
  CATEGORY         VARCHAR(150)   NOT NULL,
  NAME             VARCHAR(150)   NOT NULL,
  GROUPID          VARCHAR(150)   NULL,
  JSON             VARCHAR(10000) NOT NULL,
  CONSTRAINT ENTITY_DATA_STORE_PK PRIMARY KEY (ID),
  CONSTRAINT ENTITY_DATA_STORE_FK_PROJECT FOREIGN KEY (PROJECT) REFERENCES PROJECTS (ID)
  ON DELETE CASCADE,
  CONSTRAINT ENTITY_DATA_STORE_FK_BRANCH FOREIGN KEY (BRANCH) REFERENCES BRANCHES (ID)
  ON DELETE CASCADE,
  CONSTRAINT ENTITY_DATA_STORE_FK_PROMOTION_LEVEL FOREIGN KEY (PROMOTION_LEVEL) REFERENCES PROMOTION_LEVELS (ID)
  ON DELETE CASCADE,
  CONSTRAINT ENTITY_DATA_STORE_FK_VALIDATION_STAMP FOREIGN KEY (VALIDATION_STAMP) REFERENCES VALIDATION_STAMPS (ID)
  ON DELETE CASCADE,
  CONSTRAINT ENTITY_DATA_STORE_FK_BUILD FOREIGN KEY (BUILD) REFERENCES BUILDS (ID)
  ON DELETE CASCADE,
  CONSTRAINT ENTITY_DATA_STORE_FK_PROMOTION_RUN FOREIGN KEY (PROMOTION_RUN) REFERENCES PROMOTION_RUNS (ID)
  ON DELETE CASCADE,
  CONSTRAINT ENTITY_DATA_STORE_FK_VALIDATION_RUN FOREIGN KEY (VALIDATION_RUN) REFERENCES VALIDATION_RUNS (ID)
  ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ENTITY_DATA_STORE_IX_GROUP
  ON ENTITY_DATA_STORE (CATEGORY, GROUPID);
CREATE INDEX IF NOT EXISTS ENTITY_DATA_STORE_IX_KEY
  ON ENTITY_DATA_STORE (CATEGORY, NAME);
CREATE INDEX IF NOT EXISTS ENTITY_DATA_STORE_IX_ALL
  ON ENTITY_DATA_STORE (CATEGORY, NAME, CREATION);
CREATE INDEX IF NOT EXISTS ENTITY_DATA_STORE_IX_CREATION
  ON ENTITY_DATA_STORE (CREATION);

CREATE TABLE ENTITY_DATA_STORE_AUDIT (
  ID         INTEGER     NOT NULL AUTO_INCREMENT,
  RECORD_ID  INTEGER     NOT NULL,
  AUDIT_TYPE VARCHAR(10) NOT NULL,
  TIMESTAMP  VARCHAR(24) NOT NULL,
  USER       VARCHAR(40) NOT NULL,
  CONSTRAINT ENTITY_DATA_STORE_AUDIT_PK PRIMARY KEY (ID),
  CONSTRAINT ENTITY_DATA_STORE_AUDIT_FK_ENTITY_DATA_STORE FOREIGN KEY (RECORD_ID) REFERENCES ENTITY_DATA_STORE (ID)
  ON DELETE CASCADE
);
