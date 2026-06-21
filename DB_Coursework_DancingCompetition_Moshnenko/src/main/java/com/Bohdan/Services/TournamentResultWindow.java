package com.Bohdan.Services;

import com.Bohdan.Models.Table;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TournamentResultWindow extends JFrame {

    // Внутрішній клас для зручного сортування результатів у Java
    private static class DanceStat {
        String name;
        int count;
        String avg;
        String max;

        DanceStat(String name, int count, String avg, String max) {
            this.name = name;
            this.count = count;
            this.avg = avg;
            this.max = max;
        }
    }

    public TournamentResultWindow(int compId, String compName, String date, Table table1, Table[] allTables, Map<String, String> filters) {
        setTitle("Аналітика турніру: " + compName);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // ПАНЕЛЬ ТОП-3 ПАР
        JPanel topPanel = new JPanel();
        topPanel.setBorder(BorderFactory.createTitledBorder("ТОП-3 пари турніру"));
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        try (Connection conn = DriverManager.getConnection(AppConfig.getDbUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword());
             PreparedStatement pstmt = conn.prepareStatement("SELECT pair_name, avg_score FROM dbo.vTopPairs WHERE competition_id = ?")) {

            pstmt.setInt(1, compId);
            try (ResultSet rs = pstmt.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    double avgScore = rs.getDouble("avg_score");
                    JLabel label = new JLabel(rank + ". " + rs.getString("pair_name") + " — Середній бал: " + String.format("%.2f", avgScore));
                    label.setFont(new Font("SansSerif", Font.BOLD, 14));
                    topPanel.add(label);
                    rank++;
                }
                if (rank == 1) {
                    topPanel.add(new JLabel("Для цього турніру ще немає виставлених оцінок або пар."));
                }
            }
        } catch (SQLException e) {
            topPanel.add(new JLabel("Дані для ТОП-3 недоступні."));
        }

        // ОСНОВНА ТАБЛИЦЯ СТАТИСТИКИ ЗА ТАНЦЯМИ
        DefaultTableModel model = new DefaultTableModel(new String[]{"Танець", "К-ть виступів", "Середній бал", "Макс. бал"}, 0);
        JTable table = new JTable(model);

        List<DanceStat> statList = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(AppConfig.getDbUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword())) {

            List<String> allDances = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 // ДОДАЄМО DISTINCT СЮДИ:
                 ResultSet rs = stmt.executeQuery("SELECT DISTINCT name FROM dbo.Dance ORDER BY name")) {
                while (rs.next()) {
                    allDances.add(rs.getString("name"));
                }
            }

            // 2. Готуємо SQL-запит для збору статистики
            StringBuilder statsSql = new StringBuilder(
                    "SELECT COUNT(DISTINCT p.pair_id) AS total_perf, " +
                            "       AVG(CAST(s.value AS FLOAT)) AS avg_score, " +
                            "       MAX(s.value) AS max_score " +
                            "FROM dbo.Performance p " +
                            "INNER JOIN dbo.Dance d ON p.dance_id = d.dance_id " +
                            "LEFT JOIN dbo.Score s ON p.performance_id = s.performance_id " +
                            "WHERE p.competition_id = ? AND d.name = ?"
            );

            if (date != null && !date.isEmpty()) {
                statsSql.append(" AND CAST(p.scheduled_datetime AS DATE) = ?");
            }

            try (PreparedStatement pstmt = conn.prepareStatement(statsSql.toString())) {
                for (String danceName : allDances) {
                    pstmt.setInt(1, compId);
                    pstmt.setString(2, danceName);

                    if (date != null && !date.isEmpty()) {
                        pstmt.setString(3, date.trim());
                    }

                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            int totalPerformances = rs.getInt("total_perf");
                            double avgScore = rs.getDouble("avg_score");
                            boolean isAvgNull = rs.wasNull();
                            double maxScore = rs.getDouble("max_score");
                            boolean isMaxNull = rs.wasNull();

                            String avgScoreStr = (totalPerformances == 0 || isAvgNull) ? "0.00" : String.format("%.2f", avgScore);
                            String maxScoreStr = (totalPerformances == 0 || isMaxNull) ? "0.00" : String.format("%.2f", maxScore);

                            // Тимчасово зберігаємо об'єкт у список
                            statList.add(new DanceStat(danceName, totalPerformances, avgScoreStr, maxScoreStr));
                        }
                    }
                }
            }

            // 3. СОРТУВАННЯ: спочатку ті, де більше виступів (descending).
            // Якщо к-ть однакова (наприклад, нулі) — сортуємо за алфавітом назви танцю.
            statList.sort((a, b) -> {
                if (b.count != a.count) {
                    return Integer.compare(b.count, a.count);
                }
                return a.name.compareTo(b.name);
            });

            // 4. Заливаємо вже відсортовані дані в Swing-модель таблиці
            for (DanceStat stat : statList) {
                model.addRow(new Object[]{stat.name, stat.count, stat.avg, stat.max});
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Помилка завантаження статистики танців: " + e.getMessage());
        }

        // КНОПКА НАЗАД
        JButton closeButton = new JButton("⬅ Назад до списку змагань");
        closeButton.setPreferredSize(new Dimension(0, 40));
        closeButton.setFont(new Font("SansSerif", Font.BOLD, 12));
        closeButton.addActionListener(e -> SwingUtilities.invokeLater(() -> {
            dispose();
            new TableScreen(table1, allTables, filters);
        }));

        // Компонування інтерфейсу
        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(closeButton, BorderLayout.SOUTH);

        setVisible(true);
    }
}