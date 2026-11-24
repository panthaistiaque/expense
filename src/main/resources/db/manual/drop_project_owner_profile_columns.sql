-- Run once on an existing MySQL/TiDB database after deploying the updated Project entity.
ALTER TABLE `project`
    DROP COLUMN `owner_name`,
    DROP COLUMN `owner_image_url`;
