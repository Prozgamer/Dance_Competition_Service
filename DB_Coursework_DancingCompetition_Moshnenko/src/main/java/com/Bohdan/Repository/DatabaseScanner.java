package com.Bohdan.Repository;

import com.Bohdan.Advice.DatabaseErrorHandler;
import com.Bohdan.Models.Field;
import com.Bohdan.Models.Table;
import com.Bohdan.Services.AppConfig;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseScanner {

    private static String cleanTableName(String tableName) {
        if (tableName == null) return "";
        return tableName.contains(".") ? tableName.substring(tableName.lastIndexOf(".") + 1).trim() : tableName.trim();
    }

    public static List<Table> ScanDatabase(){
        List<Table> tables = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(AppConfig.getDbUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword())){
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet rsTables = metaData.getTables(null, "dbo", "%", new String[]{"TABLE"});
            while (rsTables.next()){
                String rawTableName = rsTables.getString("TABLE_NAME");
                String tableName = cleanTableName(rawTableName);
                if (tableName.equalsIgnoreCase("sysdiagrams") || tableName.equalsIgnoreCase("TestTable")
                        || tableName.startsWith("trace_xe") || tableName.equalsIgnoreCase("CompetitionStats")) continue;

                List<Field> fields = new ArrayList<>();
                ResultSet rsColumns = metaData.getColumns(null, "dbo", rawTableName, "%");
                while (rsColumns.next()){
                    fields.add(new Field(rsColumns.getString("COLUMN_NAME"), rsColumns.getString("TYPE_NAME")));
                }
                tables.add(new Table(tableName, fields));
            }
        } catch (SQLException e){
            DatabaseErrorHandler.handleSQLException(null, e);
        }
        return tables;
    }

    public static Map<String, Integer> getLookupMap(Table parentTable) {
        Map<String, Integer> lookupMap = new HashMap<>();

        // Очищаємо ім'я таблиці від зайвих пробілів
        String tableName = parentTable.Name().trim();

        // Дефолтне значення (резервне), якщо таблиця не підпаде під хардкод
        String idColumn = parentTable.fields().get(0).Name();

        String sql;

        // ЖОРСТКИЙ ХАРДКОД ДЛЯ КОЖНОЇ ТАБЛИЦІ (Усуває помилки динамічного визначення полів)
        if (tableName.equalsIgnoreCase("Performance")) {
            idColumn = "performance_id";
            sql = "SELECT performance_id, CONCAT(N'Виступ №', performance_id) AS display_name FROM [Performance]";
        } else if (tableName.equalsIgnoreCase("Score")) {
            idColumn = "score_id";
            sql = "SELECT score_id, CONCAT(CAST(value AS NVARCHAR), N' - ', criteria) AS display_name FROM [Score]";
        } else if (tableName.equalsIgnoreCase("Participant")) {
            idColumn = "participant_id";
            sql = "SELECT participant_id, (first_name + ' ' + last_name) AS display_name FROM [Participant]";
        } else if (tableName.equalsIgnoreCase("Judge")) {
            idColumn = "judge_id";
            sql = "SELECT judge_id, (first_name + ' ' + last_name) AS display_name FROM [Judge]";
        } else if (tableName.equalsIgnoreCase("Country")) {
            idColumn = "country_id";
            sql = "SELECT country_id, country_name AS display_name FROM [Country]";
        } else if (tableName.equalsIgnoreCase("Category")) {
            idColumn = "category_id";
            sql = "SELECT category_id, category_name AS display_name FROM [Category]";
        } else if (tableName.equalsIgnoreCase("Dance")) {
            // Ось тут тепер стовідсотковий точний хардкод для танців!
            idColumn = "dance_id";
            sql = "SELECT dance_id, name AS display_name FROM [Dance]";
        } else if (tableName.equalsIgnoreCase("Pair")) {
            idColumn = "pair_id";
            sql = "SELECT pair_id, name AS display_name FROM [Pair]";
        } else {
            // Універсальний фолбек, якщо з'явиться нова таблиця
            sql = "SELECT " + idColumn + ", name AS display_name FROM [" + tableName + "]";
        }

        try (Connection conn = DriverManager.getConnection(AppConfig.getDbUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword());
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String displayName = rs.getString("display_name");
                if (displayName != null) {
                    // Зберігаємо чіткий примітивний int за правильним названим idColumn
                    lookupMap.put(displayName.trim(), rs.getInt(idColumn));
                }
            }
        } catch (SQLException e) {
            System.err.println("ПОМИЛКА getLookupMap для таблиці " + tableName + ": " + e.getMessage());
            DatabaseErrorHandler.handleSQLException(null, e);
        }
        return lookupMap;
    }
    public static List<Object[]> getRows(String tableName, Map<String, String> filters) {
        String cleanName = cleanTableName(tableName);
        List<Object[]> data = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM " + cleanName);

        if (filters != null && !filters.isEmpty()) {
            sql.append(" WHERE ");
            int count = 0;
            for (String col : filters.keySet()) {
                // Перевіряємо, чи це колонки з датами (за назвою)
                if (col.toLowerCase().contains("date") || col.toLowerCase().contains("time")) {
                    // Приводимо поле бази до чистої дати для точного збігу, ігноруючи години/хвилини
                    sql.append("CAST(").append(col).append(" AS DATE) = ?");
                } else {
                    // Для всіх інших текстових полів залишаємо гнучкий LIKE
                    sql.append(col).append(" LIKE ?");
                }
                sql.append(++count < filters.size() ? " AND " : "");
            }
        }

        try (Connection conn = DriverManager.getConnection(AppConfig.getDbUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword());
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            int i = 1;
            if (filters != null) {
                for (Map.Entry<String, String> entry : filters.entrySet()) {
                    String col = entry.getKey();
                    String val = entry.getValue();

                    if (col.toLowerCase().contains("date") || col.toLowerCase().contains("time")) {
                        // Передаємо чисту дату. Якщо користувач ввів коректну дату, SQL Server сам її розпарсить
                        pstmt.setString(i++, val);
                    } else {
                        // Звичайний текстовий фільтр з масками
                        pstmt.setString(i++, "%" + val + "%");
                    }
                }
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                int cols = rs.getMetaData().getColumnCount();
                while(rs.next()){
                    Object[] row = new Object[cols];
                    for(int j = 1; j <= cols; j++) row[j-1] = rs.getObject(j);
                    data.add(row);
                }
            }
        } catch (SQLException e){
            DatabaseErrorHandler.handleSQLException(null, e);
        }
        return data;
    }
}
