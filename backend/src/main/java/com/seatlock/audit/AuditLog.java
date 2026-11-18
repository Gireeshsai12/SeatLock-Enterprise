package com.seatlock.audit;

import com.seatlock.shared.PageWindow;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLog {
    private final JdbcTemplate jdbc;
    public AuditLog(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(UUID actor, String action, Object entity) {
        jdbc.update("insert into audit_log(actor,action,entity) values (?,?,?)", actor, action, entity.toString());
    }

    public List<Map<String,Object>> list(PageWindow page) {
        return jdbc.queryForList("select * from audit_log order by id desc limit ? offset ?", page.size(), page.offset());
    }
}
