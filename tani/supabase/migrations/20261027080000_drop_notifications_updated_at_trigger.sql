-- notifications has no updated_at column, so this dashboard-era trigger made
-- every update fail ("record new has no field updated_at"), including marking
-- a notification as read.
DROP TRIGGER IF EXISTS trg_notifications_updated_at ON public.notifications;
