package com.enicarthage.refugeanimalier.dao;

import com.enicarthage.refugeanimalier.service.FiltresCatalogue;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

final class RechercheSQL {
    private RechercheSQL() {}

    // Columns and alias are constants supplied by the DAOs; user values are always bound.
    static void appliquer(StringBuilder sql, List<String> valeurs, FiltresCatalogue filtres, String alias, String... colonnes) {
        if (filtres.q() != null) {
            sql.append(" AND (");
            for (int index = 0; index < colonnes.length; index++) {
                if (index > 0) sql.append(" OR ");
                sql.append("LOCATE(LOWER(?), LOWER(COALESCE(").append(colonnes[index]).append(", ''))) > 0");
                valeurs.add(filtres.q());
            }
            sql.append(")");
        }
        if (filtres.espece() != null) {
            sql.append(" AND LOWER(").append(alias).append(".espece) = LOWER(?)");
            valeurs.add(filtres.espece());
        }
        if (filtres.statutAnimal() != null) {
            sql.append(" AND ").append(alias).append(".statut = ?");
            valeurs.add(filtres.statutAnimal());
        }
    }

    static void lier(PreparedStatement statement, List<String> valeurs) throws SQLException {
        for (int index = 0; index < valeurs.size(); index++) statement.setString(index + 1, valeurs.get(index));
    }
}
