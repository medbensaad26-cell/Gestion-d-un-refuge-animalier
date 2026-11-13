package com.enicarthage.refugeanimalier.service;

import com.enicarthage.refugeanimalier.dao.DashboardDAO;
import java.sql.SQLException;
import java.util.Map;

public class DashboardService {
    private final DashboardDAO dao = new DashboardDAO();

    public Map<String, Object> summary(long adminId) throws SQLException {
        if (adminId <= 0) throw new SecurityException("Session invalide");
        var result = dao.summary(adminId);
        if (result == null) throw new SecurityException("Compte administrateur introuvable");
        return result;
    }
}
