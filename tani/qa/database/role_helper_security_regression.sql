\set ON_ERROR_STOP on

select tests.assert_true(
  not has_function_privilege('anon', 'public.current_user_role()', 'execute'),
  'anonymous callers cannot execute current_user_role()'
);

select tests.assert_true(
  has_function_privilege('authenticated', 'public.current_user_role()', 'execute'),
  'authenticated callers retain current_user_role() access for RLS/RPC paths'
);
