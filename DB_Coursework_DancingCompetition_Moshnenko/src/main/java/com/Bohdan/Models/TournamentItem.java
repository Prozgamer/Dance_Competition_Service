package com.Bohdan.Models;

/**
 * Компактна модель для відображення турніру в JComboBox.
 * Завдяки record ми отримуємо автоматичний конструктор, методи доступу, equals, hashCode та toString.
 */
public record TournamentItem(int id, String name) {
    @Override
    public String toString() {
        return name; // Визначає, що саме буде відображатися в JComboBox
    }
}