SELECT
    dept_id,
    SUM(sale_amount) AS total_sales,
    COUNT(DISTINCT order_id) AS order_count,
    SUM(profit) / NULLIF(SUM(sale_amount), 0) AS profit_rate
FROM APP.DWD_SALE_ORDER
WHERE order_status = 'DONE'
GROUP BY dept_id
HAVING SUM(sale_amount) > 100;
