package com.Bohdan.Services;

import com.Bohdan.Models.Table;

import javax.swing.*;
import java.awt.*;

public class MainMenu extends JFrame {
    private Table[] tables;
    public MainMenu(Table[] tables) {
        this.tables = tables;
        int amount_of_tables = tables.length;
        setTitle("Dancing Competition");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        // Створюємо модель та кнопки
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(0, 3, 10, 10)); // 0 рядків = автоматично, 3 колонки

        for(Table table : tables){
                JButton table_choice = new JButton(table.Name());
                panel.add(table_choice);
                table_choice.addActionListener(e -> {
                    SwingUtilities.invokeLater(() -> {
                        new SearchWindow(table, tables);
                    });
                    dispose();
                });
        }

        add(panel);
        setVisible(true);

    }
}