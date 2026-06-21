package com.Bohdan.Services;

import com.Bohdan.Models.Field;
import com.Bohdan.Models.Table;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Map;

/**
 * Вікно вибору числового поля для розрахунку середнього значення (AVG).
 */
public class AvgWindow extends JFrame {
    public AvgWindow(Table table, Table[] allTables, Map<String, String> filters) {
        String tableName = table.Name();
        setTitle("Розрахунок середнього для: " + tableName);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(400, 250);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(15, 15));

        JPanel mainPanel = new JPanel(new GridLayout(0, 1, 10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel label = new JLabel("Оберіть числове поле для розрахунку середнього значення:", SwingConstants.CENTER);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        mainPanel.add(label);

        // Фільтруємо метадані СУБД: залишаємо тільки реальні числові показники
        ArrayList<String> numericFields = new ArrayList<>();
        for (Field f : table.fields()) {
            String type = f.Type().toLowerCase();
            // Перевіряємо, чи це числовий тип
            if (type.contains("int") || type.contains("decimal") || type.contains("float") || type.contains("numeric") || type.contains("real")) {
                // Виключаємо системні та зовнішні ID (первинні та зовнішні ключі)
                if (!f.Name().endsWith("_id") && !f.Name().equalsIgnoreCase("id")) {
                    numericFields.add(f.Name());
                }
            }
        }

        JComboBox<String> comboBox = new JComboBox<>(numericFields.toArray(new String[0]));
        mainPanel.add(comboBox);
        add(mainPanel, BorderLayout.CENTER);

        // Панель керування (кнопки)
        JPanel controlPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(0, 15, 15, 15));

        JButton calculateButton = new JButton("Розрахувати середнє");
        JButton backButton = new JButton("⬅ Назад до таблиці");

        // Якщо в таблиці немає відповідних числових колонок — блокуємо розрахунок
        if (numericFields.isEmpty()) {
            comboBox.addItem("Числові поля відсутні");
            comboBox.setEnabled(false);
            calculateButton.setEnabled(false);
        }

        calculateButton.addActionListener(ev -> SwingUtilities.invokeLater(() -> {
            String selectedField = (String) comboBox.getSelectedItem();
            if (selectedField != null) {
                dispose();
                // Відкриваємо вікно з готовим результатом розрахунку
                new AvgResultWindow(table, allTables, filters, selectedField);
            }
        }));

        backButton.addActionListener(ev -> SwingUtilities.invokeLater(() -> {
            dispose();
            new TableScreen(table, allTables, filters);
        }));

        controlPanel.add(backButton);
        controlPanel.add(calculateButton);
        add(controlPanel, BorderLayout.SOUTH);

        setVisible(true);
    }
}