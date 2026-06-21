package com.Bohdan.Services;

import com.Bohdan.Models.Field;
import com.Bohdan.Models.Table;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Map;

/**
 * Клас CountWindow дозволяє користувачеві обрати стовпець для проведення групування та підрахунку.
 */
public class CountWindow extends JFrame {
    public CountWindow(Table table, Table[] allTables, Map<String, String> filters) {
        String tableName = table.Name();
        setTitle("Розрахунок статистики для: " + tableName);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(400, 250);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(15, 15));

        JPanel mainPanel = new JPanel(new GridLayout(0, 1, 10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel label = new JLabel("Оберіть поле, за яким бажаєте виконати підрахунок:", SwingConstants.CENTER);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        mainPanel.add(label);

        //Розумний пропуск першого автоінкрементного ID без зависання циклу ---
        ArrayList<String> comboBoxItems = new ArrayList<>();
        boolean isFirst = true;
        for (Field field : table.fields()) {
            if (isFirst) {
                isFirst = false; // Помічаємо, що перший (ID) пропущено
                continue;
            }
            comboBoxItems.add(field.Name());
        }

        //Створюємо JComboBox
        JComboBox<String> comboBox = new JComboBox<>(comboBoxItems.toArray(new String[0]));
        mainPanel.add(comboBox);

        add(mainPanel, BorderLayout.CENTER);

        // Панель з кнопками керування
        JPanel controlPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(0, 15, 15, 15));

        JButton countButton = new JButton("Порахувати");
        JButton backButton = new JButton("⬅ Назад до таблиці");

        countButton.addActionListener(ev -> SwingUtilities.invokeLater(() -> {
            String selectedField = (String) comboBox.getSelectedItem();
            if (selectedField != null) {
                dispose();
                // Відкриваємо друге вікно з результатами розрахунку
                new CountedWindow(table, allTables, filters, selectedField);
            }
        }));

        backButton.addActionListener(ev -> SwingUtilities.invokeLater(() -> {
            dispose();
            new TableScreen(table, allTables, filters);
        }));

        controlPanel.add(backButton);
        controlPanel.add(countButton);
        add(controlPanel, BorderLayout.SOUTH);

        setVisible(true);
    }
}