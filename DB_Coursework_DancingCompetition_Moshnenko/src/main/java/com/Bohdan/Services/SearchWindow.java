package com.Bohdan.Services;

import com.Bohdan.Models.Field;
import com.Bohdan.Models.Table;
import com.Bohdan.Repository.DatabaseScanner;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Клас SearchWindow реалізує інтерактивну форму пошуку та фільтрації даних.
 * Автоматично перетворює зовнішні ключі (FK) у випадаючі списки для зручного вибору.
 */
public class SearchWindow extends JFrame {
    private Map<String, JTextField> fieldsMap = new HashMap<>();
    private Map<String, JComboBox<String>> comboMap = new HashMap<>();
    private Map<String, Map<String, Integer>> lookupRegistry = new HashMap<>();
    private static final String ANY_VALUE_OPTION = "— Будь-яке значення —";

    public SearchWindow(Table table, Table[] allTables) {
        String tableName = table.Name();
        setTitle("Пошук та фільтрація в: " + tableName);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(500, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Кнопка для повернення назад до вибору таблиць
        JButton backButton = new JButton("⬅ До списку таблиць");
        backButton.addActionListener(ev -> SwingUtilities.invokeLater(() -> {
            new MainMenu(allTables);
            dispose();
        }));
        add(backButton, BorderLayout.NORTH);

        // Головна панель з відступами
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Динамічна панель полів
        JPanel fieldsPanel = new JPanel(new GridLayout(0, 2, 10, 15));

        for (Field field : table.fields()) {
            JLabel label = new JLabel(field.Name() + " (" + field.Type() + "):");
            fieldsPanel.add(label);

            // МОДИФІКАЦІЯ ТУТ: Перевіряємо, чи є поле зовнішнім ключем (FK),
            // але виключаємо партнерів ТА performance_id (завжди лишаємо їх текстовими)
            if (field.Name().endsWith("_id")
                    && !field.Name().contains("partner")
                    && !field.Name().equalsIgnoreCase("performance_id")) {

                String parentTableName = field.Name().substring(0, field.Name().lastIndexOf("_"));
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
                    // Витягуємо дані зв'язків {"ПІБ/Назва" -> ID}
                    Map<String, Integer> items = DatabaseScanner.getLookupMap(parentTable);
                    lookupRegistry.put(field.Name(), items);

                    // Готуємо список елементів для комбобоксу, де перший пункт — без фільтрації
                    ArrayList<String> comboBoxItems = new ArrayList<>();
                    comboBoxItems.add(ANY_VALUE_OPTION);
                    comboBoxItems.addAll(items.keySet());

                    JComboBox<String> comboBox = new JComboBox<>(comboBoxItems.toArray(new String[0]));
                    fieldsPanel.add(comboBox);
                    comboMap.put(field.Name(), comboBox);
                } else {
                    JTextField textField = new JTextField(15);
                    fieldsPanel.add(textField);
                    fieldsMap.put(field.Name(), textField);
                }
            } else {
                // Звичайні текстові, числові поля або наш performance_id для вільного введення
                JTextField textField = new JTextField(15);
                fieldsPanel.add(textField);
                fieldsMap.put(field.Name(), textField);
            }
        }

        mainPanel.add(new JScrollPane(fieldsPanel), BorderLayout.CENTER);
        add(mainPanel, BorderLayout.CENTER);

        // Кнопка запуску пошуку
        JButton searchButton = new JButton("Знайти дані");
        searchButton.setPreferredSize(new Dimension(0, 45));
        searchButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        this.getRootPane().setDefaultButton(searchButton);

        searchButton.addActionListener(e -> {
            Map<String, String> filters = new HashMap<>();

            // 1. Збираємо значення зі звичайних текстових полів (включаючи performance_id)
            for (Map.Entry<String, JTextField> entry : fieldsMap.entrySet()) {
                String value = entry.getValue().getText().trim();
                if (!value.isEmpty()) {
                    filters.put(entry.getKey(), value);
                }
            }

            // 2. Збираємо значення з випадаючих списків (FK)
            for (Map.Entry<String, JComboBox<String>> entry : comboMap.entrySet()) {
                String columnName = entry.getKey();
                String selected = (String) entry.getValue().getSelectedItem();

                // Якщо обрано реальне значення (не "Будь-яке"), то знаходимо його ID в реєстрі
                if (selected != null && !selected.equals(ANY_VALUE_OPTION)) {
                    Integer idVal = lookupRegistry.get(columnName).get(selected);
                    if (idVal != null) {
                        filters.put(columnName, String.valueOf(idVal));
                    }
                }
            }

            // Відкриваємо екран результатів пошуку із застосованими фільтрами
            SwingUtilities.invokeLater(() -> {
                dispose();
                new TableScreen(table, allTables, filters);
            });
        });

        add(searchButton, BorderLayout.SOUTH);
        setVisible(true);
    }
}
