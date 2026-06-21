package com.Bohdan.Repository;

import com.Bohdan.Advice.DatabaseErrorHandler;
import com.Bohdan.Services.AppConfig;

import java.sql.*;
import javax.swing.*;
import java.awt.*;

/**
 * Клас DatabaseModifier відповідає за виконання будь-яких операцій модифікації даних (CUD - Create, Update, Delete).
 * Реалізує транзакційну безпеку, роботу зі збереженими процедурами та універсальні запити.
 */
public class DatabaseModifier {

    public static boolean insertSimpleRecord(Component parent, String sql, Object... databaseParams) {
        try (Connection conn = DriverManager.getConnection(AppConfig.getDbUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword());
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Безпечна підстановка
            for (int i = 0; i < databaseParams.length; i++) {
                pstmt.setObject(i + 1, databaseParams[i]);
            }

            pstmt.executeUpdate();
            JOptionPane.showMessageDialog(parent, "Запис успішно додано!", "Успіх", JOptionPane.INFORMATION_MESSAGE);
            return true;

        } catch (SQLException e) {
            // Наш глобальний обробник перехопить будь-яке порушення CHECK, UNIQUE чи FOREIGN KEY
            DatabaseErrorHandler.handleSQLException(parent, e);
            return false;
        }
    }

    /**
     * Спеціалізоване транзакційне додавання учасника через збережену процедуру СУБД.
     */
    public static boolean registerParticipant(Component parent, String firstName, String lastName, String birthDate, String gender, int countryId) {
        String sql = "{call dbo.usp_RegisterParticipantSafe(?, ?, ?, ?, ?)}";

        try (Connection conn = DriverManager.getConnection(AppConfig.getDbUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword());
             CallableStatement cstmt = conn.prepareCall(sql)) {

            cstmt.setString(1, firstName);
            cstmt.setString(2, lastName);
            cstmt.setString(3, java.sql.Date.valueOf(birthDate).toString()); // Валідація та приведення формату дати (YYYY-MM-DD)
            cstmt.setString(4, gender);
            cstmt.setInt(5, countryId);

            cstmt.execute();
            JOptionPane.showMessageDialog(parent, "Учасника успішно додано через процедуру!", "Успіх", JOptionPane.INFORMATION_MESSAGE);
            return true;

        } catch (SQLException e) {
            // Перехоплює як помилку відсутності країни (код 50001), так і порушення унікальності особи
            DatabaseErrorHandler.handleSQLException(parent, e);
            return false;
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(parent, "Некоректний формат дати! Використовуйте YYYY-MM-DD", "Помилка формату", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * Спеціалізоване створення танцювальної пари через атомарну збережену процедуру.
     */
    public static boolean registerPair(Component parent, String pairName, int categoryId, int partner1Id, int partner2Id) {
        String sql = "{call dbo.usp_CreateFullPair(?, ?, ?, ?)}";

        try (Connection conn = DriverManager.getConnection(AppConfig.getDbUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword());
             CallableStatement cstmt = conn.prepareCall(sql)) {

            cstmt.setString(1, pairName);
            cstmt.setInt(2, categoryId);
            cstmt.setInt(3, partner1Id);
            cstmt.setInt(4, partner2Id);

            cstmt.execute();
            JOptionPane.showMessageDialog(parent, "Пару успішно додано через процедуру!", "Успіх", JOptionPane.INFORMATION_MESSAGE);
            return true;

        } catch (SQLException e) {
            DatabaseErrorHandler.handleSQLException(parent, e);
            return false;
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(parent, "Некоректний формат даних!", "Помилка формату", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * Універсальний параметризований метод для оновлення (редагування) записів у базі даних.
     */
    public static boolean updateSimpleRecord(Component parent, String sql, Object... databaseParams) {
        try (Connection conn = DriverManager.getConnection(AppConfig.getDbUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword());
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Виведемо запит у консоль для перевірки
            System.out.println("Executing SQL: " + sql);

            for (int i = 0; i < databaseParams.length; i++) {
                pstmt.setObject(i + 1, databaseParams[i]);
                System.out.println("Param [" + (i + 1) + "]: " + databaseParams[i] + " (Type: " + (databaseParams[i] != null ? databaseParams[i].getClass().getSimpleName() : "null") + ")");
            }

            // executeUpdate() повертає кількість змінених рядків
            int rowsAffected = pstmt.executeUpdate();
            System.out.println("Rows affected: " + rowsAffected);

            if (rowsAffected > 0) {
                JOptionPane.showMessageDialog(parent, "Запис успішно оновлено!", "Успіх", JOptionPane.INFORMATION_MESSAGE);
                return true;
            } else {
                JOptionPane.showMessageDialog(parent, "Попередження: Жодного запису не було знайдено за вказаним ID!", "Увага", JOptionPane.WARNING_MESSAGE);
                return false;
            }

        } catch (SQLException e) {
            System.err.println("ПОМИЛКА SQL в updateSimpleRecord: " + e.getMessage());
            DatabaseErrorHandler.handleSQLException(parent, e);
            return false;
        }
    }

    /**
     * Універсальний метод видалення рядка з диспетчеризацією викликів збережених процедур для вузлових таблиць.
     */
    public static boolean deleteRecord(Component parent, String tableName, String keyColumnName, int id) {
        // Якщо видаляємо Пару, Країну або Учасника — викликаємо відповідні процедури транзакційного очищення каскадів
        if (tableName.equalsIgnoreCase("Pair")) {
            return executeDeleteProcedure(parent, "{call dbo.usp_DeletePairSafe(?)}", id, "Пару");
        }
        if (tableName.equalsIgnoreCase("Country")) {
            return executeDeleteProcedure(parent, "{call dbo.usp_DeleteCountrySafe(?)}", id, "Країну");
        }
        if (tableName.equalsIgnoreCase("Participant")) {
            return executeDeleteProcedure(parent, "{call dbo.usp_DeleteParticipantSafe(?)}", id, "Учасника та його танцювальні пари");
        }

        // Для всіх інших простих довідників (Судді, Категорії) виконуємо звичайний DELETE запит
        String sql = "DELETE FROM " + tableName + " WHERE " + keyColumnName + " = ?";

        try (Connection conn = DriverManager.getConnection(AppConfig.getDbUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword());
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                JOptionPane.showMessageDialog(parent, "Запис успішно видалено!", "Успіх", JOptionPane.INFORMATION_MESSAGE);
                return true;
            } else {
                JOptionPane.showMessageDialog(parent, "Запис із таким ID не знайдено.", "Попередження", JOptionPane.WARNING_MESSAGE);
                return false;
            }

        } catch (SQLException e) {
            DatabaseErrorHandler.handleSQLException(parent, e);
            return false;
        }
    }

    /**
     * Допоміжний метод для безпечного виклику збережених процедур видалення.
     */
    private static boolean executeDeleteProcedure(Component parent, String procedureSql, int id, String entityName) {
        try (Connection conn = DriverManager.getConnection(AppConfig.getDbUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword());
             CallableStatement cstmt = conn.prepareCall(procedureSql)) {

            cstmt.setInt(1, id);
            cstmt.execute();

            JOptionPane.showMessageDialog(parent, entityName + " успішно видалено разом з усіма залежними даними!", "Успіх", JOptionPane.INFORMATION_MESSAGE);
            return true;

        } catch (SQLException e) {
            DatabaseErrorHandler.handleSQLException(parent, e);
            return false;
        }
    }
}
