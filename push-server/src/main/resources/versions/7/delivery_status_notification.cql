CREATE TABLE delivery_status_notification (
	user text,
	server_id text,
	delivery boolean,
	read_receipt boolean,
	PRIMARY KEY (user, server_id)
);
