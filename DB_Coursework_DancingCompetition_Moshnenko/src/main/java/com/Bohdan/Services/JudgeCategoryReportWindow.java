package com.Bohdan.Services;

import com.Bohdan.Models.Table;
import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.Map;

public class JudgeCategoryReportWindow extends JFrame {

    public JudgeCategoryReportWindow(Table table, Table[] allTables, Map<String, String> filters) {
        setTitle("Параметри розрахунку оцінки за кваліфікацією");
        setSize(460, 320);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // GridBagLayout захищає інтерфейс від потворного розтягування компонентів
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new java.awt.Insets(5, 5, 5, 5);
        gbc.weightx = 1.0;

        JComboBox<String> danceBox = new JComboBox<>();
        JComboBox<String> qualificationBox = new JComboBox<>();

        // Завантаження даних згідно з вашим DDL-скриптом
        try (Connection conn = DriverManager.getConnection(AppConfig.getDbUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword())) {

            // 1. Завантаження унікальних назв танців (таблиця dbo.Dance, поле name)
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT DISTINCT name FROM dbo.Dance ORDER BY name")) {
                while (rs.next()) {
                    danceBox.addItem(rs.getString("name"));
                }
            }

            // 2. Завантаження унікальних кваліфікацій суддів (таблиця dbo.Judge, поле qualification)
            String qualQuery = "SELECT DISTINCT qualification FROM dbo.Judge " +
                    "WHERE qualification IS NOT NULL AND qualification != '' " +
                    "ORDER BY qualification";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(qualQuery)) {
                while (rs.next()) {
                    qualificationBox.addItem(rs.getString("qualification"));
                }
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Помилка завантаження параметрів: " + e.getMessage(), "Помилка", JOptionPane.ERROR_MESSAGE);
        }

        JButton calculateButton = new JButton("Обчислити середню оцінку");
        calculateButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        calculateButton.setPreferredSize(new Dimension(0, 35));

        calculateButton.addActionListener(e -> {
            String selectedDance = (String) danceBox.getSelectedItem();
            String selectedQual = (String) qualificationBox.getSelectedItem();

            if (selectedDance != null && selectedQual != null) {
                calculateAndShowResult(selectedDance, selectedQual);
            } else {
                JOptionPane.showMessageDialog(this, "Будь ласка, перевірте наявність даних у випадаючих списках.");
            }
        });

        JButton cancelButton = new JButton("⬅ Скасувати");
        cancelButton.addActionListener(e -> {
            dispose();
            new TableScreen(table, allTables, filters);
        });

        // Розміщення елементів на формі
        gbc.gridx = 0; gbc.gridy = 0;
        mainPanel.add(new JLabel("Оберіть танець:"), gbc);

        gbc.gridy = 1;
        mainPanel.add(danceBox, gbc);

        gbc.gridy = 2;
        mainPanel.add(new JLabel("Оберіть кваліфікацію судді:"), gbc);

        gbc.gridy = 3;
        mainPanel.add(qualificationBox, gbc);

        gbc.gridy = 4; gbc.insets = new java.awt.Insets(15, 5, 5, 5);
        mainPanel.add(calculateButton, gbc);

        gbc.gridy = 5; gbc.insets = new java.awt.Insets(5, 5, 5, 5);
        mainPanel.add(cancelButton, gbc);

        add(mainPanel);
        setVisible(true);
    }

    private void calculateAndShowResult(String danceName, String qualification) {
        // Точний SQL-запит, який повністю відповідає вашій базі даних
        String sql = "SELECT AVG(CAST(s.value AS FLOAT)) AS avg_score, COUNT(s.score_id) AS total_scores " +
                "FROM dbo.Score s " +
                "INNER JOIN dbo.Performance p ON s.performance_id = p.performance_id " +
                "INNER JOIN dbo.Dance d ON p.dance_id = d.dance_id " +
                "INNER JOIN dbo.Judge j ON s.judge_id = j.judge_id " +
                "WHERE d.name = ? AND j.qualification = ?";

        try (Connection conn = DriverManager.getConnection(AppConfig.getDbUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword());
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, danceName);
            pstmt.setString(2, qualification);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    double avgScore = rs.getDouble("avg_score");
                    int totalScores = rs.getInt("total_scores");

                    if (rs.wasNull() || totalScores == 0) {
                        JOptionPane.showMessageDialog(this,
                                "Судді з кваліфікацією \"" + qualification + "\" ще не виставляли оцінок за танець \"" + danceName + "\".",
                                "Результат розрахунку", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        String message = String.format(
                                "Аналітичний підсумок:\n\n" +
                                        "Танець: %s\n" +
                                        "Кваліфікація суддів: %s\n" +
                                        "Кількість врахованих оцінок: %d\n" +
                                        "Середній бал: %.2f",
                                danceName, qualification, totalScores, avgScore
                        );
                        JOptionPane.showMessageDialog(this, message, "Результат обчислення успішний", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Помилка обчислення в СУБД: " + e.getMessage(), "Помилка SQL", JOptionPane.ERROR_MESSAGE);
        }
    }
}