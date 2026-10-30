-- Run only on the disposable migration-validation database, after the feature migration.
-- The two existing-table fixtures need only id columns. Nothing is retained after ROLLBACK.
BEGIN;
INSERT INTO workflow_requests(id) VALUES (9001);
INSERT INTO grouped_approval_versions(id,created_at,updated_at,version,family_key,revision,name,state,enabled)
VALUES (9001,now(),now(),0,'constraint-test',1,'Constraint test','PUBLISHED',true);
INSERT INTO grouped_approval_cycles(id,created_at,updated_at,version,request_id,cycle_no,path_version_id,path_name,state,current_ordinal)
VALUES (9001,now(),now(),0,9001,1,9001,'Constraint test','ACTIVE',1);
DO $$
DECLARE duplicate_rejected boolean := false;
BEGIN
    BEGIN
        INSERT INTO grouped_approval_cycles(created_at,updated_at,version,request_id,cycle_no,path_version_id,path_name,state,current_ordinal)
        VALUES (now(),now(),0,9001,2,9001,'Constraint test','ACTIVE',1);
    EXCEPTION WHEN unique_violation THEN duplicate_rejected := true;
    END;
    IF NOT duplicate_rejected THEN RAISE EXCEPTION 'Two active cycles were accepted'; END IF;
END $$;
INSERT INTO grouped_approval_definitions(id,created_at,updated_at,version,path_version_id,ordinal,name,policy)
VALUES (9001,now(),now(),0,9001,1,'Review','ALL');
INSERT INTO grouped_approval_definition_members(definition_id,member_order,employee_id) VALUES (9001,0,11);
DO $$
DECLARE duplicate_rejected boolean := false;
BEGIN
    BEGIN
        INSERT INTO grouped_approval_definition_members(definition_id,member_order,employee_id) VALUES (9001,1,11);
    EXCEPTION WHEN unique_violation THEN duplicate_rejected := true;
    END;
    IF NOT duplicate_rejected THEN RAISE EXCEPTION 'Duplicate group member was accepted'; END IF;
END $$;
ROLLBACK;
