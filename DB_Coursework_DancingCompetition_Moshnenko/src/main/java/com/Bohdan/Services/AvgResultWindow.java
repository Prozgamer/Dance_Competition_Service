package com.Bohdan.Services;

import com.Bohdan.Models.Table;

import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.Map;

/**
 * Вікно відображення розрахованого середнього значення (AVG) з урахуванням фільтрації.
 */
public class AvgResultWindow extends JFrame {
    public AvgResultWindow(Table table, Table[] allTables, Map<String, String> filters, String selectedField) {
        setTitle("Середнє значення по полю: " + selectedField);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(450, 250);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(15, 15));

        JPanel contentPanel = new JPanel(new GridLayout(0, 1, 15, 15));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel infoLabel = new JLabel("Таблиця: " + table.Name() + " | Поле: " + selectedField, SwingConstants.CENTER);
        infoLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        contentPanel.add(infoLabel);

        JLabel resultLabel = new JLabel("Обчислення...", SwingConstants.CENTER);
        resultLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        resultLabel.setForeground(new Color(0, 102, 204)); // Приємний синій колір акценту
        contentPanel.add(resultLabel);

        add(contentPanel, BorderLayout.CENTER);

        //ДИНАМІЧНИЙ SQL ЗАПИТ ДО БАЗЕ ДАНИХ (AVG)
        // Формуємо розрахунок із примусовим приведенням (CAST) до FLOAT, щоб MS SQL не округляв INT до цілого числа при діленні!
        StringBuilder sql = new StringBuilder("SELECT AVG(CAST(" + selectedField + " AS FLOAT)) AS avg_value FROM " + table.Name());

        // Застосовуємо поточні фільтри, якщо вони активні на екрані таблиці
        if (filters != null && !filters.isEmpty()) {
            sql.append(" WHERE ");
            int count = 0;
            for (String colName : filters.keySet()) {
                if (count > 0) sql.append(" AND ");
                sql.append(colName).append(" LIKE ?");
                count++;
            }
        }

        try (Connection conn = DriverManager.getConnection(AppConfig.getDbUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword());
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            // Заповнюємо плейсхолдери фільтрації
            if (filters != null && !filters.isEmpty()) {
                int paramIndex = 1;
                for (String value : filters.values()) {
                    pstmt.setString(paramIndex, "%" + value + "%");
                    paramIndex++;
                }
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    double average = rs.getDouble("avg_value");
                    if (rs.wasNull()) {
                        resultLabel.setText("Немає записів для розрахунку середнього");
                    } else {
                        resultLabel.setText(String.format("Середнє значення: %.2f", average));
                    }
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Помилка розрахунку в базі даних: " + e.getMessage(), "Помилка СУБД", JOptionPane.ERROR_MESSAGE);
        }

        // Кнопка закриття/повернення
        JButton backButton = new JButton("⬅ Назад до вибору поля");
        backButton.setPreferredSize(new Dimension(0, 40));
        backButton.setFont(new Font("SansSerif", Font.BOLD, 12));
        backButton.addActionListener(e -> SwingUtilities.invokeLater(() -> {
            dispose();
            new AvgWindow(table, allTables, filters);
        }));
        add(backButton, BorderLayout.SOUTH);

        setVisible(true);
    }
}