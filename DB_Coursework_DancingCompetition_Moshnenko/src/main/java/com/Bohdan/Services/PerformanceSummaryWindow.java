package com.Bohdan.Services;

import com.Bohdan.Models.Table;

import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.Map;

/**
 * Вікно відображення детальних підсумків та аналітики для конкретного виділеного виступу (Performance).
 * Використовує базу даних представлення (View) для завантаження агрегованої статистики оцінок.
 */
public class PerformanceSummaryWindow extends JFrame {

    public PerformanceSummaryWindow(Table table, Table[] allTables, Map<String, String> filters, int performanceId) {
        setTitle("Аналітичний звіт виступу №" + performanceId);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(550, 500); // Злегка збільшено розмір для додаткових полів етапу та дати
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(15, 15));

        // Головна панель з відступами
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Заголовок звіту
        JLabel headerLabel = new JLabel("ПІДБОРНИЙ АНАЛІТИЧНИЙ ЗВІТ", SwingConstants.CENTER);
        headerLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        headerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(headerLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Панель для красивого карткового виведення інформації
        JPanel cardPanel = new JPanel(new GridLayout(0, 1, 8, 10));
        cardPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 102, 204), 2, true),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        JLabel pairLabel = new JLabel("Танцювальна пара: завантаження...");
        JLabel danceLabel = new JLabel("Танець: завантаження...");
        JLabel compLabel = new JLabel("Змагання: завантаження...");
        JLabel stageLabel = new JLabel("Етап змагання: завантаження...");
        JLabel judgesCountLabel = new JLabel("Оцінило суддів: завантаження...");
        JLabel maxScoreLabel = new JLabel("Найвища оцінка: завантаження...");
        JLabel minScoreLabel = new JLabel("Найнижча оцінка: завантаження...");
        JLabel avgScoreLabel = new JLabel("Середній підсумковий бал: завантаження...");

        // Налаштування шрифтів
        Font boldFont = new Font("SansSerif", Font.BOLD, 14);
        Font regularFont = new Font("SansSerif", Font.PLAIN, 13);

        pairLabel.setFont(boldFont);
        danceLabel.setFont(regularFont);
        compLabel.setFont(regularFont);
        stageLabel.setFont(regularFont);
        judgesCountLabel.setFont(regularFont);
        maxScoreLabel.setFont(regularFont);
        minScoreLabel.setFont(regularFont);

        avgScoreLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        avgScoreLabel.setForeground(new Color(0, 128, 64));

        cardPanel.add(pairLabel);
        cardPanel.add(danceLabel);
        cardPanel.add(compLabel);
        cardPanel.add(stageLabel);
        cardPanel.add(new JSeparator(JSeparator.HORIZONTAL));
        cardPanel.add(judgesCountLabel);
        cardPanel.add(maxScoreLabel);
        cardPanel.add(minScoreLabel);
        cardPanel.add(avgScoreLabel);

        mainPanel.add(cardPanel);
        add(mainPanel, BorderLayout.CENTER);

        // Використовуємо створену вьюшку dbo.vPerformanceSummary
        String sql = "SELECT pair_name, dance_name, style, comp_name, comp_date, stage, sequence_number, " +
                "       judges_count, max_score, min_score, avg_score " +
                "FROM dbo.vPerformanceSummary " +
                "WHERE performance_id = ?";

        try (Connection conn = DriverManager.getConnection(AppConfig.getDbUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword());
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, performanceId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    pairLabel.setText("Танцювальна пара:  " + rs.getString("pair_name"));
                    String danceName = rs.getString("dance_name");
                    String danceStyle = rs.getString("style");
                    String styleStr = (danceStyle != null) ? " (" + danceStyle + ")" : "";

                    danceLabel.setText("Стиль танцю:  " + danceName + styleStr);

                    // Виводимо назву турніру разом із датою проведення
                    Date compDate = rs.getDate("comp_date");
                    String dateStr = compDate != null ? " (" + compDate.toString() + ")" : "";
                    compLabel.setText("Турнір:  " + rs.getString("comp_name") + dateStr);

                    // Виводимо етап та черговість виступу
                    String stage = rs.getString("stage");
                    int seqNum = rs.getInt("sequence_number");
                    String stageStr = stage != null ? stage : "Відбірковий тур";
                    stageLabel.setText("Етап:  " + stageStr + " (Черга виступу №" + seqNum + ")");

                    int judgesCount = rs.getInt("judges_count");
                    if (judgesCount == 0) {
                        judgesCountLabel.setText("Оцінило суддів:  0 (Виступ ще не оцінено)");
                        maxScoreLabel.setText("Найвища оцінка:  —");
                        minScoreLabel.setText("Найнижча оцінка:  —");
                        avgScoreLabel.setText("Середній підсумковий бал:  Немає оцінок");
                        avgScoreLabel.setForeground(Color.RED);
                    } else {
                        judgesCountLabel.setText("Оцінило суддів:  " + judgesCount);
                        maxScoreLabel.setText("Найвища оцінка:  " + String.format("%.2f", rs.getDouble("max_score")) + " балів");
                        minScoreLabel.setText("Найнижча оцінка:  " + String.format("%.2f", rs.getDouble("min_score")) + " балів");
                        avgScoreLabel.setText("Середній підсумковий бал:  " + String.format("%.2f", rs.getDouble("avg_score")) + " балів");
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "Помилка: Виступ із таким ID не знайдено в представленні бази даних.", "Помилка", JOptionPane.ERROR_MESSAGE);
                }
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Помилка отримання даних з представлення: " + e.getMessage(), "Помилка СУБД", JOptionPane.ERROR_MESSAGE);
        }

        // Кнопка закриття
        JButton closeButton = new JButton("⬅ Назад до списку виступів");
        closeButton.setPreferredSize(new Dimension(0, 40));
        closeButton.setFont(new Font("SansSerif", Font.BOLD, 12));
        closeButton.addActionListener(e -> SwingUtilities.invokeLater(() -> {
            dispose();
            new TableScreen(table, allTables, filters);
        }));
        add(closeButton, BorderLayout.SOUTH);

        setVisible(true);
    }
}