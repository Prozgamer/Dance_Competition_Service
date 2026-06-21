USE DancingCompetition;
GO

/*1. ОБМЕЖЕННЯ АТРИБУТА ТА ДОМЕНА*/
-- Використовуємо CHECK для обмеження діапазону значень (домен балів)
-- Тісно пов'язане з конкретним атрибутом 'value'
ALTER TABLE dbo.Score
ADD CONSTRAINT CHK_Score_Value_Range
CHECK (value >= 0 AND value <= 100);
-- Перевіряємо, що мінімальний вік меньший за максимальний
ALTER TABLE dbo.Category
ADD CONSTRAINT CHK_Category_Age_Range
CHECK (age_max >= age_min OR age_max IS NULL);

/*2. ОБМЕЖЕННЯ КОРТЕЖУ*/
-- Перевірка логічного зв'язку між двома полями одного рядка
ALTER TABLE dbo.Competition
ADD CONSTRAINT CHK_Competition_Duration
CHECK (end_date >= start_date);

/*3. ОБМЕЖЕННЯ ВІДНОШЕННЯ*/
-- Гарантуємо унікальність пари в межах одного змагання для конкретного танцю
-- Це обмеження діє на рівні всієї таблиці Performance
ALTER TABLE dbo.Performance
ADD CONSTRAINT UQ_Perf_Pair_Dance_Comp
UNIQUE (pair_id, dance_id, competition_id);
-- Гарантуємо, що 1 суддя може оцінити виступ тільки 1 раз
ALTER TABLE dbo.Score
ADD CONSTRAINT UQ_Score_performance_judge UNIQUE (performance_id, judge_id);

/*4. ОБМЕЖЕННЯ БАЗИ ДАНИХ (Посилальна цілісність)*/
-- Встановлюємо зв'язок між таблицями з правилом каскадного видалення
ALTER TABLE dbo.Score
ADD CONSTRAINT FK_Score_Performance_Action
FOREIGN KEY (performance_id) REFERENCES dbo.Performance(performance_id)
ON DELETE CASCADE;

SELECT
    tab.name AS TableName,
    obj.name AS ConstraintName,
    obj.type_desc AS ConstraintType,
    def.definition
FROM sys.objects obj
INNER JOIN sys.objects tab ON obj.parent_object_id = tab.object_id
LEFT JOIN sys.check_constraints def ON obj.object_id = def.object_id
WHERE obj.type_desc LIKE '%CONSTRAINT'
ORDER BY TableName;