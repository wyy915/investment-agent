SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `investment_db`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

DELIMITER //

DROP PROCEDURE IF EXISTS move_table_if_target_missing//
CREATE PROCEDURE move_table_if_target_missing(
  IN old_schema_name varchar(64),
  IN old_table_name varchar(64),
  IN new_schema_name varchar(64),
  IN new_table_name varchar(64)
)
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = old_schema_name
      AND table_name = old_table_name
  ) AND NOT EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = new_schema_name
      AND table_name = new_table_name
  ) THEN
    SET @move_sql = CONCAT(
      'RENAME TABLE `', old_schema_name, '`.`', old_table_name,
      '` TO `', new_schema_name, '`.`', new_table_name, '`'
    );
    PREPARE move_statement FROM @move_sql;
    EXECUTE move_statement;
    DEALLOCATE PREPARE move_statement;
  END IF;
END//

DELIMITER ;

SET @old_schema_name = CONCAT('di', 'et_db');

CALL move_table_if_target_missing(@old_schema_name, CONCAT('di', 'et_messages'), 'investment_db', 'investment_messages');
CALL move_table_if_target_missing(@old_schema_name, CONCAT('di', 'et_request_trace'), 'investment_db', 'investment_request_trace');
CALL move_table_if_target_missing(@old_schema_name, CONCAT('di', 'et_sessions'), 'investment_db', 'investment_sessions');
CALL move_table_if_target_missing(@old_schema_name, 'bank_branch', 'investment_db', 'bank_branch');
CALL move_table_if_target_missing(@old_schema_name, 'bank_employee', 'investment_db', 'bank_employee');
CALL move_table_if_target_missing(@old_schema_name, 'investment_slot_option', 'investment_db', 'investment_slot_option');
CALL move_table_if_target_missing(@old_schema_name, 'investment_product', 'investment_db', 'investment_product');
CALL move_table_if_target_missing(@old_schema_name, 'recommend_feedback', 'investment_db', 'recommend_feedback');

DROP PROCEDURE IF EXISTS move_table_if_target_missing;
