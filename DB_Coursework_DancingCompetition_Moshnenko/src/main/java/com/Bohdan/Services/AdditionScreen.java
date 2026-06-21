package com.Bohdan.Services;

import com.Bohdan.Models.Field;
import com.Bohdan.Models.Table;
import com.Bohdan.Repository.DatabaseModifier;
import com.Bohdan.Repository.DatabaseScanner;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AdditionScreen extends JFrame {
    // Мапа для звичайних текстових полів
    private Map<String, JTextField> fieldsMap = new HashMap<>();
    // Мапа для випадаючих списків (JComboBox)
    private Map<String, JComboBox<String>> comboMap = new HashMap<>();
    // Реєстр мап зв'язків {"Назва" -> ID} для кожного зовнішнього ключа
    private Map<String, Map<String, Integer>> lookupRegistry = new HashMap<>();

    public AdditionScreen(Table table, Table[] tables, Map<String, String> filters) {
        String tableName = table.Name();
        setTitle("Додавання запису в: " + tableName);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(500, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JButton backButton = new JButton("⬅ Назад до таблиці");
        backButton.addActionListener(ev -> SwingUtilities.invokeLater(() -> {
            dispose();
            new TableScreen(table, tables, filters);
        }));
        add(backButton, BorderLayout.NORTH);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel fieldsPanel = new JPanel(new GridLayout(0, 2, 10, 15));

        // РОЗУМНЕ ВИЗНАЧЕННЯ СУРОГАТНОГО ID
        String firstFieldName = table.fields().get(0).Name().toLowerCase();
        boolean hasSurrogateId = firstFieldName.contains(tableName.toLowerCase() + "_")
                || firstFieldName.equals(tableName.toLowerCase() + "id")
                || firstFieldName.equals("id");

        boolean isFirstSkipped = !hasSurrogateId;

        // Генерація інтерфейсу на основі полів таблиці
        for (Field field : table.fields()) {
            if (!isFirstSkipped) {
                isFirstSkipped = true; // Пропускаємо автоінкрементний ID, якщо він є
                continue;
            }

            // Мітка для поля
            JLabel label = new JLabel(field.Name() + " (" + field.Type() + "):");
            fieldsPanel.add(label);

            // МОДИФІКАЦІЯ: Якщо це performance_id, робимо його текстовим полем!
            if (field.Name().equalsIgnoreCase("performance_id")) {
                JTextField textField = new JTextField(15);
                // Можна додати підказку для користувача
                textField.setToolTipText("Введіть ID виступу (число) вручну");
                fieldsPanel.add(textField);
                fieldsMap.put(field.Name(), textField);
            }
            // Для всіх інших зовнішніх ключів залишаємо зручні комбобокси
            else if (field.Name().endsWith("_id") && !field.Name().contains("partner")) {
                String parentTableName = field.Name().substring(0, field.Name().lastIndexOf("_"));

                if (parentTableName.equalsIgnoreCase("coach")) {
                    parentTableName = "Participant";
                }

                Table parentTable = null;
                for (Table t : tables) {
                    if (t.Name().equalsIgnoreCase(parentTableName)) {
                        parentTable = t;
                        break;
                    }
                }

                if (parentTable != null) {
                    Map<String, Integer> items = DatabaseScanner.getLookupMap(parentTable);
                    lookupRegistry.put(field.Name(), items);

                    JComboBox<String> comboBox = new JComboBox<>(items.keySet().toArray(new String[0]));
                    fieldsPanel.add(comboBox);
                    comboMap.put(field.Name(), comboBox);
                } else {
                    JTextField textField = new JTextField(15);
                    fieldsPanel.add(textField);
                    fieldsMap.put(field.Name(), textField);
                }
            } else {
                // Створення звичайних текстових полів
                JTextField textField = new JTextField(15);
                fieldsPanel.add(textField);
                fieldsMap.put(field.Name(), textField);
            }
        }

        mainPanel.add(new JScrollPane(fieldsPanel), BorderLayout.CENTER);
        add(mainPanel, BorderLayout.CENTER);

        JButton createButton = new JButton("Create");
        createButton.setPreferredSize(new Dimension(0, 40));
        createButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        this.getRootPane().setDefaultButton(createButton);

        createButton.addActionListener(e -> {
            boolean isInserted = false;
            try {
                // ВАРІАНТ 1: Спеціалізоване додавання Учасника
                if (tableName.equalsIgnoreCase("Participant")) {
                    String firstName = fieldsMap.get("first_name").getText().trim();
                    String lastName = fieldsMap.get("last_name").getText().trim();
                    String birthDate = fieldsMap.get("birth_date").getText().trim();
                    String gender = fieldsMap.get("gender").getText().trim();

                    String selectedCountry = (String) comboMap.get("country_id").getSelectedItem();
                    int countryId = lookupRegistry.get("country_id").get(selectedCountry);

                    isInserted = DatabaseModifier.registerParticipant(this, firstName, lastName, birthDate, gender, countryId);

                    // ВАРІАНТ 2: ДИНАМІЧНИЙ INSERT (Тут додається оцінка з текстовим performance_id)
                } else {
                    StringBuilder columns = new StringBuilder();
                    StringBuilder placeholders = new StringBuilder();
                    java.util.List<Object> paramValues = new ArrayList<>();

                    for (Field f : table.fields()) {
                        String columnName = f.Name();
                        String rawValue = "";

                        if (comboMap.containsKey(columnName)) {
                            String selected = (String) comboMap.get(columnName).getSelectedItem();
                            if (selected != null && lookupRegistry.get(columnName) != null) {
                                rawValue = String.valueOf(lookupRegistry.get(columnName).get(selected));
                            }
                        } else if (fieldsMap.containsKey(columnName)) {
                            rawValue = fieldsMap.get(columnName).getText().trim();
                        } else {
                            continue; // Пропускаємо автоінкрементний ID самої таблиці
                        }

                        if (rawValue.isEmpty()) continue;

                        if (columns.length() > 0) {
                            columns.append(", ");
                            placeholders.append(", ");
                        }
                        columns.append(columnName);
                        placeholders.append("?");

                        // Оскільки performance_id має тип INT, Java автоматично спарсить текст у число тут:
                        if (f.Type().toLowerCase().contains("int")) {
                            paramValues.add(Integer.parseInt(rawValue));
                        } else if (f.Type().toLowerCase().contains("float") || f.Type().toLowerCase().contains("numeric")) {
                            paramValues.add(Double.parseDouble(rawValue));
                        } else {
                            paramValues.add(rawValue);
                        }
                    }

                    String sql = "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ")";
                    isInserted = DatabaseModifier.insertSimpleRecord(this, sql, paramValues.toArray());
                }

                if (isInserted) {
                    SwingUtilities.invokeLater(() -> new TableScreen(table, tables, filters));
                    dispose();
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Помилка введення: Числові поля мають містити лише цифри! Перевірте правильність ID виступу або балу.", "Помилка валідації", JOptionPane.ERROR_MESSAGE);
            } catch (NullPointerException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Критична помилка: Не знайдено компонент поля або значення в базі даних.", "Помилка", JOptionPane.ERROR_MESSAGE);
            }
        });

        add(createButton, BorderLayout.SOUTH);
        setVisible(true);
    }
}