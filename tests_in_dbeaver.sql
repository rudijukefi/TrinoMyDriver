select 1;

SELECT 
    u.name, 
    {fn CONCAT('User: ', u.email)}, 
    o.id 
FROM {oj mysql.demo_db.users u LEFT OUTER JOIN mysql.demo_db.users o ON u.id = o.id} 
WHERE u.created_at >= {ts '2024-01-01 00:00:00'}
  AND {fn ABS(u.id)} > 0;

SELECT * FROM {oj mysql.demo_db.users u LEFT JOIN mysql.demo_db.users o ON u.id = o.id};

SELECT {fn CONCAT(o.name, '-', u.email)} FROM {oj mysql.demo_db.users u LEFT JOIN mysql.demo_db.users o ON u.id = o.id};

SELECT {fn CONCAT(name, '-', email)} FROM mysql.demo_db.users;

select {ts '2024-01-01 00:00:00'} from mysql.demo_db.users;

select * from mysql.demo_db.users;

SELECT * FROM mysql.demo_db.users u WHERE created_at >= {ts '2024-01-15 00:00:00'}