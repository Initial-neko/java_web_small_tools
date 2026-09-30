WITH base AS (
    SELECT dept_id, amount
    FROM APP.DWD_ORDER
    WHERE order_status = 'DONE'
)
SELECT dept_id, SUM(amount) AS total_amount
FROM base
GROUP BY dept_id;
