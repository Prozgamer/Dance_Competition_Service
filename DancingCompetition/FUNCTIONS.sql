USE DancingCompetition;
GO
--Скалярні функції (Scalar Functions)

-- 1. Функція для розрахунку віку учасника на поточний момент
CREATE FUNCTION dbo.fn_GetParticipantAge (@BirthDate DATE)
RETURNS INT
AS
BEGIN
    RETURN DATEDIFF(YEAR, @BirthDate, GETDATE());
END;
GO

-- 2. Функція для отримання середнього балу конкретного виступу
CREATE FUNCTION dbo.fn_GetAvgPerformanceScore (@PerformanceID INT)
RETURNS DECIMAL(5,2)
AS
BEGIN
    DECLARE @AvgScore DECIMAL(5,2);
    SELECT @AvgScore = AVG(CAST(value AS DECIMAL(5,2)))
    FROM dbo.Score WHERE performance_id = @PerformanceID;
    RETURN ISNULL(@AvgScore, 0);
END;
GO

-- 3. Функція форматування імені (Прізвище та ініціали: "PartLN P.")
CREATE FUNCTION dbo.fn_FormatParticipantName (@FirstName NVARCHAR(50), @LastName NVARCHAR(50))
RETURNS NVARCHAR(60)
AS
BEGIN
    RETURN @LastName + ' ' + LEFT(@FirstName, 1) + '.';
END;
GO

-- Inline функції (Inline Table-Valued Functions)

-- 4. Отримати всіх учасників з конкретної країни
CREATE FUNCTION dbo.fn_GetParticipantsByCountry (@CountryID INT)
RETURNS TABLE
AS
RETURN (
    SELECT first_name, last_name, birth_date
    FROM dbo.Participant
    WHERE country_id = @CountryID
);
GO

-- 5. Отримати список виступів у межах конкретної категорії
CREATE FUNCTION dbo.fn_GetPerformancesByCategory (@CategoryID INT)
RETURNS TABLE
AS
RETURN (
    SELECT performance_id, scheduled_datetime, pair_id
    FROM dbo.Performance
    WHERE category_id = @CategoryID
);
GO

-- 6. Отримати оцінки для конкретного виступу з іменами суддів
CREATE FUNCTION dbo.fn_GetScoresDetails (@PerformanceID INT)
RETURNS TABLE
AS
RETURN (
    SELECT s.value, j.first_name AS JudgeName, j.last_name AS JudgeLastName
    FROM dbo.Score s
    JOIN dbo.Judge j ON s.judge_id = j.judge_id
    WHERE s.performance_id = @PerformanceID
);
GO

-- Multi-statement функції (Multi-statement Table-Valued Functions)

-- 7. Формування таблиці лідерів для конкретного змагання
CREATE FUNCTION dbo.fn_GetCompetitionLeaderboard (@CompID INT)
RETURNS @Leaderboard TABLE (
    PairName NVARCHAR(100),
    TotalScore DECIMAL(10,2),
    RankInt INT
)
AS
BEGIN
    INSERT INTO @Leaderboard
    SELECT p.name, SUM(s.value),
           RANK() OVER (ORDER BY SUM(s.value) DESC)
    FROM dbo.Pair p
    JOIN dbo.Performance perf ON p.pair_id = perf.pair_id
    JOIN dbo.Score s ON perf.performance_id = s.performance_id
    WHERE perf.competition_id = @CompID
    GROUP BY p.name;

    RETURN;
END;
GO
-- 8. Статистика по країнах (кількість учасників та їх середній вік)
CREATE FUNCTION dbo.fn_GetCountryStats()
RETURNS @Stats TABLE (
    CountryName NVARCHAR(100),
    ParticipantCount INT,
    AverageAge INT
)
AS
BEGIN
    INSERT INTO @Stats
    SELECT c.country_name, COUNT(p.participant_id),
           AVG(DATEDIFF(YEAR, p.birth_date, GETDATE()))
    FROM dbo.Country c
    LEFT JOIN dbo.Participant p ON c.country_id = p.country_id
    GROUP BY c.country_name;

    RETURN;
END;
GO

-- Запити з варіанту

-- 1. Розклад проведених виступів для команди (Inline TVF)
CREATE FUNCTION dbo.fn_GetTeamCompletedSchedule (@TargetDate DATE, @CountryID INT)
RETURNS TABLE
AS
RETURN (
    SELECT p.performance_id, pr.name AS PairName, d.name AS DanceName, p.scheduled_datetime
    FROM dbo.Performance p
    JOIN dbo.Pair pr ON p.pair_id = pr.pair_id
    JOIN dbo.Dance d ON p.dance_id = d.dance_id
    WHERE CAST(p.scheduled_datetime AS DATE) = @TargetDate
      AND pr.country_id = @CountryID
      -- Перевірка, чи є хоча б одна оцінка (виступ проведено)
      AND EXISTS (SELECT 1 FROM dbo.Score s WHERE s.performance_id = p.performance_id)
);
GO

-- 2. Підсумки в загальному заліку на день конкурсу (Multi-statement TVF)
CREATE FUNCTION dbo.fn_GetStandingsByDay (@TargetDay DATE)
RETURNS @Standings TABLE (
    RankInt INT,
    PairName NVARCHAR(100),
    AverageTotalScore DECIMAL(5,2)
)
AS
BEGIN
    INSERT INTO @Standings
    SELECT
        RANK() OVER (ORDER BY AVG(CAST(s.value AS DECIMAL(5,2))) DESC),
        p.name,
        AVG(CAST(s.value AS DECIMAL(5,2)))
    FROM dbo.Pair p
    JOIN dbo.Performance perf ON p.pair_id = perf.pair_id
    JOIN dbo.Score s ON perf.performance_id = s.performance_id
    WHERE CAST(perf.scheduled_datetime AS DATE) <= @TargetDay
    GROUP BY p.name;

    RETURN;
END;
GO

-- 3. Середня оцінка за танець від певної категорії суддів (Scalar Function)
CREATE FUNCTION dbo.fn_AvgScoreByJudgeCategory (@DanceID INT, @JudgeQual NVARCHAR(50))
RETURNS DECIMAL(5,2)
AS
BEGIN
    DECLARE @Result DECIMAL(5,2);
    SELECT @Result = AVG(CAST(s.value AS DECIMAL(5,2)))
    FROM dbo.Score s
    JOIN dbo.Performance p ON s.performance_id = p.performance_id
    JOIN dbo.Judge j ON s.judge_id = j.judge_id
    WHERE p.dance_id = @DanceID AND j.qualification = @JudgeQual;

    RETURN ISNULL(@Result, 0);
END;
GO

-- 4. Танці, які не виконує жодна пара (Inline TVF)
CREATE FUNCTION dbo.fn_GetUnperformedDances ()
RETURNS TABLE
AS
RETURN (
    SELECT d.name AS DanceName, d.style
    FROM dbo.Dance d
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.Performance p WHERE p.dance_id = d.dance_id
    )
);
GO

-- 5. Перелік виконаних танців за популярністю на день (Inline TVF)
CREATE FUNCTION dbo.fn_GetDancePopularityByDay (@TargetDate DATE)
RETURNS TABLE
AS
RETURN (
    SELECT TOP 100 PERCENT d.name, COUNT(p.pair_id) AS ExecutionCount
    FROM dbo.Dance d
    JOIN dbo.Performance p ON d.dance_id = p.dance_id
    WHERE CAST(p.scheduled_datetime AS DATE) = @TargetDate
    GROUP BY d.name
    ORDER BY ExecutionCount DESC
);
GO
SELECT * FROM dbo.fn_GetDancePopularityByDay('2026-04-13');