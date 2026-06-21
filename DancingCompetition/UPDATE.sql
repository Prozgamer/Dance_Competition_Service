USE DancingCompetition;
GO

-- Припустимо, суддя John Smith отримав нову категорію
UPDATE dbo.Judge
SET qualification = 'International Master'
WHERE last_name = 'Smith' AND first_name = 'John';

-- Оновлюємо опис рівня для всіх категорій 'Junior'
UPDATE dbo.Category
SET level = 'Beginner-Intermediate'
WHERE category_name = 'Junior';

-- Якщо змагання затримуються, переносимо всі виступи на 30 хвилин вперед
UPDATE dbo.Performance
SET scheduled_datetime = DATEADD(MINUTE, 30, scheduled_datetime)
WHERE competition_id = (SELECT TOP 1 competition_id FROM dbo.Competition WHERE name LIKE '%Cup%');

-- Видаляємо оцінки конкретного виступу (наприклад, через апеляцію)
DELETE FROM dbo.Score
WHERE performance_id = 1;

-- Видаляємо виступ пари, якщо вони дискваліфіковані або знялися
DELETE FROM dbo.Performance
WHERE pair_id = (SELECT TOP 1 pair_id FROM dbo.Pair WHERE name LIKE '%Smith%');

-- Оновлюємо контактну інформацію для всіх учасників
-- Формуємо номер телефону як '+380-50-' + ID учасника
UPDATE dbo.Participant
SET contact_info = '+380-50-' + RIGHT('000' + CAST(participant_id AS NVARCHAR(10)), 3)
WHERE contact_info IS NULL;
GO


PRINT 'UPDATE.SQL executed successfully!';
GO


