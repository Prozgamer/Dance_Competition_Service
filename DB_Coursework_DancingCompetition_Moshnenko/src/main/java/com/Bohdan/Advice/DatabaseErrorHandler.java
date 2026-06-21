package com.Bohdan.Advice;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

public class DatabaseErrorHandler {

        public static void handleSQLException(Component parent, SQLException e) {
            int errorCode = e.getErrorCode();
            String sqlState = e.getSQLState();
            String message = e.getMessage();

            // 1. Помилки з кастомних тригерів
            if (errorCode == 50000 || message.contains("Помилка!")) {
                JOptionPane.showMessageDialog(parent,
                        message,
                        "Порушення бізнес-правила",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            // 2. Стандартні коди MS SQL Server
            switch (errorCode) {
                case 2627: // Унікальний ключ (Unique Constraint)
                case 2601: // Дублікат індексу
                    JOptionPane.showMessageDialog(parent,
                            "Помилка: Такий запис уже існує в базі даних (порушено унікальність ID або зв'язки полів).",
                            "Дублікат даних",
                            JOptionPane.ERROR_MESSAGE);
                    break;

                case 547: // Порушення Foreign Key або Check констрейнту
                    if (message.contains("DELETE")) {
                        JOptionPane.showMessageDialog(parent,
                                "Не вдалося видалити запис: на нього посилаються інші таблиці бази даних!",
                                "Помилка посилальної цілісності",
                                JOptionPane.ERROR_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(parent,
                                "Помилка: Введені дані порушують правила перевірки банку даних (CHECK або неіснуючий ID зв'язку).",
                                "Некоректні дані",
                                JOptionPane.ERROR_MESSAGE);
                    }
                    break;

                case 208: // Немає такої таблиці
                    JOptionPane.showMessageDialog(parent,
                            "Помилка: Об'єкт бази даних не знайдено.",
                            "Помилка структури SQL",
                            JOptionPane.ERROR_MESSAGE);
                    break;

                default: // Усі інші критичні помилки
                    JOptionPane.showMessageDialog(parent,
                            "Критична помилка бази даних [" + errorCode + "]:\n" + message,
                            "SQL Error",
                            JOptionPane.ERROR_MESSAGE);
                    break;
            }
        }
}
