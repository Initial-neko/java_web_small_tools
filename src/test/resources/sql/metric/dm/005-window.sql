SELECT
    dept_id,
    SUM(amount) OVER (PARTITION BY dept_id) AS dept_total
FROM APP.DWD_ORDER;
