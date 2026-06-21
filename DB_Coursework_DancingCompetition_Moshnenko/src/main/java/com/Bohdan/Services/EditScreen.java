package com.Bohdan.Services;

import com.Bohdan.Models.Field;
import com.Bohdan.Models.Table;
import com.Bohdan.Repository.DatabaseModifier;
import com.Bohdan.Repository.DatabaseScanner;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Клас EditScreen реалізує концепт інтерактивного редагування Master-Detail.
 * Зверху відображається таблиця, а знизу динамічно малюється форма редагування обраного рядка.
 */
public class EditScreen extends JFrame {
    private JTable mainTable;
    private DefaultTableModel tableModel;

    // Панель, де будуть малюватися поля редагування для обраного рядка
    private JPanel editFormPanel;

    // Елементи керування форми редагування
    private Map<String, JTextField> fieldsMap = new HashMap<>();
    private Map<String, JComboBox<String>> comboMap = new HashMap<>();
    private Map<String, Map<String, Integer>> lookupRegistry = new HashMap<>();

    // Карта для визначення, які колонки за індексом є зовнішніми ключами (FK)
    private Map<Integer, Boolean> isFieldFK = new HashMap<>();

    private Table currentTable;
    private Table[] allTables;
    private Map<String, String> currentFilters;

    public EditScreen(Table table, Table[] tables) {
        this(table, tables, Map.of());
    }

    public EditScreen(Table table, Table[] allTables, Map<String, String> filters) {
        this.currentTable = table;
        this.allTables = allTables;
        this.currentFilters = filters;

        setTitle("Інтерактивне редагування: " + table.Name());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 650);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // --- ВЕРХНЯ КНОПКА НАЗАД ---
        JButton backButton = new JButton("⬅ Назад до пошуку");
        backButton.addActionListener(ev -> SwingUtilities.invokeLater(() -> {
            dispose();
            new SearchWindow(table, allTables);
        }));
        add(backButton, BorderLayout.NORTH);

        // --- АНАЛІЗ ЗОВНІШНІХ КЛЮЧІВ ---
        int index = 0;
        for (Field field : table.fields()) {
            if (field.Name().endsWith("_id") && !field.Name().contains("partner")) {
                String parentTableName = field.Name().substring(0, field.Name().lastIndexOf("_"));
                if (parentTableName.equalsIgnoreCase("coach")) {
                    parentTableName = "Participant";
                }

                // Перевіряємо, чи існує така батьківська таблиця
                boolean exists = false;
                for (Table t : allTables) {
                    if (t.Name().equalsIgnoreCase(parentTableName)) {
                        exists = true;
                        break;
                    }
                }
                isFieldFK.put(index, exists);
            } else {
                isFieldFK.put(index, false);
            }
            index++;
        }

        // --- НАЛАШТУВАННЯ ТАБЛИЦІ ---
        tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Редагування всередині клітинок таблиці заборонено
            }
        };

        for (Field field : table.fields()) {
            tableModel.addColumn(field.Name());
        }

        mainTable = new JTable(tableModel);
        mainTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Завантаження даних з бази
        List<Object[]> dataFromTable = DatabaseScanner.getRows(table.Name(), filters);
        for (Object[] row : dataFromTable) {
            tableModel.addRow(row);
        }

        JScrollPane scrollPane = new JScrollPane(mainTable);
        add(scrollPane, BorderLayout.CENTER);

        // --- ПАНЕЛЬ РЕДАГУВАННЯ (ЗНИЗУ) ---
        JPanel southPanel = new JPanel(new BorderLayout(10, 10));
        southPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        editFormPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        editFormPanel.setBorder(BorderFactory.createTitledBorder("Оберіть рядок в таблиці для редагування"));
        southPanel.add(editFormPanel, BorderLayout.CENTER);

        // Кнопка збереження змін
        JButton saveButton = new JButton("Зберегти зміни в БД");
        saveButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        saveButton.setEnabled(false); // Активується лише коли обрано рядок
        southPanel.add(saveButton, BorderLayout.SOUTH);

        add(southPanel, BorderLayout.SOUTH);

        // --- ЛІСЕНЕР КЛІКУ ПО РЯДКУ ТАБЛИЦІ ---
        mainTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = mainTable.getSelectedRow();
                if (selectedRow != -1) {
                    buildEditForm(selectedRow);
                    saveButton.setEnabled(true);
                }
            }
        });

        // --- ОБРОБНИК ЗБЕРЕЖЕННЯ ---
        saveButton.addActionListener(e -> {
            int selectedRow = mainTable.getSelectedRow();
            if (selectedRow == -1) return;

            try {
                StringBuilder setClause = new StringBuilder();
                List<Object> paramValues = new ArrayList<>();
                String idColumnName = table.fields().get(0).Name();
                Object recordId = mainTable.getValueAt(selectedRow, 0);

                for (Field f : table.fields()) {
                    String columnName = f.Name();
                    if (columnName.equalsIgnoreCase(idColumnName)) continue; // Пропускаємо ID

                    String rawValue = "";
                    if (comboMap.containsKey(columnName)) {
                        String selected = (String) comboMap.get(columnName).getSelectedItem();
                        if (selected != null) {
                            rawValue = String.valueOf(lookupRegistry.get(columnName).get(selected));
                        }
                    } else if (fieldsMap.containsKey(columnName)) {
                        rawValue = fieldsMap.get(columnName).getText().trim();
                    }

                    if (setClause.length() > 0) setClause.append(", ");
                    setClause.append(columnName).append(" = ?");

                    // Конвертація типів
                    if (rawValue.isEmpty()) {
                        paramValues.add(null);
                    } else if (f.Type().toLowerCase().contains("int")) {
                        paramValues.add(Integer.parseInt(rawValue));
                    } else if (f.Type().toLowerCase().contains("float") || f.Type().toLowerCase().contains("numeric")) {
                        paramValues.add(Double.parseDouble(rawValue));
                    } else {
                        paramValues.add(rawValue);
                    }
                }

                paramValues.add(recordId);
                String sql = "UPDATE " + table.Name() + " SET " + setClause + " WHERE " + idColumnName + " = ?";

                boolean isUpdated = DatabaseModifier.updateSimpleRecord(this, sql, paramValues.toArray());
                if (isUpdated) {
                    // Перезавантажуємо вікно для відображення нових даних
                    SwingUtilities.invokeLater(() -> {
                        dispose();
                        new EditScreen(table, allTables, filters);
                    });
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Помилка збереження даних: " + ex.getMessage(), "Помилка", JOptionPane.ERROR_MESSAGE);
            }
        });

        setVisible(true);
    }

    /**
     * Метод динамічно будує форму під таблицею, коли користувач клікає на будь-який рядок
     */
    private void buildEditForm(int selectedRow) {
        editFormPanel.removeAll(); // Очищуємо стару форму
        fieldsMap.clear();
        comboMap.clear();
        lookupRegistry.clear();

        editFormPanel.setBorder(BorderFactory.createTitledBorder("Редагування рядка з ID: " + mainTable.getValueAt(selectedRow, 0)));

        int fieldIndex = 0;
        for (Field field : currentTable.fields()) {
            JLabel label = new JLabel(field.Name() + " (" + field.Type() + "):");
            editFormPanel.add(label);

            Object currentValue = mainTable.getValueAt(selectedRow, fieldIndex);
            String valStr = currentValue != null ? currentValue.toString() : "";

            // 1. Первинний ключ (ID) - показуємо заблокованим
            if (fieldIndex == 0) {
                JTextField idField = new JTextField(valStr, 15);
                idField.setEnabled(false);
                editFormPanel.add(idField);
                fieldsMap.put(field.Name(), idField);
            }
            // 2. Якщо поле є зовнішнім ключем (FK)
            else if (isFieldFK.get(fieldIndex)) {
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
                    Map<String, Integer> items = DatabaseScanner.getLookupMap(parentTable);
                    lookupRegistry.put(field.Name(), items);

                    JComboBox<String> comboBox = new JComboBox<>(items.keySet().toArray(new String[0]));
                    editFormPanel.add(comboBox);
                    comboMap.put(field.Name(), comboBox);

                    // Фокусуємо комбобокс на поточному значенні
                    if (currentValue != null) {
                        try {
                            int currentId = Integer.parseInt(valStr);
                            for (Map.Entry<String, Integer> entry : items.entrySet()) {
                                if (entry.getValue() == currentId) {
                                    comboBox.setSelectedItem(entry.getKey());
                                    break;
                                }
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            // 3. Звичайне текстове або числове поле
            else {
                JTextField textField = new JTextField(valStr, 15);
                editFormPanel.add(textField);
                fieldsMap.put(field.Name(), textField);
            }
            fieldIndex++;
        }

        editFormPanel.revalidate();
        editFormPanel.repaint();
    }
}