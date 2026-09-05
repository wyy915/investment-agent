SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `meal_item`;

DROP TABLE IF EXISTS `investment_messages`;
CREATE TABLE `investment_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `intent` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `agent_trace_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `idx_message_session` (`session_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `investment_request_trace`;
CREATE TABLE `investment_request_trace` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `trace_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_count` int NOT NULL DEFAULT 0,
  `duration_ms` bigint DEFAULT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `trace_json` json NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `expected_intent` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `expected_slots` json DEFAULT NULL,
  `expected_clarify_action` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `expected_compliance_result` tinyint DEFAULT NULL,
  `expected_product_ids` json DEFAULT NULL,
  `labeled_by` bigint DEFAULT NULL,
  `labeled_at` datetime DEFAULT NULL,
  `label_note` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_request_trace` (`trace_id`),
  INDEX `idx_request_trace_session` (`session_id`, `created_at`),
  INDEX `idx_request_trace_user` (`user_id`, `created_at`),
  INDEX `idx_request_trace_status` (`status`, `created_at`),
  INDEX `idx_request_trace_label` (`expected_intent`, `labeled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `investment_sessions`;
CREATE TABLE `investment_sessions` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `phase` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `slots` json NOT NULL,
  `last_recommendations` json NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `idx_session_user` (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `bank_employee`;
DROP TABLE IF EXISTS `bank_branch`;
CREATE TABLE `bank_branch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `branch_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `branch_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `parent_branch_id` bigint DEFAULT NULL,
  `branch_level` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '分行',
  `region_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_branch_code` (`branch_code`),
  INDEX `idx_branch_parent` (`parent_branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `bank_employee` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_no` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `employee_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `branch_id` bigint NOT NULL,
  `role_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '客户经理',
  `title` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_employee_no` (`employee_no`),
  INDEX `idx_employee_branch` (`branch_id`),
  INDEX `idx_employee_role` (`role_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `bank_branch` (`branch_code`, `branch_name`, `parent_branch_id`, `branch_level`, `region_name`, `status`, `created_at`, `updated_at`) VALUES
('HQ', '总行', NULL, '总行', '全国', 'ACTIVE', NOW(), NOW()),
('BJ-01', '北京分行', 1, '分行', '华北', 'ACTIVE', NOW(), NOW()),
('SH-01', '上海分行', 1, '分行', '华东', 'ACTIVE', NOW(), NOW());

INSERT INTO `bank_employee` (`employee_no`, `employee_name`, `branch_id`, `role_type`, `title`, `status`, `created_at`, `updated_at`) VALUES
('EMP-0001', '张晨', 2, '客户经理', '高级客户经理', 'ACTIVE', NOW(), NOW()),
('EMP-0002', '李娜', 3, '客户经理', '客户经理', 'ACTIVE', NOW(), NOW()),
('EMP-1001', '产品运营', 1, '产品人员', '产品运营', 'ACTIVE', NOW(), NOW());

DROP TABLE IF EXISTS `investment_slot_option`;
CREATE TABLE `investment_slot_option` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `slot_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `option_value` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_investment_slot_option` (`slot_name`, `option_value`),
  INDEX `idx_slot_enabled` (`slot_name`, `enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `investment_slot_option` (`slot_name`, `option_value`, `sort_order`, `enabled`, `created_at`, `updated_at`) VALUES
('investmentAmount','5万以内',10,1,NOW(),NOW()),
('investmentAmount','5万-20万',20,1,NOW(),NOW()),
('investmentAmount','20万-100万',30,1,NOW(),NOW()),
('investmentAmount','100万以上',40,1,NOW(),NOW()),
('investmentHorizon','随时可用',10,1,NOW(),NOW()),
('investmentHorizon','1个月',20,1,NOW(),NOW()),
('investmentHorizon','3个月',30,1,NOW(),NOW()),
('investmentHorizon','6个月',40,1,NOW(),NOW()),
('investmentHorizon','1年以上',50,1,NOW(),NOW()),
('investmentHorizon','组合配置',60,1,NOW(),NOW()),
('riskPreference','保守',10,1,NOW(),NOW()),
('riskPreference','稳健',20,1,NOW(),NOW()),
('riskPreference','平衡',30,1,NOW(),NOW()),
('riskPreference','进取',40,1,NOW(),NOW()),
('liquidityNeed','随时可用',10,1,NOW(),NOW()),
('liquidityNeed','短期可能用钱',20,1,NOW(),NOW()),
('liquidityNeed','可封闭持有',30,1,NOW(),NOW()),
('returnExpectation','高于活期',10,1,NOW(),NOW()),
('returnExpectation','稳健收益',20,1,NOW(),NOW()),
('returnExpectation','接受波动换收益',30,1,NOW(),NOW()),
('productType','存款',10,1,NOW(),NOW()),
('productType','现金管理',20,1,NOW(),NOW()),
('productType','固定收益类',30,1,NOW(),NOW()),
('productType','混合类',40,1,NOW(),NOW()),
('productType','基金',50,1,NOW(),NOW()),
('productType','保险',60,1,NOW(),NOW()),
('customerProfile','新手',10,1,NOW(),NOW()),
('customerProfile','退休客户',20,1,NOW(),NOW()),
('customerProfile','企业主',30,1,NOW(),NOW()),
('customerProfile','工资结余客户',40,1,NOW(),NOW()),
('restriction','不买基金',10,1,NOW(),NOW()),
('restriction','不接受封闭',20,1,NOW(),NOW()),
('restriction','只看低风险',30,1,NOW(),NOW()),
('restriction','不能亏本金',40,1,NOW(),NOW());

DROP TABLE IF EXISTS `financial_product`;
DROP TABLE IF EXISTS `investment_product`;
CREATE TABLE `investment_product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_user_id` bigint DEFAULT NULL,
  `scope_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '全行',
  `scope_branch_id` bigint DEFAULT NULL,
  `scope_manager_id` bigint DEFAULT NULL,
  `issuer_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '本行',
  `product_series` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `product_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `risk_level` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `investment_horizon` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `liquidity_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `min_amount` decimal(18,2) DEFAULT NULL,
  `currency` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CNY',
  `expected_return_desc` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `return_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fee_desc` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `redemption_rule` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sale_start_at` datetime DEFAULT NULL,
  `sale_end_at` datetime DEFAULT NULL,
  `suitability_profile` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `asset_allocation` json DEFAULT NULL,
  `target_customer` json NOT NULL,
  `tags` json NOT NULL,
  `compliance_note` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'AVAILABLE',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `idx_product_scope` (`scope_type`, `scope_branch_id`, `scope_manager_id`),
  INDEX `idx_product_source` (`source_type`, `owner_user_id`),
  INDEX `idx_product_status` (`status`),
  INDEX `idx_product_risk_horizon` (`risk_level`, `investment_horizon`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `investment_product` (`source_type`, `owner_user_id`, `scope_type`, `scope_branch_id`, `scope_manager_id`, `issuer_name`, `product_series`, `product_name`, `product_code`, `product_type`, `risk_level`, `investment_horizon`, `liquidity_type`, `min_amount`, `currency`, `expected_return_desc`, `return_type`, `fee_desc`, `redemption_rule`, `sale_start_at`, `sale_end_at`, `suitability_profile`, `asset_allocation`, `target_customer`, `tags`, `compliance_note`, `status`, `created_at`, `updated_at`) VALUES
('PUBLIC', NULL, '全行', NULL, NULL, '本行理财子公司', '现金管理系列', '示例日日开放现金管理A', 'DEMO-CASH-001', '现金管理', '保守', '随时可用', '随时可用', 1.00, 'CNY', '业绩比较基准随市场调整', '高于活期', '示例费率：销售服务费按产品说明书执行', '工作日可申赎，到账时间以产品说明书为准', DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 365 DAY), '适合保守型及以上客户作流动性管理参考', JSON_OBJECT('cash','70%','bond','30%'), JSON_ARRAY('新手','工资结余客户','退休客户'), JSON_ARRAY('开放式','现金管理'), '不承诺收益，不保证本金，实际风险以产品说明书和风险揭示书为准。', 'AVAILABLE', NOW(), NOW()),
('PUBLIC', NULL, '全行', NULL, NULL, '本行理财子公司', '短债稳健系列', '示例短债稳健30天', 'DEMO-BOND-030', '固定收益类', '稳健', '1个月', '短期可能用钱', 1000.00, 'CNY', '参考短债类产品波动特征，不代表实际收益', '稳健收益', '示例费率：管理费、托管费按说明书执行', '最短持有期后可申请赎回', DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_ADD(NOW(), INTERVAL 180 DAY), '适合稳健型及以上客户短期限资金安排参考', JSON_OBJECT('bond','85%','cash','15%'), JSON_ARRAY('新手','工资结余客户'), JSON_ARRAY('短期限','债券'), '需结合客户风险测评和资金使用计划，不构成最终销售建议。', 'AVAILABLE', NOW(), NOW()),
('PUBLIC', NULL, '全行', NULL, NULL, '本行理财子公司', '短债稳健系列', '示例短债稳健90天', 'DEMO-BOND-090', '固定收益类', '稳健', '3个月', '短期可能用钱', 10000.00, 'CNY', '业绩比较基准为区间表达，非收益承诺', '稳健收益', '示例费率：按产品说明书执行', '持有满90天后按开放日规则赎回', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 240 DAY), '适合稳健型及以上客户配置短期限固收资产参考', JSON_OBJECT('bond','88%','cash','12%'), JSON_ARRAY('工资结余客户','企业主'), JSON_ARRAY('短期限','债券'), '产品净值可能波动，推荐前需核验客户适当性。', 'AVAILABLE', NOW(), NOW()),
('PUBLIC', NULL, '全行', NULL, NULL, '本行理财子公司', '固收开放系列', '示例半年灵活稳健持有', 'DEMO-FI-180-FLEX', '固定收益类', '稳健', '6个月', '短期可能用钱', 10000.00, 'CNY', '业绩比较基准为区间表达，非收益承诺', '稳健收益', '示例费率：按产品说明书执行', '每月开放一次赎回，具体到账以说明书为准', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 240 DAY), '适合半年左右资金安排且保留一定流动性需求的稳健型客户', JSON_OBJECT('bond','82%','cash','18%'), JSON_ARRAY('工资结余客户','退休客户'), JSON_ARRAY('开放式','债券'), '开放规则不等于随时到账，推荐时需说明赎回周期。', 'AVAILABLE', NOW(), NOW()),
('PUBLIC', NULL, '全行', NULL, NULL, '本行理财子公司', '固收增强系列', '示例稳健固收180天', 'DEMO-FI-180', '固定收益类', '稳健', '6个月', '可封闭持有', 10000.00, 'CNY', '业绩比较基准为区间表达，非收益承诺', '稳健收益', '示例费率：管理费、托管费按说明书执行', '封闭期内不可赎回，到期后按规则兑付', DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_ADD(NOW(), INTERVAL 180 DAY), '适合可接受6个月封闭安排的稳健型及以上客户', JSON_OBJECT('bond','80%','non_standard','10%','cash','10%'), JSON_ARRAY('退休客户','工资结余客户'), JSON_ARRAY('封闭','低波动','债券'), '封闭期与客户资金安排必须匹配，不得弱化流动性风险。', 'AVAILABLE', NOW(), NOW()),
('PUBLIC', NULL, '全行', NULL, NULL, '本行理财子公司', '固收增强系列', '示例一年期固收优选', 'DEMO-FI-365', '固定收益类', '稳健', '1年以上', '可封闭持有', 50000.00, 'CNY', '业绩比较基准为区间表达，非收益承诺', '稳健收益', '示例费率：按说明书执行', '封闭运作，到期或开放期按规则赎回', DATE_SUB(NOW(), INTERVAL 45 DAY), DATE_ADD(NOW(), INTERVAL 90 DAY), '适合中长期稳健配置客户参考', JSON_OBJECT('bond','82%','cash','8%','preferred','10%'), JSON_ARRAY('退休客户','企业主'), JSON_ARRAY('封闭','债券'), '仅用于内部筛选参考，需向客户充分揭示期限和净值波动。', 'AVAILABLE', NOW(), NOW()),
('PUBLIC', NULL, '全行', NULL, NULL, '本行理财子公司', '平衡配置系列', '示例平衡配置一年持有', 'DEMO-MIX-365', '混合类', '平衡', '1年以上', '可封闭持有', 50000.00, 'CNY', '权益和固收资产占比会影响净值波动', '接受波动换收益', '示例费率：按产品说明书执行', '一年持有期，到期后可按开放日赎回', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 210 DAY), '适合平衡型及以上、能接受一定波动的客户', JSON_OBJECT('bond','60%','equity','25%','cash','15%'), JSON_ARRAY('企业主'), JSON_ARRAY('封闭','波动收益','权益'), '不得向保守或稳健客户推荐超出其风险承受能力的产品。', 'AVAILABLE', NOW(), NOW()),
('PUBLIC', NULL, '全行', NULL, NULL, '本行代销', '基金优选系列', '示例债券基金优选', 'DEMO-FUND-BOND', '基金', '平衡', '1年以上', '短期可能用钱', 100.00, 'CNY', '基金净值随市场波动，无固定收益', '接受波动换收益', '示例费率：申购赎回费以基金合同为准', '开放式基金，赎回到账以基金规则为准', DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_ADD(NOW(), INTERVAL 365 DAY), '适合平衡型及以上且了解基金波动的客户', JSON_OBJECT('fund','100%'), JSON_ARRAY('企业主','工资结余客户'), JSON_ARRAY('基金','开放式','波动收益'), '基金产品不保证本金和收益，需完成代销适当性匹配。', 'AVAILABLE', NOW(), NOW()),
('PUBLIC', NULL, '全行', NULL, NULL, '本行代销', '保障配置系列', '示例稳健增额终身寿', 'DEMO-INS-LIFE', '保险', '稳健', '1年以上', '可封闭持有', 10000.00, 'CNY', '保险利益以合同条款为准', '稳健收益', '示例费率：费用结构以保险合同为准', '退保可能产生损失，现金价值以合同为准', DATE_SUB(NOW(), INTERVAL 90 DAY), DATE_ADD(NOW(), INTERVAL 365 DAY), '适合有长期保障和资产规划需求的稳健型及以上客户', JSON_OBJECT('insurance','100%'), JSON_ARRAY('退休客户','企业主'), JSON_ARRAY('保险','长期','封闭'), '保险不是银行存款，需按保险销售规则完成双录和风险提示。', 'AVAILABLE', NOW(), NOW()),
('PUBLIC', NULL, '分行', 2, NULL, '北京分行', '北京分行特色系列', '示例北京分行季季稳', 'DEMO-BJ-QTR', '固定收益类', '稳健', '3个月', '短期可能用钱', 5000.00, 'CNY', '业绩比较基准为区间表达，非收益承诺', '稳健收益', '示例费率：按产品说明书执行', '季度开放，开放期内可申请赎回', DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 120 DAY), '适合北京分行稳健型客户短期限资金参考', JSON_OBJECT('bond','86%','cash','14%'), JSON_ARRAY('工资结余客户','退休客户'), JSON_ARRAY('分行特色','短期限','债券'), '分行范围产品需确认客户所属机构和销售权限。', 'AVAILABLE', NOW(), NOW()),
('PUBLIC', NULL, '分行', 3, NULL, '上海分行', '上海分行特色系列', '示例上海分行稳享半年', 'DEMO-SH-180', '固定收益类', '稳健', '6个月', '可封闭持有', 20000.00, 'CNY', '业绩比较基准为区间表达，非收益承诺', '稳健收益', '示例费率：按产品说明书执行', '封闭180天，到期按规则兑付', DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_ADD(NOW(), INTERVAL 180 DAY), '适合上海分行可接受封闭期的稳健型客户', JSON_OBJECT('bond','84%','cash','16%'), JSON_ARRAY('退休客户','工资结余客户'), JSON_ARRAY('分行特色','封闭','债券'), '推荐前需确认分行销售权限和客户适当性。', 'AVAILABLE', NOW(), NOW()),
('PUBLIC', NULL, '全行', NULL, NULL, '本行理财子公司', '进取配置系列', '示例进取权益优选', 'DEMO-EQ-365', '基金', '进取', '1年以上', '可封闭持有', 10000.00, 'CNY', '权益市场波动较大，不代表实际收益', '接受波动换收益', '示例费率：按产品合同执行', '建议长期持有，赎回按开放日规则执行', DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_ADD(NOW(), INTERVAL 365 DAY), '仅适合进取型且能承受较大净值波动的客户', JSON_OBJECT('equity','80%','cash','20%'), JSON_ARRAY('企业主'), JSON_ARRAY('基金','权益','波动收益'), '不得推荐给风险承受能力不足客户。', 'AVAILABLE', NOW(), NOW()),
('PERSONAL', 1, '客户经理', NULL, 1, '北京分行', '客户经理精选', '示例张晨现金快线', 'DEMO-MGR-CASH', '现金管理', '保守', '随时可用', '随时可用', 1.00, 'CNY', '业绩比较基准随市场调整', '高于活期', '示例费率：按说明书执行', '工作日可申赎', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 365 DAY), '适合保守型客户闲置资金流动性管理参考', JSON_OBJECT('cash','75%','bond','25%'), JSON_ARRAY('新手','工资结余客户'), JSON_ARRAY('客户经理精选','开放式'), '仅用于客户经理内部筛选，需核验正式产品材料。', 'AVAILABLE', NOW(), NOW()),
('PERSONAL', 1, '客户经理', NULL, 1, '北京分行', '客户经理精选', '示例张晨稳健90天', 'DEMO-MGR-090', '固定收益类', '稳健', '3个月', '短期可能用钱', 5000.00, 'CNY', '业绩比较基准为区间表达，非收益承诺', '稳健收益', '示例费率：按说明书执行', '90天后按规则赎回', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 180 DAY), '适合稳健型客户短期限安排参考', JSON_OBJECT('bond','88%','cash','12%'), JSON_ARRAY('新手','工资结余客户'), JSON_ARRAY('客户经理精选','短期限','债券'), '不得承诺收益或弱化净值波动。', 'AVAILABLE', NOW(), NOW()),
('PERSONAL', 1, '客户经理', NULL, 1, '北京分行', '客户经理精选', '示例张晨半年灵活持有', 'DEMO-MGR-180-FLEX', '固定收益类', '稳健', '6个月', '短期可能用钱', 20000.00, 'CNY', '业绩比较基准为区间表达，非收益承诺', '稳健收益', '示例费率：按说明书执行', '月度开放赎回', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 180 DAY), '适合半年左右可能用钱但可接受赎回周期的稳健型客户', JSON_OBJECT('bond','83%','cash','17%'), JSON_ARRAY('工资结余客户','退休客户'), JSON_ARRAY('客户经理精选','开放式','债券'), '需提示月度开放不等于随时可用。', 'AVAILABLE', NOW(), NOW()),
('PERSONAL', 1, '客户经理', NULL, 1, '北京分行', '客户经理精选', '示例张晨稳享半年', 'DEMO-MGR-180', '固定收益类', '稳健', '6个月', '可封闭持有', 50000.00, 'CNY', '业绩比较基准为区间表达，非收益承诺', '稳健收益', '示例费率：按说明书执行', '封闭180天', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 180 DAY), '适合能接受半年封闭安排的稳健型客户', JSON_OBJECT('bond','85%','cash','15%'), JSON_ARRAY('退休客户','工资结余客户'), JSON_ARRAY('客户经理精选','封闭','债券'), '推荐前需确认客户资金六个月内无刚性支取需求。', 'AVAILABLE', NOW(), NOW()),
('PERSONAL', 1, '客户经理', NULL, 1, '北京分行', '客户经理精选', '示例张晨平衡配置', 'DEMO-MGR-MIX', '混合类', '平衡', '1年以上', '可封闭持有', 100000.00, 'CNY', '净值可能随权益和债券市场波动', '接受波动换收益', '示例费率：按说明书执行', '一年持有期', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 240 DAY), '适合平衡型及以上、有中长期资金安排的客户', JSON_OBJECT('bond','65%','equity','20%','cash','15%'), JSON_ARRAY('企业主'), JSON_ARRAY('客户经理精选','封闭','波动收益'), '不可推荐给保守或稳健客户。', 'AVAILABLE', NOW(), NOW()),
('PUBLIC', NULL, '全行', NULL, NULL, '本行', '存款优选系列', '示例活期增强存款', 'DEMO-DEP-CALL', '存款', '保守', '随时可用', '随时可用', 1.00, 'CNY', '示例存款利率描述，以实际挂牌和产品规则为准', '高于活期', '示例费率：无销售服务费', '支持按规则支取，具体以产品条款为准', DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 365 DAY), '适合保守型客户作日常备用金管理参考', JSON_OBJECT('deposit','100%'), JSON_ARRAY('新手','工资结余客户','退休客户'), JSON_ARRAY('存款','低风险','开放式'), '存款产品仍需核验实际利率、期限、提前支取规则和销售权限。', 'AVAILABLE', NOW(), NOW()),
('PUBLIC', NULL, '全行', NULL, NULL, '本行', '存款优选系列', '示例三月安心存款', 'DEMO-DEP-090', '存款', '保守', '3个月', '可封闭持有', 5000.00, 'CNY', '示例存款利率描述，以实际挂牌和产品规则为准', '稳健收益', '示例费率：无销售服务费', '持有到期按规则支取，提前支取规则以条款为准', DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 365 DAY), '适合保守型客户三个月资金安排参考', JSON_OBJECT('deposit','100%'), JSON_ARRAY('新手','退休客户','工资结余客户'), JSON_ARRAY('存款','低风险','封闭'), '推荐前需确认客户资金三个月内无刚性支取需求。', 'AVAILABLE', NOW(), NOW()),
('PUBLIC', NULL, '全行', NULL, NULL, '本行理财子公司', '保守固收系列', '示例保守半年固收', 'DEMO-FI-CONS-180', '固定收益类', '保守', '6个月', '可封闭持有', 10000.00, 'CNY', '业绩比较基准为区间表达，非收益承诺', '稳健收益', '示例费率：按产品说明书执行', '封闭180天，到期后按规则兑付', DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_ADD(NOW(), INTERVAL 210 DAY), '适合保守型及以上、可接受半年封闭安排的客户', JSON_OBJECT('bond','90%','cash','10%'), JSON_ARRAY('退休客户','工资结余客户'), JSON_ARRAY('封闭','低波动','债券'), '固定收益类产品不等于保本，需充分揭示净值波动和流动性限制。', 'AVAILABLE', NOW(), NOW()),
('PUBLIC', NULL, '全行', NULL, NULL, '本行理财子公司', '月度开放系列', '示例稳健月月开放固收', 'DEMO-FI-MONTHLY', '固定收益类', '稳健', '1个月', '随时可用', 1000.00, 'CNY', '业绩比较基准为区间表达，非收益承诺', '稳健收益', '示例费率：按产品说明书执行', '每月开放一次赎回，到账规则以说明书为准', DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_ADD(NOW(), INTERVAL 240 DAY), '适合稳健型及以上客户短期限资金安排参考', JSON_OBJECT('bond','84%','cash','16%'), JSON_ARRAY('新手','工资结余客户'), JSON_ARRAY('开放式','债券','短期限'), '月度开放不等于实时到账，需提示开放日和到账周期。', 'AVAILABLE', NOW(), NOW()),
('PUBLIC', NULL, '全行', NULL, NULL, '本行理财子公司', '平衡开放系列', '示例平衡半年开放混合', 'DEMO-MIX-180-FLEX', '混合类', '平衡', '6个月', '短期可能用钱', 10000.00, 'CNY', '权益和固收资产占比会影响净值波动', '接受波动换收益', '示例费率：按产品说明书执行', '定期开放赎回，具体以产品说明书为准', DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_ADD(NOW(), INTERVAL 210 DAY), '适合平衡型及以上、可接受中等波动的客户', JSON_OBJECT('bond','65%','equity','20%','cash','15%'), JSON_ARRAY('企业主','工资结余客户'), JSON_ARRAY('开放式','波动收益','权益'), '不得向风险承受能力低于平衡型的客户推荐。', 'AVAILABLE', NOW(), NOW()),
('PUBLIC', NULL, '全行', NULL, NULL, '本行代销', '基金优选系列', '示例进取六个月基金观察', 'DEMO-FUND-ADV-180', '基金', '进取', '6个月', '短期可能用钱', 100.00, 'CNY', '基金净值随市场波动，无固定收益', '接受波动换收益', '示例费率：申购赎回费以基金合同为准', '开放式基金，赎回到账以基金规则为准', DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_ADD(NOW(), INTERVAL 365 DAY), '适合进取型、能承受较大净值波动的客户', JSON_OBJECT('fund','100%'), JSON_ARRAY('企业主','工资结余客户'), JSON_ARRAY('基金','开放式','波动收益'), '基金产品不保证本金和收益，需完成代销适当性匹配。', 'AVAILABLE', NOW(), NOW()),
('PUBLIC', NULL, '全行', NULL, NULL, '本行代销', '基金优选系列', '示例进取一年基金配置', 'DEMO-FUND-ADV-365', '基金', '进取', '1年以上', '短期可能用钱', 100.00, 'CNY', '基金净值随市场波动，无固定收益', '接受波动换收益', '示例费率：申购赎回费以基金合同为准', '开放式基金，赎回到账以基金规则为准', DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_ADD(NOW(), INTERVAL 365 DAY), '适合进取型、有一年以上配置计划的客户', JSON_OBJECT('fund','100%'), JSON_ARRAY('企业主'), JSON_ARRAY('基金','开放式','权益','波动收益'), '不得推荐给风险承受能力不足客户。', 'AVAILABLE', NOW(), NOW()),
('PERSONAL', 1, '客户经理', NULL, 1, '北京分行', '客户经理精选', '示例张晨安心三月存款', 'DEMO-MGR-DEP-090', '存款', '保守', '3个月', '可封闭持有', 5000.00, 'CNY', '示例存款利率描述，以实际挂牌和产品规则为准', '稳健收益', '示例费率：无销售服务费', '持有到期按规则支取', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 180 DAY), '适合保守型客户三个月资金安排参考', JSON_OBJECT('deposit','100%'), JSON_ARRAY('新手','退休客户','工资结余客户'), JSON_ARRAY('客户经理精选','存款','低风险'), '需核验实际存款规则、提前支取规则和客户适当性。', 'AVAILABLE', NOW(), NOW()),
('PERSONAL', 1, '客户经理', NULL, 1, '北京分行', '客户经理精选', '示例张晨进取基金观察', 'DEMO-MGR-FUND-ADV', '基金', '进取', '1年以上', '短期可能用钱', 100.00, 'CNY', '基金净值随市场波动，无固定收益', '接受波动换收益', '示例费率：以基金合同为准', '开放式基金，赎回到账以基金规则为准', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 240 DAY), '适合进取型、有中长期配置计划的客户', JSON_OBJECT('fund','100%'), JSON_ARRAY('企业主'), JSON_ARRAY('客户经理精选','基金','波动收益'), '仅适合进取型客户，不得弱化净值波动和本金损失风险。', 'AVAILABLE', NOW(), NOW());

DROP TABLE IF EXISTS `recommend_feedback`;
CREATE TABLE `recommend_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `product_id` bigint DEFAULT NULL,
  `action` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `rating` int DEFAULT NULL,
  `reason` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `idx_feedback_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

