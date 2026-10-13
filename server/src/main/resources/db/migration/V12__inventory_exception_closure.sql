ALTER TABLE inventory_item
    ADD COLUMN resolution_status VARCHAR(24) NOT NULL DEFAULT 'PENDING';

UPDATE inventory_item
SET resolution_status = CASE
    WHEN actual_status IS NULL THEN 'PENDING'
    WHEN difference_type IS NULL OR difference_type = 'NONE' THEN 'NOT_REQUIRED'
    ELSE 'OPEN'
END;

UPDATE inventory_item i
JOIN exception_record e ON e.inventory_item_id = i.id
SET i.resolution_status = CASE WHEN e.status = 'RESOLVED' THEN 'RESOLVED' ELSE 'OPEN' END;

INSERT INTO exception_record (inventory_item_id, exception_type, description, status)
SELECT i.id, i.difference_type, COALESCE(i.difference_description, '存量盘点差异'), 'OPEN'
FROM inventory_item i
WHERE i.resolution_status = 'OPEN'
  AND NOT EXISTS (
      SELECT 1 FROM exception_record e WHERE e.inventory_item_id = i.id
  );

CREATE INDEX idx_exception_inventory_status
    ON exception_record (inventory_item_id, status);
