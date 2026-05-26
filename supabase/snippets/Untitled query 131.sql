DO $$ 
DECLARE 
    r RECORD;
BEGIN
    -- Шукаємо всі таблиці, які мають колонки created_at або updated_at зі старим типом
    FOR r IN 
        SELECT table_name, column_name 
        FROM information_schema.columns 
        WHERE column_name IN ('created_at', 'updated_at') 
          AND data_type = 'timestamp without time zone'
          AND table_schema = 'public'
    LOOP
        -- Конвертуємо в timestamptz. 
        -- 'AT TIME ZONE 'UTC'' каже базі: "вважай, що ці старі цифри були в UTC"
        EXECUTE format('ALTER TABLE %I ALTER COLUMN %I TYPE timestamptz USING %I AT TIME ZONE ''UTC''', 
                       r.table_name, r.column_name, r.column_name);
    END LOOP;
END $$;