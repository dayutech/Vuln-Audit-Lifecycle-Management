CREATE TABLE IF NOT EXISTS asset_metadata_base (
    id UUID NOT NULL PRIMARY KEY,
    asset_id UUID NOT NULL,
    asset_version_id UUID NULL,
    claimed_by UUID NULL,
    version INT NOT NULL DEFAULT 1,
    system_architecture VARCHAR(50),
    runtime_platform VARCHAR(255),
    deployment_method VARCHAR(255),
    programming_languages_json TEXT,
    programming_language VARCHAR(100),
    framework VARCHAR(100),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_asset_metadata_base_asset_version (asset_id, asset_version_id)
);

CREATE TABLE IF NOT EXISTS asset_metadata_network (
    id UUID NOT NULL PRIMARY KEY,
    asset_id UUID NOT NULL,
    asset_version_id UUID NULL,
    claimed_by UUID NULL,
    version INT NOT NULL DEFAULT 1,
    service_ports TEXT,
    api_endpoints TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_asset_metadata_network_asset_version (asset_id, asset_version_id)
);

CREATE TABLE IF NOT EXISTS asset_metadata_env (
    id UUID NOT NULL PRIMARY KEY,
    asset_id UUID NOT NULL,
    asset_version_id UUID NULL,
    claimed_by UUID NULL,
    version INT NOT NULL DEFAULT 1,
    install_path VARCHAR(500),
    startup_script_path TEXT,
    config_file_path TEXT,
    startup_command VARCHAR(1000),
    database_type VARCHAR(50),
    database_connection VARCHAR(500),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_asset_metadata_env_asset_version (asset_id, asset_version_id)
);

CREATE TABLE IF NOT EXISTS asset_metadata_account (
    id UUID NOT NULL PRIMARY KEY,
    asset_id UUID NOT NULL,
    asset_version_id UUID NULL,
    claimed_by UUID NULL,
    version INT NOT NULL DEFAULT 1,
    admin_username VARCHAR(100),
    admin_password_encrypted VARCHAR(255),
    account_credentials_json TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_asset_metadata_account_asset_version (asset_id, asset_version_id)
);

CREATE TABLE IF NOT EXISTS asset_metadata_extra (
    id UUID NOT NULL PRIMARY KEY,
    asset_id UUID NOT NULL,
    asset_version_id UUID NULL,
    claimed_by UUID NULL,
    version INT NOT NULL DEFAULT 1,
    special_notes TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_asset_metadata_extra_asset_version (asset_id, asset_version_id)
);

SET @table_exists := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'asset_app_metadata'
);

SET @expr_asset_version_id := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'asset_version_id'
) > 0, 'source.asset_version_id', 'NULL');
SET @expr_claimed_by := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'claimed_by'
) > 0, 'source.claimed_by', 'NULL');
SET @expr_version := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'version'
) > 0, 'source.version', '1');
SET @expr_created_at := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'created_at'
) > 0, 'source.created_at', 'CURRENT_TIMESTAMP');
SET @expr_updated_at := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'updated_at'
) > 0, 'source.updated_at', 'CURRENT_TIMESTAMP');

SET @expr_system_architecture := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'system_architecture'
) > 0, 'source.system_architecture', 'NULL');
SET @expr_runtime_platform := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'runtime_platform'
) > 0, 'source.runtime_platform', 'NULL');
SET @expr_deployment_method := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'deployment_method'
) > 0, 'source.deployment_method', 'NULL');
SET @expr_programming_languages_json := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'programming_languages_json'
) > 0, 'source.programming_languages_json', 'NULL');
SET @expr_programming_language := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'programming_language'
) > 0, 'source.programming_language', 'NULL');
SET @expr_framework := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'framework'
) > 0, 'source.framework', 'NULL');

SET @expr_service_ports := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'service_ports'
) > 0, 'source.service_ports', 'NULL');
SET @expr_api_endpoints := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'api_endpoints'
) > 0, 'source.api_endpoints', 'NULL');

SET @expr_install_path := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'install_path'
) > 0, 'source.install_path', 'NULL');
SET @expr_startup_script_path := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'startup_script_path'
) > 0, 'source.startup_script_path', 'NULL');
SET @expr_config_file_path := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'config_file_path'
) > 0, 'source.config_file_path', 'NULL');
SET @expr_startup_command := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'startup_command'
) > 0, 'source.startup_command', 'NULL');
SET @expr_database_type := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'database_type'
) > 0, 'source.database_type', 'NULL');
SET @expr_database_connection := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'database_connection'
) > 0, 'source.database_connection', 'NULL');

SET @expr_admin_username := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'admin_username'
) > 0, 'source.admin_username', 'NULL');
SET @expr_admin_password_encrypted := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'admin_password_encrypted'
) > 0, 'source.admin_password_encrypted', 'NULL');
SET @expr_account_credentials_json := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'account_credentials_json'
) > 0, 'source.account_credentials_json', 'NULL');

SET @expr_special_notes := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset_app_metadata' AND column_name = 'special_notes'
) > 0, 'source.special_notes', 'NULL');

SET @sql_base := IF(@table_exists > 0,
    CONCAT(
        'INSERT INTO asset_metadata_base (id, asset_id, asset_version_id, claimed_by, version, system_architecture, runtime_platform, deployment_method, programming_languages_json, programming_language, framework, created_at, updated_at) ',
        'SELECT UUID(), source.asset_id, ', @expr_asset_version_id, ', ', @expr_claimed_by, ', ', @expr_version, ', ',
        @expr_system_architecture, ', ', @expr_runtime_platform, ', ', @expr_deployment_method, ', ',
        @expr_programming_languages_json, ', ', @expr_programming_language, ', ', @expr_framework, ', ',
        @expr_created_at, ', ', @expr_updated_at, ' ',
        'FROM asset_app_metadata source ',
        'LEFT JOIN asset_metadata_base existing ON existing.asset_id = source.asset_id AND (existing.asset_version_id <=> ', @expr_asset_version_id, ') ',
        'WHERE existing.id IS NULL'
    ),
    'SELECT 1'
);
PREPARE stmt_base FROM @sql_base;
EXECUTE stmt_base;
DEALLOCATE PREPARE stmt_base;

SET @sql_network := IF(@table_exists > 0,
    CONCAT(
        'INSERT INTO asset_metadata_network (id, asset_id, asset_version_id, claimed_by, version, service_ports, api_endpoints, created_at, updated_at) ',
        'SELECT UUID(), source.asset_id, ', @expr_asset_version_id, ', ', @expr_claimed_by, ', ', @expr_version, ', ',
        @expr_service_ports, ', ', @expr_api_endpoints, ', ', @expr_created_at, ', ', @expr_updated_at, ' ',
        'FROM asset_app_metadata source ',
        'LEFT JOIN asset_metadata_network existing ON existing.asset_id = source.asset_id AND (existing.asset_version_id <=> ', @expr_asset_version_id, ') ',
        'WHERE existing.id IS NULL'
    ),
    'SELECT 1'
);
PREPARE stmt_network FROM @sql_network;
EXECUTE stmt_network;
DEALLOCATE PREPARE stmt_network;

SET @sql_env := IF(@table_exists > 0,
    CONCAT(
        'INSERT INTO asset_metadata_env (id, asset_id, asset_version_id, claimed_by, version, install_path, startup_script_path, config_file_path, startup_command, database_type, database_connection, created_at, updated_at) ',
        'SELECT UUID(), source.asset_id, ', @expr_asset_version_id, ', ', @expr_claimed_by, ', ', @expr_version, ', ',
        @expr_install_path, ', ', @expr_startup_script_path, ', ', @expr_config_file_path, ', ',
        @expr_startup_command, ', ', @expr_database_type, ', ', @expr_database_connection, ', ',
        @expr_created_at, ', ', @expr_updated_at, ' ',
        'FROM asset_app_metadata source ',
        'LEFT JOIN asset_metadata_env existing ON existing.asset_id = source.asset_id AND (existing.asset_version_id <=> ', @expr_asset_version_id, ') ',
        'WHERE existing.id IS NULL'
    ),
    'SELECT 1'
);
PREPARE stmt_env FROM @sql_env;
EXECUTE stmt_env;
DEALLOCATE PREPARE stmt_env;

SET @sql_account := IF(@table_exists > 0,
    CONCAT(
        'INSERT INTO asset_metadata_account (id, asset_id, asset_version_id, claimed_by, version, admin_username, admin_password_encrypted, account_credentials_json, created_at, updated_at) ',
        'SELECT UUID(), source.asset_id, ', @expr_asset_version_id, ', ', @expr_claimed_by, ', ', @expr_version, ', ',
        @expr_admin_username, ', ', @expr_admin_password_encrypted, ', ', @expr_account_credentials_json, ', ',
        @expr_created_at, ', ', @expr_updated_at, ' ',
        'FROM asset_app_metadata source ',
        'LEFT JOIN asset_metadata_account existing ON existing.asset_id = source.asset_id AND (existing.asset_version_id <=> ', @expr_asset_version_id, ') ',
        'WHERE existing.id IS NULL'
    ),
    'SELECT 1'
);
PREPARE stmt_account FROM @sql_account;
EXECUTE stmt_account;
DEALLOCATE PREPARE stmt_account;

SET @sql_extra := IF(@table_exists > 0,
    CONCAT(
        'INSERT INTO asset_metadata_extra (id, asset_id, asset_version_id, claimed_by, version, special_notes, created_at, updated_at) ',
        'SELECT UUID(), source.asset_id, ', @expr_asset_version_id, ', ', @expr_claimed_by, ', ', @expr_version, ', ',
        @expr_special_notes, ', ', @expr_created_at, ', ', @expr_updated_at, ' ',
        'FROM asset_app_metadata source ',
        'LEFT JOIN asset_metadata_extra existing ON existing.asset_id = source.asset_id AND (existing.asset_version_id <=> ', @expr_asset_version_id, ') ',
        'WHERE existing.id IS NULL'
    ),
    'SELECT 1'
);
PREPARE stmt_extra FROM @sql_extra;
EXECUTE stmt_extra;
DEALLOCATE PREPARE stmt_extra;

SET @sql_drop_old := IF(@table_exists > 0, 'DROP TABLE asset_app_metadata', 'SELECT 1');
PREPARE stmt_drop_old FROM @sql_drop_old;
EXECUTE stmt_drop_old;
DEALLOCATE PREPARE stmt_drop_old;
