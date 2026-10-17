package com.campus.charity.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.sql.DataSource;

/** 可重复运行的非破坏性迁移，在服务接收请求前完成。历史捐赠不补造财务确认。 */
@Component("requirementsUpgrade")
public class RequirementsUpgrade {
    private final DataSource dataSource;
    private final JdbcTemplate db;
    public RequirementsUpgrade(DataSource dataSource, JdbcTemplate db) {
        this.dataSource = dataSource; this.db = db;
    }
    @PostConstruct
    public void upgrade() {
        new ResourceDatabasePopulator(new ClassPathResource("db/upgrade-20261017.sql")).execute(dataSource);
        addColumn("sys_user", "identity_test_only", "TINYINT NOT NULL DEFAULT 0");
        addColumn("identity_review", "verification_mode", "VARCHAR(16) NOT NULL DEFAULT 'MANUAL'");
        db.execute("ALTER TABLE identity_review MODIFY COLUMN file_id BIGINT NULL");
        addColumn("donation", "order_id", "BIGINT NULL");
        addColumn("donation", "confirmation_id", "BIGINT NULL");
        addColumn("donation", "confirm_user_id", "BIGINT NULL");
        addColumn("donation", "confirm_time", "DATETIME NULL");
        addColumn("donation", "refund_status", "VARCHAR(24) NOT NULL DEFAULT 'NONE'");
        addColumn("chain_evidence_snapshot", "event_key", "VARCHAR(180) NULL");
        addIndex("donation", "uk_donation_order", "UNIQUE INDEX uk_donation_order(order_id)");
        addIndex("chain_evidence_snapshot", "uk_snapshot_event", "UNIQUE INDEX uk_snapshot_event(event_key)");
        db.execute("ALTER TABLE donation MODIFY COLUMN remark VARCHAR(500) NULL");
        new ResourceDatabasePopulator(new ClassPathResource("db/finance-views.sql")).execute(dataSource);
        if (db.queryForObject("SELECT COUNT(*) FROM schema_upgrade WHERE version='20261017-finance'", Integer.class) == 0) {
            // 保留全部历史行，仅纠正新版筹款统计，不把旧 PAID/CONFIRMED 解释为财务确认。
            db.update("UPDATE project p SET current_amount=(SELECT COALESCE(SUM(d.amount),0) FROM donation d WHERE d.project_id=p.project_id AND d.deleted=0 AND d.status='CONFIRMED' AND d.confirmation_id IS NOT NULL AND d.refund_status<>'REFUNDED'), donor_count=(SELECT COUNT(DISTINCT d.donor_id) FROM donation d WHERE d.project_id=p.project_id AND d.deleted=0 AND d.status='CONFIRMED' AND d.confirmation_id IS NOT NULL AND d.refund_status<>'REFUNDED') WHERE p.deleted=0");
            db.update("INSERT INTO schema_upgrade(version,applied_time) VALUES('20261017-finance',NOW())");
        }
    }
    private void addColumn(String table, String column, String definition) {
        if (db.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?", Integer.class, table, column) == 0)
            db.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }
    private void addIndex(String table, String index, String definition) {
        if (db.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=? AND index_name=?", Integer.class, table, index) == 0)
            db.execute("ALTER TABLE " + table + " ADD " + definition);
    }
}
