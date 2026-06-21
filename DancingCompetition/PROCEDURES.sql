USE DancingCompetition;
GO

-- 1. Системна процедура sp_help
PRINT N'Отримання детальної інформації про таблицю Participant:';
EXEC sp_help 'dbo.Participant';
GO
-- Специфікація: Повертає повний опис об'єкта (стовпці, типи даних, власник, ключі).
-- Використання: Для швидкої перевірки обмежень (constraints) та типів даних
-- перед написанням нових запитів або зміною структури.


-- 2. Системна процедура sp_spaceused
PRINT N'Аналіз місця, яке займає таблиця Score (найбільша в БД):';
EXEC sp_spaceused 'dbo.Score';
GO
-- Специфікація: Відображає кількість рядків, зарезервоване місце та місце,
-- яке займають дані та індекси.
-- Використання: Оскільки ми наповнили таблицю Score 9000+ записами, ця процедура
-- допомагає оцінити темпи росту бази та навантаження на дискову підсистему.


-- 3. Системна процедура sp_helpindex
PRINT N'Перегляд усіх індексів таблиці Performance:';
EXEC sp_helpindex 'dbo.Performance';
GO
-- Специфікація: Повертає список усіх індексів таблиці, їхній опис та стовпці, на яких вони побудовані.
-- Використання: Для аудиту продуктивності. Дозволяє переконатися, що складні JOIN-запити
-- між таблицями Pair, Competition та Dance використовують індекси.

-- 1. Процедура для швидкого перегляду топ-результатів (Top Performers)
CREATE OR ALTER PROCEDURE ##sp_GlobalTopScores
AS
BEGIN
    SELECT TOP 10 p.name AS PairName, AVG(s.value) AS AverageScore
    FROM dbo.Pair p
    JOIN dbo.Performance perf ON p.pair_id = perf.pair_id
    JOIN dbo.Score s ON perf.performance_id = s.performance_id
    GROUP BY p.name
    ORDER BY AverageScore DESC;
END;
GO

-- 2. Процедура для аудиту завантаженості суддів
CREATE OR ALTER PROCEDURE ##sp_GlobalJudgeAudit
AS
BEGIN
    SELECT j.last_name AS JudgeName, COUNT(s.score_id) AS ScoresGiven
    FROM dbo.Judge j
    LEFT JOIN dbo.Score s ON j.judge_id = s.judge_id
    GROUP BY j.last_name
    ORDER BY ScoresGiven DESC;
END;
GO
exec ##sp_GlobalJudgeAudit;
-- 3. Процедура для перевірки цілісності звN'язків (учасники без країн)
CREATE OR ALTER PROCEDURE ##sp_GlobalDataIntegrityCheck
AS
BEGIN
    SELECT COUNT(*) AS ParticipantsWithoutCountry
    FROM dbo.Participant
    WHERE country_id IS NULL;
END;
GO

-- 1. Тимчасова процедура для аналізу заповненості категорій
CREATE OR ALTER PROCEDURE #sp_LocalCategoryStats
AS
BEGIN
    SELECT c.category_name AS CategoryName, COUNT(p.pair_id) AS TotalPairs
    FROM dbo.Category c
    LEFT JOIN dbo.Pair p ON c.category_id = p.category_id
    GROUP BY c.category_name;
END;
GO

-- 2. Тимчасова процедура для перевірки віку учасників (молодше 18 років)
CREATE OR ALTER PROCEDURE #sp_LocalJuniorCheck
AS
BEGIN
    SELECT first_name, last_name, birth_date,
           DATEDIFF(YEAR, birth_date, GETDATE()) AS Age
    FROM dbo.Participant
    WHERE DATEDIFF(YEAR, birth_date, GETDATE()) < 18;
END;
GO

-- 3. Тимчасова процедура для підрахунку середнього балу по конкретному танцю
CREATE OR ALTER PROCEDURE #sp_LocalDancePerformance @DanceName NVARCHAR(50)
AS
BEGIN
    SELECT d.name AS Dance, AVG(s.value) AS AvgScore
    FROM dbo.Dance d
    JOIN dbo.Performance perf ON d.dance_id = perf.dance_id
    JOIN dbo.Score s ON perf.performance_id = s.performance_id
    WHERE d.name LIKE '%' + @DanceName + '%'
    GROUP BY d.name;
END;
GO


-- 1. Реєстрація нового учасника з перевіркою (Транзакція)
CREATE OR ALTER PROCEDURE dbo.usp_RegisterParticipantSafe
    @FirstName NVARCHAR(50),
    @LastName NVARCHAR(50),
    @BirthDate DATE,
    @Gender CHAR(1),
    @CountryID INT
AS
BEGIN
    SET NOCOUNT ON;
    BEGIN TRY
        BEGIN TRANSACTION;

        -- Перевірка, чи існує країна
        IF NOT EXISTS (SELECT 1 FROM dbo.Country WHERE country_id = @CountryID)
            THROW 50001, N'Країна з таким ID не існує. Реєстрація скасована.', 1;

        INSERT INTO dbo.Participant (first_name, last_name, birth_date, gender, country_id)
        VALUES (@FirstName, @LastName, @BirthDate, @Gender, @CountryID);

        COMMIT TRANSACTION;
        PRINT N'Учасника успішно зареєстровано.';
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        PRINT N'Помилка: ' + ERROR_MESSAGE();
    END CATCH
END;
GO


/* СПЕЦИФІКАЦІЯ ПРОЦЕДУРИ: dbo.usp_CreateFullPair
ПРИЗНАЧЕННЯ: Атомарне створення танцювальної пари та реєстрація її учасників.
*/
CREATE OR ALTER PROCEDURE dbo.usp_CreateFullPair
    @PairName NVARCHAR(100),
    @CategoryID INT,
    @Partner1_ID INT,
    @Partner2_ID INT
AS
BEGIN
    SET NOCOUNT ON;
    BEGIN TRY
        BEGIN TRANSACTION;

        -- 1. Створення запису про пару
        INSERT INTO dbo.Pair (name, category_id)
        VALUES (@PairName, @CategoryID);

        DECLARE @NewPairID INT = SCOPE_IDENTITY();

        -- 2. Додавання першого учасника
        INSERT INTO dbo.Pair_member (pair_id, participant_id)
        VALUES (@NewPairID, @Partner1_ID);

        -- 3. Додавання другого учасника
        INSERT INTO dbo.Pair_member (pair_id, participant_id)
        VALUES (@NewPairID, @Partner2_ID);

        COMMIT TRANSACTION;
        PRINT N'Транзакція успішна: Пару та партнерів зареєстровано.';
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        PRINT N'Транзакція скасована (ROLLBACK). Причина: ' + ERROR_MESSAGE();
    END CATCH
END;

-- Реєстрація з помилкою (неіснуюча країна) - перевірка ROLLBACK:
EXEC dbo.usp_RegisterParticipantSafe 'Error', 'User', '2000-01-01', 'F', 999;
SELECT p.first_name AS Name, p.last_name AS Surname
FROM dbo.Participant p
WHERE p.first_name = 'Error' AND p.last_name = 'User'
-- Створення пари:
EXEC dbo.usp_CreateFullPair
    @PairName = 'Victory Dance',
    @CategoryID = 1,
    @Partner1_ID = 3,
    @Partner2_ID = 101;
SELECT p.name AS Pair_name
FROM dbo.Pair p
WHERE p.name = 'Victory Dance'
--Завдання 10
CREATE OR ALTER PROCEDURE dbo.usp_BulkInsertParticipants
    @Amount INT,          -- Кількість рядків для додавання
    @CountryID INT        -- ID країни, до якої привN'язати нових учасників
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @Counter INT = 1;
    DECLARE @FirstName NVARCHAR(50);
    DECLARE @LastName NVARCHAR(50);

    BEGIN TRY
        BEGIN TRANSACTION;

        WHILE @Counter <= @Amount
        BEGIN
            SET @FirstName = 'TestUser_' + CAST(@Counter AS NVARCHAR(10));
            SET @LastName = 'AutoGenerated';

            INSERT INTO dbo.Participant (first_name, last_name, birth_date, gender, country_id)
            VALUES (@FirstName, @LastName, '2000-01-01', 'M', @CountryID);

            SET @Counter = @Counter + 1;
        END;

        COMMIT TRANSACTION;
        PRINT N'Успішно додано ' + CAST(@Amount AS NVARCHAR(10)) + ' рядків.';
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        PRINT N'Помилка при масовій вставці: ' + ERROR_MESSAGE();
    END CATCH
END;
GO

-- Перевірка результату:
SELECT COUNT(*) FROM dbo.Participant WHERE last_name = 'AutoGenerated';
CREATE SEQUENCE dbo.Seq_DanceID
    START WITH 11  -- Почнемо з 11, щоб не заважати існуючим даним
    INCREMENT BY 1;

CREATE OR ALTER PROCEDURE dbo.usp_InsertDanceWithSequence
    @DanceName NVARCHAR(100),
    @Style NVARCHAR(50)
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @NewID INT;

    BEGIN TRY
        -- 1. Отримуємо наступне значення з послідовності
        SET @NewID = NEXT VALUE FOR dbo.Seq_DanceID;

        BEGIN TRANSACTION;
            -- 2. Дозволяємо ручну вставку в IDENTITY-поле
            SET IDENTITY_INSERT dbo.Dance ON;

            -- 3. Вставляємо дані (Тільки змінні у VALUES!)
            INSERT INTO dbo.Dance (dance_id, name, style)
            VALUES (@NewID, @DanceName, @Style);

            -- 4. Вимикаємо назад
            SET IDENTITY_INSERT dbo.Dance OFF;
        COMMIT TRANSACTION;

        -- Повертаємо ID
        SELECT @NewID AS NewPrimaryKey;
    END TRY
    BEGIN CATCH
        -- Відкочуємо транзакцію, якщо вона активна
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;

        -- Обов'язково вимикаємо IDENTITY_INSERT при помилці
        IF (OBJECTPROPERTY(OBJECT_ID('dbo.Dance'), 'TableHasActiveFulltextIndex') = 0)
            SET IDENTITY_INSERT dbo.Dance OFF;

        SELECT NULL AS NewPrimaryKey;

        -- PRINT ERROR_MESSAGE();
    END CATCH
END;
GO
EXEC dbo.usp_InsertDanceWithSequence @DanceName = 'Salsa', @Style = 'Hot latin dance';

CREATE OR ALTER PROCEDURE dbo.usp_DeletePairSafe
    @PairID INT
AS
BEGIN
    SET NOCOUNT ON;
    BEGIN TRY
        BEGIN TRANSACTION;

            -- Спочатку видаляємо записи про членів цієї пари з проміжної таблиці (через NO ACTION)
            DELETE FROM dbo.Pair_member WHERE pair_id = @PairID;

            -- Тепер видаляємо саму пару.
            -- Виступи (Performance) та оцінки (Score) видаляться автоматично завдяки вбудованому ON DELETE CASCADE!
            DELETE FROM dbo.Pair WHERE pair_id = @PairID;

        COMMIT TRANSACTION;
        PRINT N'Пару та її склад успішно видалено з бази даних.';
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO


CREATE OR ALTER PROCEDURE dbo.usp_DeleteParticipantSafe
    @ParticipantID INT
AS
BEGIN
    SET NOCOUNT ON;
    BEGIN TRY
        BEGIN TRANSACTION;

            -- Зберігаємо ID всіх пар, де танцював цей учасник
            DECLARE @PairsToDelete TABLE (pair_id INT);
            INSERT INTO @PairsToDelete
            SELECT DISTINCT pair_id FROM dbo.Pair_member WHERE participant_id = @ParticipantID;

            -- Зачищаємо зв'язки партнерів цих пар у таблиці Pair_member
            DELETE FROM dbo.Pair_member WHERE pair_id IN (SELECT pair_id FROM @PairsToDelete);

            -- Видаляємо самі пари, які розпалися (їхні виступи та оцінки злетять по каскаду)
            DELETE FROM dbo.Pair WHERE pair_id IN (SELECT pair_id FROM @PairsToDelete);

            -- Тепер, коли всі зв'язки та пари зачищено, спокійно видаляємо самого учасника
            DELETE FROM dbo.Participant WHERE participant_id = @ParticipantID;

        COMMIT TRANSACTION;
        PRINT N'Учасника та пов''язані з ним танцювальні пари повністю видалено.';
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO


CREATE OR ALTER PROCEDURE dbo.usp_DeleteCountrySafe
    @CountryID INT
AS
BEGIN
    SET NOCOUNT ON;
    BEGIN TRY
        BEGIN TRANSACTION;

            -- Шукаємо та зберігаємо ID всіх пар цієї країни
            DECLARE @PairsToDelete TABLE (pair_id INT);
            INSERT INTO @PairsToDelete
            SELECT pair_id FROM dbo.Pair WHERE country_id = @CountryID;

            -- Видаляємо зв'язки учасників цих пар у таблиці Pair_member
            DELETE FROM dbo.Pair_member WHERE pair_id IN (SELECT pair_id FROM @PairsToDelete);

            -- Видаляємо самі пари цієї країни (виступи та оцінки зникнуть автоматично)
            DELETE FROM dbo.Pair WHERE country_id = @CountryID;

            -- Видаляємо всіх учасників (танцюристів), які належать до цієї країни
            DELETE FROM dbo.Participant WHERE country_id = @CountryID;

            -- Наприкінці видаляємо саму країну
            DELETE FROM dbo.Country WHERE country_id = @CountryID;

        COMMIT TRANSACTION;
        PRINT N'Країну, її учасників та всі пов''язані пари успішно видалено без залишку полів NULL.';
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO