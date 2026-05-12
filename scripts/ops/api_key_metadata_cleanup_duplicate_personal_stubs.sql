-- One-off cleanup (usage_db): remove PERSONAL api_key_metadata stub rows that duplicate richer rows per key_id.
-- Preconditions: run on staging first; backup or snapshot; review counts from the SELECT below before DELETE.
--
-- Deletes PERSONAL rows where provider and alias are both blank, when another PERSONAL row exists for the same
-- key_id (different user_id) with at least one of provider or alias populated (Identity-sync vs usage stub).

-- Preview rows that would be removed:
-- SELECT s.key_id, s.user_id, s.provider, s.alias, s.updated_at
-- FROM api_key_metadata s
-- WHERE s.key_scope = 'PERSONAL'
--   AND (s.provider IS NULL OR trim(s.provider) = '')
--   AND (s.alias IS NULL OR trim(s.alias) = '')
--   AND EXISTS (
--       SELECT 1 FROM api_key_metadata r
--       WHERE r.key_scope = 'PERSONAL'
--         AND r.key_id = s.key_id
--         AND r.user_id <> s.user_id
--         AND (
--             (r.provider IS NOT NULL AND trim(r.provider) <> '')
--             OR (r.alias IS NOT NULL AND trim(r.alias) <> '')
--         )
--   );

DELETE FROM api_key_metadata s
WHERE s.key_scope = 'PERSONAL'
  AND (s.provider IS NULL OR trim(s.provider) = '')
  AND (s.alias IS NULL OR trim(s.alias) = '')
  AND EXISTS (
      SELECT 1 FROM api_key_metadata r
      WHERE r.key_scope = 'PERSONAL'
        AND r.key_id = s.key_id
        AND r.user_id <> s.user_id
        AND (
            (r.provider IS NOT NULL AND trim(r.provider) <> '')
            OR (r.alias IS NOT NULL AND trim(r.alias) <> '')
        )
    );
