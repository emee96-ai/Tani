SELECT tests.assert_true(NOT EXISTS(
 SELECT 1 FROM pg_policies WHERE schemaname='public'
   AND (coalesce(qual,'') ~ '(?<!SELECT )auth\.uid\(\)' OR coalesce(with_check,'') ~ '(?<!SELECT )auth\.uid\(\)')
),'every public policy evaluates auth.uid() once per query');
