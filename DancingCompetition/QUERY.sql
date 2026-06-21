USE DancingCompetition;
GO

-- 1. Запит для виявлення «суддівського фаворитизму» (HAVING та Агрегація)
SELECT
    j.judge_id,
    (j.first_name + ' ' + j.last_name) AS judge_name,
    p.pair_id,
    p.name AS pair_name,
    AVG(s.value) AS avg_score_by_this_judge
FROM dbo.Score s
INNER JOIN dbo.Judge j ON s.judge_id = j.judge_id
INNER JOIN dbo.Performance perf ON s.performance_id = perf.performance_id
INNER JOIN dbo.Pair p ON perf.pair_id = p.pair_id
GROUP BY j.judge_id, j.first_name, j.last_name, p.pair_id, p.name
HAVING AVG(s.value) > 90.00
ORDER BY avg_score_by_this_judge DESC;
GO

-- 2. Запит на виведення повної статистики турнірів (Багатотабличний комплексний JOIN)
SELECT
    c.competition_id,
    c.name AS competition_name,
    c.location,
    COUNT(DISTINCT perf.performance_id) AS total_performances,
    COUNT(DISTINCT pm.participant_id) AS total_unique_dancers,
    ROUND(AVG(s.value), 2) AS general_average_score
FROM dbo.Competition c
LEFT JOIN dbo.Performance perf ON c.competition_id = perf.competition_id
LEFT JOIN dbo.Pair_member pm ON perf.pair_id = pm.pair_id
LEFT JOIN dbo.Score s ON perf.performance_id = s.performance_id
GROUP BY c.competition_id, c.name, c.location
ORDER BY total_performances DESC;
GO

-- 3. Аналітичний рейтинг пар всередині кожної категорії (Оконна функція DENSE_RANK)
SELECT
    cat.category_name,
    p.pair_id,
    p.name AS pair_name,
    ROUND(AVG(s.value), 2) AS final_score,
    DENSE_RANK() OVER (
        PARTITION BY p.category_id
        ORDER BY AVG(s.value) DESC
    ) AS place_in_category
FROM dbo.Score s
INNER JOIN dbo.Performance perf ON s.performance_id = perf.performance_id
INNER JOIN dbo.Pair p ON perf.pair_id = p.pair_id
INNER JOIN dbo.Category cat ON p.category_id = cat.category_id
GROUP BY cat.category_name, p.category_id, p.pair_id, p.name;
GO

-- 4. Пошук «універсальних» спортсменів, які виступали у кількох стилях (Підзапит з EXISTS)
SELECT
    part.participant_id,
    (part.first_name + ' ' + part.last_name) AS dancer_name,
    c.country_name
FROM dbo.Participant part
INNER JOIN dbo.Country c ON part.country_id = c.country_id
WHERE EXISTS (
    SELECT 1
    FROM dbo.Pair_member pm
    INNER JOIN dbo.Performance perf ON pm.pair_id = perf.pair_id
    INNER JOIN dbo.Dance d ON perf.dance_id = d.dance_id
    WHERE pm.participant_id = part.participant_id
    GROUP BY pm.participant_id
    HAVING COUNT(DISTINCT d.style) >= 2
);
GO

-- 5. Статистика завантаженості суддівської колегії (Корельований підзапит)
SELECT
    j.judge_id,
    (j.first_name + ' ' + j.last_name) AS judge_name,
    (SELECT COUNT(*) FROM dbo.Score s WHERE s.judge_id = j.judge_id) AS total_evaluated_performances,
    CASE
        WHEN (SELECT COUNT(*) FROM dbo.Score s WHERE s.judge_id = j.judge_id) >
             (SELECT COUNT(*) / COUNT(DISTINCT judge_id) FROM dbo.Score)
        THEN N'Вище середнього'
        ELSE N'В межах норми'
    END AS workload_status
FROM dbo.Judge j
ORDER BY total_evaluated_performances DESC;
GO
