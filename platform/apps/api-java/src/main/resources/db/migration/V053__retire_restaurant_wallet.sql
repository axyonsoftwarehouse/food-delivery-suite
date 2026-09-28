-- Keep historical ledger and payout rows intact. The marker replaces merchant sale entries
-- as the idempotency record for future courier settlements.
CREATE TABLE order_finance_postings (
  order_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
  posted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_order_finance_postings_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

INSERT INTO order_finance_postings (order_id)
SELECT DISTINCT order_id FROM ledger_entries
WHERE order_id IS NOT NULL AND kind IN ('sale', 'delivery_fee', 'tip');

UPDATE commission_rules SET active = FALSE WHERE active = TRUE;
UPDATE subscription_packages SET commission_percent = 0 WHERE commission_percent <> 0;
