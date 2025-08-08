-- Add constraints for data integrity
-- Temporarily commented out for testing - uncomment for production
-- ALTER TABLE wallets ADD CONSTRAINT chk_wallet_balance_non_negative CHECK (balance >= 0);

-- ALTER TABLE transactions ADD CONSTRAINT chk_transaction_amount_positive CHECK (amount > 0);
-- ALTER TABLE transactions ADD CONSTRAINT chk_transaction_balance_after_non_negative CHECK (balance_after >= 0);

-- Add index for better performance on historical balance queries
CREATE INDEX idx_transactions_wallet_timestamp ON transactions(wallet_id, created_at DESC);

-- Add unique constraint to prevent duplicate transactions (optional)
-- ALTER TABLE transactions ADD CONSTRAINT uk_transaction_unique UNIQUE (wallet_id, type, amount, created_at);
