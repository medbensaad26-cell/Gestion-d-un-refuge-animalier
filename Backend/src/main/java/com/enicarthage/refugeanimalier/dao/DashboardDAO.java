package com.enicarthage.refugeanimalier.dao;

import com.enicarthage.refugeanimalier.config.DatabaseConnection;
import java.sql.SQLException;
import java.util.Map;

public class DashboardDAO {
    public Map<String, Object> summary(long adminId) throws SQLException {
        // One read statement gives a consistent view without fetching applicant records.
        String sql = "SELECT ad.id, ad.nom, ad.email, "
                + "(SELECT COUNT(*) FROM animal) AS animaux, "
                + "(SELECT COUNT(*) FROM annonce) AS annonces, "
                + "(SELECT COUNT(*) FROM demande) AS demandes "
                + "FROM admin ad WHERE ad.id = ?";
        try (var connection = DatabaseConnection.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, adminId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) return null;
                return Map.of("admin", Map.of("id", result.getLong("id"), "nom", result.getString("nom"), "email", result.getString("email")),
                        "animaux", result.getLong("animaux"), "annonces", result.getLong("annonces"), "demandes", result.getLong("demandes"));
            }
        }
    }
}
