-- =====================================================================
-- Shopwise :: V1 baseline schema
-- PostgreSQL 16
-- =====================================================================

-- ---------------------------------------------------------------------
-- Identity & access
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    role            VARCHAR(32)  NOT NULL DEFAULT 'CUSTOMER',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- Catalog
-- ---------------------------------------------------------------------
CREATE TABLE category (
    id              BIGSERIAL PRIMARY KEY,
    slug            VARCHAR(80)  NOT NULL UNIQUE,
    name            VARCHAR(120) NOT NULL,
    description     TEXT,
    icon            VARCHAR(16),
    display_order   INT          NOT NULL DEFAULT 0
);

CREATE TABLE product (
    id                  BIGSERIAL PRIMARY KEY,
    sku                 VARCHAR(40)   NOT NULL UNIQUE,
    slug                VARCHAR(160)  NOT NULL UNIQUE,
    name                VARCHAR(200)  NOT NULL,
    brand               VARCHAR(120)  NOT NULL,
    short_description   VARCHAR(400),
    description         TEXT,
    category_id         BIGINT        NOT NULL REFERENCES category (id),
    price               NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    compare_at_price    NUMERIC(12, 2),
    currency            CHAR(3)       NOT NULL DEFAULT 'USD',
    stock_quantity      INT           NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    rating              NUMERIC(3, 2) NOT NULL DEFAULT 0 CHECK (rating >= 0 AND rating <= 5),
    review_count        INT           NOT NULL DEFAULT 0,
    image_url           TEXT,
    accent_color        VARCHAR(16),
    tags                VARCHAR(400),
    -- Free-form spec sheet used to build side-by-side comparison tables.
    attributes          JSONB         NOT NULL DEFAULT '{}'::jsonb,
    -- Marketing bullet points rendered on the product page.
    highlights          JSONB         NOT NULL DEFAULT '[]'::jsonb,
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_category ON product (category_id);
CREATE INDEX idx_product_price ON product (price);
CREATE INDEX idx_product_active ON product (active);
CREATE INDEX idx_product_attributes ON product USING GIN (attributes);

-- ---------------------------------------------------------------------
-- Cart
-- ---------------------------------------------------------------------
CREATE TABLE cart (
    id              BIGSERIAL PRIMARY KEY,
    public_id       UUID         NOT NULL UNIQUE,
    user_id         BIGINT       REFERENCES app_user (id) ON DELETE CASCADE,
    guest_token     VARCHAR(64),
    status          VARCHAR(24)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_cart_user_active ON cart (user_id) WHERE status = 'ACTIVE' AND user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_cart_guest_active ON cart (guest_token) WHERE status = 'ACTIVE' AND guest_token IS NOT NULL;

CREATE TABLE cart_item (
    id              BIGSERIAL PRIMARY KEY,
    cart_id         BIGINT NOT NULL REFERENCES cart (id) ON DELETE CASCADE,
    product_id      BIGINT NOT NULL REFERENCES product (id),
    quantity        INT    NOT NULL CHECK (quantity > 0 AND quantity <= 99),
    unit_price      NUMERIC(12, 2) NOT NULL,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (cart_id, product_id)
);

-- ---------------------------------------------------------------------
-- Orders
-- ---------------------------------------------------------------------
CREATE TABLE shop_order (
    id                  BIGSERIAL PRIMARY KEY,
    reference           VARCHAR(24)  NOT NULL UNIQUE,
    user_id             BIGINT       REFERENCES app_user (id) ON DELETE SET NULL,
    guest_email         VARCHAR(255),
    status              VARCHAR(24)  NOT NULL DEFAULT 'CONFIRMED',
    subtotal            NUMERIC(12, 2) NOT NULL,
    shipping_fee        NUMERIC(12, 2) NOT NULL DEFAULT 0,
    tax_total           NUMERIC(12, 2) NOT NULL DEFAULT 0,
    grand_total         NUMERIC(12, 2) NOT NULL,
    currency            CHAR(3)      NOT NULL DEFAULT 'USD',
    payment_method      VARCHAR(32)  NOT NULL DEFAULT 'CARD_DEMO',
    shipping_name       VARCHAR(160) NOT NULL,
    shipping_line1      VARCHAR(200) NOT NULL,
    shipping_line2      VARCHAR(200),
    shipping_city       VARCHAR(120) NOT NULL,
    shipping_postcode   VARCHAR(32)  NOT NULL,
    shipping_country    VARCHAR(80)  NOT NULL,
    placed_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_user ON shop_order (user_id);
CREATE INDEX idx_order_placed_at ON shop_order (placed_at DESC);

CREATE TABLE order_item (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL REFERENCES shop_order (id) ON DELETE CASCADE,
    product_id      BIGINT REFERENCES product (id) ON DELETE SET NULL,
    sku             VARCHAR(40)  NOT NULL,
    product_name    VARCHAR(200) NOT NULL,
    image_url       TEXT,
    unit_price      NUMERIC(12, 2) NOT NULL,
    quantity        INT          NOT NULL CHECK (quantity > 0),
    line_total      NUMERIC(12, 2) NOT NULL
);

-- ---------------------------------------------------------------------
-- Shopping assistant
-- ---------------------------------------------------------------------
CREATE TABLE conversation (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID         NOT NULL UNIQUE,
    user_id             BIGINT       REFERENCES app_user (id) ON DELETE SET NULL,
    guest_token         VARCHAR(64),
    title               VARCHAR(200) NOT NULL DEFAULT 'New conversation',
    status              VARCHAR(24)  NOT NULL DEFAULT 'ACTIVE',
    channel             VARCHAR(24)  NOT NULL DEFAULT 'WEB',
    message_count       INT          NOT NULL DEFAULT 0,
    last_intent         VARCHAR(48),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_message_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversation_user ON conversation (user_id);
CREATE INDEX idx_conversation_guest ON conversation (guest_token);

CREATE TABLE chat_message (
    id                  BIGSERIAL PRIMARY KEY,
    conversation_id     BIGINT      NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    role                VARCHAR(16) NOT NULL,
    content             TEXT        NOT NULL,
    intent              VARCHAR(48),
    confidence          NUMERIC(4, 3),
    latency_ms          INT,
    model               VARCHAR(64),
    tool_calls          JSONB,
    blocks              JSONB,
    safety_flags        JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_message_conversation ON chat_message (conversation_id, id);

CREATE TABLE escalation_ticket (
    id                  BIGSERIAL PRIMARY KEY,
    reference           VARCHAR(24) NOT NULL UNIQUE,
    conversation_id     BIGINT      NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    user_id             BIGINT      REFERENCES app_user (id) ON DELETE SET NULL,
    contact_email       VARCHAR(255),
    reason              VARCHAR(64) NOT NULL,
    priority            VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    status              VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    summary             TEXT        NOT NULL,
    transcript_excerpt  TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE assistant_event (
    id                  BIGSERIAL PRIMARY KEY,
    conversation_id     BIGINT      REFERENCES conversation (id) ON DELETE SET NULL,
    user_id             BIGINT      REFERENCES app_user (id) ON DELETE SET NULL,
    event_type          VARCHAR(48) NOT NULL,
    intent              VARCHAR(48),
    tool_name           VARCHAR(64),
    outcome             VARCHAR(32),
    value_num           NUMERIC(12, 4),
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_assistant_event_type ON assistant_event (event_type, created_at DESC);

CREATE TABLE moderation_log (
    id                  BIGSERIAL PRIMARY KEY,
    conversation_id     BIGINT,
    direction           VARCHAR(8)  NOT NULL,
    category            VARCHAR(48) NOT NULL,
    severity            VARCHAR(16) NOT NULL,
    action              VARCHAR(24) NOT NULL,
    excerpt             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_moderation_log_created ON moderation_log (created_at DESC);
