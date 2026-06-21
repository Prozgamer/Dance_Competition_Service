USE DancingCompetition;
GO

-- 1. ТРИГЕР АУДИТУ ЗМІНИ ОЦІНОК (DML AFTER UPDATE)
-- Запис до таблиці логів, якщо хтось міняє оцінку судді
CREATE OR ALTER TRIGGER trg_AfterScoreUpdate
ON dbo.Score
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    INSERT INTO dbo.Auditlog (ActionDescription, LogTimestamp, AffectedTable, PerformedBy)
    SELECT
        N'UPDATE: Зміна оцінки для PerformanceID ' + CAST(i.performance_id AS VARCHAR) +
        N': Стара оцінка ' + CAST(d.value AS VARCHAR) + N' -> Нова ' + CAST(i.value AS VARCHAR),
        GETDATE(),
        'Score',
        SUSER_NAME()
    FROM inserted i
    JOIN deleted d ON i.score_id = d.score_id;
END;
GO


-- 2. ТРИГЕР БЕЗПЕЧНОЇ ЗМІНИ ТРИВАЛОСТІ ТАНЦЮ (DML INSTEAD OF + ТРАНЗАКЦІЯ)
-- Загортаємо створення тригера в транзакцію за допомогою динамічного SQL,
-- щоб уникнути помилок компіляції пакетів та гарантувати атомарність оновлення коду.
BEGIN TRY
    BEGIN TRANSACTION;

    -- Видаляємо старий тригер, якщо він існує
    IF OBJECT_ID('dbo.trg_CheckDanceDuration', 'TR') IS NOT NULL
        DROP TRIGGER dbo.trg_CheckDanceDuration;

    -- Створюємо оновлений тригер із безпечним розділенням логіки INSERT / UPDATE
    DECLARE @CreateTriggerSQL NVARCHAR(MAX) = N'
    CREATE TRIGGER trg_CheckDanceDuration
    ON dbo.Dance
    INSTEAD OF INSERT, UPDATE
    AS
    BEGIN
        SET NOCOUNT ON;

        -- Бізнес-логіка: забороняємо танці менше 1 хвилини та логуємо спробу порушення
        IF EXISTS (SELECT 1 FROM inserted WHERE duration_minutes < 1)
        BEGIN
            INSERT INTO dbo.Auditlog (ActionDescription, LogTimestamp, AffectedTable, PerformedBy)
            VALUES (N''ERROR: Спроба встановити тривалість менше 1 хвилини'', GETDATE(), N''Dance'', SUSER_NAME());

            RAISERROR (N''Помилка! Мінімальна тривалість виступу — 1 хвилина.'', 16, 1);
            RETURN;
        END;

        -- Розділяємо логіку вставки та оновлення даних
        IF EXISTS (SELECT 1 FROM deleted)
        BEGIN
            -- Безпечний UPDATE для масових операцій через INNER JOIN
            UPDATE d
            SET d.name = i.name,
                d.style = i.style,
                d.duration_minutes = i.duration_minutes
            FROM dbo.Dance d
            INNER JOIN inserted i ON d.dance_id = i.dance_id;
        END
        ELSE
        BEGIN
            -- Безпечний INSERT нових записів
            INSERT INTO dbo.Dance (name, style, duration_minutes)
            SELECT name, style, duration_minutes
            FROM inserted;
        END
    END;
    ';
    EXEC sp_executesql @CreateTriggerSQL;

    COMMIT TRANSACTION;
    PRINT N'Тригер trg_CheckDanceDuration успішно оновлено в межах транзакції.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    DECLARE @ErrMsg NVARCHAR(4000) = ERROR_MESSAGE();
    RAISERROR(@ErrMsg, 16, 1);
END CATCH;
GO


-- 3. ТРИГЕР АУДИТУ ВИДАЛЕННЯ ЗМАГАНЬ (DML AFTER DELETE)
CREATE OR ALTER TRIGGER trg_CompetitionDeleteAudit
ON dbo.Competition
AFTER DELETE
AS
BEGIN
    SET NOCOUNT ON;
    -- Фіксуємо в журналі, яке саме змагання було видалено
    INSERT INTO dbo.Auditlog (ActionDescription, LogTimestamp, AffectedTable, PerformedBy)
    SELECT
        N'DELETE: Видалено змагання: "' + d.name + N'" (ID: ' + CAST(d.competition_id AS NVARCHAR) + N')',
        GETDATE(),
        N'Competition',
        SUSER_NAME()
    FROM deleted d;
END;
GO


-- 4. ТРИГЕР АУДИТУ ТА ЗАХИСТУ СТРУКТУРИ БД (DDL TRIGGER)
-- Цей тригер спрацьовує, коли хтось намагається змінити структуру таблиць бази даних
CREATE OR ALTER TRIGGER trg_DDLAudit
ON DATABASE
FOR CREATE_TABLE, ALTER_TABLE, DROP_TABLE
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @EventData XML = EVENTDATA();
    DECLARE @ObjectName NVARCHAR(100) = @EventData.value('(/EVENT_INSTANCE/ObjectName)[1]', 'NVARCHAR(100)');
    DECLARE @EventType NVARCHAR(100) = @EventData.value('(/EVENT_INSTANCE/EventType)[1]', 'NVARCHAR(100)');

    -- Логуємо DDL подію в таблицю аудиту
    INSERT INTO dbo.Auditlog (ActionDescription, LogTimestamp, AffectedTable, PerformedBy)
    VALUES (
        N'DDL Операція: ' + @EventType + N' над об''єктом ' + @ObjectName,
        GETDATE(),
        @ObjectName,
        SUSER_NAME()
    );

    -- Якщо користувач намагається ВИДАЛИТИ таблицю (DROP), ми це жорстко блокуємо
    IF @EventType = 'DROP_TABLE'
    BEGIN
        PRINT N'Видалення таблиць у цій базі заборонено DDL тригером системи!';
        ROLLBACK; -- Скасовуємо транзакцію видалення
    END
END;
GO


-- 5. ТРИГЕР АУДИТУ ВХОДУ НА СЕРВЕР (LOGON TRIGGER)
-- УВАГА: Створюється на рівні всього сервера (ALL SERVER).
-- Фіксує кожне успішне підключення користувача до СУБД.
CREATE OR ALTER TRIGGER trg_LogonAudit
ON ALL SERVER
FOR LOGON
AS
BEGIN
    -- Перевірка на випадок, якщо база даних з логами тимчасово недоступна,
    -- щоб не заблокувати підключення адміністратора до сервера
    IF DB_ID('DancingCompetition') IS NOT NULL
    BEGIN
        INSERT INTO DancingCompetition.dbo.Auditlog (ActionDescription, LogTimestamp, AffectedTable, PerformedBy)
        VALUES (
            N'Вхід у систему (Logon Event)',
            GETDATE(),
            N'SERVER',
            ORIGINAL_LOGIN()
        );
    END
END;
GO

-- 6. ТРИГЕР КОНТРОЛЮ ЧАСОВИХ МЕЖ ВИСТУПУ
ALTER TRIGGER dbo.trg_CheckPerformanceTimeBoundaries
ON dbo.Performance
INSTEAD OF INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    -- Перевірка меж турніру за твоїм реальним полем scheduled_datetime
    IF EXISTS (
        SELECT 1
        FROM inserted i
        INNER JOIN dbo.Competition c ON i.competition_id = c.competition_id
        WHERE i.scheduled_datetime < c.start_date OR i.scheduled_datetime > c.end_date
    )
    BEGIN
        INSERT INTO dbo.Auditlog (ActionDescription, LogTimestamp, AffectedTable, PerformedBy)
        VALUES (N'ERROR: Спроба встановити дату виступу поза межами турніру', GETDATE(), N'Performance', SYSTEM_USER);

        RAISERROR (N'Помилка! Дата виступу не збігається з часовими межами проведення цього змагання.', 16, 1);
        RETURN;
    END;

    -- Операція UPDATE (Оновлення існуючих записів з реальними іменами полів!)
    IF EXISTS (SELECT 1 FROM deleted)
    BEGIN
        UPDATE p
        SET p.competition_id = i.competition_id,
            p.pair_id = i.pair_id,
            p.dance_id = i.dance_id,
            p.scheduled_datetime = i.scheduled_datetime,
            p.stage = i.stage,
            p.sequence_number = i.sequence_number,
            p.category_id = i.category_id
        FROM dbo.Performance p
        INNER JOIN inserted i ON p.performance_id = i.performance_id;
    END
    ELSE
    -- Операція INSERT
    BEGIN
        INSERT INTO dbo.Performance (competition_id, pair_id, dance_id, scheduled_datetime, stage, sequence_number, category_id)
        SELECT competition_id, pair_id, dance_id, scheduled_datetime, stage, sequence_number, category_id
        FROM inserted;
    END
END;
UPDATE dbo.Performance
set scheduled_datetime = '2100-05-13'
where performance_id = 2;

