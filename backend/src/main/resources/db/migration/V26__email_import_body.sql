ALTER TABLE email_imports
    ADD COLUMN body LONGTEXT NULL,
    ADD COLUMN body_format VARCHAR(16) NULL;
