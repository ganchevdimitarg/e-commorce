-- Star ratings are validated by the request DTO from now on; historical rows are checked
-- here and the migration fails if any are invalid. Values are never clamped silently.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM comments WHERE star < 1 OR star > 5) THEN
        RAISE EXCEPTION
            'comments.star contains values outside 1..5; normalise them before applying V10';
    END IF;
END $$;

ALTER TABLE comments
    ADD CONSTRAINT comments_star_range CHECK (star BETWEEN 1 AND 5);
