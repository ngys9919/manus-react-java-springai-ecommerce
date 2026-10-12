-- =====================================================================
-- Shopwise :: V3 product interactions and recommendation seed data
-- PostgreSQL 16
-- =====================================================================

-- ---------------------------------------------------------------------
-- Interaction log
--
-- One row per thing a shopper did with a product. This is the raw material
-- for every recommendation strategy: co-occurrence needs to know which
-- products share shoppers, trending needs to know when things happened, and
-- personalisation needs to know what this shopper already looked at.
--
-- `weight` is stored rather than derived so the aggregation queries stay
-- plain sums. It is written from the InteractionType enum at insert time,
-- so the enum remains the single source of truth for the values.
--
-- `recommendation_id` groups the impressions of one rendered rail with the
-- clicks that followed, which is what makes click-through measurable.
-- ---------------------------------------------------------------------
CREATE TABLE product_interaction (
    id                BIGSERIAL    PRIMARY KEY,
    product_id        BIGINT       NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    user_id           BIGINT       REFERENCES app_user (id) ON DELETE SET NULL,
    -- 'user:<id>' for signed-in shoppers, 'guest:<token>' otherwise. Keeping the
    -- identity in one column means anonymous browsing still contributes.
    actor_key         VARCHAR(80)  NOT NULL,
    interaction       VARCHAR(24)  NOT NULL,
    weight            NUMERIC(6, 3) NOT NULL DEFAULT 1.0,
    recommendation_id VARCHAR(64),
    surface           VARCHAR(40),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_interaction_product ON product_interaction (product_id, interaction);
CREATE INDEX idx_interaction_actor   ON product_interaction (actor_key, created_at DESC);
CREATE INDEX idx_interaction_recent  ON product_interaction (created_at DESC);
CREATE INDEX idx_interaction_kind    ON product_interaction (interaction, created_at DESC);
CREATE INDEX idx_interaction_reco    ON product_interaction (recommendation_id)
    WHERE recommendation_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- Seeded behaviour history
--
-- The catalogue alone cannot demonstrate collaborative filtering: with no
-- interaction history every co-occurrence query returns nothing and the
-- "customers also bought" rail is permanently empty. This generates a
-- plausible history instead, with deliberate structure:
--
--   * shoppers browse mostly within one category, unevenly distributed so
--     the catalogue does not look uniformly popular;
--   * a share of them also browse a complementary category;
--   * buyers take a specific complementary product with them, drawn from a
--     small per-product bundle set rather than at random. Scattering
--     purchases across every possible pair would leave each pair with one
--     or two examples, which is not enough to claim anything;
--   * purchases skew recent, so time-decayed trending has a real signal.
--
-- setseed makes it reproducible: rebuilding the database yields the same
-- dataset, so screenshots and tests stay stable.
-- ---------------------------------------------------------------------
SELECT setseed(0.4242);

-- Categories numbered by display order, so the seed never depends on the
-- surrogate key values.
CREATE TEMP TABLE seed_category ON COMMIT DROP AS
SELECT id, row_number() OVER (ORDER BY display_order, id) AS rn
FROM category;

-- Which categories go together: laptops with audio, phones with watches,
-- cameras with smart home.
CREATE TEMP TABLE seed_pair (left_rn INT, right_rn INT) ON COMMIT DROP;
INSERT INTO seed_pair (left_rn, right_rn) VALUES (1, 3), (2, 4), (5, 6);

-- 160 synthetic shoppers. `pair_rn` is the complementary category that makes
-- laptop shoppers look at headphones, phone shoppers at watches, and so on.
CREATE TEMP TABLE seed_shopper ON COMMIT DROP AS
SELECT
    'seed-guest-' || lpad(i::text, 3, '0') AS actor_key,
    CASE
        WHEN i % 12 < 3 THEN 1
        WHEN i % 12 < 6 THEN 2
        WHEN i % 12 < 8 THEN 3
        WHEN i % 12 < 10 THEN 4
        WHEN i % 12 < 11 THEN 5
        ELSE 6
    END AS home_rn,
    CASE
        WHEN i % 12 < 3 THEN 3
        WHEN i % 12 < 6 THEN 4
        WHEN i % 12 < 8 THEN 1
        WHEN i % 12 < 10 THEN 2
        WHEN i % 12 < 11 THEN 6
        ELSE 5
    END AS pair_rn
FROM generate_series(1, 160) AS i;

-- Browsing within the home category.
INSERT INTO product_interaction (product_id, actor_key, interaction, weight, surface, created_at)
SELECT p.id, s.actor_key, 'VIEW', 1.0, 'seed',
       now() - (random() * random() * interval '20 days')
FROM seed_shopper s
JOIN seed_category c ON c.rn = s.home_rn
JOIN LATERAL (
    SELECT id FROM product WHERE category_id = c.id AND active ORDER BY random() LIMIT 5
) p ON TRUE;

-- Browsing the complementary category.
INSERT INTO product_interaction (product_id, actor_key, interaction, weight, surface, created_at)
SELECT p.id, s.actor_key, 'VIEW', 1.0, 'seed',
       now() - (random() * random() * interval '20 days')
FROM seed_shopper s
JOIN seed_category c ON c.rn = s.pair_rn
JOIN LATERAL (
    SELECT id FROM product WHERE category_id = c.id AND active ORDER BY random() LIMIT 3
) p ON TRUE;

-- Two of the viewed products go into a cart.
INSERT INTO product_interaction (product_id, actor_key, interaction, weight, surface, created_at)
SELECT v.product_id, v.actor_key, 'CART_ADD', 3.0, 'seed',
       now() - (random() * random() * interval '14 days')
FROM (
    SELECT pi.product_id, pi.actor_key,
           row_number() OVER (PARTITION BY pi.actor_key ORDER BY random()) AS pick
    FROM product_interaction pi
    WHERE pi.surface = 'seed' AND pi.interaction = 'VIEW'
) v
WHERE v.pick <= 2;

-- Most carts convert, but not all: abandoned carts are realistic and keep the
-- purchase signal stronger than the cart signal rather than identical to it.
INSERT INTO product_interaction (product_id, actor_key, interaction, weight, surface, created_at)
SELECT c.product_id, c.actor_key, 'PURCHASE', 5.0, 'seed',
       now() - (random() * random() * interval '10 days')
FROM (
    SELECT pi.product_id, pi.actor_key,
           row_number() OVER (PARTITION BY pi.actor_key ORDER BY random()) AS pick
    FROM product_interaction pi
    WHERE pi.surface = 'seed' AND pi.interaction = 'CART_ADD'
) c
WHERE c.pick = 1 AND random() < 0.8;

-- Each product's bundle partners: up to two products from the complementary
-- category. Restricting to a small set is what makes co-purchase dense enough
-- to be evidence rather than coincidence.
CREATE TEMP TABLE seed_bundle ON COMMIT DROP AS
SELECT ranked.product_id, ranked.bundle_id
FROM (
    SELECT a.id AS product_id,
           b.id AS bundle_id,
           row_number() OVER (PARTITION BY a.id ORDER BY b.id) AS pick
    FROM product a
    JOIN seed_category a_rn ON a_rn.id = a.category_id
    JOIN seed_pair sp ON sp.left_rn = a_rn.rn OR sp.right_rn = a_rn.rn
    JOIN seed_category b_rn
      ON b_rn.rn = CASE WHEN sp.left_rn = a_rn.rn THEN sp.right_rn ELSE sp.left_rn END
    JOIN product b ON b.category_id = b_rn.id AND b.active
    WHERE a.active
) ranked
WHERE ranked.pick <= 2;

-- Buyers take a bundle partner with them most of the time. This is the
-- structure that makes "frequently bought together" a real claim: the same
-- pairings recur across many independent baskets.
INSERT INTO product_interaction (product_id, actor_key, interaction, weight, surface, created_at)
SELECT DISTINCT ON (buyer.actor_key)
       sb.bundle_id, buyer.actor_key, 'PURCHASE', 5.0, 'seed',
       now() - (random() * random() * interval '10 days')
FROM (
    SELECT DISTINCT pi.actor_key, pi.product_id
    FROM product_interaction pi
    WHERE pi.interaction = 'PURCHASE' AND pi.surface = 'seed'
) buyer
JOIN seed_bundle sb ON sb.product_id = buyer.product_id
WHERE random() < 0.7
-- Exactly one complement per basket. Letting a buyer take both of a product's
-- bundle partners produced baskets with two laptops and one pair of headphones,
-- which is not what a real basket looks like and makes the co-purchase signal
-- noisier than it needs to be.
ORDER BY buyer.actor_key, random();

-- A little wishlisting, which sits between browsing and buying in intent.
INSERT INTO product_interaction (product_id, actor_key, interaction, weight, surface, created_at)
SELECT v.product_id, v.actor_key, 'WISHLIST', 2.5, 'seed',
       now() - (random() * random() * interval '16 days')
FROM (
    SELECT pi.product_id, pi.actor_key,
           row_number() OVER (PARTITION BY pi.actor_key ORDER BY random()) AS pick
    FROM product_interaction pi
    WHERE pi.surface = 'seed' AND pi.interaction = 'VIEW' AND random() < 0.35
) v
WHERE v.pick = 1;

COMMENT ON TABLE product_interaction IS
    'Append-only behaviour log. Seeded with a synthetic history in V3 so that '
    'co-occurrence, trending and personalisation all have data to work with.';
