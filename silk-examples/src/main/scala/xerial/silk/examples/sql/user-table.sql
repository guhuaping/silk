    SELECT coalesce(u, account_id) as usr, account_id
    FROM  (
      SELECT time, regexp_extract(user, '([0-9]+)/.*', 1) as u, account_id
      FROM presto_production.query_completion
      WHERE TD_TIME_RANGE(time, TD_TIME_ADD({{start}}, '-{{days}}d', '{{timezone}}'))
    ) s2
  ) s
  WHERE usr IS NOT NULL
  GROUP BY TD_TIME_FORMAT(time, 'yyyy-MM-dd', '{{timezone}}'), usr
  ORDER BY time, usr ASC LIMIT 10000
