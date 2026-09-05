SET NAMES utf8mb4;

DELIMITER //

DROP PROCEDURE IF EXISTS add_investment_product_column_if_missing//
CREATE PROCEDURE add_investment_product_column_if_missing(
  IN column_name_param varchar(64),
  IN column_definition_param text
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'investment_product'
      AND column_name = column_name_param
  ) THEN
    SET @alter_sql = CONCAT('ALTER TABLE `investment_product` ADD COLUMN ', column_definition_param);
    PREPARE alter_statement FROM @alter_sql;
    EXECUTE alter_statement;
    DEALLOCATE PREPARE alter_statement;
  END IF;
END//

DELIMITER ;

CALL add_investment_product_column_if_missing('issuer_name', "`issuer_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '本行'");
CALL add_investment_product_column_if_missing('product_series', "`product_series` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL");
CALL add_investment_product_column_if_missing('currency', "`currency` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CNY'");
CALL add_investment_product_column_if_missing('expected_return_desc', "`expected_return_desc` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL");
CALL add_investment_product_column_if_missing('fee_desc', "`fee_desc` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL");
CALL add_investment_product_column_if_missing('redemption_rule', "`redemption_rule` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL");
CALL add_investment_product_column_if_missing('sale_start_at', "`sale_start_at` datetime DEFAULT NULL");
CALL add_investment_product_column_if_missing('sale_end_at', "`sale_end_at` datetime DEFAULT NULL");
CALL add_investment_product_column_if_missing('suitability_profile', "`suitability_profile` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL");
CALL add_investment_product_column_if_missing('asset_allocation', "`asset_allocation` json DEFAULT NULL");
CALL add_investment_product_column_if_missing('compliance_note', "`compliance_note` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL");

DROP PROCEDURE IF EXISTS add_investment_product_column_if_missing;
