#!/usr/bin/env python3
"""
ReconDrive Visual Asset & Screenshot Generator
Generates high-resolution, pixel-perfect PNG screenshots for GitHub README:
1. screenshot_01_android_auto_dashboard.png (Android Auto in-vehicle head unit display, 1280x720)
2. screenshot_02_handset_tactical_hud.png (Handset Jetpack Compose Tactical HUD, 800x1400)
3. screenshot_03_skin_selector_map_themes.png (Map Skin customization dialog, 800x1100)
4. screenshot_04_skin_selector_fog_optics.png (Fog Range & Opacity calibration dialog, 800x1100)
"""

import os
from PIL import Image, ImageDraw, ImageFont
import math

OUTPUT_DIR = "/root/recondrive/screenshots"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Fonts
FONT_TITLE = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 20)
FONT_SUB = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 12)
FONT_HEAD = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 13)
FONT_TEXT = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 11)
FONT_TEXT_BOLD = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 11)
FONT_MONO = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf', 13)
FONT_MONO_LG = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf', 36)
FONT_MONO_XL = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf', 52)
FONT_MONO_SM = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf', 10)
FONT_MONO_XS = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf', 9)


# ==============================================================================
# 1. SCREENSHOT 01: ANDROID AUTO IN-VEHICLE DISPLAY (1280 x 720)
# ==============================================================================

def generate_screenshot_01():
    print("Generating Screenshot 01: Android Auto Head Unit Display...")
    W, H = 1280, 720
    img = Image.new('RGB', (W, H), '#0A0D12')
    draw = ImageDraw.Draw(img)

    cx, cy = W / 2.0, H / 2.0

    # 1. Background Vector Map Roads (Tactical Obsidian)
    # Minor roads
    draw.line([(0, cy + 180), (W, cy - 80)], fill='#1C2636', width=3)
    draw.line([(cx - 350, 0), (cx + 200, H)], fill='#1C2636', width=3)
    draw.line([(100, 0), (W - 100, H)], fill='#1C2636', width=2)
    draw.line([(0, cy - 200), (W, cy + 220)], fill='#1C2636', width=2)

    # Major thoroughfare
    draw.line([(0, cy + 90), (cx - 150, cy + 90), (cx - 50, cy - 40), (cx + 300, cy - 140), (W, cy - 160)], fill='#00A8CC', width=6)

    # 2. Explored Road Track (Phosphor Green #00E676 with neon glow)
    explored_pts = [
        (cx - 280, cy + 220),
        (cx - 100, cy + 120),
        (cx, cy),
        (cx + 140, cy - 110),
        (cx + 320, cy - 180)
    ]
    # Glow pass
    for w_glow in (16, 12, 8):
        col = '#00E676' if w_glow == 8 else '#004D25'
        draw.line(explored_pts, fill=col, width=w_glow)

    # 3. Active Navigation Route (Tactical Amber #FFD600)
    route_pts = [
        (cx, cy),
        (cx + 140, cy - 110),
        (cx + 180, cy - 250)
    ]
    for w_glow in (20, 14, 10):
        col = '#FFD600' if w_glow == 10 else '#594A00'
        draw.line(route_pts, fill=col, width=w_glow)

    # 4. Fog of War Shroud & Glowing Cyan Boundary
    beamRadius = 180.0
    for gr in (4, 3, 2, 1):
        draw.ellipse([(cx - beamRadius - gr, cy - beamRadius - gr), (cx + beamRadius + gr, cy + beamRadius + gr)], outline='#00E5FF', width=2)

    # Outer Shroud Mask (simulate dark shroud outside cleared beam)
    mask = Image.new('L', (W, H), 242)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.ellipse([(cx - beamRadius, cy - beamRadius), (cx + beamRadius, cy + beamRadius)], fill=0)
    shroud = Image.new('RGB', (W, H), '#070A0F')
    img.paste(shroud, (0, 0), mask)

    # Faint Radar Rings
    for r in (120, 240, 380, 520):
        draw.ellipse([(cx - r, cy - r), (cx + r, cy + r)], outline='#16222F', width=1)
    draw.line([(cx - 30, cy), (cx + 30, cy)], fill='#16222F', width=1)
    draw.line([(cx, cy - 30), (cx, cy + 30)], fill='#16222F', width=1)

    # 5. Vehicle Cursor (Arrow pointing 45° NE)
    arrow_pts = [
        (cx + 14, cy - 14),
        (cx - 8, cy - 4),
        (cx - 4, cy + 4),
        (cx - 14, cy + 14)
    ]
    draw.polygon(arrow_pts, fill='#00E5FF')

    # 6. Android Auto NavigationTemplate Header (Turn Card: Left Top)
    card_w, card_h = 440, 142
    draw.rounded_rectangle([(24, 24), (24 + card_w, 24 + card_h)], radius=12, fill='#161B22', outline='#30363D', width=1)
    
    # Maneuver Icon Box (64 x 64)
    draw.rounded_rectangle([(38, 38), (102, 102)], radius=8, fill='#21262D')
    # Right turn arrow graphic
    draw.line([(56, 80), (56, 60), (84, 60)], fill='#00E5FF', width=5)
    draw.polygon([(84, 52), (94, 60), (84, 68)], fill='#00E5FF')

    draw.text((116, 36), "In 400 meters", fill='#8B949E', font=FONT_HEAD)
    draw.text((116, 56), "Bear slight right onto", fill='#F0F6FC', font=FONT_TEXT)
    draw.text((116, 74), "Recon Tactical Byway", fill='#00E5FF', font=FONT_TITLE)

    # Lane Guidance Pills (Turn Right active, Go Straight secondary)
    draw.rounded_rectangle([(116, 108), (154, 130)], radius=4, fill='#21262D', outline='#00E5FF', width=1)
    draw.text((127, 110), "➔", fill='#00E5FF', font=FONT_TEXT_BOLD)
    draw.rounded_rectangle([(160, 108), (198, 130)], radius=4, fill='#21262D')
    draw.text((172, 110), "↑", fill='#8B949E', font=FONT_TEXT_BOLD)

    # Trip Progress (Remaining 14.2 km • 18 min • 15:42 ETA)
    draw.rounded_rectangle([(24, 178), (24 + card_w, 222)], radius=8, fill='#11161D', outline='#21262D', width=1)
    draw.text((40, 191), "14.2 km  •  18 min  •  15:42 ETA", fill='#F0F6FC', font=FONT_MONO)

    # Speed limit roundel badge (50 km/h)
    draw.ellipse([(24 + card_w - 56, 178 + 6), (24 + card_w - 24, 178 + 38)], outline='#FF3333', width=3, fill='#F0F6FC')
    draw.text((24 + card_w - 49, 178 + 14), "50", fill='#0A0D12', font=FONT_MONO_SM)

    # 7. Android Auto In-Car Action Strip (Right Top)
    actions = [
        ("Map: Tactic", "#00E5FF"),
        ("Fog: Radar", "#00E676"),
        ("180m", "#FFD600"),
        ("95%", "#FF9100"),
    ]
    ax = W - 24
    for atxt, acol in reversed(actions):
        tw = draw.textlength(atxt, font=FONT_MONO) + 28
        ax -= tw + 8
        draw.rounded_rectangle([(ax, 24), (ax + tw, 64)], radius=6, fill='#161B22', outline='#30363D', width=1)
        draw.text((ax + 14, 37), atxt, fill=acol, font=FONT_MONO)

    # 8. Bottom In-Car Status Strip
    draw.rectangle([(0, H - 48), (W, H)], fill='#0D1117')
    draw.line([(0, H - 48), (W, H - 48)], fill='#21262D', width=1)
    
    draw.text((24, H - 32), "RECONDRIVE TACTICAL // ANDROID AUTO HEAD UNIT ACTIVE", fill='#00E5FF', font=FONT_MONO_SM)
    status_right = "GRID: 51.5074°N, 0.1278°W  •  SPD: 48 KM/H  •  TERRITORY: 5.42 km² UNCOVERED"
    rw = draw.textlength(status_right, font=FONT_MONO_SM)
    draw.text((W - rw - 24, H - 32), status_right, fill='#8B949E', font=FONT_MONO_SM)

    out_path = os.path.join(OUTPUT_DIR, "screenshot_01_android_auto_dashboard.png")
    img.save(out_path)
    print(f"Saved: {out_path} ({os.path.getsize(out_path)} bytes)")


# ==============================================================================
# 2. SCREENSHOT 02: HANDSET TACTICAL HUD & J.A.R.V.I.S. AI COCKPIT (800 x 1600)
# ==============================================================================

def generate_screenshot_02():
    print("Generating Screenshot 02: Handset Tactical HUD & J.A.R.V.I.S. AI Cockpit...")
    W, H = 800, 1600
    img = Image.new('RGB', (W, H), '#070A0F')
    draw = ImageDraw.Draw(img)

    font_jarvis_title = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 24)
    font_contact_name = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 16)
    font_place_name = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 14)

    # 1. Status Bar
    draw.text((24, 16), "15:42", fill='#F0F6FC', font=FONT_HEAD)
    draw.text((W - 140, 16), "5G  ●●● 100%", fill='#8B949E', font=FONT_MONO_SM)

    # 2. Header Row
    draw.text((24, 52), "RECON // J.A.R.V.I.S. HUD", fill='#00E5FF', font=FONT_HEAD)
    draw.text((24, 74), "GRID: 50.8555°N, 0.2449°W  •  SUSSEX, UK", fill='#8B949E', font=FONT_MONO_SM)

    # Status Badges
    draw.rounded_rectangle([(W - 250, 50), (W - 150, 82)], radius=4, fill='#0D1B1E', outline='#00E5FF', width=1)
    draw.text((W - 240, 60), "● AI ONLINE", fill='#00E5FF', font=FONT_MONO_XS)

    draw.rounded_rectangle([(W - 140, 50), (W - 84, 82)], radius=4, fill='#121721', outline='#21262D', width=1)
    draw.text((W - 130, 60), "SKINS", fill='#8B949E', font=FONT_MONO_XS)

    draw.rounded_rectangle([(W - 74, 50), (W - 24, 82)], radius=4, fill='#121721', outline='#00E676', width=1)
    draw.text((W - 68, 60), "v1.0.8", fill='#00E676', font=FONT_MONO_XS)

    # =========================================================================
    # 3. J.A.R.V.I.S. HERO AI ASSISTANT CARD ("Where to, sir?")
    # =========================================================================
    jarvis_y = 98
    jarvis_h = 195
    draw.rounded_rectangle([(24, jarvis_y), (W - 24, jarvis_y + jarvis_h)], radius=12, fill='#0B111B', outline='#00E5FF', width=2)

    # Holographic Arc Reactor (Concentric glowing rings)
    arc_cx = 78
    arc_cy = jarvis_y + 80
    for r in (42, 34, 26, 18, 10):
        alpha_col = '#004A59' if r > 30 else ('#009BB5' if r > 18 else '#00E5FF')
        draw.ellipse([(arc_cx - r, arc_cy - r), (arc_cx + r, arc_cy + r)], outline=alpha_col, width=2)
    # Arc Reactor Core Center
    draw.ellipse([(arc_cx - 6, arc_cy - 6), (arc_cx + 6, arc_cy + 6)], fill='#FFFFFF')
    # Radial energy ticks
    for angle in range(0, 360, 45):
        rad = math.radians(angle)
        x1 = arc_cx + 26 * math.cos(rad)
        y1 = arc_cy + 26 * math.sin(rad)
        x2 = arc_cx + 34 * math.cos(rad)
        y2 = arc_cy + 34 * math.sin(rad)
        draw.line([(x1, y1), (x2, y2)], fill='#00E5FF', width=2)

    # Arc Reactor Status Tag
    draw.text((arc_cx - 24, jarvis_y + 140), "ARC MK-85", fill='#00E5FF', font=FONT_MONO_XS)

    # Assistant Prompt Text
    draw.text((140, jarvis_y + 24), "J.A.R.V.I.S. INTELLIGENCE CORE // ACTIVE", fill='#00E5FF', font=FONT_MONO_SM)
    draw.text((140, jarvis_y + 48), "Where to, sir?", fill='#FFFFFF', font=font_jarvis_title)
    draw.text((140, jarvis_y + 84), "Select a waypoint below or initiate voice command protocol", fill='#8B949E', font=FONT_TEXT)

    # Hero Action Buttons
    # Voice Button
    draw.rounded_rectangle([(140, jarvis_y + 120), (330, jarvis_y + 165)], radius=6, fill='#101F2D', outline='#00E5FF', width=1)
    draw.text((160, jarvis_y + 134), "🎙️  VOICE ENGAGE", fill='#00E5FF', font=FONT_HEAD)

    # Add Destination Button
    draw.rounded_rectangle([(345, jarvis_y + 120), (510, jarvis_y + 165)], radius=6, fill='#00E5FF')
    draw.text((370, jarvis_y + 134), "+ ADD PLACE", fill='#0A0D12', font=FONT_HEAD)

    # Full Map Quick Launch
    draw.rounded_rectangle([(525, jarvis_y + 120), (W - 40, jarvis_y + 165)], radius=6, fill='#121721', outline='#21262D', width=1)
    draw.text((550, jarvis_y + 134), "🗺️  MAP TAB", fill='#8B949E', font=FONT_HEAD)

    # =========================================================================
    # 4. SAVED DESTINATIONS SECTION (STANDALONE CARDS)
    # =========================================================================
    sec1_y = jarvis_y + jarvis_h + 16
    draw.text((26, sec1_y), "SAVED DESTINATIONS", fill='#00E5FF', font=FONT_HEAD)
    draw.text((200, sec1_y + 2), "•  Direct target waypoints", fill='#8B949E', font=FONT_MONO_SM)

    # Card 1: HOME (With Photo Thumbnail added!)
    card1_y = sec1_y + 24
    draw.rounded_rectangle([(24, card1_y), (W - 24, card1_y + 88)], radius=8, fill='#111622', outline='#21262D', width=1)

    # Photo Thumbnail (Simulating user-added photo)
    draw.rounded_rectangle([(38, card1_y + 12), (102, card1_y + 76)], radius=6, fill='#192A3E', outline='#00E5FF', width=2)
    # Mini photo content: Modern house / driveway silhouette
    draw.polygon([(70, card1_y + 26), (48, card1_y + 44), (92, card1_y + 44)], fill='#00E5FF')
    draw.rectangle([(54, card1_y + 44), (86, card1_y + 68)], fill='#0D1926')
    draw.rectangle([(64, card1_y + 54), (76, card1_y + 68)], fill='#00E5FF')

    # Card 1 Text
    draw.text((118, card1_y + 16), "Home", fill='#FFFFFF', font=font_place_name)
    draw.text((170, card1_y + 18), "•  RESIDENCE", fill='#8B949E', font=FONT_MONO_XS)
    draw.text((118, card1_y + 40), "42 Elm Grove, Brighton, Sussex", fill='#8B949E', font=FONT_TEXT)
    draw.text((118, card1_y + 60), "14.2 km SW  •  50.8312°N, 0.1278°W", fill='#00E676', font=FONT_MONO_SM)

    # Engage Button
    draw.rounded_rectangle([(W - 130, card1_y + 24), (W - 38, card1_y + 64)], radius=6, fill='#00E5FF')
    draw.text((W - 110, card1_y + 36), "ENGAGE", fill='#0A0D12', font=FONT_HEAD)

    # Card 2: WORK (No Photo added -> Strictly renders icon fallback badge!)
    card2_y = card1_y + 98
    draw.rounded_rectangle([(24, card2_y), (W - 24, card2_y + 88)], radius=8, fill='#111622', outline='#21262D', width=1)

    # Icon Fallback Badge (honoring the user rule: "only show photo if one has been added")
    draw.rounded_rectangle([(38, card2_y + 12), (102, card2_y + 76)], radius=6, fill='#17212F', outline='#303E54', width=1)
    draw.text((58, card2_y + 32), "💼", fill='#FFD600', font=font_place_name)

    # Card 2 Text
    draw.text((118, card2_y + 16), "Work", fill='#FFFFFF', font=font_place_name)
    draw.text((165, card2_y + 18), "•  CORPORATE HQ", fill='#8B949E', font=FONT_MONO_XS)
    draw.text((118, card2_y + 40), "Gatwick Aviation Campus, Perimeter Road", fill='#8B949E', font=FONT_TEXT)
    draw.text((118, card2_y + 60), "28.6 km N  •  51.1537°N, 0.1821°W", fill='#00E676', font=FONT_MONO_SM)

    # Engage Button
    draw.rounded_rectangle([(W - 130, card2_y + 24), (W - 38, card2_y + 64)], radius=6, fill='#00E5FF')
    draw.text((W - 110, card2_y + 36), "ENGAGE", fill='#0A0D12', font=FONT_HEAD)

    # =========================================================================
    # 5. PERSON SUB-LISTS (STACKED CARDS) - e.g. "👤 Annabell"
    # =========================================================================
    sec2_y = card2_y + 104
    draw.text((26, sec2_y), "TACTICAL CONTACTS & WAYPOINT CLUSTERS", fill='#00E5FF', font=FONT_HEAD)
    draw.text((360, sec2_y + 2), "•  Stacked sub-lists", fill='#8B949E', font=FONT_MONO_SM)

    # Annabell's Stacked Card Container (with physical card layering shadow)
    stack_y = sec2_y + 26
    
    # Layer 2 (Bottom card shadow offset)
    draw.rounded_rectangle([(36, stack_y + 12), (W - 36, stack_y + 242)], radius=12, fill='#0A0D12', outline='#16202E', width=1)
    # Layer 1 (Middle card shadow offset)
    draw.rounded_rectangle([(30, stack_y + 6), (W - 30, stack_y + 236)], radius=12, fill='#0E1420', outline='#212E40', width=1)
    
    # Front Layer Card
    draw.rounded_rectangle([(24, stack_y), (W - 24, stack_y + 230)], radius=12, fill='#121A28', outline='#00E5FF', width=2)

    # Person Header Bar
    # Contact Avatar Circle
    draw.ellipse([(40, stack_y + 14), (80, stack_y + 54)], fill='#1F2D40', outline='#00E5FF', width=1)
    draw.text((52, stack_y + 22), "👤", fill='#00E5FF', font=font_place_name)

    # Contact Name & Badge
    draw.text((94, stack_y + 16), "Annabell", fill='#FFFFFF', font=font_contact_name)
    draw.rounded_rectangle([(180, stack_y + 16), (360, stack_y + 40)], radius=4, fill='#1E2E44')
    draw.text((190, stack_y + 22), "2 SAVED PLACES • EXPANDED ▼", fill='#00E5FF', font=FONT_MONO_XS)

    # Add sub-destination button
    draw.rounded_rectangle([(W - 130, stack_y + 14), (W - 40, stack_y + 44)], radius=4, fill='#182333', outline='#303E54', width=1)
    draw.text((W - 116, stack_y + 22), "+ ADD", fill='#00E5FF', font=FONT_MONO_SM)

    # Divider line
    draw.line([(40, stack_y + 58), (W - 40, stack_y + 58)], fill='#212E40', width=1)

    # Annabell Sub-Destination 1: Home (With user-added photo thumbnail!)
    sub1_y = stack_y + 68
    draw.rounded_rectangle([(38, sub1_y), (W - 38, sub1_y + 72)], radius=6, fill='#172130', outline='#26354A', width=1)
    
    # Photo Thumbnail
    draw.rounded_rectangle([(48, sub1_y + 8), (104, sub1_y + 64)], radius=4, fill='#203248', outline='#FF4081', width=1)
    draw.polygon([(76, sub1_y + 18), (56, sub1_y + 36), (96, sub1_y + 36)], fill='#FF80AB')
    draw.rectangle([(62, sub1_y + 36), (90, sub1_y + 58)], fill='#121C28')

    draw.text((116, sub1_y + 12), "Home", fill='#FFFFFF', font=font_place_name)
    draw.text((116, sub1_y + 32), "19 King's Road, Hove, East Sussex", fill='#8B949E', font=FONT_TEXT)
    draw.text((116, sub1_y + 50), "8.4 km E  •  50.8242°N, 0.1558°W", fill='#00E676', font=FONT_MONO_SM)

    draw.rounded_rectangle([(W - 128, sub1_y + 18), (W - 50, sub1_y + 54)], radius=4, fill='#00E5FF')
    draw.text((W - 114, sub1_y + 28), "ENGAGE", fill='#0A0D12', font=FONT_HEAD)

    # Annabell Sub-Destination 2: Design Studio (No photo -> Icon badge fallback!)
    sub2_y = sub1_y + 80
    draw.rounded_rectangle([(38, sub2_y), (W - 38, sub2_y + 72)], radius=6, fill='#172130', outline='#26354A', width=1)

    # Icon Fallback Badge
    draw.rounded_rectangle([(48, sub2_y + 8), (104, sub2_y + 64)], radius=4, fill='#1C2638', outline='#303E54', width=1)
    draw.text((66, sub2_y + 26), "🎨", fill='#00E5FF', font=font_place_name)

    draw.text((116, sub2_y + 12), "Design Studio", fill='#FFFFFF', font=font_place_name)
    draw.text((116, sub2_y + 32), "North Laine Creative Hub, Gloucester Rd, Brighton", fill='#8B949E', font=FONT_TEXT)
    draw.text((116, sub2_y + 50), "11.7 km NE  •  50.8279°N, 0.1389°W", fill='#00E676', font=FONT_MONO_SM)

    draw.rounded_rectangle([(W - 128, sub2_y + 18), (W - 50, sub2_y + 54)], radius=4, fill='#00E5FF')
    draw.text((W - 114, sub2_y + 28), "ENGAGE", fill='#0A0D12', font=FONT_HEAD)

    # =========================================================================
    # 6. REAL-TIME SPEED & HEADING TELEMETRY
    # =========================================================================
    tel_y = stack_y + 242
    draw.rounded_rectangle([(24, tel_y), (W - 24, tel_y + 104)], radius=8, fill='#101622', outline='#21262D', width=1)

    # Speed
    draw.text((90, tel_y + 18), "48", fill='#00E5FF', font=FONT_MONO_XL)
    draw.text((70, tel_y + 78), "KM/H  (30 MPH)", fill='#8B949E', font=FONT_MONO_SM)

    # Divider
    draw.line([(W / 2, tel_y + 14), (W / 2, tel_y + 90)], fill='#21262D', width=1)

    # Heading
    draw.text((W / 2 + 70, tel_y + 18), "45°", fill='#FFD600', font=FONT_MONO_XL)
    draw.text((W / 2 + 54, tel_y + 78), "HEADING // NE (BEARING)", fill='#8B949E', font=FONT_MONO_SM)

    # =========================================================================
    # 7. EXPLORATION INTEL & RADAR RECON
    # =========================================================================
    radar_y = tel_y + 116
    draw.rounded_rectangle([(24, radar_y), (W - 24, radar_y + 140)], radius=8, fill='#0B1017', outline='#00E5FF', width=1)
    
    # Left mini radar
    r_cx, r_cy = 84, radar_y + 70
    for rr in (48, 32, 16):
        draw.ellipse([(r_cx - rr, r_cy - rr), (r_cx + rr, r_cy + rr)], outline='#162534', width=1)
    draw.line([(r_cx - 50, r_cy), (r_cx + 50, r_cy)], fill='#162534', width=1)
    draw.line([(r_cx, r_cy - 50), (r_cx, r_cy + 50)], fill='#162534', width=1)
    # Cleared area & vehicle cursor
    draw.ellipse([(r_cx - 24, r_cy - 24), (r_cx + 24, r_cy + 24)], outline='#00E5FF', width=2)
    draw.polygon([(r_cx + 8, r_cy - 8), (r_cx - 4, r_cy - 2), (r_cx - 2, r_cy + 2), (r_cx - 8, r_cy + 8)], fill='#00E5FF')

    # Exploration Intel Numbers
    draw.text((150, radar_y + 16), "TACTICAL EXPLORATION INTEL  •  RADAR SCANNING 24/7", fill='#00E5FF', font=FONT_MONO_SM)
    draw.text((150, radar_y + 44), "5.42 km²", fill='#FFFFFF', font=FONT_MONO_LG)
    draw.text((150, radar_y + 88), "Territory Uncovered", fill='#8B949E', font=FONT_TEXT)

    draw.text((W - 220, radar_y + 44), "4.15 km", fill='#00E676', font=FONT_MONO_LG)
    draw.text((W - 220, radar_y + 88), "Uncharted Streak", fill='#8B949E', font=FONT_TEXT)
    draw.text((150, radar_y + 114), "Surveillance Beam: 50m Default  •  Opacity: 50%", fill='#8B949E', font=FONT_MONO_XS)

    # =========================================================================
    # 8. BOTTOM NAVIGATION BAR
    # =========================================================================
    nav_y = H - 90
    draw.rectangle([(0, nav_y), (W, H)], fill='#0D1117')
    draw.line([(0, nav_y), (W, nav_y)], fill='#21262D', width=1)

    tabs = [
        ("HUD", "#00E5FF", True),
        ("MAP", "#8B949E", False),
        ("SKINS", "#8B949E", False),
        ("OFFLINE", "#8B949E", False),
    ]
    tab_step = W / len(tabs)
    for i, (tname, tcol, is_active) in enumerate(tabs):
        tx_c = i * tab_step + tab_step / 2
        tw = draw.textlength(tname, font=FONT_HEAD)
        draw.text((tx_c - tw / 2, nav_y + 16), tname, fill=tcol, font=FONT_HEAD)
        if is_active:
            draw.line([(tx_c - 16, nav_y + 36), (tx_c + 16, nav_y + 36)], fill='#00E5FF', width=2)

    # Android Home Gesture Bar
    draw.rounded_rectangle([(W / 2 - 60, H - 14), (W / 2 + 60, H - 10)], radius=2, fill='#484F58')

    out_path = os.path.join(OUTPUT_DIR, "screenshot_02_handset_tactical_hud.png")
    img.save(out_path)
    print(f"Saved: {out_path} ({os.path.getsize(out_path)} bytes)")


# ==============================================================================
# 3. SCREENSHOT 03: MAP SKINS CUSTOMIZATION DIALOG (800 x 1100)
# ==============================================================================

def generate_screenshot_03():
    print("Generating Screenshot 03: Map Skins Customization Dialog...")
    W, H = 800, 1100
    img = Image.new('RGB', (W, H), '#0A0D12')
    draw = ImageDraw.Draw(img)

    # Modal Box (x=40, y=60, w=720, h=980)
    mx, my, mw, mh = 40, 60, 720, 980
    draw.rounded_rectangle([(mx, my), (mx + mw, my + mh)], radius=12, fill='#0D1117', outline='#21262D', width=2)

    # Modal Header
    draw.text((mx + 24, my + 24), "CUSTOMIZATION & FOG OPTICS", fill='#00E5FF', font=FONT_HEAD)
    draw.text((mx + 24, my + 48), "Map cartography, fog skins & optics are completely decoupled", fill='#8B949E', font=FONT_TEXT)
    draw.text((mx + mw - 40, my + 24), "✕", fill='#8B949E', font=FONT_HEAD)

    # 3 Balanced Tabs (Width = 680 / 3 = 226px each)
    tab_y = my + 84
    tab_w = (mw - 40) // 3
    draw.rectangle([(mx + 20, tab_y), (mx + mw - 20, tab_y + 42)], fill='#121721')
    
    # Selected Tab: MAP THEMES (Tab 0)
    draw.rectangle([(mx + 20, tab_y), (mx + 20 + tab_w, tab_y + 42)], fill='#1C2128')
    draw.line([(mx + 20, tab_y + 40), (mx + 20 + tab_w, tab_y + 40)], fill='#00E5FF', width=3)
    
    t0 = "MAP THEMES (6)"
    draw.text((mx + 20 + (tab_w - draw.textlength(t0, font=FONT_HEAD)) / 2, tab_y + 14), t0, fill='#00E5FF', font=FONT_HEAD)
    
    t1 = "FOG SKINS (6)"
    draw.text((mx + 20 + tab_w + (tab_w - draw.textlength(t1, font=FONT_HEAD)) / 2, tab_y + 14), t1, fill='#8B949E', font=FONT_HEAD)
    
    t2 = "RANGE & OPACITY"
    draw.text((mx + 20 + 2 * tab_w + (tab_w - draw.textlength(t2, font=FONT_HEAD)) / 2, tab_y + 14), t2, fill='#8B949E', font=FONT_HEAD)

    # List of Map Skins (Full clean descriptions)
    skins = [
        ("Tactical Obsidian", "High-contrast stealth black with electric cyan corridors and deep navy water.", "#0A0D12", "#00E5FF", "#FFD600", "#06131D", True),
        ("Cyberpunk 2077", "Pitch-black void with screaming magenta arteries and electric cyan secondary roads.", "#05050A", "#FF007F", "#FFE600", "#0D0826", False),
        ("Military Topo", "Tactical olive drab terrain, desert tan arteries, and phosphor green waypoints.", "#141A12", "#D4A373", "#70E000", "#0E1C1A", False),
        ("Midnight Monochrome", "Understated minimalist graphite and charcoal tones with pure white active routing.", "#0E0E10", "#828292", "#FFFFFF", "#18181C", False),
        ("Classic OSM Daylight", "Traditional bright paper cartography with soft blue rivers and orange highways.", "#F2EFE9", "#FCA765", "#0066FF", "#AAD3DF", False),
        ("Vintage Cartographer", "18th-century aged parchment canvas with dark walnut engraved linework.", "#EADBBF", "#4A321E", "#9E2A2B", "#9FB7A8", False),
    ]

    sy = tab_y + 60
    for name, desc, bg, hway, route, water, is_sel in skins:
        card_col = '#1C2128' if is_sel else '#121721'
        border_col = '#00E5FF' if is_sel else '#21262D'
        draw.rounded_rectangle([(mx + 20, sy), (mx + mw - 20, sy + 90)], radius=8, fill=card_col, outline=border_col, width=2 if is_sel else 1)

        # Swatch Box
        draw.rounded_rectangle([(mx + 36, sy + 18), (mx + 110, sy + 72)], radius=6, fill=bg, outline='#30363D', width=1)
        draw.ellipse([(mx + 46, sy + 38), (mx + 60, sy + 52)], fill=hway)
        draw.ellipse([(mx + 66, sy + 38), (mx + 80, sy + 52)], fill=route)
        draw.ellipse([(mx + 86, sy + 38), (mx + 100, sy + 52)], fill=water)

        draw.text((mx + 126, sy + 18), name, fill='#00E5FF' if is_sel else '#F0F6FC', font=FONT_HEAD)
        draw.text((mx + 126, sy + 42), desc, fill='#8B949E', font=FONT_TEXT)

        if is_sel:
            draw.text((mx + mw - 60, sy + 32), "✓", fill='#00E5FF', font=FONT_TITLE)

        sy += 104

    # Bottom Active Summary Bar
    draw.rounded_rectangle([(mx + 20, my + mh - 90), (mx + mw - 20, my + mh - 20)], radius=8, fill='#121721', outline='#21262D', width=1)
    draw.text((mx + 36, my + mh - 76), "ACTIVE PAIRING", fill='#8B949E', font=FONT_MONO_XS)
    draw.text((mx + 36, my + mh - 58), "Tactical Obsidian + Tactical Radar Shroud (180m @ 95%)", fill='#F0F6FC', font=FONT_HEAD)

    draw.rounded_rectangle([(mx + mw - 140, my + mh - 76), (mx + mw - 36, my + mh - 34)], radius=6, fill='#00E5FF')
    draw.text((mx + mw - 110, my + mh - 62), "APPLY", fill='#0A0D12', font=FONT_HEAD)

    out_path = os.path.join(OUTPUT_DIR, "screenshot_03_skin_selector_map_themes.png")
    img.save(out_path)
    print(f"Saved: {out_path} ({os.path.getsize(out_path)} bytes)")


# ==============================================================================
# 4. SCREENSHOT 04: FOG RANGE & OPACITY CALIBRATION DIALOG (800 x 1100)
# ==============================================================================

def generate_screenshot_04():
    print("Generating Screenshot 04: Fog Range & Opacity Calibration Dialog...")
    W, H = 800, 1100
    img = Image.new('RGB', (W, H), '#0A0D12')
    draw = ImageDraw.Draw(img)

    # Modal Box
    mx, my, mw, mh = 40, 60, 720, 980
    draw.rounded_rectangle([(mx, my), (mx + mw, my + mh)], radius=12, fill='#0D1117', outline='#21262D', width=2)

    # Modal Header
    draw.text((mx + 24, my + 24), "CUSTOMIZATION & FOG OPTICS", fill='#00E5FF', font=FONT_HEAD)
    draw.text((mx + 24, my + 48), "Calibrate surveillance scan beam radius and shroud darkness", fill='#8B949E', font=FONT_TEXT)
    draw.text((mx + mw - 40, my + 24), "✕", fill='#8B949E', font=FONT_HEAD)

    # 3 Balanced Tabs (Width = 680 / 3 = 226px each)
    tab_y = my + 84
    tab_w = (mw - 40) // 3
    draw.rectangle([(mx + 20, tab_y), (mx + mw - 20, tab_y + 42)], fill='#121721')
    
    t0 = "MAP THEMES (6)"
    draw.text((mx + 20 + (tab_w - draw.textlength(t0, font=FONT_HEAD)) / 2, tab_y + 14), t0, fill='#8B949E', font=FONT_HEAD)
    
    t1 = "FOG SKINS (6)"
    draw.text((mx + 20 + tab_w + (tab_w - draw.textlength(t1, font=FONT_HEAD)) / 2, tab_y + 14), t1, fill='#8B949E', font=FONT_HEAD)

    # Selected Tab: RANGE & OPACITY (Tab 2)
    draw.rectangle([(mx + 20 + 2 * tab_w, tab_y), (mx + mw - 20, tab_y + 42)], fill='#1C2128')
    draw.line([(mx + 20 + 2 * tab_w, tab_y + 40), (mx + mw - 20, tab_y + 40)], fill='#00E5FF', width=3)
    t2 = "RANGE & OPACITY"
    draw.text((mx + 20 + 2 * tab_w + (tab_w - draw.textlength(t2, font=FONT_HEAD)) / 2, tab_y + 14), t2, fill='#00E5FF', font=FONT_HEAD)

    # 1. Live Interactive Optics Mini-Radar
    mini_y = tab_y + 60
    mini_h = 170
    draw.rounded_rectangle([(mx + 20, mini_y), (mx + mw - 20, mini_y + mini_h)], radius=8, fill='#0A0D12', outline='#00E5FF', width=1)
    
    mcx = mx + mw / 2.0
    mcy = mini_y + mini_h / 2.0 - 10
    
    # Faint concentric radar range circles
    for rad in (30, 60, 90):
        draw.ellipse([(mcx - rad, mcy - rad), (mcx + rad, mcy + rad)], outline='#16222F', width=1)
    draw.line([(mcx - 100, mcy), (mcx + 100, mcy)], fill='#16222F', width=1)
    draw.line([(mcx, mcy - 70), (mcx, mcy + 70)], fill='#16222F', width=1)

    # Road line
    draw.line([(mx + 40, mcy + 30), (mx + mw - 40, mcy - 30)], fill='#00A8CC', width=4)
    # Explored track
    draw.line([(mcx - 120, mcy + 40), (mcx, mcy), (mcx + 120, mcy - 40)], fill='#00E676', width=6)

    # Cleared Circle (50m radius scaled)
    mini_r = 48
    for gr in (3, 2, 1):
        draw.ellipse([(mcx - mini_r - gr, mcy - mini_r - gr), (mcx + mini_r + gr, mcy + mini_r + gr)], outline='#00E5FF', width=1)
    draw.ellipse([(mcx - mini_r, mcy - mini_r), (mcx + mini_r, mcy + mini_r)], outline='#00E5FF', width=2)
    
    # Player marker
    draw.polygon([(mcx + 8, mcy - 8), (mcx - 4, mcy - 2), (mcx - 2, mcy + 2), (mcx - 8, mcy + 8)], fill='#00E5FF')

    draw.text((mcx - 140, mini_y + mini_h - 26), "SURVEILLANCE BEAM: 50M  •  OPACITY: 50% DEFAULT", fill='#00E5FF', font=FONT_MONO_SM)

    # 2. Fog Range Slider Card
    card1_y = mini_y + mini_h + 20
    draw.rounded_rectangle([(mx + 20, card1_y), (mx + mw - 20, card1_y + 220)], radius=8, fill='#121721', outline='#21262D', width=1)
    draw.text((mx + 38, card1_y + 20), "FOG OF WAR RANGE (SURVEILLANCE BEAM)", fill='#00E5FF', font=FONT_HEAD)
    draw.text((mx + mw - 160, card1_y + 20), "50 METERS", fill='#F0F6FC', font=FONT_MONO)

    # Slider Track
    sl_y = card1_y + 66
    draw.rectangle([(mx + 38, sl_y), (mx + mw - 38, sl_y + 10)], fill='#21262D')
    # Active track (50m / 500m min position)
    sl_active = (mx + mw - 76) * 0.02
    draw.rectangle([(mx + 38, sl_y), (mx + 38 + sl_active, sl_y + 10)], fill='#00E5FF')
    draw.ellipse([(mx + 38 + sl_active - 10, sl_y - 5), (mx + 38 + sl_active + 10, sl_y + 15)], fill='#00E5FF')

    draw.text((mx + 38, sl_y + 24), "50m (Hardcore)", fill='#8B949E', font=FONT_MONO_XS)
    draw.text((mx + mw - 140, sl_y + 24), "500m (Satellite)", fill='#8B949E', font=FONT_MONO_XS)

    # Preset Buttons
    draw.text((mx + 38, card1_y + 128), "QUICK RANGE PRESETS:", fill='#8B949E', font=FONT_MONO_XS)
    presets = [("50m", True), ("100m", False), ("180m", False), ("260m", False), ("450m", False)]
    px = mx + 38
    for ptxt, is_sel in presets:
        pw = draw.textlength(ptxt, font=FONT_MONO_SM) + 20
        bg_btn = '#00E5FF' if is_sel else '#0D1117'
        fg_btn = '#0A0D12' if is_sel else '#8B949E'
        draw.rounded_rectangle([(px, card1_y + 150), (px + pw, card1_y + 188)], radius=4, fill=bg_btn, outline='#00E5FF' if is_sel else '#21262D', width=1)
        draw.text((px + 10, card1_y + 162), ptxt, fill=fg_btn, font=FONT_MONO_SM)
        px += pw + 8

    # 3. Fog Opacity Slider Card
    card2_y = card1_y + 240
    draw.rounded_rectangle([(mx + 20, card2_y), (mx + mw - 20, card2_y + 220)], radius=8, fill='#121721', outline='#21262D', width=1)
    draw.text((mx + 38, card2_y + 20), "SHROUD OPACITY (DARKNESS & DENSITY)", fill='#00E676', font=FONT_HEAD)
    draw.text((mx + mw - 160, card2_y + 20), "50% DENSITY", fill='#F0F6FC', font=FONT_MONO)

    # Slider Track
    sl2_y = card2_y + 66
    draw.rectangle([(mx + 38, sl2_y), (mx + mw - 38, sl2_y + 10)], fill='#21262D')
    # Active track (50% ~ 38%)
    sl2_active = (mx + mw - 76) * 0.38
    draw.rectangle([(mx + 38, sl2_y), (mx + 38 + sl2_active, sl2_y + 10)], fill='#00E676')
    draw.ellipse([(mx + 38 + sl2_active - 10, sl2_y - 5), (mx + 38 + sl2_active + 10, sl2_y + 15)], fill='#00E676')

    draw.text((mx + 38, sl2_y + 24), "20% (Subtle)", fill='#8B949E', font=FONT_MONO_XS)
    draw.text((mx + mw - 140, sl2_y + 24), "100% (Blackout)", fill='#8B949E', font=FONT_MONO_XS)

    # Presets
    draw.text((mx + 38, card2_y + 128), "QUICK OPACITY PRESETS:", fill='#8B949E', font=FONT_MONO_XS)
    opac_presets = [("30% Mist", False), ("50% Half", True), ("75% Dark", False), ("100% Void", False)]
    px = mx + 38
    for optxt, is_sel in opac_presets:
        pw = draw.textlength(optxt, font=FONT_MONO_SM) + 20
        bg_btn = '#00E676' if is_sel else '#0D1117'
        fg_btn = '#0A0D12' if is_sel else '#8B949E'
        draw.rounded_rectangle([(px, card2_y + 150), (px + pw, card2_y + 188)], radius=4, fill=bg_btn, outline='#00E676' if is_sel else '#21262D', width=1)
        draw.text((px + 10, card2_y + 162), optxt, fill=fg_btn, font=FONT_MONO_SM)
        px += pw + 8

    # Bottom Active Summary Bar
    draw.rounded_rectangle([(mx + 20, my + mh - 90), (mx + mw - 20, my + mh - 20)], radius=8, fill='#121721', outline='#21262D', width=1)
    draw.text((mx + 36, my + mh - 76), "ACTIVE OPTICS PROFILE", fill='#8B949E', font=FONT_MONO_XS)
    draw.text((mx + 36, my + mh - 58), "Surveillance: 50m  •  Density: 50%  •  Dynamic Speed-Scaling: ACTIVE", fill='#F0F6FC', font=FONT_HEAD)

    draw.rounded_rectangle([(mx + mw - 140, my + mh - 76), (mx + mw - 36, my + mh - 34)], radius=6, fill='#00E5FF')
    draw.text((mx + mw - 110, my + mh - 62), "APPLY", fill='#0A0D12', font=FONT_HEAD)

    out_path = os.path.join(OUTPUT_DIR, "screenshot_04_skin_selector_fog_optics.png")
    img.save(out_path)
    print(f"Saved: {out_path} ({os.path.getsize(out_path)} bytes)")


# ==============================================================================
# 5. SCREENSHOT 05: IN-APP GITHUB RELEASES UPDATER (800 x 1100)
# ==============================================================================

def generate_screenshot_05():
    print("Generating Screenshot 05: In-App GitHub Releases Updater...")
    W, H = 800, 1100
    img = Image.new('RGB', (W, H), '#0A0D12')
    draw = ImageDraw.Draw(img)

    # Modal Box
    mx, my, mw, mh = 40, 70, 720, 960
    draw.rounded_rectangle([(mx, my), (mx + mw, my + mh)], radius=12, fill='#0D1117', outline='#21262D', width=2)

    # Modal Header
    draw.text((mx + 28, my + 28), "SYSTEM UPDATER", fill='#00E5FF', font=FONT_HEAD)
    draw.text((mx + 28, my + 52), "Direct OTA channel connected to GitHub Releases API", fill='#8B949E', font=FONT_TEXT)
    draw.text((mx + mw - 40, my + 28), "✕", fill='#8B949E', font=FONT_HEAD)

    # 1. Current Build Status Card
    c1_y = my + 86
    draw.rounded_rectangle([(mx + 24, c1_y), (mx + mw - 24, c1_y + 72)], radius=8, fill='#121721', outline='#21262D', width=1)
    draw.text((mx + 40, c1_y + 16), "INSTALLED BUILD", fill='#8B949E', font=FONT_MONO_XS)
    draw.text((mx + 40, c1_y + 36), "1.0.0-tactical (Build 1)", fill='#F0F6FC', font=FONT_HEAD)

    draw.rounded_rectangle([(mx + mw - 190, c1_y + 20), (mx + mw - 40, c1_y + 52)], radius=4, fill='#0D1117', outline='#00E5FF', width=1)
    draw.text((mx + mw - 176, c1_y + 28), "GITHUB RELEASES", fill='#00E5FF', font=FONT_MONO_XS)

    # 2. Update Available Banner Card (Amber Border)
    c2_y = c1_y + 90
    draw.rounded_rectangle([(mx + 24, c2_y), (mx + mw - 24, c2_y + 110)], radius=8, fill='#121721', outline='#FFD600', width=1)
    draw.text((mx + 40, c2_y + 16), "NEW TACTICAL RELEASE AVAILABLE", fill='#FFD600', font=FONT_HEAD)
    draw.text((mx + mw - 140, c2_y + 16), "v1.0.1 ➔", fill='#00E5FF', font=FONT_HEAD)

    draw.text((mx + 40, c2_y + 42), "ReconDrive v1.0.1 — Tactical In-Vehicle Navigation", fill='#F0F6FC', font=FONT_HEAD)
    draw.text((mx + 40, c2_y + 68), "Asset: ReconDrive-v1.0.1-tactical.apk  •  Size: 46.5 MB  •  Published: Today", fill='#8B949E', font=FONT_TEXT)

    # 3. Live Streaming Download Progress Card
    c3_y = c2_y + 128
    draw.rounded_rectangle([(mx + 24, c3_y), (mx + mw - 24, c3_y + 110)], radius=8, fill='#161B22', outline='#00E5FF', width=1)
    draw.text((mx + 40, c3_y + 16), "DOWNLOADING APK PACKAGE...", fill='#00E5FF', font=FONT_HEAD)
    draw.text((mx + mw - 100, c3_y + 16), "68%", fill='#00E5FF', font=FONT_HEAD)

    # Progress bar
    pb_y = c3_y + 44
    draw.rectangle([(mx + 40, pb_y), (mx + mw - 40, pb_y + 10)], fill='#21262D')
    pb_active = (mw - 80) * 0.68
    draw.rectangle([(mx + 40, pb_y), (mx + 40 + pb_active, pb_y + 10)], fill='#00E5FF')

    draw.text((mx + 40, c3_y + 68), "31.6 MB / 46.5 MB  •  ReconDrive-v1.0.1-tactical.apk", fill='#8B949E', font=FONT_MONO_SM)

    # 4. Release Notes / Changelog Box
    c4_y = c3_y + 128
    draw.text((mx + 28, c4_y), "RELEASE INTEL & CHANGELOG", fill='#8B949E', font=FONT_MONO_XS)
    
    box_y = c4_y + 20
    draw.rounded_rectangle([(mx + 24, box_y), (mx + mw - 24, box_y + 240)], radius=8, fill='#121721', outline='#21262D', width=1)
    
    notes = [
        "• Added In-App GitHub Releases OTA Updater with real-time download streaming.",
        "• Direct integration with Android Package Installer via Secure FileProvider.",
        "• Enhanced Android Auto NavigationTemplate maneuver card padding and alignments.",
        "• Real-world Fog of War spatial persistence optimization with Room DB.",
        "• Decoupled 6 Map Themes and 6 Fog Skins with real-time in-car action cycling.",
        "• Added dynamic speed limits (50 km/h roundel) and lane guidance indicators."
    ]
    ny = box_y + 18
    for line in notes:
        draw.text((mx + 40, ny), line, fill='#F0F6FC' if line.startswith("• Added") else '#8B949E', font=FONT_TEXT)
        ny += 34

    # 5. Bottom Action Buttons
    act_y = box_y + 260
    draw.rounded_rectangle([(mx + 24, act_y), (mx + mw - 24, act_y + 54)], radius=8, fill='#00E5FF')
    txt_inst = "DOWNLOAD & INSTALL UPDATE"
    ti_w = draw.textlength(txt_inst, font=FONT_HEAD)
    draw.text((mx + 24 + (mw - 48 - ti_w) / 2, act_y + 18), txt_inst, fill='#0A0D12', font=FONT_HEAD)

    draw.rounded_rectangle([(mx + 24, act_y + 66), (mx + mw - 24, act_y + 110)], radius=8, fill='#121721', outline='#21262D', width=1)
    txt_gh = "VIEW RELEASE ON GITHUB"
    tg_w = draw.textlength(txt_gh, font=FONT_TEXT)
    draw.text((mx + 24 + (mw - 48 - tg_w) / 2, act_y + 80), txt_gh, fill='#8B949E', font=FONT_TEXT)

    out_path = os.path.join(OUTPUT_DIR, "screenshot_05_in_app_github_updater.png")
    img.save(out_path)
    print(f"Saved: {out_path} ({os.path.getsize(out_path)} bytes)")


# ==============================================================================
# 6. SCREENSHOT 06: INTERACTIVE GOOGLE MAPS-STYLE UK MAP (800 x 1400)
# ==============================================================================

def generate_screenshot_06():
    print("Generating Screenshot 06: Interactive Google Maps-style UK Map with Live OSM Cartography...")
    W, H = 800, 1400
    img = Image.new('RGB', (W, H), '#080C14')
    draw = ImageDraw.Draw(img)

    cx, cy = W / 2.0, H / 2.0

    # 1. Background Vector Landmass & Streamed OSM Street Grid
    # Streamed raster tile background grid texture
    for grid_x in range(0, W, 48):
        draw.line([(grid_x, 0), (grid_x, H)], fill='#0F1622', width=1)
    for grid_y in range(0, H, 48):
        draw.line([(0, grid_y), (W, grid_y)], fill='#0F1622', width=1)

    # Local & Residential Street Grid (High-Contrast Tactical Slate #567498)
    for sx in range(40, W - 40, 48):
        # North-south local streets with dark casing
        draw.line([(sx, 120), (sx, H - 180)], fill='#000000', width=4)
        draw.line([(sx, 120), (sx, H - 180)], fill='#567498', width=2)
    for sy in range(140, H - 200, 48):
        # East-west local streets with dark casing
        draw.line([(20, sy), (W - 20, sy)], fill='#000000', width=4)
        draw.line([(20, sy), (W - 20, sy)], fill='#567498', width=2)

    # Secondary city street blocks (OSM urban building & street density)
    for bx in range(88, W - 88, 48):
        for by in range(180, H - 240, 48):
            if math.hypot(bx - cx, by - cy) < 320:
                draw.rectangle([(bx + 4, by + 4), (bx + 44, by + 44)], fill='#0E1624', outline='#1C2A3E', width=1)

    # 2. Vector Water Features (River Thames meandering with realistic bends & bridges)
    thames_pts = [
        (0, cy + 180),
        (cx - 300, cy + 160),
        (cx - 200, cy + 120),
        (cx - 120, cy + 85),
        (cx - 40, cy + 60),     # Westminster Bridge
        (cx + 40, cy + 70),     # London Bridge
        (cx + 100, cy + 55),    # Tower Bridge
        (cx + 170, cy + 110),   # Isle of Dogs / Canary Wharf loop
        (cx + 220, cy + 70),
        (cx + 310, cy + 120),
        (W, cy + 150)
    ]
    draw.line(thames_pts, fill='#040E18', width=34)
    draw.line(thames_pts, fill='#0A2238', width=18)
    draw.line(thames_pts, fill='#0E3352', width=8)

    # Bridges across Thames
    for b_offset in [(-40, 60), (0, 65), (40, 70), (100, 55)]:
        bx = cx + b_offset[0]
        by = cy + b_offset[1]
        draw.line([(bx - 8, by - 12), (bx + 8, by + 12)], fill='#2B3B52', width=4)

    # 3. Greater London Road Arterials & Circulars
    # A406 North Circular
    nc_pts = [
        (cx - 240, cy + 20),
        (cx - 210, cy - 80),
        (cx - 140, cy - 140),
        (cx, cy - 165),
        (cx + 140, cy - 140),
        (cx + 220, cy - 80),
        (cx + 240, cy + 20)
    ]
    draw.line(nc_pts, fill='#000000', width=8)
    draw.line(nc_pts, fill='#00D4FF', width=4)

    # A205 South Circular
    sc_pts = [
        (cx - 230, cy + 30),
        (cx - 180, cy + 120),
        (cx - 80, cy + 170),
        (cx + 60, cy + 175),
        (cx + 180, cy + 130),
        (cx + 240, cy + 40)
    ]
    draw.line(sc_pts, fill='#000000', width=8)
    draw.line(sc_pts, fill='#00D4FF', width=4)

    # M25 Outer London Orbital Motorway
    draw.ellipse([(cx - 340, cy - 280), (cx + 340, cy + 280)], outline='#000000', width=14)
    draw.ellipse([(cx - 340, cy - 280), (cx + 340, cy + 280)], outline='#38E1FF', width=6)

    # Radial Motorways & Trunks
    # M1 North
    draw.line([(cx - 60, cy - 80), (cx - 140, cy - 260), (cx - 200, 0)], fill='#000000', width=9)
    draw.line([(cx - 60, cy - 80), (cx - 140, cy - 260), (cx - 200, 0)], fill='#38E1FF', width=6)
    # M4 West
    draw.line([(cx - 80, cy + 10), (cx - 280, cy + 20), (0, cy + 30)], fill='#000000', width=9)
    draw.line([(cx - 80, cy + 10), (cx - 280, cy + 20), (0, cy + 30)], fill='#38E1FF', width=6)

    # A-Road Radial Corridors (High-Contrast Electric Cyan #00D4FF)
    a_roads = [
        # A40 Westway
        [(cx - 240, cy - 40), (cx - 80, cy - 10), (cx, cy)],
        # A4 Piccadilly / Knightsbridge
        [(cx - 200, cy + 30), (cx - 60, cy + 15), (cx, cy)],
        # A11 Whitechapel / Stratford
        [(cx, cy), (cx + 120, cy - 30), (cx + 240, cy - 60)],
        # A13 Commercial Rd / Thames Gateway
        [(cx, cy + 20), (cx + 160, cy + 30), (W, cy + 60)],
        # A2 Old Kent Rd & A3 South
        [(cx, cy + 20), (cx + 120, cy + 140), (cx + 240, cy + 260)],
        [(cx, cy + 20), (cx - 80, cy + 140), (cx - 180, cy + 280)]
    ]
    for pts in a_roads:
        draw.line(pts, fill='#000000', width=7)
        draw.line(pts, fill='#00D4FF', width=4)

    # 4. Explored Fog of War Trail & 50m / 50% Active Shroud
    explored_trail = [
        (cx - 180, cy + 140),
        (cx - 80, cy + 70),
        (cx, cy),
        (cx + 80, cy - 60),
        (cx + 140, cy - 140)
    ]
    for gw in (20, 14, 6):
        draw.line(explored_trail, fill='#00E676' if gw == 6 else '#004D25', width=gw)

    # Fog of War 50% Shroud (Mask outside cleared 50m beam)
    beam_r = 95.0
    shroud = Image.new('RGBA', (W, H), (7, 10, 15, 128))  # 50% opacity
    shroud_draw = ImageDraw.Draw(shroud)
    shroud_draw.ellipse([(cx - beam_r, cy - beam_r), (cx + beam_r, cy + beam_r)], fill=(0, 0, 0, 0))
    img.paste(shroud, (0, 0), shroud)

    # Cleared surveillance beam glowing perimeter
    for gr in (3, 2, 1):
        draw.ellipse([(cx - beam_r - gr, cy - beam_r - gr), (cx + beam_r + gr, cy + beam_r + gr)], outline='#00E5FF', width=1)
    draw.ellipse([(cx - beam_r, cy - beam_r), (cx + beam_r, cy + beam_r)], outline='#00E5FF', width=2)
    # Inner pulse ring
    draw.ellipse([(cx - beam_r * 0.9, cy - beam_r * 0.9), (cx + beam_r * 0.9, cy + beam_r * 0.9)], outline='#00E5FF44', width=1)

    # 5. Target Waypoint (Amber destination pin & dashed route line)
    wp_x, wp_y = cx + 180, cy - 200
    draw.line([(cx, cy), (wp_x, wp_y)], fill='#FFD600', width=3)
    draw.ellipse([(wp_x - 14, wp_y - 14), (wp_x + 14, wp_y + 14)], fill='#FFD600')
    draw.ellipse([(wp_x - 24, wp_y - 24), (wp_x + 24, wp_y + 24)], outline='#FFD600', width=2)

    # 6. Vehicle GPS Cursor (Cyan tactical triangle)
    draw.ellipse([(cx - 24, cy - 24), (cx + 24, cy + 24)], fill='#00E5FF22')
    draw.polygon([(cx + 14, cy - 14), (cx - 6, cy - 2), (cx - 2, cy + 2), (cx - 12, cy + 12)], fill='#00E5FF')

    # 7. Settlement & Place Labels on Map
    places = [
        ("London", cx - 8, cy - 24, True),
        ("Westminster", cx - 70, cy + 30, False),
        ("Camden", cx - 50, cy - 90, False),
        ("Stratford", cx + 130, cy - 40, False),
        ("Greenwich", cx + 110, cy + 90, False),
        ("Watford", cx - 180, cy - 240, False),
        ("Dartford", cx + 260, cy + 140, False),
        ("Heathrow", cx - 260, cy + 40, False),
    ]
    for pname, px, py, is_cap in places:
        dot_r = 5 if is_cap else 3
        draw.ellipse([(px - dot_r, py - dot_r), (px + dot_r, py + dot_r)], fill='#FFD600' if is_cap else '#00E5FF')
        draw.text((px + 8, py - 7), pname, fill='#FFFFFF' if is_cap else '#8B949E', font=FONT_HEAD if is_cap else FONT_TEXT_BOLD)

    # Road Shields
    draw.rounded_rectangle([(cx - 320, cy - 10), (cx - 280, cy + 12)], radius=3, fill='#003366', outline='#00E5FF', width=1)
    draw.text((cx - 314, cy - 8), "M25", fill='#FFFFFF', font=FONT_MONO_XS)

    draw.rounded_rectangle([(cx - 150, cy - 170), (cx - 118, cy - 148)], radius=3, fill='#003366', outline='#00E5FF', width=1)
    draw.text((cx - 142, cy - 168), "M1", fill='#FFFFFF', font=FONT_MONO_XS)

    draw.rounded_rectangle([(cx - 10, cy - 185), (cx + 36, cy - 163)], radius=3, fill='#004422', outline='#00E676', width=1)
    draw.text((cx - 4, cy - 183), "A406", fill='#FFFFFF', font=FONT_MONO_XS)

    # 8. Top Floating Search Bar & City Jump Chips
    draw.rounded_rectangle([(24, 48), (W - 24, 96)], radius=8, fill='#121721', outline='#21262D', width=1)
    draw.text((44, 62), "🔍", fill='#00E5FF', font=FONT_SUB)
    draw.text((74, 64), "United Kingdom OSM • Tap map to drop waypoint", fill='#8B949E', font=FONT_TEXT)
    draw.rounded_rectangle([(W - 130, 56), (W - 36, 88)], radius=4, fill='#070A0F', outline='#00E676', width=1)
    draw.text((W - 118, 64), "UK OSM", fill='#00E676', font=FONT_MONO_XS)

    # Top OSM Status Active Indicator Chips
    draw.rounded_rectangle([(24, 106), (360, 134)], radius=12, fill='#121721', outline='#00E5FF', width=1)
    draw.ellipse([(36, 117), (44, 125)], fill='#00E676')
    draw.text((50, 114), "OSM ACTIVE • NO API KEY REQUIRED", fill='#00E5FF', font=FONT_MONO_XS)

    draw.rounded_rectangle([(372, 106), (510, 134)], radius=12, fill='#121721', outline='#FFD600', width=1)
    draw.text((384, 114), "MODE: HYBRID", fill='#FFD600', font=FONT_MONO_XS)

    # Quick Jump Chips
    chips = ["🏛️ London", "🏖️ Brighton", "🐝 Manchester", "🏭 Birmingham", "🏰 Edinburgh", "🐉 Cardiff", "⚓ Belfast"]
    ch_x = 24
    for chip in chips:
        ch_w = draw.textlength(chip, font=FONT_TEXT) + 24
        draw.rounded_rectangle([(ch_x, 144), (ch_x + ch_w, 178)], radius=16, fill='#121721', outline='#21262D', width=1)
        draw.text((ch_x + 12, 154), chip, fill='#F0F6FC' if "London" in chip or "Brighton" in chip else '#8B949E', font=FONT_TEXT)
        ch_x += ch_w + 8

    # 8. Right Floating Tactical Controls (Google Maps-style)
    fab_x = W - 74
    # Compass
    draw.ellipse([(fab_x, 180), (fab_x + 48, 228)], fill='#121721', outline='#21262D', width=1)
    draw.text((fab_x + 16, 194), "🧭", fill='#00E5FF', font=FONT_HEAD)

    # Fog Toggle
    draw.ellipse([(fab_x, 240), (fab_x + 48, 288)], fill='#00E5FF', outline='#00E5FF', width=1)
    draw.text((fab_x + 16, 254), "👁️", fill='#0A0D12', font=FONT_HEAD)

    # Skins
    draw.ellipse([(fab_x, 300), (fab_x + 48, 348)], fill='#121721', outline='#21262D', width=1)
    draw.text((fab_x + 16, 314), "🎨", fill='#00E5FF', font=FONT_HEAD)

    # Zoom (+)
    draw.rounded_rectangle([(fab_x + 4, 360), (fab_x + 44, 400)], radius=6, fill='#121721', outline='#21262D', width=1)
    draw.text((fab_x + 18, 370), "+", fill='#F0F6FC', font=FONT_TITLE)

    # Zoom (−)
    draw.rounded_rectangle([(fab_x + 4, 408), (fab_x + 44, 448)], radius=6, fill='#121721', outline='#21262D', width=1)
    draw.text((fab_x + 20, 416), "−", fill='#F0F6FC', font=FONT_TITLE)

    # Re-Center GPS Target FAB
    draw.ellipse([(fab_x - 6, 470), (fab_x + 54, 530)], fill='#00E5FF', outline='#00E5FF', width=2)
    draw.text((fab_x + 12, 488), "🎯", fill='#0A0D12', font=FONT_TITLE)

    # Telemetry Badge
    card_y = H - 250
    telem_y = card_y - 75
    draw.rounded_rectangle([(24, telem_y), (220, telem_y + 62)], radius=6, fill='#121721EE', outline='#21262D', width=1)
    draw.text((36, telem_y + 8), "Z13.5 • 51.5074°N, 0.1278°W", fill='#00E5FF', font=FONT_MONO_XS)
    draw.text((36, telem_y + 24), "LOCK: GPS VEHICLE", fill='#00E676', font=FONT_MONO_XS)
    draw.text((36, telem_y + 40), "FOG: 50m @ 50%", fill='#00E5FF', font=FONT_MONO_XS)

    # 9. Bottom Destination Card (Waypointed to M1/North)
    draw.rounded_rectangle([(24, card_y), (W - 24, card_y + 130)], radius=12, fill='#121721', outline='#00E5FF', width=1)
    draw.text((44, card_y + 16), "TACTICAL WAYPOINT // M1 CORRIDOR", fill='#FFD600', font=FONT_HEAD)
    draw.text((44, card_y + 40), "Distance: 14.2 km  •  ETA: ~18 min  •  Heading: 352° N", fill='#F0F6FC', font=FONT_MONO_SM)

    # Action Buttons inside card
    draw.rounded_rectangle([(44, card_y + 68), (W / 2 + 100, card_y + 112)], radius=6, fill='#00E5FF')
    draw.text((80, card_y + 80), "NAVIGATE TO TARGET", fill='#0A0D12', font=FONT_HEAD)

    draw.rounded_rectangle([(W / 2 + 116, card_y + 68), (W - 44, card_y + 112)], radius=6, fill='#161B22', outline='#21262D', width=1)
    draw.text((W / 2 + 150, card_y + 80), "CLEAR", fill='#8B949E', font=FONT_HEAD)

    # 10. Bottom Navigation Bar with MAP selected
    nav_y = H - 90
    draw.rectangle([(0, nav_y), (W, H)], fill='#0D1117')
    draw.line([(0, nav_y), (W, nav_y)], fill='#21262D', width=1)
    
    tabs = [
        ("HUD", "#8B949E", False),
        ("MAP", "#00E5FF", True),
        ("SKINS", "#8B949E", False),
        ("OFFLINE", "#8B949E", False),
    ]
    tab_step = W / len(tabs)
    for i, (tname, tcol, is_active) in enumerate(tabs):
        tx_c = i * tab_step + tab_step / 2
        tw = draw.textlength(tname, font=FONT_HEAD)
        draw.text((tx_c - tw / 2, nav_y + 16), tname, fill=tcol, font=FONT_HEAD)
        if is_active:
            draw.line([(tx_c - 16, nav_y + 36), (tx_c + 16, nav_y + 36)], fill='#00E5FF', width=2)

    # Android Home Gesture Bar
    draw.rounded_rectangle([(W / 2 - 60, H - 14), (W / 2 + 60, H - 10)], radius=2, fill='#484F58')

    out_path = os.path.join(OUTPUT_DIR, "screenshot_06_interactive_uk_map.png")
    img.save(out_path)
    print(f"Saved: {out_path} ({os.path.getsize(out_path)} bytes)")


# ==============================================================================
# 7. SCREENSHOT 07: 24/7 PERSISTENT NOTIFICATION & BACKGROUND TRACKING (800 x 1400)
# ==============================================================================

def generate_screenshot_07():
    print("Generating Screenshot 07: 24/7 Background Notification & Service...")
    W, H = 800, 1400
    img = Image.new('RGB', (W, H), '#0A0D12')
    draw = ImageDraw.Draw(img)

    # 1. Android Status Bar
    draw.rectangle([(0, 0), (W, 40)], fill='#0D1117')
    draw.text((24, 12), "10:42", fill='#F0F6FC', font=FONT_HEAD)
    draw.text((W - 140, 12), "5G  92% 🔋", fill='#8B949E', font=FONT_SUB)

    # 2. Top Header with 24/7 Radar Active Badge
    draw.text((24, 60), "RECON // TACTICAL RADAR", fill='#00E5FF', font=FONT_HEAD)
    draw.text((24, 82), "GRID: 51.5074°N, 0.1278°W  •  LONDON SECTOR", fill='#8B949E', font=FONT_MONO_SM)

    # Badges Row
    badge_x = W - 24 - 110
    draw.rounded_rectangle([(badge_x, 60), (badge_x + 110, 92)], radius=6, fill='#121721', outline='#00E676', width=1)
    draw.text((badge_x + 12, 70), "🛰️ RADAR 24/7", fill='#00E676', font=FONT_MONO_SM)

    badge_x2 = badge_x - 110
    draw.rounded_rectangle([(badge_x2, 60), (badge_x2 + 100, 92)], radius=6, fill='#121721', outline='#00E676', width=1)
    draw.text((badge_x2 + 10, 70), "🚗 AUTO ON", fill='#00E676', font=FONT_MONO_SM)

    # 3. ONGOING PERSISTENT NOTIFICATION CARD (Highlight Feature)
    notif_y = 120
    notif_h = 190
    draw.rounded_rectangle([(16, notif_y), (W - 16, notif_y + notif_h)], radius=14, fill='#161B22', outline='#00E5FF', width=2)

    # Notification Header line
    draw.text((36, notif_y + 16), "🧭 ReconDrive • Ongoing Exploration Service", fill='#8B949E', font=FONT_MONO_SM)
    draw.text((W - 90, notif_y + 16), "Active", fill='#00E676', font=FONT_MONO_SM)

    # Notification Title
    draw.text((36, notif_y + 44), "🛰️ ReconDrive Tactical Radar Active", fill='#F0F6FC', font=FONT_TITLE)

    # Notification Body
    draw.text((36, notif_y + 78), "Explored: 42.50 km²  •  Streak: 14.8 km  •  Speed: 68 km/h", fill='#00E5FF', font=FONT_MONO)
    draw.text((36, notif_y + 106), "24/7 BACKGROUND SURVEILLANCE • Fog unmasked continuously while app is closed", fill='#8B949E', font=FONT_MONO_SM)

    # Notification Action Buttons
    btn_y = notif_y + 136
    draw.rounded_rectangle([(36, btn_y), (180, btn_y + 38)], radius=6, fill='#00E5FF')
    draw.text((56, btn_y + 10), "OPEN RADAR", fill='#0A0D12', font=FONT_HEAD)

    draw.rounded_rectangle([(196, btn_y), (340, btn_y + 38)], radius=6, fill='#21262D', outline='#30363D', width=1)
    draw.text((220, btn_y + 10), "PAUSE RADAR", fill='#F0F6FC', font=FONT_HEAD)

    # 4. Telemetry Dual Gauges (Speed & Heading)
    gauge_y = notif_y + notif_h + 24
    draw.rounded_rectangle([(16, gauge_y), (W - 16, gauge_y + 120)], radius=12, fill='#121721', outline='#21262D', width=1)

    # Speed
    draw.text((W / 4 - 30, gauge_y + 18), "68", fill='#00E5FF', font=FONT_MONO_XL)
    draw.text((W / 4 - 40, gauge_y + 85), "KM/H (42 MPH)", fill='#8B949E', font=FONT_MONO_SM)

    # Divider
    draw.line([(W / 2, gauge_y + 20), (W / 2, gauge_y + 100)], fill='#21262D', width=2)

    # Heading
    draw.text((3 * W / 4 - 40, gauge_y + 18), "352°", fill='#00E676', font=FONT_MONO_XL)
    draw.text((3 * W / 4 - 45, gauge_y + 85), "HEADING // N-NW", fill='#8B949E', font=FONT_MONO_SM)

    # 5. Radar Mini Canvas
    radar_y = gauge_y + 144
    radar_h = 420
    draw.rounded_rectangle([(16, radar_y), (W - 16, radar_y + radar_h)], radius=12, fill='#070A0F', outline='#00E5FF', width=1)

    rcx, rcy = W / 2, radar_y + radar_h / 2
    for r in [60, 130, 200]:
        draw.ellipse([(rcx - r, rcy - r), (rcx + r, rcy + r)], outline='#00E5FF22', width=1)

    draw.line([(rcx - 20, rcy), (rcx + 20, rcy)], fill='#00E5FF44', width=2)
    draw.line([(rcx, rcy - 20), (rcx, rcy + 20)], fill='#00E5FF44', width=2)

    # Explored road trail
    trail = [(rcx - 120, rcy + 160), (rcx - 60, rcy + 80), (rcx, rcy), (rcx + 80, rcy - 120)]
    for i in range(len(trail) - 1):
        draw.line([trail[i], trail[i+1]], fill='#00E676', width=5)

    # Vehicle cursor
    draw.polygon([(rcx, rcy - 16), (rcx + 12, rcy + 14), (rcx, rcy + 8), (rcx - 12, rcy + 14)], fill='#00E5FF')

    draw.text((36, radar_y + 20), "RADAR: ACTIVE TELEMETRY INGESTION", fill='#00E676', font=FONT_MONO_SM)
    draw.text((W - 220, radar_y + 20), "BACKGROUND RECON: ON", fill='#00E5FF', font=FONT_MONO_SM)

    # 6. Exploration Career Intel Card
    intel_y = radar_y + radar_h + 20
    draw.rounded_rectangle([(16, intel_y), (W - 16, intel_y + 180)], radius=12, fill='#121721', outline='#21262D', width=1)

    draw.text((36, intel_y + 16), "EXPLORATION INTEL", fill='#8B949E', font=FONT_HEAD)
    draw.text((W - 160, intel_y + 16), "TACTICAL SCOUT", fill='#00E5FF', font=FONT_HEAD)

    draw.text((36, intel_y + 50), "42.50 km²", fill='#F0F6FC', font=FONT_MONO_LG)
    draw.text((36, intel_y + 98), "Territory Uncovered", fill='#8B949E', font=FONT_SUB)

    draw.text((W - 200, intel_y + 50), "14.80 km", fill='#00E676', font=FONT_MONO_LG)
    draw.text((W - 200, intel_y + 98), "Uncharted Streak", fill='#8B949E', font=FONT_SUB)

    # Divider and Background Notification status
    draw.line([(36, intel_y + 130), (W - 36, intel_y + 130)], fill='#21262D', width=1)
    draw.text((36, intel_y + 146), "🔔 24/7 Fog Unmasking (Notification Active)", fill='#00E5FF', font=FONT_MONO_SM)
    draw.text((W - 110, intel_y + 146), "ACTIVE", fill='#00E676', font=FONT_HEAD)

    # 7. Bottom Navigation Bar
    nav_y = H - 90
    draw.rectangle([(0, nav_y), (W, H)], fill='#0D1117')
    draw.line([(0, nav_y), (W, nav_y)], fill='#21262D', width=1)
    
    tabs = [
        ("HUD", "#00E5FF", True),
        ("MAP", "#8B949E", False),
        ("SKINS", "#8B949E", False),
        ("OFFLINE", "#8B949E", False),
    ]
    tab_step = W / len(tabs)
    for i, (tname, tcol, is_active) in enumerate(tabs):
        tx_c = i * tab_step + tab_step / 2
        tw = draw.textlength(tname, font=FONT_HEAD)
        draw.text((tx_c - tw / 2, nav_y + 16), tname, fill=tcol, font=FONT_HEAD)
        if is_active:
            draw.line([(tx_c - 16, nav_y + 36), (tx_c + 16, nav_y + 36)], fill='#00E5FF', width=2)

    # Android Home Gesture Bar
    draw.rounded_rectangle([(W / 2 - 60, H - 14), (W / 2 + 60, H - 10)], radius=2, fill='#484F58')

    out_path = os.path.join(OUTPUT_DIR, "screenshot_07_background_notification_service.png")
    img.save(out_path)
    print(f"Saved: {out_path} ({os.path.getsize(out_path)} bytes)")


# ==============================================================================
# 8. SCREENSHOT 08: STARTUP NETWORK & UPDATE SYNC DIALOG (800 x 1200)
# ==============================================================================

def generate_screenshot_08():
    print("Generating Screenshot 08: Startup Update & Sync Dialog...")
    W, H = 800, 1200
    img = Image.new('RGB', (W, H), '#080B10')
    draw = ImageDraw.Draw(img)

    # 1. Dimmed Background Tactical Map & HUD
    draw.rectangle([(0, 0), (W, 40)], fill='#05070A')
    draw.text((24, 12), "10:45", fill='#484F58', font=FONT_HEAD)
    draw.text((W - 140, 12), "5G  94% 🔋", fill='#30363D', font=FONT_SUB)

    # Background roads (dimmed)
    draw.line([(0, 200), (W, 500)], fill='#121721', width=4)
    draw.line([(100, 0), (700, H)], fill='#121721', width=3)
    draw.line([(0, 700), (W, 300)], fill='#161B22', width=6)

    # 2. Centered Modal Dialog
    dw, dh = 720, 920
    dx, dy = (W - dw) // 2, (H - dh) // 2

    # Modal shadow / glow
    draw.rounded_rectangle([(dx - 4, dy - 4), (dx + dw + 4, dy + dh + 4)], radius=18, fill='#00E5FF11')
    draw.rounded_rectangle([(dx, dy), (dx + dw, dy + dh)], radius=16, fill='#0D1117', outline='#00E5FF', width=2)

    # Dialog Header
    draw.text((dx + 28, dy + 28), "TACTICAL SYSTEM SYNC", fill='#00E5FF', font=FONT_TITLE)
    draw.text((dx + 28, dy + 58), "Network Online • Auto-Audited Releases & Vector Datasets", fill='#8B949E', font=FONT_MONO_SM)

    # Close Icon
    draw.text((dx + dw - 48, dy + 28), "✕", fill='#8B949E', font=FONT_TITLE)

    # -------------------------------------------------------------------------
    # CARD 1: UK OPENSTREETMAP DATA UPDATE
    # -------------------------------------------------------------------------
    c1_y = dy + 100
    c1_h = 330
    draw.rounded_rectangle([(dx + 24, c1_y), (dx + dw - 24, c1_y + c1_h)], radius=12, fill='#121721', outline='#00E676', width=1)

    # Card 1 Badge
    draw.text((dx + 44, c1_y + 18), "🗺️ UK OPENSTREETMAP DATA", fill='#00E676', font=FONT_HEAD)
    draw.rounded_rectangle([(dx + dw - 170, c1_y + 16), (dx + dw - 44, c1_y + 40)], radius=4, fill='#00E67622', outline='#00E676', width=1)
    draw.text((dx + dw - 160, c1_y + 22), "2026.11.07-UK", fill='#00E676', font=FONT_MONO_XS)

    # Card 1 Title & Description
    draw.text((dx + 44, c1_y + 54), "United Kingdom Complete Vector Extract", fill='#F0F6FC', font=FONT_HEAD)
    draw.text((dx + 44, c1_y + 78), "Comprehensive road network geometries, motorways (M1, M25, M6, M4),", fill='#8B949E', font=FONT_TEXT)
    draw.text((dx + 44, c1_y + 98), "A-road bypasses, roundabout updates, and speed limits across UK bounds.", fill='#8B949E', font=FONT_TEXT)

    # Specs
    draw.text((dx + 44, c1_y + 130), "Installed: 2026.10.15-uk-base  •  Available: 2026.11.07-uk-osm", fill='#F0F6FC', font=FONT_MONO_SM)
    draw.text((dx + 44, c1_y + 154), "Download Size: 1,650.0 MB (Full UK Regional Pack)", fill='#00E5FF', font=FONT_MONO_SM)

    # Progress bar preview
    draw.rounded_rectangle([(dx + 44, c1_y + 190), (dx + dw - 44, c1_y + 200)], radius=5, fill='#21262D')
    draw.rounded_rectangle([(dx + 44, c1_y + 190), (dx + 44 + int((dw - 88) * 0.42), c1_y + 200)], radius=5, fill='#00E676')
    draw.text((dx + 44, c1_y + 210), "Unpacking vector tiles: 42% (693.0 MB / 1,650.0 MB)", fill='#00E676', font=FONT_MONO_XS)

    # Action Buttons
    btn1_y = c1_y + 250
    draw.rounded_rectangle([(dx + 44, btn1_y), (dx + dw - 160, btn1_y + 54)], radius=8, fill='#00E676')
    draw.text((dx + 120, btn1_y + 16), "UPDATE MAP DATA (1.65 GB)", fill='#0A0D12', font=FONT_HEAD)

    draw.rounded_rectangle([(dx + dw - 144, btn1_y), (dx + dw - 44, btn1_y + 54)], radius=8, fill='#161B22', outline='#30363D', width=1)
    draw.text((dx + dw - 110, btn1_y + 18), "SKIP", fill='#8B949E', font=FONT_HEAD)

    # -------------------------------------------------------------------------
    # CARD 2: APPLICATION BUILD UPDATE
    # -------------------------------------------------------------------------
    c2_y = c1_y + c1_h + 20
    c2_h = 310
    draw.rounded_rectangle([(dx + 24, c2_y), (dx + dw - 24, c2_y + c2_h)], radius=12, fill='#121721', outline='#FFD600', width=1)

    # Card 2 Badge
    draw.text((dx + 44, c2_y + 18), "⚡ APPLICATION UPDATE", fill='#FFD600', font=FONT_HEAD)
    draw.rounded_rectangle([(dx + dw - 150, c2_y + 16), (dx + dw - 44, c2_y + 40)], radius=4, fill='#FFD60022', outline='#FFD600', width=1)
    draw.text((dx + dw - 138, c2_y + 22), "BUILD v1.0.2", fill='#FFD600', font=FONT_MONO_XS)

    # Card 2 Title & Description
    draw.text((dx + 44, c2_y + 54), "ReconDrive v1.0.2 — 24/7 Background Radar Service", fill='#F0F6FC', font=FONT_HEAD)
    draw.text((dx + 44, c2_y + 78), "Adds persistent foreground location service, live territory notification,", fill='#8B949E', font=FONT_TEXT)
    draw.text((dx + 44, c2_y + 98), "Android 14 compatibility, and device boot auto-restart exploration.", fill='#8B949E', font=FONT_TEXT)

    # Specs
    draw.text((dx + 44, c2_y + 130), "Installed: v1.0.1  •  Available: v1.0.2  (Size: 46.7 MB)", fill='#F0F6FC', font=FONT_MONO_SM)
    draw.text((dx + 44, c2_y + 154), "Source: GitHub Official Releases (CharlesLennon/recondrive)", fill='#00E5FF', font=FONT_MONO_SM)

    # Action Buttons
    btn2_y = c2_y + 200
    draw.rounded_rectangle([(dx + 44, btn2_y), (dx + dw - 160, btn2_y + 54)], radius=8, fill='#FFD600')
    draw.text((dx + 130, btn2_y + 16), "UPDATE APPLICATION (46.7 MB)", fill='#0A0D12', font=FONT_HEAD)

    draw.rounded_rectangle([(dx + dw - 144, btn2_y), (dx + dw - 44, btn2_y + 54)], radius=8, fill='#161B22', outline='#30363D', width=1)
    draw.text((dx + dw - 110, btn2_y + 18), "SKIP", fill='#8B949E', font=FONT_HEAD)

    # -------------------------------------------------------------------------
    # BOTTOM ACTION: PROCEED / SKIP ALL
    # -------------------------------------------------------------------------
    bot_y = c2_y + c2_h + 20
    draw.rounded_rectangle([(dx + 24, bot_y), (dx + dw - 24, bot_y + 50)], radius=8, fill='#161B22', outline='#30363D', width=1)
    draw.text((dx + 160, bot_y + 16), "PROCEED TO RADAR (SKIP ALL FOR NOW)", fill='#8B949E', font=FONT_HEAD)

    out_path = os.path.join(OUTPUT_DIR, "screenshot_08_startup_update_sync_dialog.png")
    img.save(out_path)
    print(f"Saved: {out_path} ({os.path.getsize(out_path)} bytes)")


if __name__ == '__main__':
    generate_screenshot_01()
    generate_screenshot_02()
    generate_screenshot_03()
    generate_screenshot_04()
    generate_screenshot_05()
    generate_screenshot_06()
    generate_screenshot_07()
    generate_screenshot_08()
    print("All 8 screenshots generated successfully!")
