SELECT resource_name, current_utilization, max_utilization, limit_value
FROM v$resource_limit
WHERE resource_name IN ('processes','sessions');

--

SELECT name, value
FROM v$parameter
WHERE name IN ('processes','sessions');
