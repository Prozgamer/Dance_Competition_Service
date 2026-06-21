USE DancingCompetition;
GO
-- Створення представлення для адміністратора (без конфіденційних даних)
CREATE OR ALTER VIEW vParticipantNames AS
SELECT
    participant_id,
    first_name,
    last_name,
    gender
FROM dbo.Participant;
GO
SELECT TOP 10 * FROM vParticipantNames;
GO

--Якщо, нарпиклад нам треба тільки ім'я та фамілія, то можно зробити запит ніби до звичайної таблиці
SELECT TOP 10
    first_name,
    last_name
FROM vParticipantNames


-- Створюємо представлення для списку учасників-чоловіків
CREATE OR ALTER VIEW vMaleParticipants AS
SELECT
    participant_id,
    first_name,
    last_name,
    gender,
    country_id
FROM dbo.Participant
WHERE gender = 'M';
GO


-- Створення складного представлення для перегляду результатів учасників
CREATE OR ALTER VIEW vParticipantResults AS
SELECT
    p.participant_id,
    p.first_name,
    p.last_name,
    perf.performance_id,
    s.value AS score_value,
    s.criteria
FROM dbo.Participant p
JOIN dbo.Pair_member pm ON p.participant_id = pm.participant_id
JOIN dbo.Performance perf ON pm.pair_id = perf.pair_id
JOIN dbo.Score s ON perf.performance_id = s.performance_id;
GO

-- Створюємо представлення для підрахунку активності учасників
CREATE OR ALTER VIEW vParticipantPerformanceCount AS
SELECT
    p.participant_id,
    p.first_name,
    p.last_name,
    COUNT(pm.pair_id) AS PerformanceCount
FROM dbo.Participant p
LEFT JOIN dbo.Pair_member pm ON p.participant_id = pm.participant_id
-- Групуємо по учаснику, щоб функція COUNT знала, для кого саме рахувати виступи
GROUP BY p.participant_id, p.first_name, p.last_name;
GO

-- Створюємо представлення на основі попереднього
CREATE OR ALTER VIEW vActiveParticipants AS
SELECT
    participant_id,
    first_name,
    last_name,
    PerformanceCount
FROM vParticipantPerformanceCount -- Звертаємось до вже створеної в'ю
WHERE PerformanceCount >= 2;
GO

--Додаємо поле country
ALTER VIEW vActiveParticipants AS
SELECT
    v.participant_id,
    v.first_name,
    v.last_name,
    v.PerformanceCount,
    c.country_name AS country
FROM vParticipantPerformanceCount v-- Звертаємось до вже створеної в'ю
JOIN dbo.Participant p ON v.participant_id = p.participant_id
JOIN dbo.Country c ON p.country_id = c.country_id
GO

CREATE OR ALTER VIEW vPerformanceSummaryAliased AS
SELECT
    performance_id AS ID_Виступу, --Виористовуємо псевдоніми
    pair_id AS Номер_Пари,
    scheduled_datetime AS Час_Виступу,
    stage AS Номер_Сцени
FROM dbo.Performance;
GO

-- Створюємо представлення, де база сама рахує бонусні бали
CREATE OR ALTER VIEW vScoreWithBonus AS
SELECT
    score_id,
    performance_id,
    criteria,
    value AS BaseScore,
    -- Обчислюваний стовпець: додаємо 15% бонусу до базової оцінки
    value * 1.15 AS FinalScoreWithBonus
FROM dbo.Score;
GO

-- 1. Створюємо представлення для високих оцінок (більше 80 балів)
CREATE OR ALTER VIEW vHighScores AS
SELECT
    score_id,
    performance_id,
    judge_id,
    criteria,
    value
FROM dbo.Score
WHERE value > 80
WITH CHECK OPTION; -- Ця опція забороняє додавати бали <= 80 через це в'ю
GO

-- 1. Створюємо зашифроване представлення
CREATE OR ALTER VIEW vEncryptedParticipants
WITH ENCRYPTION
AS
SELECT
    participant_id,
    first_name,
    last_name
FROM dbo.Participant;
GO

USE DancingCompetition;
GO

-- 1. Створюємо представлення для публічного розкладу (мінімум даних)
CREATE OR ALTER VIEW vPublicPerformanceSchedule AS
SELECT
    performance_id,
    scheduled_datetime,
    stage
FROM dbo.Performance;
GO

-- 2. Створення технічного користувача
IF NOT EXISTS (SELECT * FROM sys.database_principals WHERE name = 'ReadOnlyUser')
BEGIN
    CREATE USER ReadOnlyUser WITHOUT LOGIN;
END
GO

-- 3. НАДАННЯ ПРАВ (Головна частина завдання)
-- Тепер користувач може бачити ТІЛЬКИ розклад, але не саму таблицю Performance
GRANT SELECT ON vPublicPerformanceSchedule TO ReadOnlyUser;
GO
-- Створення представлення для підсумкової таблиці результатів
CREATE OR ALTER VIEW dbo.vPerformanceSummary AS
SELECT
    p.performance_id,
    p.pair_id,
    COALESCE(pr.name, N'Без назви') AS pair_name,
    COALESCE(d.name, N'Невідомий танець') AS dance_name,
    COALESCE(c.name, N'Невідоме змагання') AS comp_name,
    d.style AS style,
    c.start_date AS comp_date,
    p.stage,
    p.sequence_number,
    COUNT(s.score_id) AS judges_count,
    MAX(s.value) AS max_score,
    MIN(s.value) AS min_score,
    AVG(CAST(s.value AS FLOAT)) AS avg_score
FROM dbo.Performance p
LEFT JOIN dbo.Pair pr ON p.pair_id = pr.pair_id
LEFT JOIN dbo.Dance d ON p.dance_id = d.dance_id
LEFT JOIN dbo.Competition c ON p.competition_id = c.competition_id
LEFT JOIN dbo.Score s ON p.performance_id = s.performance_id
GROUP BY
    p.performance_id,
    p.pair_id,
    pr.name,
    d.name,
    d.style,
    c.name,
    c.start_date,
    p.stage,
    p.sequence_number;
GO

-- Вью для представлення підсумкової таблиці для змагання
CREATE OR ALTER VIEW dbo.vTournamentSummary AS
SELECT
    c.competition_id,
    c.name AS competition_name,
    -- Приводимо DATETIME виступу до DATE для зручної фільтрації по днях
    CAST(p.scheduled_datetime AS DATE) AS performance_date,
    d.name AS dance_name,
    COUNT(DISTINCT p.performance_id) AS total_performances,
    AVG(CAST(s.value AS FLOAT)) AS avg_score,
    MAX(s.value) AS max_score
FROM dbo.Competition c
INNER JOIN dbo.Performance p ON c.competition_id = p.competition_id
INNER JOIN dbo.Dance d ON p.dance_id = d.dance_id
LEFT JOIN dbo.Score s ON p.performance_id = s.performance_id
GROUP BY c.competition_id, c.name, CAST(p.scheduled_datetime AS DATE), d.name;
GO
-- Вью для представлення TOP3 для змагання
CREATE OR ALTER VIEW dbo.vTopPairs AS
WITH RankedPairs AS (
    SELECT
        p.competition_id,
        pr.name AS pair_name,
        AVG(CAST(s.value AS FLOAT)) AS avg_score,
        -- Робимо ранг за сер. балом
        DENSE_RANK() OVER (PARTITION BY p.competition_id ORDER BY AVG(CAST(s.value AS FLOAT)) DESC) as rank_pos
    FROM dbo.Performance p
    JOIN dbo.Pair pr ON p.pair_id = pr.pair_id
    JOIN dbo.Score s ON p.performance_id = s.performance_id
    GROUP BY p.competition_id, pr.pair_id, pr.name
)
SELECT
    competition_id,
    pair_name,
    avg_score,
    rank_pos
FROM RankedPairs
WHERE rank_pos <= 3; -- Залишаємо тільки перші три
GO
SELECT * FROM dbo.vTopPairs WHERE competition_id = 1;
