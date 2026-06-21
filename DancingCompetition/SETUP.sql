CREATE DATABASE DancingCompetition;
GO
USE DancingCompetition
go
-- 1) Country
CREATE TABLE dbo.Country (
    country_id INT IDENTITY(1,1) PRIMARY KEY,
    country_name NVARCHAR(100) NOT NULL,
    iso_code CHAR(2) NOT NULL,
    continent NVARCHAR(50) NULL,
    CONSTRAINT UQ_Country_iso UNIQUE (iso_code)
);

-- 2) Category
CREATE TABLE dbo.Category (
    category_id INT IDENTITY(1,1) PRIMARY KEY,
    category_name NVARCHAR(100) NOT NULL,
    age_min INT NULL,
    age_max INT NULL,
    level NVARCHAR(20) NOT NULL,
    CONSTRAINT CHK_Category_age CHECK (age_min IS NULL OR age_min >= 0),
    CONSTRAINT CHK_Category_level CHECK (level IN ('Beginner','Intermediate','Advanced','Open'))
);

-- 3) Dance
CREATE TABLE dbo.Dance (
    dance_id INT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(100) NOT NULL,
    style NVARCHAR(50) NULL,
    duration_minutes INT          NOT NULL DEFAULT 3,
    CONSTRAINT CHK_Dance_duration CHECK (duration_minutes BETWEEN 1 AND 3)
);

-- 4) Competition
CREATE TABLE dbo.Competition (
    competition_id INT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(150) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    location NVARCHAR(150) NULL,
    rules_doc  NVARCHAR(255) NULL,
    CONSTRAINT CHK_Competition_dates CHECK (end_date >= start_date)
);

-- 5) Participant
CREATE TABLE dbo.Participant (
    participant_id INT IDENTITY(1,1) PRIMARY KEY,
    country_id INT NOT NULL,
    first_name NVARCHAR(50) NOT NULL,
    last_name NVARCHAR(50) NOT NULL,
    birth_date DATE NOT NULL,
    gender CHAR(1) NOT NULL,
    contact_info NVARCHAR(255) NULL,
    CONSTRAINT CHK_Participant_gender CHECK (gender IN ('M','F')),
    CONSTRAINT FK_Participant_Country FOREIGN KEY (country_id) REFERENCES dbo.Country(country_id),
    CONSTRAINT UQ_Participant_person UNIQUE (first_name, last_name, birth_date, country_id)
);

-- 6) Judge
CREATE TABLE dbo.Judge (
    judge_id INT IDENTITY(1,1) PRIMARY KEY,
    first_name NVARCHAR(50) NOT NULL,
    last_name NVARCHAR(50) NOT NULL,
    qualification NVARCHAR(100) NULL,
    contact_info NVARCHAR(255) NULL
);

-- 7) Pair
CREATE TABLE dbo.Pair (
    pair_id INT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(150) NULL,
    country_id INT NULL,
    coach_id INT NULL,
    coach_name NVARCHAR(150) NULL,
    category_id INT NOT NULL,
    CONSTRAINT FK_Pair_Category FOREIGN KEY (category_id) REFERENCES dbo.Category(category_id),
    CONSTRAINT FK_Pair_Country FOREIGN KEY (country_id) REFERENCES dbo.Country(country_id),
    CONSTRAINT FK_Pair_Coach FOREIGN KEY (coach_id) REFERENCES dbo.Participant(participant_id)
);

-- 8) Pair_member
CREATE TABLE dbo.Pair_member (
    pair_id INT NOT NULL,
    participant_id INT NOT NULL,
    role_in_pair NVARCHAR(50) NULL, -- e.g., 'Leader', 'Follower'
    joined_date DATE NULL,
    CONSTRAINT PK_Pair_member PRIMARY KEY (pair_id, participant_id),
    CONSTRAINT FK_PM_Pair FOREIGN KEY (pair_id) REFERENCES dbo.Pair(pair_id),
    CONSTRAINT FK_PM_Participant FOREIGN KEY (participant_id) REFERENCES dbo.Participant(participant_id),
    CONSTRAINT UQ_Pair_role UNIQUE (pair_id, role_in_pair)
);

-- 9) Performance
CREATE TABLE dbo.Performance (
    performance_id INT IDENTITY(1,1) PRIMARY KEY,
    pair_id INT NOT NULL,
    dance_id INT NOT NULL,
    competition_id INT NOT NULL,
    scheduled_datetime DATETIME NOT NULL,
    stage NVARCHAR(100) NULL,
    sequence_number INT NULL,
    category_id INT NULL,
    CONSTRAINT FK_Perf_Pair FOREIGN KEY (pair_id) REFERENCES dbo.Pair(pair_id),
    CONSTRAINT FK_Perf_Dance FOREIGN KEY (dance_id) REFERENCES dbo.Dance(dance_id),
    CONSTRAINT FK_Perf_Competition FOREIGN KEY (competition_id) REFERENCES dbo.Competition(competition_id),
    CONSTRAINT FK_Perf_Category FOREIGN KEY (category_id) REFERENCES dbo.Category(category_id)
);

-- unique(1 dance per pair)
ALTER TABLE dbo.Performance
ADD CONSTRAINT UQ_Performance_pair_comp_dance UNIQUE (pair_id, competition_id, dance_id);

-- 10) Score
CREATE TABLE dbo.Score (
    score_id INT IDENTITY(1,1) PRIMARY KEY,
    performance_id INT NOT NULL,
    judge_id INT NOT NULL,
    value DECIMAL(5,2) NOT NULL,
    criteria NVARCHAR(200) NULL,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_Score_Perf FOREIGN KEY (performance_id) REFERENCES dbo.Performance(performance_id),
    CONSTRAINT FK_Score_Judge FOREIGN KEY (judge_id) REFERENCES dbo.Judge(judge_id)
);

-- 1 score per judge
ALTER TABLE dbo.Score
ADD CONSTRAINT UQ_Score_performance_judge UNIQUE (performance_id, judge_id);

-- domain check score.value
ALTER TABLE dbo.Score
ADD CONSTRAINT CHK_Score_value CHECK (value >= 0 AND value <= 100);

-- 1. Видаляємо всі проблемні констрейнти, щоб почати з чистого листа
ALTER TABLE dbo.Participant DROP CONSTRAINT IF EXISTS FK_Participant_Country;
ALTER TABLE dbo.Pair DROP CONSTRAINT IF EXISTS FK_Pair_Country;
ALTER TABLE dbo.Pair DROP CONSTRAINT IF EXISTS FK_Pair_Coach;
ALTER TABLE dbo.Pair_member DROP CONSTRAINT IF EXISTS FK_PM_Participant;
ALTER TABLE dbo.Pair_member DROP CONSTRAINT IF EXISTS FK_PM_Pair;
ALTER TABLE dbo.Performance DROP CONSTRAINT IF EXISTS FK_Perf_Pair;
ALTER TABLE dbo.Score DROP CONSTRAINT IF EXISTS FK_Score_Perf;

-- 2. Встановлюємо КАСКАД: Країна -> Учасник (Головний шлях)
ALTER TABLE dbo.Participant ADD CONSTRAINT FK_Participant_Country
    FOREIGN KEY (country_id) REFERENCES dbo.Country(country_id) ON DELETE CASCADE;

-- 3. Встановлюємо NO ACTION: Країна -> Пара (РОЗРИВАЄМО ПЕРШИЙ КОНФЛІКТ)
-- Тепер при видаленні країни пари не видаляються каскадом від країни,
-- що дозволяє уникнути помилки Line 8.
ALTER TABLE dbo.Pair ADD CONSTRAINT FK_Pair_Country
    FOREIGN KEY (country_id) REFERENCES dbo.Country(country_id) ON DELETE NO ACTION;

-- 4. Встановлюємо SET NULL: Тренер -> Пара
-- Якщо видалити учасника-тренера, пара залишиться, просто поле coach_id занулиться.
ALTER TABLE dbo.Pair ADD CONSTRAINT FK_Pair_Coach
    FOREIGN KEY (coach_id) REFERENCES dbo.Participant(participant_id) ON DELETE SET NULL;

-- 5. Встановлюємо КАСКАД: Учасник -> Член пари (Головний шлях)
-- Видаляємо людину — вона зникає зі складу всіх пар.
ALTER TABLE dbo.Pair_member ADD CONSTRAINT FK_PM_Participant
    FOREIGN KEY (participant_id) REFERENCES dbo.Participant(participant_id) ON DELETE CASCADE;

-- 6. Встановлюємо NO ACTION: Пара -> Член пари (РОЗРИВАЄМО ДРУГИЙ КОНФЛІКТ)
-- Це прибирає перетин каскадів у таблиці Pair_member.
ALTER TABLE dbo.Pair_member ADD CONSTRAINT FK_PM_Pair
    FOREIGN KEY (pair_id) REFERENCES dbo.Pair(pair_id) ON DELETE NO ACTION;

-- 7. Встановлюємо КАСКАД: Пара -> Виступ
ALTER TABLE dbo.Performance ADD CONSTRAINT FK_Perf_Pair
    FOREIGN KEY (pair_id) REFERENCES dbo.Pair(pair_id) ON DELETE CASCADE;

-- 8. Встановлюємо КАСКАД: Виступ -> Оцінка
ALTER TABLE dbo.Score ADD CONSTRAINT FK_Score_Perf
    FOREIGN KEY (performance_id) REFERENCES dbo.Performance(performance_id) ON DELETE CASCADE;

