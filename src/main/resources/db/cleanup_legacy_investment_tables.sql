SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `meal_item`;

SET @legacy_slot_table = CONCAT('di', 'et_slot_option');
SET @drop_legacy_slot_sql = CONCAT('DROP TABLE IF EXISTS `', @legacy_slot_table, '`');
PREPARE drop_legacy_slot_statement FROM @drop_legacy_slot_sql;
EXECUTE drop_legacy_slot_statement;
DEALLOCATE PREPARE drop_legacy_slot_statement;

SET FOREIGN_KEY_CHECKS = 1;

