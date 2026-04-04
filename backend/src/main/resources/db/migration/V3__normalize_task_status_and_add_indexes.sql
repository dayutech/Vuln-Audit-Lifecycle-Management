UPDATE tasks
SET status = 'pending'
WHERE status IS NULL
   OR TRIM(status) = ''
   OR LOWER(status) NOT IN ('pending', 'in_progress', 'completed', 'cancelled', 'assigned');

CREATE INDEX idx_tasks_assigned_to_status ON tasks (assigned_to, status);
CREATE INDEX idx_tasks_reference_id ON tasks (reference_id);
