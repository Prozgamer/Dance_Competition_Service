package com.Bohdan.Services;

import com.Bohdan.Models.Table;
import com.Bohdan.Repository.DatabaseScanner;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Клас CountedWindow відображає таблицю з результатами групування (GROUP BY) та підрахунку кількості записів.
 */
public class CountedWindow extends JFrame {

    public CountedWindow(Table table, Table[] allTables, Map<String, String> filters, String selectedField) {
        setTitle("Результати підрахунку за полем: " + selectedField);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(500, 450);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Верхня панель з інформацією
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JLabel titleLabel = new JLabel("Групування таблиці " + table.Name() + " за стовпцем " + selectedField);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        infoPanel.add(titleLabel);
        add(infoPanel, BorderLayout.NORTH);

        // Налаштування JTable для виведення статистики
        String[] columns = {"Значення поля", "Кількість повторень"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false; // Таблиця тільки для перегляду
            }
        };
        JTable statsTable = new JTable(model);
        JScrollPane scrollPane = new JScrollPane(statsTable);
        add(scrollPane, BorderLayout.CENTER);

        //РОЗУМНЕ ВІДОБРАЖЕННЯ ЗОВНІШНІХ КЛЮЧІВ (ID -> Ім'я/Назва)
        Map<Integer, String> lookupTranslations = new HashMap<>();
        if (selectedField.endsWith("_id") && !selectedField.contains("partner")) {
            String parentTableName = selectedField.substring(0, selectedField.lastIndexOf("_"));
            if (parentTableName.equalsIgnoreCase("coach")) {
                parentTableName = "Participant";
            }

            Table parentTable = null;
            for (Table t : allTables) {
                if (t.Name().equalsIgnoreCase(parentTableName)) {
                    parentTable = t;
                    break;
                }
            }

            if (parentTable != null) {
                // Витягуємо словник відповідностей {"Назва" -> ID} та інвертуємо його в {ID -> "Назва"}
                Map<String, Integer> lookupMap = DatabaseScanner.getLookupMap(parentTable);
                for (Map.Entry<String, Integer> entry : lookupMap.entrySet()) {
                    lookupTranslations.put(entry.getValue(), entry.getKey());
                }
            }
        }

        //ЗАПИТ ДО БАЗИ ДАНИХ (GROUP BY + COUNT)
        // Ми підраховуємо дані з урахуванням поточних фільтрів пошуку, якщо вони були застосовані
        StringBuilder sql = new StringBuilder("SELECT " + selectedField + ", COUNT(*) AS cnt FROM " + table.Name());
        if (filters != null && !filters.isEmpty()) {
            sql.append(" WHERE ");
            int count = 0;
            for (String colName : filters.keySet()) {
                if (count > 0) sql.append(" AND ");
                sql.append(colName).append(" LIKE ?");
                count++;
            }
        }
        sql.append(" GROUP BY ").append(selectedField).append(" ORDER BY cnt DESC");

        try (Connection conn = DriverManager.getConnection(AppConfig.getDbUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword());
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            // Підставляємо параметри фільтрації, якщо вони є
            if (filters != null && !filters.isEmpty()) {
                int paramIndex = 1;
                for (String value : filters.values()) {
                    pstmt.setString(paramIndex, "%" + value + "%");
                    paramIndex++;
                }
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    // Примусово зчитуємо значення як примітив int
                    int idValue = rs.getInt(selectedField);
                    boolean wasNull = rs.wasNull();
                    int count = rs.getInt("cnt");

                    String displayValue;

                    if (wasNull) {
                        displayValue = "Не вказано (NULL)";
                    } else {
                        displayValue = String.valueOf(idValue);

                        // Перевіряємо наявність текстового імені за чистим int-ключем у мапі
                        if (lookupTranslations.containsKey(idValue)) {
                            displayValue = lookupTranslations.get(idValue);
                        }
                    }

                    model.addRow(new Object[]{displayValue, count + " раз(а)"});
                }
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Помилка при розрахунку статистики СУБД: " + e.getMessage(), "Помилка", JOptionPane.ERROR_MESSAGE);
        }

        // Кнопка повернення назад до вибору стовпця
        JButton backButton = new JButton("⬅ Назад до вибору поля");
        backButton.setPreferredSize(new Dimension(0, 40));
        backButton.setFont(new Font("SansSerif", Font.BOLD, 12));
        backButton.addActionListener(e -> SwingUtilities.invokeLater(() -> {
            dispose();
            new CountWindow(table, allTables, filters);
        }));
        add(backButton, BorderLayout.SOUTH);

        setVisible(true);
    }
}