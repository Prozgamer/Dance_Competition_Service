package com.Bohdan.Services;

import com.Bohdan.Models.Table;
import com.Bohdan.Repository.DatabaseScanner;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<Table> tablesList = DatabaseScanner.ScanDatabase();
        if (tablesList.isEmpty()) {
            System.out.println("База порожня або підключення не вдалося!");
            return;
        }
        // Перетворюємо в масив для MainMenu
        Table[] tablesArray = tablesList.toArray(new Table[0]);
        new MainMenu(tablesArray);
    }
}