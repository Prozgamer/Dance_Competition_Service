--Реалізація через курсор
SET STATISTICS TIME ON;

DECLARE @PerfID INT, @Style NVARCHAR(100);
DECLARE @AvgScore FLOAT, @JCount INT;

-- Оголошуємо курсор для перебору виступів
DECLARE Cursor1 CURSOR FOR
SELECT p.performance_id, d.style
FROM dbo.Performance p
JOIN dbo.Dance d ON p.dance_id = d.dance_id
WHERE p.competition_id = 1;

OPEN Cursor1;

FETCH NEXT FROM Cursor1 INTO @PerfID, @Style;

WHILE @@FETCH_STATUS = 0
BEGIN
    -- Розраховуємо агрегати вручну для кожного рядка (імітація JOIN/GROUP BY)
    SELECT @AvgScore = AVG(value), @JCount = COUNT(judge_id)
    FROM dbo.Score
    WHERE performance_id = @PerfID;

    -- Виводимо результат лише якщо він відповідає умові HAVING
    IF @AvgScore > 70
        PRINT 'PerfID: ' + CAST(@PerfID AS VARCHAR) + ' | Style: ' + @Style + ' | Avg: ' + CAST(@AvgScore AS VARCHAR);

    FETCH NEXT FROM Cursor1 INTO @PerfID, @Style;
END;

CLOSE Cursor1;
DEALLOCATE Cursor1;

SET STATISTICS TIME OFF;

-- 1. Підготовка змінних для роботи
DECLARE @PerfID INT, @Style NVARCHAR(100);
DECLARE @AvgScore FLOAT, @JCount INT;

-- 2. Оголошуємо курсор ОДИН РАЗ (DECLARE)
DECLARE Cursor2 CURSOR FOR
SELECT p.performance_id, d.style
FROM dbo.Performance p
JOIN dbo.Dance d ON p.dance_id = d.dance_id
WHERE p.competition_id = 1;

-- ПЕРШИЙ ЗАПУСК (Для рядка №4 таблиці)
PRINT '=== ПЕРШИЙ ЗАПУСК КУРСОРУ ===';
SET STATISTICS TIME ON; -- Вмикаємо заміри часу

OPEN Cursor2;

FETCH NEXT FROM Cursor2 INTO @PerfID, @Style;

WHILE @@FETCH_STATUS = 0
BEGIN
    -- Розрахунок середнього балу для поточного виступу
    SELECT @AvgScore = AVG(CAST(value AS FLOAT)), @JCount = COUNT(judge_id)
    FROM dbo.Score
    WHERE performance_id = @PerfID;

    -- Умова фільтрації (аналог HAVING AVG > 70)
    IF @AvgScore > 70
    BEGIN
        PRINT 'PerfID: ' + CAST(@PerfID AS VARCHAR) + ' | Style: ' + @Style + ' | Avg Score: ' + CAST(@AvgScore AS VARCHAR);
    END

    FETCH NEXT FROM Cursor2 INTO @PerfID, @Style;
END;

CLOSE Cursor2; -- Закриваємо, але НЕ видаляємо (DEALLOCATE не робимо)
SET STATISTICS TIME OFF; -- Вимикаємо заміри часу


-- ДРУГИЙ ЗАПУСК (Для рядка №5 таблиці)
PRINT '=== ДРУГИЙ ЗАПУСК КУРСОРУ (БЕЗ DEALLOCATE) ===';
SET STATISTICS TIME ON;

OPEN Cursor2; -- Просто відкриваємо знову вже існуючий курсор

FETCH NEXT FROM Cursor2 INTO @PerfID, @Style;

WHILE @@FETCH_STATUS = 0
BEGIN
    -- Та сама логіка
    SELECT @AvgScore = AVG(CAST(value AS FLOAT)), @JCount = COUNT(judge_id)
    FROM dbo.Score
    WHERE performance_id = @PerfID;

    IF @AvgScore > 70
    BEGIN
        PRINT 'PerfID: ' + CAST(@PerfID AS VARCHAR) + ' | Style: ' + @Style + ' | Avg Score: ' + CAST(@AvgScore AS VARCHAR);
    END

    FETCH NEXT FROM Cursor2 INTO @PerfID, @Style;
END;

CLOSE Cursor2;
SET STATISTICS TIME OFF;


-- 3. КІНЦЕВИЙ ЕТАП: Видаляємо курсор з пам'яті зовсім
DEALLOCATE Cursor2;