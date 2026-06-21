package com.Bohdan.Services;

import com.Bohdan.Models.Field;
import com.Bohdan.Models.Table;
import com.Bohdan.Repository.DatabaseModifier;
import com.Bohdan.Repository.DatabaseScanner;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class TableScreen extends JFrame {
    public TableScreen(Table table, Table[] tables) {
        this(table, tables, Map.of());
    }

    public TableScreen(Table table, Table[] allTables, Map<String, String> filters) {
        setTitle("Результати пошуку: " + table.Name());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1500, 450);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));

        // Кнопка Back повертає нас назад у вікно пошуку (Тут тепер ТІЛЬКИ один лісенер)
        JButton backButton = new JButton("⬅ Назад до пошуку");
        backButton.addActionListener(ev -> SwingUtilities.invokeLater(() -> {
            dispose();
            new SearchWindow(table, allTables);
        }));
        add(backButton, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable maintable = new JTable(model);

        // Вмикаємо сортувальник в UI
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        maintable.setRowSorter(sorter);

        // Додаємо колонки
        for(Field field : table.fields()){
            model.addColumn(field.Name());
        }

        // Завантажуємо ТІЛЬКИ відфільтровані дані з бази
        List<Object[]> dataFromTable = DatabaseScanner.getRows(table.Name(), filters);
        for (Object[] row : dataFromTable) {
            model.addRow(row);
        }

        // Кнопка видалення
        JButton deleteButton = new JButton("Видалити виділений рядок");
        controlPanel.add(deleteButton);

        deleteButton.addActionListener(e -> {
            int viewRow = maintable.getSelectedRow();

            if (viewRow == -1) {
                JOptionPane.showMessageDialog(this, "Будь ласка, виберіть рядок у таблиці для видалення!", "Увага", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int modelRow = maintable.convertRowIndexToModel(viewRow);
            int recordId = Integer.parseInt(maintable.getModel().getValueAt(modelRow, 0).toString());

            int confirm = JOptionPane.showConfirmDialog(this,
                    "Ви впевнені, що хочете видалити запис з ID: " + recordId + "?",
                    "Підтвердження видалення", JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                String tableName = table.Name();
                java.util.List<Field> Columns = table.fields();
                String keyColumn = Columns.get(0).Name();
                // Передаємо 'this' замість null для гарного центрування вікна
                boolean isDeleted = DatabaseModifier.deleteRecord(this, tableName, keyColumn, recordId);

                if (isDeleted) {
                    new TableScreen(table, allTables, filters);
                    dispose();
                }
            }
        });

        // Кнопка переходу на екран додавання даних
        JButton createButton = new JButton("Вставити інформацію");
        createButton.addActionListener(ev -> SwingUtilities.invokeLater(() -> {
            dispose();
            new AdditionScreen(table, allTables, filters);
        }));

        // Кнопка переходу на екран зміни даних
        JButton EditButton = new JButton("Змінити інформацію");
        EditButton.addActionListener(ev -> SwingUtilities.invokeLater(() -> {
            dispose();
            new EditScreen(table, allTables, filters);
        }));

        //ДОДАВАННЯ СПЕЦІАЛЬНОЇ КНОПКИ ПІДСУМКУ ДЛЯ ВИСТУПУ
        if ("Performance".equalsIgnoreCase(table.Name())) {
            JButton summaryButton = new JButton("Підсумки виступу");
            summaryButton.setBackground(new Color(255, 215, 0));
            summaryButton.addActionListener(ev -> {
                int row = maintable.getSelectedRow();
                if (row == -1) {
                    JOptionPane.showMessageDialog(this, "Будь ласка, оберіть виступ у таблиці!");
                    return;
                }
                int modelRow = maintable.convertRowIndexToModel(row);
                int perfId = Integer.parseInt(maintable.getModel().getValueAt(modelRow, 0).toString());

                // Передаємо всі необхідні параметри, які вимагає конструктор PerformanceSummaryWindow
                dispose();
                new PerformanceSummaryWindow(table, allTables, filters, perfId);
            });
            controlPanel.add(summaryButton);
        }
        //ДОДАВАННЯ СПЕЦІАЛЬНОЇ КНОПКИ ПІДСУМКУ ДЛЯ ЗМАГАННЯ
        if ("Competition".equalsIgnoreCase(table.Name())) {
            JButton reportButton = new JButton("Звіт по турніру");
            reportButton.setBackground(new Color(135, 206, 235)); // Світло-блакитний колір
            reportButton.addActionListener(ev -> {
                // Викликаємо вікно звіту
                dispose();
                new TournamentReportWindow(table, allTables, filters);
            });
            controlPanel.add(reportButton);
        }

        JButton countButton = new JButton("Зробити розрахунок");
        countButton.addActionListener(ev -> SwingUtilities.invokeLater(() -> {
            dispose();
            new CountWindow(table, allTables, filters);
        }));

        JButton avgButton = new JButton("Розрахунок AVG");
        avgButton.addActionListener(ev -> SwingUtilities.invokeLater(() -> {
            dispose();
            new AvgWindow(table, allTables, filters);
        }));

        JButton judgeCategoryAvgButton = new JButton("AVG за категорією судді");
        judgeCategoryAvgButton.setBackground(new Color(216, 191, 216));
        judgeCategoryAvgButton.addActionListener(ev -> SwingUtilities.invokeLater(() -> {
            dispose();
            new JudgeCategoryReportWindow(table, allTables, filters);
        }));
        controlPanel.add(judgeCategoryAvgButton);
        controlPanel.add(countButton);
        controlPanel.add(avgButton);
        controlPanel.add(createButton);
        controlPanel.add(EditButton);

        JScrollPane scrollPane = new JScrollPane(maintable);
        add(scrollPane, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.SOUTH);

        setVisible(true);
    }
}
