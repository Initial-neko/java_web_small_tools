SELECT
    SUM(CASE WHEN pay_status = 'PAID' THEN amount ELSE 0 END) AS paid_amount
FROM APP.DWD_ORDER;
