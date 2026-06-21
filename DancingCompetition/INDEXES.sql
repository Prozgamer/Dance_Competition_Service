USE DancingCompetition;
GO

-- 1. Кластеризований індекс (Clustered)
-- Створюється автоматично з PK. Приклад явного визначення для таблиці Dance:
-- ALTER TABLE dbo.Dance ADD CONSTRAINT PK_Dance_ID PRIMARY KEY CLUSTERED (dance_id);

-- 2. Некластеризований індекс для прискорення пошуку учасників за прізвищем
CREATE NONCLUSTERED INDEX IX_Participant_LastName
ON dbo.Participant (last_name);
GO

-- 3. Унікальний індекс для забезпечення унікальності ISO-кодів країн
CREATE UNIQUE NONCLUSTERED INDEX UQ_Country_iso
ON dbo.Country (iso_code);
GO

-- 4. Покриваючий індекс із включеними стовпцями для оптимізації агрегації балів
CREATE NONCLUSTERED INDEX IX_Score_Performance_Value
ON dbo.Score (performance_id)
INCLUDE (value);
GO

-- 5. Фільтрований індекс для оптимізації аналітичних вибірок високих оцінок
CREATE NONCLUSTERED INDEX IX_Score_HighValue
ON dbo.Score (value)
WHERE value > 90;
GO

-- 6. Системний скрипт для моніторингу фрагментації та фізичного стану індексів
SELECT
    i.name AS [Ім’я індексу],
    i.type_desc AS [Тип індексу],
    CASE
        WHEN i.is_unique = 1 THEN 'Unique'
        ELSE 'Not Unique'
    END AS [Унікальність],
    FORMAT(ips.avg_fragmentation_in_percent, 'N2') + ' %' AS [Рівень фрагментації],
    t.name AS [Таблиця],
    ips.page_count AS [Кількість сторінок]
FROM sys.indexes i
INNER JOIN sys.tables t ON i.object_id = t.object_id
INNER JOIN sys.dm_db_index_physical_stats(DB_ID(), NULL, NULL, NULL, 'LIMITED') ips
    ON i.object_id = ips.object_id AND i.index_id = ips.index_id
WHERE t.is_ms_shipped = 0
  AND i.name IS NOT NULL
ORDER BY t.name, [Рівень фрагментації] DESC;
GO

