USE DancingCompetition;
GO

-- 1. Повне очищення (видаляємо дані, щоб уникнути конфліктів)
DELETE FROM dbo.Score; DELETE FROM dbo.Performance; DELETE FROM dbo.Pair_member;
DELETE FROM dbo.Pair; DELETE FROM dbo.Participant; DELETE FROM dbo.Judge;
DELETE FROM dbo.Competition; DELETE FROM dbo.Dance; DELETE FROM dbo.Category; DELETE FROM dbo.Country;
GO

-- 2. Цикл заповнення
DECLARE @i INT = 1;
DECLARE @char1 CHAR(1), @char2 CHAR(1);

WHILE @i <= 115
BEGIN
    -- Генеруємо унікальний дволітерний код (напр., AA, AB, AC...)
    SET @char1 = CHAR(65 + (@i / 26));as
    SET @char2 = CHAR(65 + (@i % 26));

    SET IDENTITY_INSERT dbo.Country ON;
    INSERT INTO dbo.Country (country_id, country_name, iso_code, continent)
    VALUES (@i, 'Country_' + CAST(@i AS NVARCHAR), @char1 + @char2, 'Continent');
    SET IDENTITY_INSERT dbo.Country OFF;

    SET IDENTITY_INSERT dbo.Category ON;
    INSERT INTO dbo.Category (category_id, category_name, age_min, age_max, level)
    VALUES (@i, 'Cat_' + CAST(@i AS NVARCHAR), 10 + (@i % 10), 40,
            CASE WHEN @i % 2 = 0 THEN 'Advanced' ELSE 'Open' END);
    SET IDENTITY_INSERT dbo.Category OFF;

    SET IDENTITY_INSERT dbo.Dance ON;
    INSERT INTO dbo.Dance (dance_id, name, style, duration_minutes)
    VALUES (@i, 'Dance_' + CAST(@i AS NVARCHAR), 'Style', 3);
    SET IDENTITY_INSERT dbo.Dance OFF;

    SET IDENTITY_INSERT dbo.Competition ON;
    INSERT INTO dbo.Competition (competition_id, name, start_date, end_date, location)
    VALUES (@i, 'Comp_' + CAST(@i AS NVARCHAR), '2026-01-01', '2026-12-31', 'City');
    SET IDENTITY_INSERT dbo.Competition OFF;

    SET IDENTITY_INSERT dbo.Judge ON;
    INSERT INTO dbo.Judge (judge_id, first_name, last_name)
    VALUES (@i, 'JudgeFN' + CAST(@i AS NVARCHAR), 'JudgeLN' + CAST(@i AS NVARCHAR));
    SET IDENTITY_INSERT dbo.Judge OFF;

   SET IDENTITY_INSERT dbo.Participant ON;

INSERT INTO dbo.Participant (participant_id, country_id, first_name, last_name, birth_date, gender)
VALUES (
    @i,
    @i,
    'PartFN' + CAST(@i AS NVARCHAR),
    'PartLN' + CAST(@i AS NVARCHAR),
    -- Додаємо @i днів до 1 січня 2000 року. Це автоматично створить різні дати.
    DATEADD(day, @i, '2000-01-01'),
    CASE WHEN @i % 2 = 0 THEN 'M' ELSE 'F' END -- Додамо трохи різноманіття в статі
);

SET IDENTITY_INSERT dbo.Participant OFF;

    SET IDENTITY_INSERT dbo.Pair ON;
    INSERT INTO dbo.Pair (pair_id, name, country_id, category_id)
    VALUES (@i, 'Pair_' + CAST(@i AS NVARCHAR), @i, @i);
    SET IDENTITY_INSERT dbo.Pair OFF;

    INSERT INTO dbo.Pair_member (pair_id, participant_id, role_in_pair)
    VALUES (@i, @i, 'Leader');

    SET IDENTITY_INSERT dbo.Performance ON;
    INSERT INTO dbo.Performance (performance_id, pair_id, dance_id, competition_id, scheduled_datetime, category_id)
    VALUES (@i, @i, @i, @i, GETDATE(), @i);
    SET IDENTITY_INSERT dbo.Performance OFF;

    SET IDENTITY_INSERT dbo.Score ON;
    INSERT INTO dbo.Score (score_id, performance_id, judge_id, value)
    VALUES (@i, @i, @i, 70 + (@i % 30));
    SET IDENTITY_INSERT dbo.Score OFF;

    SET @i = @i + 1;
END;
GO