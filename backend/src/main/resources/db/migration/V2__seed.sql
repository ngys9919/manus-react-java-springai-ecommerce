-- =====================================================================
-- Shopwise :: V2 demo seed data
-- Demo credentials
--   customer@shopwise.dev / Password123!
--   agent@shopwise.dev    / Support123!     (role SUPPORT -> analytics + moderation)
--   admin@shopwise.dev    / Support123!     (role ADMIN)
-- =====================================================================

INSERT INTO app_user (email, password_hash, full_name, role) VALUES
 ('customer@shopwise.dev', '$2b$10$/5gT8cvcUiNK6QnsMeinmODkHTCrgS7gCNWWhUWSf73nHMEP5bXru', 'Casey Customer', 'CUSTOMER'),
 ('agent@shopwise.dev',    '$2b$10$apXdziY9ONese6vhAxR/fun/AMoaHhODVOj9X4Dwtfq9VofK0Ldna', 'Alex Agent',     'SUPPORT'),
 ('admin@shopwise.dev',    '$2b$10$apXdziY9ONese6vhAxR/fun/AMoaHhODVOj9X4Dwtfq9VofK0Ldna', 'Ari Admin',      'ADMIN');

INSERT INTO category (slug, name, description, icon, display_order) VALUES
 ('laptops',      'Laptops',            'Portable machines for work, study and creative loads.',            '💻', 1),
 ('smartphones',  'Smartphones',        'Flagships, mid-rangers and budget picks on every network.',        '📱', 2),
 ('headphones',   'Headphones & Audio', 'Over-ears, earbuds and studio gear with active noise cancelling.', '🎧', 3),
 ('smartwatches', 'Smartwatches',       'Fitness trackers and smartwatches with health sensors.',           '⌚', 4),
 ('cameras',      'Cameras',            'Mirrorless bodies, kits and action cameras for creators.',         '📷', 5),
 ('smart-home',   'Smart Home',         'Speakers, lighting, cameras and air quality sensors.',             '🏠', 6);

-- ---------------------------------------------------------------------
-- Laptops
-- ---------------------------------------------------------------------
INSERT INTO product (sku, slug, name, brand, short_description, description, category_id, price, compare_at_price, stock_quantity, rating, review_count, image_url, accent_color, tags, attributes, highlights) VALUES
('SKU-1001','aurora-book-pro-14','Aurora Book Pro 14','Nimbus',
 '14" mini-LED creator laptop with all-day battery and a colour-accurate display.',
 'The Aurora Book Pro 14 is built for people who move between the desk and the road. A mini-LED panel covers 100% of DCI-P3, the vapour-chamber cooling keeps the chip at full clocks in exports, and the 72Wh battery genuinely lasts a working day.',
 (SELECT id FROM category WHERE slug='laptops'), 1899.00, 2099.00, 14, 4.70, 218, '/media/products/SKU-1001.svg', '#4f46e5',
 'creator,premium,mini-led,usb-c',
 '{"Display":"14.2\" mini-LED 3024x1964 120Hz","Processor":"Nimbus N4 Pro 12-core","RAM":"24GB unified","Storage":"1TB NVMe SSD","Graphics":"Integrated 20-core GPU","Battery":"72Wh / up to 18h video","Weight":"1.55 kg","Ports":"3x Thunderbolt 4, HDMI 2.1, SDXC"}',
 '["100% DCI-P3 colour accuracy, factory calibrated","Fanless below 25W, silent for meetings","18-hour video playback","MagLink charging with 96W fast charge"]'),

('SKU-1002','aurora-book-air-13','Aurora Book Air 13','Nimbus',
 'Ultra-light 13" laptop that stays cool and quiet from morning to night.',
 'At 1.16 kg the Aurora Book Air 13 disappears into a bag. It is fanless, charges over USB-C and still manages a genuine 20 hours of mixed use, which makes it the safest pick for students and commuters.',
 (SELECT id FROM category WHERE slug='laptops'), 1149.00, NULL, 32, 4.50, 402, '/media/products/SKU-1002.svg', '#0ea5e9',
 'student,lightweight,fanless,budget-friendly',
 '{"Display":"13.6\" IPS 2560x1664 60Hz","Processor":"Nimbus N3 8-core","RAM":"16GB unified","Storage":"512GB NVMe SSD","Graphics":"Integrated 10-core GPU","Battery":"58Wh / up to 20h video","Weight":"1.16 kg","Ports":"2x USB-C, headphone"}',
 '["Fanless and completely silent","1.16 kg travel weight","20-hour battery life","Charges from any 65W USB-C brick"]'),

('SKU-1003','titanforge-x16','TitanForge X16 Gaming','Voltcore',
 '16" gaming and 3D workstation with discrete graphics and a 240Hz panel.',
 'TitanForge X16 pairs a 16-core CPU with discrete Voltcore graphics, so renders and game frames both land fast. The 240Hz QHD+ panel and per-key RGB make it a genuine desktop replacement.',
 (SELECT id FROM category WHERE slug='laptops'), 2299.00, 2499.00, 7, 4.40, 156, '/media/products/SKU-1003.svg', '#dc2626',
 'gaming,workstation,high-refresh,discrete-gpu',
 '{"Display":"16\" QHD+ 2560x1600 240Hz","Processor":"Voltcore V9 16-core","RAM":"32GB DDR5-5600","Storage":"2TB NVMe SSD (2 slots)","Graphics":"Voltcore RX 12GB discrete","Battery":"99Wh / up to 7h light use","Weight":"2.35 kg","Ports":"Thunderbolt 4, 3x USB-A, HDMI 2.1, 2.5GbE"}',
 '["Discrete 12GB graphics for 3D and gaming","240Hz QHD+ display","User-upgradeable RAM and storage","2.5GbE wired networking"]'),

('SKU-1004','slate-lite-15','Slate Lite 15','Corex',
 'No-nonsense 15" laptop for browsing, documents and video calls.',
 'The Slate Lite 15 covers the essentials at a price that leaves room for a monitor. Windows 11 in S mode boots in seconds, and the matte 15.6" panel is easy on the eyes for long reading sessions.',
 (SELECT id FROM category WHERE slug='laptops'), 749.00, 829.00, 0, 4.00, 511, '/media/products/SKU-1004.svg', '#64748b',
 'budget,everyday,office,matte-display',
 '{"Display":"15.6\" IPS 1920x1080 matte","Processor":"Corex C5 6-core","RAM":"8GB DDR4","Storage":"256GB NVMe SSD","Graphics":"Integrated","Battery":"45Wh / up to 9h","Weight":"1.72 kg","Ports":"2x USB-A, USB-C, HDMI 1.4, RJ45"}',
 '["Matte anti-glare 1080p panel","Full-size keyboard with number pad","Wired Ethernet for stable calls","Sub-$800 price point"]'),

-- ---------------------------------------------------------------------
-- Smartphones
-- ---------------------------------------------------------------------
('SKU-2001','pulse-12-pro','Pulse 12 Pro','Lumen',
 'Triple-camera flagship with a 1-inch main sensor and 120Hz LTPO display.',
 'The Pulse 12 Pro puts a 1-inch sensor behind a bright f/1.6 lens, so low-light shots keep detail instead of smearing it. LumenOS adds seven years of security patches.',
 (SELECT id FROM category WHERE slug='smartphones'), 1099.00, 1199.00, 21, 4.80, 934, '/media/products/SKU-2001.svg', '#7c3aed',
 'flagship,camera,5g,long-support',
 '{"Display":"6.7\" LTPO AMOLED 120Hz","Processor":"Lumen L3 Fusion","RAM":"12GB","Storage":"256GB","Main camera":"50MP 1\" + 48MP UW + 12MP 5x","Battery":"5000 mAh","Charging":"80W wired / 30W wireless","Water resistance":"IP68"}',
 '["1-inch main sensor for low light","5x periscope telephoto","7 years of OS and security updates","IP68 water and dust resistance"]'),

('SKU-2002','pulse-12','Pulse 12','Lumen',
 'The flagship experience at a mid-range price, with the same software promise.',
 'Pulse 12 keeps the 120Hz AMOLED, the LumenOS support window and the 5000 mAh battery, and trades only the telephoto lens and the 1-inch sensor for a very approachable price.',
 (SELECT id FROM category WHERE slug='smartphones'), 799.00, 849.00, 44, 4.60, 1287, '/media/products/SKU-2002.svg', '#2563eb',
 'midrange,value,5g,long-support',
 '{"Display":"6.4\" AMOLED 120Hz","Processor":"Lumen L3","RAM":"8GB","Storage":"128GB","Main camera":"50MP + 12MP ultrawide","Battery":"5000 mAh","Charging":"45W wired / 15W wireless","Water resistance":"IP67"}',
 '["Same 7-year software support as the Pro","120Hz AMOLED at mid-range price","50MP main camera with OIS","IP67 rated"]'),

('SKU-2003','vertex-ultra-5g','Vertex Ultra 5G','Voltcore',
 'Enthusiast phone with a 144Hz display, huge battery and gaming thermals.',
 'Vertex Ultra 5G is tuned for sustained performance: a vapour chamber, 144Hz display and a 6000 mAh cell that still charges to 50% in 15 minutes.',
 (SELECT id FROM category WHERE slug='smartphones'), 949.00, NULL, 12, 4.30, 388, '/media/products/SKU-2003.svg', '#f59e0b',
 'gaming,high-refresh,big-battery,5g',
 '{"Display":"6.8\" AMOLED 144Hz","Processor":"Voltcore V9 Mobile","RAM":"16GB","Storage":"512GB","Main camera":"64MP + 50MP telephoto 3x","Battery":"6000 mAh","Charging":"120W wired","Water resistance":"IP68"}',
 '["144Hz display tuned for gaming","6000 mAh battery, 120W charging","Vapour-chamber cooling for sustained load","16GB RAM as standard"]'),

('SKU-2004','nova-edge-se','Nova Edge SE','Corex',
 'Compact, capable and cheap to run — the sensible second phone.',
 'Nova Edge SE is small enough for one hand and lasts two days on light use. It is the phone to buy when the priority is battery, price and nothing fragile.',
 (SELECT id FROM category WHERE slug='smartphones'), 429.00, 479.00, 58, 4.10, 1560, '/media/products/SKU-2004.svg', '#059669',
 'budget,compact,battery,value',
 '{"Display":"6.1\" LCD 90Hz","Processor":"Corex C7","RAM":"6GB","Storage":"128GB + microSD","Main camera":"48MP + 2MP depth","Battery":"5200 mAh","Charging":"33W wired","Water resistance":"IP54 splash resistant"}',
 '["Two-day battery in light use","microSD expansion slot","One-hand 6.1\" form factor","Under $450"]'),

-- ---------------------------------------------------------------------
-- Headphones & Audio
-- ---------------------------------------------------------------------
('SKU-3001','halo-anc-studio','Halo ANC Studio','Sonare',
 'Reference-grade over-ears with adaptive noise cancelling and 60-hour battery.',
 'Halo ANC Studio is a monitoring headphone first and a travel companion second. Adaptive ANC measures the seal on your ear every second, and the 40mm beryllium-coated drivers stay flat across the range.',
 (SELECT id FROM category WHERE slug='headphones'), 349.00, 399.00, 26, 4.70, 742, '/media/products/SKU-3001.svg', '#1d4ed8',
 'anc,over-ear,studio,long-battery',
 '{"Type":"Over-ear, closed back","Battery":"60h (ANC on)","Noise cancelling":"Adaptive hybrid ANC","Connectivity":"Bluetooth 5.4, LDAC, 3.5mm, USB-C","Weight":"268 g","Drivers":"40mm beryllium-coated","Water resistance":"None","Codecs":"SBC, AAC, LDAC, aptX Lossless"}',
 '["Adaptive ANC adjusts to your seal","60 hours with ANC enabled","Flat, monitoring-grade tuning","Wired 3.5mm and USB-C audio paths"]'),

('SKU-3002','halo-buds-pro','Halo Buds Pro','Sonare',
 'Compact ANC earbuds with spatial audio and wireless charging case.',
 'Halo Buds Pro deliver most of the Studio experience in a case that fits a coin pocket, with head-tracked spatial audio for films and 8 hours per charge.',
 (SELECT id FROM category WHERE slug='headphones'), 199.00, 229.00, 63, 4.50, 1104, '/media/products/SKU-3002.svg', '#0891b2',
 'anc,earbuds,spatial-audio,wireless-charging',
 '{"Type":"In-ear true wireless","Battery":"8h + 28h case","Noise cancelling":"Hybrid ANC","Connectivity":"Bluetooth 5.4, multipoint","Weight":"5.1 g per bud","Drivers":"11mm dynamic","Water resistance":"IPX4","Codecs":"SBC, AAC, LDAC"}',
 '["Head-tracked spatial audio","Multipoint to two devices","Wireless charging case","IPX4 sweat resistance"]'),

('SKU-3003','deepwave-700','Deepwave 700','Voltcore',
 'Bass-forward ANC headphones with a low-latency gaming mode.',
 'Deepwave 700 leans into low end without smothering the mids, and the 40ms gaming mode makes it usable for competitive play as well as commutes.',
 (SELECT id FROM category WHERE slug='headphones'), 279.00, 329.00, 0, 4.20, 465, '/media/products/SKU-3003.svg', '#b91c1c',
 'anc,over-ear,bass,gaming-mode',
 '{"Type":"Over-ear, closed back","Battery":"45h (ANC on)","Noise cancelling":"Feed-forward ANC","Connectivity":"Bluetooth 5.3, USB dongle","Weight":"295 g","Drivers":"45mm dynamic","Water resistance":"None","Codecs":"SBC, AAC, aptX Adaptive"}',
 '["40ms low-latency gaming mode","45mm bass-tuned drivers","USB-C dongle included for PC","45-hour battery"]'),

('SKU-3004','airtone-lite','AirTone Lite','Corex',
 'Simple, light earbuds for calls, podcasts and the commute.',
 'AirTone Lite gets the basics right: 6 hours per charge, a pocketable case, clear call pickup and a price that makes losing one pair survivable.',
 (SELECT id FROM category WHERE slug='headphones'), 99.00, 119.00, 88, 3.90, 2130, '/media/products/SKU-3004.svg', '#16a34a',
 'budget,earbuds,calls,everyday',
 '{"Type":"In-ear true wireless","Battery":"6h + 18h case","Noise cancelling":"Passive isolation only","Connectivity":"Bluetooth 5.2","Weight":"4.4 g per bud","Drivers":"10mm dynamic","Water resistance":"IPX4","Codecs":"SBC, AAC"}',
 '["Dual-mic call clarity","Featherweight 4.4 g buds","18 hours total with case","Under $100"]'),

-- ---------------------------------------------------------------------
-- Smartwatches
-- ---------------------------------------------------------------------
('SKU-4001','chrono-s9','Chrono S9','Lumen',
 'Flagship smartwatch with ECG, temperature sensing and always-on display.',
 'Chrono S9 adds medical-grade ECG, overnight temperature trend and offline maps to the LumenOS ecosystem, with a sapphire crystal that shrugs off daily abuse.',
 (SELECT id FROM category WHERE slug='smartwatches'), 449.00, 499.00, 18, 4.60, 690, '/media/products/SKU-4001.svg', '#4f46e5',
 'flagship,ecg,sapphire,offline-maps',
 '{"Display":"1.9\" LTPO AMOLED always-on","Battery":"36h smart / 14 days saver","GPS":"Dual-band GPS + offline maps","Water resistance":"10 ATM + IP6X","Sensors":"ECG, SpO2, skin temperature, fall detection","Compatibility":"Android 10+, iOS 15+"}',
 '["ECG and skin temperature sensing","Dual-band GPS with offline maps","Sapphire crystal display","10 ATM dive-rated"]'),

('SKU-4002','chrono-se','Chrono SE','Lumen',
 'Mid-range smartwatch with the core health suite and 7-day battery.',
 'Chrono SE keeps SpO2, sleep staging and the always-on display, drops the ECG and sapphire, and stretches battery life to a full week.',
 (SELECT id FROM category WHERE slug='smartwatches'), 249.00, NULL, 41, 4.40, 1223, '/media/products/SKU-4002.svg', '#0284c7',
 'midrange,health,battery,value',
 '{"Display":"1.7\" AMOLED always-on","Battery":"7 days smart / 18 days saver","GPS":"Single-band GPS","Water resistance":"5 ATM","Sensors":"SpO2, sleep staging, heart rate","Compatibility":"Android 9+, iOS 14+"}',
 '["7-day real-world battery","SpO2 and sleep staging","Always-on AMOLED","Works with Android and iOS"]'),

('SKU-4003','trailmaster-gps','TrailMaster GPS','Voltcore',
 'Rugged multisport watch with barometric altitude and solar assist.',
 'TrailMaster GPS is built for trails and long rides: barometric altitude, a solar ring that tops the battery up outdoors, and a bezel that survives rock contact.',
 (SELECT id FROM category WHERE slug='smartwatches'), 379.00, 429.00, 9, 4.50, 302, '/media/products/SKU-4003.svg', '#ea580c',
 'outdoor,multisport,solar,rugged',
 '{"Display":"1.4\" MIP transflective","Battery":"21 days smart / 60h GPS","GPS":"Multi-band GNSS","Water resistance":"10 ATM","Sensors":"Barometric altimeter, compass, SpO2","Compatibility":"Android 8+, iOS 14+"}',
 '["Solar ring extends runtime outdoors","Barometric altitude and compass","60 hours in full GPS mode","MIL-STD-810 rugged bezel"]'),

('SKU-4004','fitband-5','FitBand 5','Corex',
 'Lightweight fitness band for steps, sleep and notifications.',
 'FitBand 5 is the cheapest sensible way into daily tracking. It weighs 24 grams, lasts two weeks and syncs sleep and step data to the Corex Health app.',
 (SELECT id FROM category WHERE slug='smartwatches'), 89.00, 99.00, 120, 4.00, 3410, '/media/products/SKU-4004.svg', '#65a30d',
 'budget,fitness-band,sleep,lightweight',
 '{"Display":"1.1\" AMOLED","Battery":"14 days","GPS":"Connected GPS via phone","Water resistance":"5 ATM","Sensors":"Heart rate, SpO2, sleep","Compatibility":"Android 8+, iOS 13+"}',
 '["24 g on the wrist, sleeps comfortably","14-day battery","5 ATM swim-proof","Connected GPS through your phone"]'),

-- ---------------------------------------------------------------------
-- Cameras
-- ---------------------------------------------------------------------
('SKU-5001','optix-r7-mirrorless','Optix R7 Mirrorless Body','Sonare',
 '33MP full-frame mirrorless body with in-body stabilisation.',
 'Optix R7 pairs a 33MP back-illuminated full-frame sensor with 8-stop in-body stabilisation and 4K120 video, in a body that weighs less than many APS-C rivals.',
 (SELECT id FROM category WHERE slug='cameras'), 1699.00, 1849.00, 11, 4.80, 274, '/media/products/SKU-5001.svg', '#0f766e',
 'full-frame,mirrorless,ibis,4k120',
 '{"Sensor":"33MP full-frame BSI CMOS","Resolution":"33 MP","Video":"4K120 10-bit internal","Mount":"Sonare R mount","Stabilisation":"8-stop 5-axis IBIS","Weight":"658 g","Viewfinder":"3.69M-dot OLED","Battery":"580 shots CIPA"}',
 '["33MP back-illuminated full-frame sensor","8-stop in-body stabilisation","4K120 10-bit internal recording","Dual card slots (CFexpress + SD)"]'),

('SKU-5002','optix-r7-kit-24-70','Optix R7 Kit 24-70mm','Sonare',
 'The Optix R7 body bundled with the constant-aperture 24-70mm f/2.8 zoom.',
 'The complete working kit: the 33MP Optix R7 body plus the 24-70mm f/2.8 zoom that covers portraits, events and documentary work without a lens change.',
 (SELECT id FROM category WHERE slug='cameras'), 2199.00, 2598.00, 6, 4.90, 121, '/media/products/SKU-5002.svg', '#115e59',
 'full-frame,kit,zoom,f2.8',
 '{"Sensor":"33MP full-frame BSI CMOS","Resolution":"33 MP","Video":"4K120 10-bit internal","Mount":"Sonare R mount (24-70mm f/2.8 included)","Stabilisation":"8-stop 5-axis IBIS + lens IS","Weight":"1.24 kg with lens","Viewfinder":"3.69M-dot OLED","Battery":"580 shots CIPA"}',
 '["Includes 24-70mm f/2.8 zoom (worth $900)","Constant f/2.8 across the zoom range","Weather-sealed lens mount","Saves $399 against buying separately"]'),

('SKU-5003','snappro-action-4k','SnapPro Action 4K','Voltcore',
 'Pocket action camera with 4K120, horizon lock and 10m waterproof body.',
 'SnapPro Action 4K shoots stabilised 4K120 without a housing, locks the horizon through full rotations and mounts to anything with the standard two-prong clip.',
 (SELECT id FROM category WHERE slug='cameras'), 399.00, 449.00, 34, 4.40, 588, '/media/products/SKU-5003.svg', '#9333ea',
 'action,4k,waterproof,stabilisation',
 '{"Sensor":"1/1.9\" CMOS","Resolution":"27 MP stills","Video":"4K120 / 2.7K240 slow motion","Mount":"Two-prong action mount","Stabilisation":"Horizon lock + gyro EIS","Weight":"154 g","Viewfinder":"2.3\" rear touchscreen","Battery":"Up to 160 min 1080p"}',
 '["Waterproof to 10 m without a housing","Horizon lock through 360° rotation","4K120 and 2.7K240 slow motion","Magnetic quick-release mount"]'),

('SKU-5004','vista-compact-zoom','Vista Compact Zoom','Corex',
 'Superzoom point-and-shoot with a 30x lens and simple controls.',
 'Vista Compact Zoom is the travel camera for people who do not want to think about settings: a 30x zoom, reliable metering and a battery that lasts a trip.',
 (SELECT id FROM category WHERE slug='cameras'), 529.00, 579.00, 23, 4.10, 812, '/media/products/SKU-5004.svg', '#475569',
 'point-and-shoot,superzoom,travel,easy',
 '{"Sensor":"1/2.3\" CMOS","Resolution":"20 MP","Video":"4K30","Mount":"Fixed 24-720mm equivalent","Stabilisation":"Optical + electronic hybrid","Weight":"299 g","Viewfinder":"Electronic viewfinder","Battery":"390 shots CIPA"}',
 '["30x optical zoom in a pocket body","Electronic viewfinder for sunny days","Simple auto modes for travel","Under $550"]'),

-- ---------------------------------------------------------------------
-- Smart Home
-- ---------------------------------------------------------------------
('SKU-6001','homehub-mini-speaker','HomeHub Mini Speaker','Lumen',
 'Room-filling smart speaker and hub with on-device wake word.',
 'HomeHub Mini doubles as a smart-home hub: Zigbee and Thread radios are built in, so lights and sensors pair directly without a bridge, and the wake word is processed on-device.',
 (SELECT id FROM category WHERE slug='smart-home'), 129.00, 149.00, 52, 4.50, 964, '/media/products/SKU-6001.svg', '#4338ca',
 'speaker,hub,thread,zigbee',
 '{"Connectivity":"Wi-Fi 6, Bluetooth 5.2, Thread, Zigbee","Power":"Mains, 30W","Compatibility":"Matter, Thread, Zigbee, Spotify Connect","Dimensions":"14 x 10 x 10 cm","Warranty":"2 years","Microphone":"4-mic array with on-device wake word"}',
 '["Built-in Thread and Zigbee hub","On-device wake word processing","Matter controller for cross-brand devices","Mute switch that cuts power to the mics"]'),

('SKU-6002','auraglow-smart-bulb-4pack','AuraGlow Smart Bulb 4-Pack','Lumen',
 'Four colour smart bulbs with 16M colours and no hub required.',
 'AuraGlow bulbs connect straight to Wi-Fi, support Matter for cross-ecosystem control, and reach 1100 lumens — bright enough to replace a 75W incandescent.',
 (SELECT id FROM category WHERE slug='smart-home'), 59.00, 79.00, 140, 4.30, 2480, '/media/products/SKU-6002.svg', '#c026d3',
 'lighting,matter,colour,value-pack',
 '{"Connectivity":"Wi-Fi 2.4GHz, Bluetooth setup, Matter","Power":"9W (75W equivalent)","Compatibility":"Matter, Alexa, Google, SmartThings","Dimensions":"A19 bulb, E26 base","Warranty":"2 years","Brightness":"1100 lumens, 16M colours"}',
 '["Four bulbs, no hub needed","Matter certified for cross-ecosystem use","1100 lumens — replaces a 75W bulb","Scenes and schedules in the app"]'),

('SKU-6003','securecam-360','SecureCam 360','Voltcore',
 'Indoor pan-tilt camera with on-device person detection and local storage.',
 'SecureCam 360 pans 355° and stores clips on a local microSD card, so recordings stay in the house unless you choose cloud backup.',
 (SELECT id FROM category WHERE slug='smart-home'), 149.00, NULL, 3, 4.40, 733, '/media/products/SKU-6003.svg', '#dc2626',
 'security,privacy,local-storage,pan-tilt',
 '{"Connectivity":"Wi-Fi 6, Bluetooth setup","Power":"Mains, 5V USB-C","Compatibility":"Matter, Alexa, Google, HomeKit","Dimensions":"11 x 8 x 8 cm","Warranty":"2 years","Resolution":"2K pan-tilt, 355° / 90°"}',
 '["Local microSD storage, no subscription required","On-device person detection","Physical privacy shutter","Matter and HomeKit compatible"]'),

('SKU-6004','pureair-monitor','PureAir Monitor','Corex',
 'Desktop air quality monitor tracking PM2.5, CO2 and volatile organics.',
 'PureAir Monitor turns invisible air quality into a number you can act on, with a colour ring and a 24-hour history that shows how cooking and ventilation change the room.',
 (SELECT id FROM category WHERE slug='smart-home'), 199.00, 229.00, 0, 4.60, 421, '/media/products/SKU-6004.svg', '#0d9488',
 'air-quality,co2,sensors,health',
 '{"Connectivity":"Wi-Fi 2.4GHz, Bluetooth","Power":"USB-C, 12W","Compatibility":"Matter, Alexa, Google","Dimensions":"12 x 12 x 6 cm","Warranty":"2 years","Sensors":"PM2.5, PM10, CO2 (NDIR), TVOC, temperature, humidity"}',
 '["NDIR CO2 sensor, not a cheap estimate","24-hour history with export","Matter certified for automations","Colour ring readable across a room"]');

-- ---------------------------------------------------------------------
-- A little order history so the assistant has something to reason about
-- ---------------------------------------------------------------------
INSERT INTO shop_order (reference, user_id, status, subtotal, shipping_fee, tax_total, grand_total, payment_method,
                        shipping_name, shipping_line1, shipping_city, shipping_postcode, shipping_country, placed_at)
VALUES
 ('SW-240915-0001', (SELECT id FROM app_user WHERE email='customer@shopwise.dev'), 'DELIVERED', 1149.00, 0.00, 91.92, 1240.92, 'CARD_DEMO',
  'Casey Customer', '18 Harbour View Road', 'Singapore', '098765', 'Singapore', now() - interval '27 days'),
 ('SW-241002-0002', (SELECT id FROM app_user WHERE email='customer@shopwise.dev'), 'SHIPPED', 199.00, 12.00, 16.88, 227.88, 'CARD_DEMO',
  'Casey Customer', '18 Harbour View Road', 'Singapore', '098765', 'Singapore', now() - interval '9 days');

INSERT INTO order_item (order_id, product_id, sku, product_name, image_url, unit_price, quantity, line_total)
SELECT o.id, p.id, p.sku, p.name, p.image_url, 1149.00, 1, 1149.00
FROM shop_order o, product p
WHERE o.reference = 'SW-240915-0001' AND p.sku = 'SKU-1002';

INSERT INTO order_item (order_id, product_id, sku, product_name, image_url, unit_price, quantity, line_total)
SELECT o.id, p.id, p.sku, p.name, p.image_url, 199.00, 1, 199.00
FROM shop_order o, product p
WHERE o.reference = 'SW-241002-0002' AND p.sku = 'SKU-3002';
