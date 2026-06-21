package com.Bohdan.Services;

import com.Bohdan.Models.Table;
import com.Bohdan.Models.TournamentItem;
import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.Map;

public class TournamentReportWindow extends JFrame {
    public TournamentReportWindow(Table table, Table[] allTables, Map<String, String> filters) {
        setTitle("Параметри звіту турніру");
        setSize(400, 250);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(0, 1, 10, 10));

        JComboBox<TournamentItem> tournamentBox = new JComboBox<>();
        JTextField dateField = new JTextField();
        dateField.setToolTipText("Формат YYYY-MM-DD (залиште порожнім для всього турніру)");

        // Завантаження списку турнірів
        DefaultComboBoxModel<TournamentItem> model = new DefaultComboBoxModel<>();
        try (Connection conn = DriverManager.getConnection(AppConfig.getDbUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT competition_id, name FROM Competition")) {
            while (rs.next()) {
                model.addElement(new TournamentItem(rs.getInt("competition_id"), rs.getString("name")));
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Помилка завантаження турнірів: " + e.getMessage());
        }
        tournamentBox.setModel(model);

        JButton generateButton = new JButton("Сформувати звіт");
        generateButton.addActionListener(e -> {
            TournamentItem selected = (TournamentItem) tournamentBox.getSelectedItem();
            if (selected != null) {
                dispose();
                new TournamentResultWindow(selected.id(), selected.toString(), dateField.getText(), table, allTables, filters);
            }
        });

        add(new JLabel("Оберіть турнір:", SwingConstants.CENTER));
        add(tournamentBox);
        add(new JLabel("Дата (YYYY-MM-DD):", SwingConstants.CENTER));
        add(dateField);
        add(generateButton);

        setVisible(true);
    }
}