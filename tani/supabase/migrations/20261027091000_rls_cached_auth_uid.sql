-- A bare auth.uid() in a policy is evaluated once per row; wrapped in a
-- sub-select Postgres evaluates it once per query. The meaning is unchanged.
-- Policies are rewritten from the catalog so dashboard-made ones are covered too.
DO $$
DECLARE p record; v_sql text;
BEGIN
 FOR p IN
  SELECT schemaname, tablename, policyname, qual, with_check FROM pg_policies
  WHERE schemaname='public'
    AND (coalesce(qual,'') ~ '(?<!SELECT )auth\.uid\(\)' OR coalesce(with_check,'') ~ '(?<!SELECT )auth\.uid\(\)')
 LOOP
  v_sql := format('ALTER POLICY %I ON %I.%I', p.policyname, p.schemaname, p.tablename);
  IF p.qual IS NOT NULL THEN
   v_sql := v_sql || ' USING (' || regexp_replace(p.qual, '(?<!SELECT )auth\.uid\(\)', '(SELECT auth.uid())', 'g') || ')';
  END IF;
  IF p.with_check IS NOT NULL THEN
   v_sql := v_sql || ' WITH CHECK (' || regexp_replace(p.with_check, '(?<!SELECT )auth\.uid\(\)', '(SELECT auth.uid())', 'g') || ')';
  END IF;
  EXECUTE v_sql;
 END LOOP;
END $$;
