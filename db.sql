CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(100) NOT NULL DEFAULT 'ROLE_USER',
    status VARCHAR(100) NOT NULL DEFAULT 'OFFLINE',
    image_url TEXT UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    is_authenticated BOOLEAN NOT NULL DEFAULT FALSE,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_account_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    is_credentials_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    is_account_non_locked BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS chat_rooms (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    creator_id UUID NOT NULL,
    type VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    CONSTRAINT fk_user FOREIGN KEY(creator_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS messages (
    id SERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    sender_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    chat_room_id INTEGER NOT NULL,
    status VARCHAR(100) NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    read_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    CONSTRAINT fk_sender_user FOREIGN KEY(sender_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_recipient_user FOREIGN KEY(recipient_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_room FOREIGN KEY(chat_room_id) REFERENCES chat_rooms(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS chat_room_memberships (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    chat_room_id INTEGER NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    role VARCHAR(100) NOT NULL DEFAULT 'MEMBER',
    CONSTRAINT fk_recipient_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_room FOREIGN KEY(chat_room_id) REFERENCES chat_rooms(id) ON DELETE CASCADE
);

CREATE TABLE otp (
    id SERIAL PRIMARY KEY,
    otp_code VARCHAR(6) NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE jwt_tokens (
    id SERIAL PRIMARY KEY,
    token TEXT NOT NULL UNIQUE,
    token_type VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tokens (
    id SERIAL PRIMARY KEY,
    token TEXT NOT NULL UNIQUE,
    token_type VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_at TIMESTAMP WITH TIME ZONE,
    user_id UUID NOT NULL,
    CONSTRAINT fk_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX chat_messages_idx_chat_id ON messages(chat_room_id);
CREATE INDEX chat_messages_idx_sender_id ON messages(sender_id);
CREATE INDEX chat_messages_idx_receiver_id ON messages(recipient_id);
CREATE INDEX chat_messages_idx_timestamp ON messages(sent_at);

ALTER TABLE messages DROP COLUMN status;
ALTER TABLE messages DROP COLUMN deleted_at;
ALTER TABLE messages DROP COLUMN read_at;

CREATE TABLE IF NOT EXISTS message_recipients (
    id SERIAL PRIMARY KEY,
    message_id INTEGER NOT NULL,
    recipient_id UUID NOT NULL,
    status VARCHAR(100) NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    read_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    CONSTRAINT fk_msg FOREIGN KEY(message_id) references messages(id),
    CONSTRAINT fk_user FOREIGN KEY(recipient_id) references users(id)
);

CREATE TABLE user_devices (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    device_token TEXT NOT NULL,
    device_type VARCHAR(50),
    last_active TIMESTAMP WITH TIME ZONE DEFAULT now(),
    CONSTRAINT fk_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

ALTER TABLE user_devices ADD COLUMN is_online BOOLEAN NOT NULL DEFAULT FALSE;

DROP TABLE jwt_tokens;